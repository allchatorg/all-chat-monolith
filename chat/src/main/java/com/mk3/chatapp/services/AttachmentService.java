package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentService {
    AttachmentDTO uploadAttachment(MultipartFile file, User user);

    void deleteAttachment(Long attachmentId);

    List<Attachment> saveAttachments(List<AttachmentDTO> attachments, Message message);

    Attachment findById(Long attachmentId);

    void softDeleteAttachments(List<Long> attachmentsIds);

    void deleteAllByUserId(String userId);
}
