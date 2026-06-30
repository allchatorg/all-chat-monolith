package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.AttachmentTypeDTO;
import com.mk3.chatapp.models.AttachmentType;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = TagMapper.class)
public interface AttachmentTypeMapper {
    AttachmentTypeDTO toDto(AttachmentType attachmentType);

    AttachmentType toEntity(AttachmentTypeDTO attachmentTypeDTO);
}