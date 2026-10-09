package com.mk3.chatapp.vip;

import com.stripe.net.RequestOptions;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Component
@Getter
public class VipConfiguration {
    @Bean(name = "vipReconciliationTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler vipReconciliationTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("vip-billing-");
        return scheduler;
    }

    @Bean(name = "vipReportingTaskScheduler", defaultCandidate = false)
    public ThreadPoolTaskScheduler vipReportingTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("vip-reporting-");
        return scheduler;
    }

    @Bean(name = "vipReportingWebhookExecutor", defaultCandidate = false)
    public ThreadPoolTaskExecutor vipReportingWebhookExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("vip-reporting-webhook-");
        // Keep AbortPolicy: saturation must never run financial work on the webhook thread.
        return executor;
    }

    @Value("${app.vip.enabled:false}")
    private boolean enabled;
    @Value("${app.vip.live-enabled:false}")
    private boolean liveEnabled;
    @Value("${app.vip.yearly-billing-enabled:false}")
    private boolean yearlyBillingEnabled;
    @Value("${stripe.api-key:}")
    private String apiKey;
    @Value("${stripe.vip.webhook-secret:}")
    private String webhookSecret;
    @Value("${stripe.vip.monthly-price-id:}")
    private String monthlyPriceId;
    @Value("${stripe.vip.yearly-price-id:}")
    private String yearlyPriceId;
    @Value("${stripe.vip.billing-portal-configuration-id:}")
    private String billingPortalConfigurationId;
    @Value("${stripe.vip.switch-portal-configuration-id:}")
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
        return enabled && hasApiKey() && (!live || liveEnabled)
                && hasValue(webhookSecret) && hasValue(monthlyPriceId)
                && (!yearlyBillingEnabled || hasValue(yearlyPriceId));
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
        return frontendUrl.replaceAll("/+$", "") + "/";
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
