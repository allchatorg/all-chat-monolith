package com.mk3.chatapp.services;

import com.mk3.chatapp.models.AttachmentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AttachmentTypeService {
    AttachmentType validateAndResolveAttachmentType(MultipartFile file);

    void validateTagsBelongToAttachmentType(Long id, List<Long> tags);

    List<AttachmentType> findAll();
}
