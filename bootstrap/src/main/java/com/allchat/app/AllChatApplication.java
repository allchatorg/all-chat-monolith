package com.allchat.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

/**
 * Single entry point for the merged modular monolith.
 *
 * <p>Component scanning, entity scanning and repository scanning are declared explicitly because the
 * three source roots ({@code com.allchat}, {@code com.mk3.chatapp}, {@code com.example.adsportalbe})
 * have no shared prefix shorter than {@code com}. All cross-cutting infrastructure annotations that
 * previously lived on the two separate application classes are consolidated here.
 */
@SpringBootApplication(scanBasePackages = {"com.allchat", "com.mk3.chatapp", "com.example.adsportalbe"})
@EnableJpaRepositories(basePackages = {
        "com.mk3.chatapp.repositories",
        "com.example.adsportalbe.repositories"
})
@EntityScan(basePackages = {
        "com.mk3.chatapp.models",
        "com.example.adsportalbe.models"
})
@EnableJpaAuditing
@EnableScheduling
@EnableRedisIndexedHttpSession(maxInactiveIntervalInSeconds = 60 * 60 * 48)
@EnableRedisRepositories
public class AllChatApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AllChatApplication.class);
        // The chat and ads modules contain many @Service/@Component/@Mapper classes with identical
        // simple names (UserServiceImpl, MailServiceImpl, FileUploadServiceImpl, UserMapperImpl, ...).
        // Naming scanned beans by fully-qualified class name keeps them from colliding.
        app.setBeanNameGenerator(new FullyQualifiedAnnotationBeanNameGenerator());
        app.run(args);
    }
}
