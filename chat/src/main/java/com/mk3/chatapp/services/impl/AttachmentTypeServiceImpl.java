package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.enums.AttachmentTypeEnum;
import com.mk3.chatapp.enums.MimeType;
import com.mk3.chatapp.exceptions.AccountLimitExceededException;
import com.mk3.chatapp.models.AttachmentType;
import com.mk3.chatapp.models.Tag;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.AttachmentTypeRepository;
import com.mk3.chatapp.services.AttachmentTypeService;
import com.mk3.chatapp.utils.AccountLimits;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AttachmentTypeServiceImpl implements AttachmentTypeService {

    private final AttachmentTypeRepository attachmentTypeRepository;

    @Override
    public AttachmentType validateAndResolveAttachmentType(MultipartFile file, User user) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        AttachmentType attachmentType = getAttachmentType(file);

        long maxFileSizeBytes = AccountLimits.attachmentBytes(user, attachmentType);
        if (file.getSize() > maxFileSizeBytes) {
            throw new AccountLimitExceededException(AccountLimitExceededException.Code.ATTACHMENT_BYTES,
                    "File size exceeds the maximum allowed size of " + maxFileSizeBytes + " bytes",
                    maxFileSizeBytes, 0, file.getSize(), user.isProActive() ? null : AccountLimits.PRO_FILE_BYTES);
        }

        return attachmentType;
    }

    @Override
    public void validateTagsBelongToAttachmentType(Long id, List<Long> tagsIds) {
        AttachmentType attachmentType = findById(id);
        List<Long> attachmentTypeIds = attachmentType.getAvailableTags().stream()
                .map(Tag::getId)
                .toList();

        for (Long tagId : tagsIds) {
            if (!attachmentTypeIds.contains(tagId)) {
                throw new IllegalArgumentException("Tag with id " + tagId + " does not belong to attachment type with id " + id);
            }
        }
    }

    @Override
    public List<AttachmentType> findAll() {
        return attachmentTypeRepository.findAll();
    }

    private AttachmentType findById(Long id) {
        return attachmentTypeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Attachment type not found with id: " + id));
    }

    private AttachmentType getAttachmentType(MultipartFile file) {
        String contentType = file.getContentType();

        if (contentType == null) {
            throw new IllegalArgumentException("Content type cannot be null");
        }

        MimeType mimeType = MimeType.fromMime(contentType);

        if (mimeType.equals(MimeType.UNKNOWN)) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }

        AttachmentTypeEnum attachmentType = mimeType.getAttachmentTypeEnum();

        if (attachmentType.equals(AttachmentTypeEnum.UNKNOWN)) {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }

        return attachmentTypeRepository.findByFileType(attachmentType)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type: " + attachmentType));
    }
}
