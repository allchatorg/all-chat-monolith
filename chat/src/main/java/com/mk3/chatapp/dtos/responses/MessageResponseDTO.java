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
        boolean senderProBadgeVisible,
        long senderProBadgeRevision,
        FontPreset senderUsernameFont,
        FontPreset senderMessageFont,
        long senderFontRevision,
        String stickerId
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
                              boolean senderProBadgeVisible, long senderProBadgeRevision) {
        this(id, content, chatRoomId, chatRoomName, senderId, senderUsername, senderRole, senderCountryCode,
                senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt, color, attachments,
                reactions, replyTo, promotion, senderProBadgeVisible, senderProBadgeRevision,
                FontPreset.DEFAULT, FontPreset.DEFAULT, 0L, null);
    }

    public MessageResponseDTO(Long id, String content, Long chatRoomId, String chatRoomName,
                              Long senderId, String senderUsername, Role senderRole, String senderCountryCode,
                              IdVerificationStatus senderIdVerificationStatus, boolean bannedUser, boolean deleted,
                              String createdAt, String editedAt, String color, List<AttachmentDTO> attachments,
                              List<ReactionSummaryDTO> reactions, ReplyInfoDTO replyTo, PromotionInfoDTO promotion,
                              boolean senderProBadgeVisible, long senderProBadgeRevision,
                              FontPreset senderUsernameFont, FontPreset senderMessageFont, long senderFontRevision) {
        this(id, content, chatRoomId, chatRoomName, senderId, senderUsername, senderRole, senderCountryCode,
                senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt, color, attachments,
                reactions, replyTo, promotion, senderProBadgeVisible, senderProBadgeRevision,
                senderUsernameFont, senderMessageFont, senderFontRevision, null);
    }

    public MessageResponseDTO(Long id, String content, Long chatRoomId, String chatRoomName,
                              Long senderId, String senderUsername, Role senderRole, String senderCountryCode,
                              IdVerificationStatus senderIdVerificationStatus, boolean bannedUser, boolean deleted,
                              String createdAt, String editedAt, String color, List<AttachmentDTO> attachments,
                              List<ReactionSummaryDTO> reactions, ReplyInfoDTO replyTo, PromotionInfoDTO promotion,
                              boolean senderProBadgeVisible, long senderProBadgeRevision, String stickerId) {
        this(id, content, chatRoomId, chatRoomName, senderId, senderUsername, senderRole, senderCountryCode,
                senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt, color, attachments,
                reactions, replyTo, promotion, senderProBadgeVisible, senderProBadgeRevision,
                FontPreset.DEFAULT, FontPreset.DEFAULT, 0L, stickerId);
    }

    public MessageResponseDTO withReplyTo(ReplyInfoDTO replyTo) {
        return new MessageResponseDTO(id, content, chatRoomId, chatRoomName, senderId, senderUsername,
                senderRole, senderCountryCode, senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt,
                color, attachments, reactions, replyTo, promotion, senderProBadgeVisible, senderProBadgeRevision,
                senderUsernameFont, senderMessageFont, senderFontRevision, stickerId);
    }

    public MessageResponseDTO withPromotion(PromotionInfoDTO promotion) {
        return new MessageResponseDTO(id, content, chatRoomId, chatRoomName, senderId, senderUsername,
                senderRole, senderCountryCode, senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt,
                color, attachments, reactions, replyTo, promotion, senderProBadgeVisible, senderProBadgeRevision,
                senderUsernameFont, senderMessageFont, senderFontRevision, stickerId);
    }

    public MessageResponseDTO withStickerId(String stickerId) {
        return new MessageResponseDTO(id, content, chatRoomId, chatRoomName, senderId, senderUsername,
                senderRole, senderCountryCode, senderIdVerificationStatus, bannedUser, deleted, createdAt, editedAt,
                color, attachments, reactions, replyTo, promotion, senderProBadgeVisible, senderProBadgeRevision,
                senderUsernameFont, senderMessageFont, senderFontRevision, stickerId);
    }
}
