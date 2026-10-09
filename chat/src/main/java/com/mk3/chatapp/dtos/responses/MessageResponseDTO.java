package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.enums.FontPreset;

import java.util.List;

public record MessageResponseDTO(
        Long id,
        String content,
        Long chatRoomId,
        String chatRoomName,
        Long senderId,
        String senderUsername,
        Role senderRole,
        String senderCountryCode,
        IdVerificationStatus senderIdVerificationStatus,
        boolean bannedUser,
        boolean deleted,
        String createdAt,
        String editedAt,
        String color,
        List<AttachmentDTO> attachments,
        List<ReactionSummaryDTO> reactions,
        ReplyInfoDTO replyTo,
        PromotionInfoDTO promotion,
        boolean senderVipBadgeVisible,
        long senderVipBadgeRevision,
        FontPreset senderUsernameFont,
        FontPreset senderMessageFont,
        long senderFontRevision,
        String stickerId,
        boolean chatRoomVipOnly
) {
    /** Compatibility for existing message producers; absent badge metadata is hidden. */
    public MessageResponseDTO(Long id, String content, Long chatRoomId, String chatRoomName,
                              Long senderId, String senderUsername, Role senderRole, String senderCountryCode,
                              IdVerificationStatus senderIdVerificationStatus, boolean bannedUser, boolean deleted,
                              String createdAt, String editedAt, String color, List<AttachmentDTO> attachments,
                              List<ReactionSummaryDTO> reactions, ReplyInfoDTO replyTo, PromotionInfoDTO promotion) {
        this(id, content, chatRoomId, chatRoomName, senderId, senderUsername, senderRole, senderCountryCode,
                senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt, color, attachments,
                reactions, replyTo, promotion, false, 0L);
    }

    public MessageResponseDTO(Long id, String content, Long chatRoomId, String chatRoomName,
                              Long senderId, String senderUsername, Role senderRole, String senderCountryCode,
                              IdVerificationStatus senderIdVerificationStatus, boolean bannedUser, boolean deleted,
                              String createdAt, String editedAt, String color, List<AttachmentDTO> attachments,
                              List<ReactionSummaryDTO> reactions, ReplyInfoDTO replyTo, PromotionInfoDTO promotion,
                              boolean senderVipBadgeVisible, long senderVipBadgeRevision) {
        this(id, content, chatRoomId, chatRoomName, senderId, senderUsername, senderRole, senderCountryCode,
                senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt, color, attachments,
                reactions, replyTo, promotion, senderVipBadgeVisible, senderVipBadgeRevision,
                FontPreset.DEFAULT, FontPreset.DEFAULT, 0L, null, false);
    }

    public MessageResponseDTO(Long id, String content, Long chatRoomId, String chatRoomName,
                              Long senderId, String senderUsername, Role senderRole, String senderCountryCode,
                              IdVerificationStatus senderIdVerificationStatus, boolean bannedUser, boolean deleted,
                              String createdAt, String editedAt, String color, List<AttachmentDTO> attachments,
                              List<ReactionSummaryDTO> reactions, ReplyInfoDTO replyTo, PromotionInfoDTO promotion,
                              boolean senderVipBadgeVisible, long senderVipBadgeRevision,
                              FontPreset senderUsernameFont, FontPreset senderMessageFont, long senderFontRevision) {
        this(id, content, chatRoomId, chatRoomName, senderId, senderUsername, senderRole, senderCountryCode,
                senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt, color, attachments,
                reactions, replyTo, promotion, senderVipBadgeVisible, senderVipBadgeRevision,
                senderUsernameFont, senderMessageFont, senderFontRevision, null, false);
    }

    public MessageResponseDTO(Long id, String content, Long chatRoomId, String chatRoomName,
                              Long senderId, String senderUsername, Role senderRole, String senderCountryCode,
                              IdVerificationStatus senderIdVerificationStatus, boolean bannedUser, boolean deleted,
                              String createdAt, String editedAt, String color, List<AttachmentDTO> attachments,
                              List<ReactionSummaryDTO> reactions, ReplyInfoDTO replyTo, PromotionInfoDTO promotion,
                              boolean senderVipBadgeVisible, long senderVipBadgeRevision, String stickerId) {
        this(id, content, chatRoomId, chatRoomName, senderId, senderUsername, senderRole, senderCountryCode,
                senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt, color, attachments,
                reactions, replyTo, promotion, senderVipBadgeVisible, senderVipBadgeRevision,
                FontPreset.DEFAULT, FontPreset.DEFAULT, 0L, stickerId, false);
    }

    public MessageResponseDTO withReplyTo(ReplyInfoDTO replyTo) {
        return new MessageResponseDTO(id, content, chatRoomId, chatRoomName, senderId, senderUsername,
                senderRole, senderCountryCode, senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt,
                color, attachments, reactions, replyTo, promotion, senderVipBadgeVisible, senderVipBadgeRevision,
                senderUsernameFont, senderMessageFont, senderFontRevision, stickerId, chatRoomVipOnly);
    }

    public MessageResponseDTO withPromotion(PromotionInfoDTO promotion) {
        return new MessageResponseDTO(id, content, chatRoomId, chatRoomName, senderId, senderUsername,
                senderRole, senderCountryCode, senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt,
                color, attachments, reactions, replyTo, promotion, senderVipBadgeVisible, senderVipBadgeRevision,
                senderUsernameFont, senderMessageFont, senderFontRevision, stickerId, chatRoomVipOnly);
    }

    public MessageResponseDTO withStickerId(String stickerId) {
        return new MessageResponseDTO(id, content, chatRoomId, chatRoomName, senderId, senderUsername,
                senderRole, senderCountryCode, senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt,
                color, attachments, reactions, replyTo, promotion, senderVipBadgeVisible, senderVipBadgeRevision,
                senderUsernameFont, senderMessageFont, senderFontRevision, stickerId, chatRoomVipOnly);
    }
}
