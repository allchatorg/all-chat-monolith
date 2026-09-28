package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.requests.UpdateFontSettingsRequest;
import com.mk3.chatapp.enums.FontPreset;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in: uses only a disposable localhost database named font_test, never application configuration. */
@EnabledIfSystemProperty(named = "font.test.database.url", matches = "jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/font_test")
class ProFontPostgresTest {
    private static AnnotationConfigApplicationContext context;
    private static ProFontService fonts;
    private static UserRepository users;
    private static TransactionTemplate transactions;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void openDatabase() {
        context = new AnnotationConfigApplicationContext(DatabaseConfiguration.class);
        fonts = context.getBean(ProFontService.class);
        users = context.getBean(UserRepository.class);
        transactions = context.getBean(TransactionTemplate.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        // User has read-only formulas over the sibling ads module's table.
        jdbc.execute("create table if not exists ads (id bigint primary key, owner_id bigint, total_cost numeric)");
    }

    @AfterAll
    static void closeDatabase() {
        if (context != null) context.close();
    }

    @Test
    void twoSimultaneousFinalSavesCommitExactlyOneChange() throws Exception {
        long userId = createUser();
        for (int index = 0; index < 4; index++) {
            fonts.updateSettings(userId, request(index % 2 == 0 ? FontPreset.INTER : FontPreset.DEFAULT));
        }
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> saveTogether(userId, FontPreset.INTER, ready, start));
            var second = executor.submit(() -> saveTogether(userId, FontPreset.OPEN_SANS, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 429);
        }
        var result = fonts.getSettings(userId);
        assertThat(result.fontRevision()).isEqualTo(5);
        assertThat(result.changesRemaining()).isZero();
        assertThat(jdbc.queryForObject("select font_changes_count from chat_user where id = ?", Integer.class, userId)).isEqualTo(5);
    }

    @Test
    void detachedOrdinaryUserSaveCannotOverwriteFontsRevisionOrQuota() {
        long userId = createUser();
        User stale = transactions.execute(status -> users.findById(userId).orElseThrow());
        fonts.updateSettings(userId, request(FontPreset.OPEN_SANS));
        stale.setUsername("renamed_" + UUID.randomUUID());
        transactions.executeWithoutResult(status -> users.saveAndFlush(stale));
        var result = fonts.getSettings(userId);
        assertThat(result.usernameFont()).isEqualTo(FontPreset.OPEN_SANS);
        assertThat(result.fontRevision()).isEqualTo(1);
        assertThat(result.changesRemaining()).isEqualTo(4);
    }

    @Test
    void repositorySweepFindsHiddenBadgeCustomFontsAndResetPreservesAllowance() {
        long userId = createUser();
        fonts.updateSettings(userId, request(FontPreset.INTER));
        jdbc.update("update chat_user set pro_paid_through = current_timestamp - interval '1 minute' where id = ?", userId);
        var candidates = transactions.execute(status -> users.findExpiredProBadgeUserIds(Instant.now(), 0L, PageRequest.of(0, 200)));
        assertThat(candidates).contains(userId);
        context.getBean(ProBadgeService.class).expireBadge(userId);
        assertThat(jdbc.queryForObject("select username_font from chat_user where id = ?", String.class, userId)).isEqualTo("DEFAULT");
        assertThat(fonts.getSettings(userId).fontRevision()).isEqualTo(2);
        assertThat(fonts.getSettings(userId).changesRemaining()).isEqualTo(4);
    }

    private static int saveTogether(long userId, FontPreset preset, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent test did not start");
        try {
            fonts.updateSettings(userId, request(preset));
            return 200;
        } catch (ResponseStatusException failure) {
            return failure.getStatusCode().value();
        }
    }

    private static long createUser() {
        return transactions.execute(status -> {
            var user = new User();
            user.setUsername("font_test_" + UUID.randomUUID());
            user.setRole(Role.USER);
            user.setShowProBadge(false);
            user.setProPaidThrough(Instant.now().plusSeconds(3600));
            return users.saveAndFlush(user).getId();
        });
    }

    private static UpdateFontSettingsRequest request(FontPreset usernameFont) {
        return new UpdateFontSettingsRequest(usernameFont, FontPreset.DEFAULT);
    }

    @Configuration
    @EnableTransactionManagement
    static class DatabaseConfiguration {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(System.getProperty("font.test.database.url") + "?connectTimeout=5&socketTimeout=20",
                    "postgres", "font_test");
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.mk3.chatapp.models");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop", "hibernate.jdbc.time_zone", "UTC",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
                    "hibernate.hbm2ddl.halt_on_error", "true"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        TransactionTemplate transactions(JpaTransactionManager manager) {
            return new TransactionTemplate(manager);
        }

        @Bean
        UserRepository users(EntityManagerFactory factory) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(UserRepository.class);
        }

        @Bean
        ProFontService fonts(UserRepository users, EntityManagerFactory factory) {
            return new ProFontService(users, SharedEntityManagerCreator.createSharedEntityManager(factory));
        }

        @Bean
        ProBadgeService badges(UserRepository users, ApplicationEventPublisher events, EntityManagerFactory factory, ProFontService fonts) {
            return new ProBadgeService(users, events, SharedEntityManagerCreator.createSharedEntityManager(factory), fonts);
        }
    }
}
