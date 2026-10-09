package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.TimeFormat;
import com.mk3.chatapp.enums.FontPreset;

import java.util.List;

public record UserDTO(
        Long id,
        String username,
        String email,
        String phoneNumber,
        String phoneNumberVerificationDate,
        String profilePictureUrl,
        Boolean isOver18,
        Boolean claimed,
        Boolean banned,
        Boolean verified,
        IdVerificationStatus idVerificationStatus,
        Boolean idVerificationUnderAge,
        Boolean subscribedToMarketingEmails,
        Boolean appliedForModerator,
        Role role,
        Long purchasedAdsCount,
        Long totalUploadUsage,
        String displayColor,
        List<TagDTO> blurredContentTags,
        TimeFormat timeFormatSetting,
        String timeZone,
        List<UserMinimalDTO> blockedUsers,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Boolean vipActive,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Boolean showVipBadge,
        boolean vipBadgeVisible,
        long vipBadgeRevision,
        FontPreset usernameFont,
        FontPreset messageFont,
        long fontRevision) {
}
