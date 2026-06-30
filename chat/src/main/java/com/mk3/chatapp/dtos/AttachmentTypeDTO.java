package com.mk3.chatapp.dtos;

import com.mk3.chatapp.enums.AttachmentTypeEnum;
import com.mk3.chatapp.enums.MimeType;

import java.util.Set;

public record AttachmentTypeDTO(
        Long id,
        AttachmentTypeEnum fileType,
        Set<MimeType> acceptedMimeTypes,
        Long maxFileSizeBytes,
        Set<TagDTO> availableTags
) {
}