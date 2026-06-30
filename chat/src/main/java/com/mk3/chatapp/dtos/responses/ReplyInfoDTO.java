package com.mk3.chatapp.dtos.responses;

public record ReplyInfoDTO(
        Long id,
        Long senderId,
        String senderUsername,
        String color,
        String content,
        boolean deleted,
        boolean hasAttachment,
        String attachmentName
) {
}
