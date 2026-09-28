package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.FontPreset;

public record ReplyInfoDTO(
        Long id,
        Long senderId,
        String senderUsername,
        String color,
        String content,
        boolean deleted,
        boolean hasAttachment,
        String attachmentName,
        boolean senderProBadgeVisible,
        long senderProBadgeRevision,
        FontPreset senderUsernameFont,
        FontPreset senderMessageFont,
        long senderFontRevision,
        String stickerId
) {
    public ReplyInfoDTO(Long id, Long senderId, String senderUsername, String color, String content,
                        boolean deleted, boolean hasAttachment, String attachmentName,
                        boolean senderProBadgeVisible, long senderProBadgeRevision) {
        this(id, senderId, senderUsername, color, content, deleted, hasAttachment, attachmentName,
                senderProBadgeVisible, senderProBadgeRevision, FontPreset.DEFAULT, FontPreset.DEFAULT, 0L, null);
    }
}
