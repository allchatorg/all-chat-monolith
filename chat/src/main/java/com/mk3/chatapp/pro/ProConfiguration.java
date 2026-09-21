package com.mk3.chatapp.pro;

import com.stripe.net.RequestOptions;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Component
@Getter
public class ProConfiguration {
    private final Environment environment;

    public ProConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean(name = "proReconciliationTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler proReconciliationTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("pro-billing-");
        return scheduler;
    }

    @Value("${app.pro.enabled:false}")
    private boolean enabled;
    @Value("${app.pro.live-enabled:false}")
    private boolean liveEnabled;
    @Value("${app.pro.yearly-billing-enabled:false}")
    private boolean yearlyBillingEnabled;
    @Value("${spring.jpa.hibernate.ddl-auto:}")
    private String schemaPolicy;
    @Value("${stripe.api-key:}")
    private String apiKey;
    @Value("${stripe.pro.webhook-secret:}")
    private String webhookSecret;
    @Value("${stripe.pro.monthly-price-id:}")
    private String monthlyPriceId;
    @Value("${stripe.pro.yearly-price-id:}")
    private String yearlyPriceId;
    @Value("${stripe.pro.billing-portal-configuration-id:}")
    private String billingPortalConfigurationId;
    @Value("${stripe.pro.switch-portal-configuration-id:}")
    private String switchPortalConfigurationId;
    @Value("${app.FRONT_END.URL:http://localhost:3000}")
    private String frontendUrl;

    public boolean hasApiKey() {
        return hasValue(apiKey) && !apiKey.contains("dummy")
                && (apiKey.startsWith("sk_test_") || apiKey.startsWith("rk_test_")
                || apiKey.startsWith("sk_live_") || apiKey.startsWith("rk_live_"));
    }

    public boolean billingAvailable() {
        boolean live = hasValue(apiKey) && (apiKey.startsWith("sk_live_") || apiKey.startsWith("rk_live_"));
        boolean persistent = java.util.Set.of("validate", "none", "update").contains(schemaPolicy);
        // Disposable schemas are allowed only for development with Stripe test keys.
        boolean disposableDevelopment = !live && environment.acceptsProfiles(Profiles.of("dev & !prod"));
        return enabled && hasApiKey() && (persistent || disposableDevelopment) && (!live || liveEnabled)
                && hasValue(webhookSecret) && hasValue(monthlyPriceId)
                && hasValue(billingPortalConfigurationId)
                && (!yearlyBillingEnabled || (hasValue(yearlyPriceId) && hasValue(switchPortalConfigurationId)));
    }

    public String priceId(String interval) {
        return "YEARLY".equals(interval) ? yearlyPriceId : monthlyPriceId;
    }

    public String intervalForPrice(String priceId) {
        if (hasValue(monthlyPriceId) && monthlyPriceId.equals(priceId)) return "MONTHLY";
        if (hasValue(yearlyPriceId) && yearlyPriceId.equals(priceId)) return "YEARLY";
        return null;
    }

    public String returnUrl() {
        return frontendUrl.replaceAll("/+$", "") + "/settings/subscriptions";
    }

    public RequestOptions requestOptions() {
        return requestOptions(null);
    }

    public RequestOptions requestOptions(String idempotencyKey) {
        var builder = RequestOptions.builder().setApiKey(apiKey)
                .setConnectTimeout(10_000).setReadTimeout(20_000).setMaxNetworkRetries(1);
        if (idempotencyKey != null) builder.setIdempotencyKey(idempotencyKey);
        return builder.build();
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
