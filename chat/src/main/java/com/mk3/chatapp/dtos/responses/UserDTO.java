package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.TimeFormat;

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
        Boolean subscribedToMarketingEmails,
        Boolean appliedForModerator,
        Role role,
        Long totalUploadUsage,
        String displayColor,
        List<TagDTO> blurredContentTags,
        TimeFormat timeFormatSetting,
        List<UserMinimalDTO> blockedUsers) {
}
