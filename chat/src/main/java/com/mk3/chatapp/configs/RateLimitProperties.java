package com.mk3.chatapp.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private final Sensitive sensitive = new Sensitive();
    private final Controllers controllers = new Controllers();
    private long globalPerIpPerHour = 12_000;
    private long apiFallbackPerHour = 2_000;

    @Getter
    @Setter
    public static class Sensitive {
        private long messagesPerMinute = 30;
        private long registerPerIpNormalPerHour = 50;
        private long registerPerIpFlaggedPerHour = 20;
        private long loginPerIpTenMinutes = 80;
        private long forgotPasswordPerIpPerHour = 12;
        private long phonePasswordResetVerifyPerIpPerHour = 30;
        private long resetPasswordPerIpPerHour = 30;
        private long claimAccountPerIpPerHour = 50;
        private long emailVerificationPerUserPerHour = 5;
        private long emailVerificationPerIpPerHour = 20;
        private long requestEmailUpdatePerUserPerHour = 5;
        private long requestEmailUpdatePerIpPerHour = 20;
        private long changeUsernamePerUserPerDay = 5;
        private long phoneVerificationPerIpNormalPerHour = 10;
        private long phoneVerificationPerIpFlaggedPerHour = 5;
        private long adsServePerHour = 20;
        private long roomMessagesPerMinute = 240;
        private long topReactedMessagesPerMinute = 120;
        private long roomHeartbeatPerMinute = 120;
        private long roomActiveSwitchPerMinute = 60;
        private long privateChatCreatePerMinute = 10;
        private long banAppealSubmitPerUserPerDay = 5;
    }

    @Getter
    @Setter
    public static class Controllers {
        private long authPerHour = 1_000;
        private long usersPerHour = 1_500;
        private long chattingPerHour = 1_800;
        private long chatRoomsPerHour = 3_000;
        private long adsPerHour = 600;
        private long settingsPerHour = 1_000;
        private long reportCasesPerHour = 600;
        private long adminPerHour = 600;
        private long privateChatsPerHour = 1_200;
        private long banAppealsPerHour = 300;
    }
}
