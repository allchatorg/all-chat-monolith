package com.mk3.chatapp.dtos;

import com.mk3.chatapp.enums.MimeType;

import java.util.Set;

public record AttachmentDTO(
        Long id,
        Long messageId,
        String name,
        Long size,
        String url,
        AttachmentTypeDTO attachmentType,
        MimeType mime,
        Set<TagDTO> tags
) {
}
