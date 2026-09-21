package com.mk3.chatapp.dtos.responses;

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
        String stickerId
) {
    public ReplyInfoDTO(Long id, Long senderId, String senderUsername, String color, String content,
                        boolean deleted, boolean hasAttachment, String attachmentName,
                        boolean senderProBadgeVisible, long senderProBadgeRevision) {
        this(id, senderId, senderUsername, color, content, deleted, hasAttachment, attachmentName,
                senderProBadgeVisible, senderProBadgeRevision, null);
    }
}
