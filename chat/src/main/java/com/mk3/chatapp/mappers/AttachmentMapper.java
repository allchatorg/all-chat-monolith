package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.services.FileUploadService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = {AttachmentTypeMapper.class, TagMapper.class})
public abstract class AttachmentMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    @Mapping(source = "message.id", target = "messageId")
    @Mapping(target = "url", expression = "java(fileUploadService.getFileUrl(attachment.getUrl()))")
    public abstract AttachmentDTO toDto(Attachment attachment);

    @Mapping(source = "messageId", target = "message.id")
    public abstract Attachment toEntity(AttachmentDTO attachmentDTO);
}
