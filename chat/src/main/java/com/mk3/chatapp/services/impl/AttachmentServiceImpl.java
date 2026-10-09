package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.TagDTO;
import com.mk3.chatapp.enums.MimeType;
import com.mk3.chatapp.exceptions.AccountLimitExceededException;
import com.mk3.chatapp.mappers.AttachmentMapper;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.AttachmentType;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.Tag;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.AttachmentRepository;
import com.mk3.chatapp.services.AttachmentService;
import com.mk3.chatapp.services.AttachmentTypeService;
import com.mk3.chatapp.services.FileUploadService;
import com.mk3.chatapp.services.TagService;
import com.mk3.chatapp.utils.AccountLimits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final AttachmentTypeService attachmentTypeService;

    private final TagService tagService;
    private final FileUploadService fileUploadService;

    private final AttachmentMapper attachmentMapper;

    @Override
    public AttachmentDTO uploadAttachment(MultipartFile file, User user) {
        AttachmentType attachmentType = attachmentTypeService.validateAndResolveAttachmentType(file, user);
        validateUploadUsage(user, file.getSize());
        File convertedFile = convertMultipartFileToFile(file);
        try {
            String fileUrl = fileUploadService.uploadFile(convertedFile);

            Attachment attachment = Attachment.builder()
                    .url(fileUrl)
                    .name(file.getOriginalFilename())
                    .size(file.getSize())
                    .attachmentType(attachmentType)
                    .mime(MimeType.fromMime(file.getContentType()))
                    .build();

            Attachment savedAttachment = attachmentRepository.save(attachment);
            return attachmentMapper.toDto(savedAttachment);
        } finally {
            deleteTemporaryFile(convertedFile);
        }
    }

    @Transactional
    @Override
    public void deleteAttachment(Long attachmentId) {
        Attachment attachment = findById(attachmentId);
        attachmentRepository.delete(attachment);
        fileUploadService.deleteFile(attachment.getUrl());
    }

    @Override
    public List<Attachment> saveAttachments(List<AttachmentDTO> attachments, Message message) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }

        return attachments.stream()
                .map(attachmentDTO -> save(attachmentDTO, message))
                .toList();
    }

    public Attachment findById(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new IllegalArgumentException("Attachment not found with id: " + attachmentId));
    }

    @Override
    public void softDeleteAttachments(List<Long> attachmentsIds) {
        var attachments = attachmentRepository.findAllById(attachmentsIds);

        attachments.forEach(attachment -> {
            attachment.setDeleted(true);
        });

        attachmentRepository.saveAll(attachments);
    }

    private File convertMultipartFileToFile(MultipartFile file) {
        File tempFile = null;
        try {
            String originalName = Objects.requireNonNull(file.getOriginalFilename());
            int dotIndex = originalName.lastIndexOf('.');
            String prefix = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName;
            String suffix = dotIndex > 0 ? originalName.substring(dotIndex) : "";

            tempFile = File.createTempFile("upload-" + prefix + "-", suffix);
            file.transferTo(tempFile);
            return tempFile;
        } catch (Exception e) {
            if (tempFile != null) {
                deleteTemporaryFile(tempFile);
            }
            throw new RuntimeException("Failed to convert MultipartFile to File: " + e.getMessage(), e);
        }
    }

    private void deleteTemporaryFile(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            log.warn("Could not delete temporary upload file {}", file, e);
            file.deleteOnExit();
        }
    }

    private Attachment save(AttachmentDTO attachmentDTO, Message message) {
        if (attachmentDTO == null) {
            throw new IllegalArgumentException("Attachment cannot be null");
        }

        List<Long> tagIds = attachmentDTO.tags() == null ? List.of()
                : attachmentDTO.tags().stream()
                .map(TagDTO::id)
                .filter(Objects::nonNull)
                .toList();

        if (!tagIds.isEmpty()) {
            attachmentTypeService.validateTagsBelongToAttachmentType(
                    attachmentDTO.attachmentType().id(),
                    tagIds);
        }

        Set<Tag> tags = tagIds.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(tagService.findByIds(tagIds));

        Attachment attachment = findById(attachmentDTO.id());
        attachment.setTags(tags);
        attachment.setMessage(message);

        return attachmentRepository.save(attachment);
    }

    private void validateUploadUsage(User user, long newFileSize) {
        if (user.getRole().isStaffMember()) {
            return;
        }

        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        long totalUploadedFilesSize = attachmentRepository.getTotalUploadedFilesSizeSince(user.getId().toString(),
                since);
        long maxAllowedSize = AccountLimits.hourlyUploadBytes(user);

        if (totalUploadedFilesSize + newFileSize > maxAllowedSize) {
            double usedMB = totalUploadedFilesSize / (1024.0 * 1024.0);
            double limitMB = maxAllowedSize / (1024.0 * 1024.0);

            throw new AccountLimitExceededException(AccountLimitExceededException.Code.HOURLY_UPLOAD_BYTES,
                    String.format(
                            "Uploading this file exceeds your hourly upload limit of %.2f MB (you've already used %.2f MB).",
                            limitMB,
                            usedMB), maxAllowedSize, totalUploadedFilesSize, newFileSize,
                    user.isVipActive() ? null : AccountLimits.VIP_HOURLY_UPLOAD_BYTES);
        }
    }

    @Override
    @Transactional
    public void deleteAllByUserId(String userId) {
        List<Attachment> attachments = attachmentRepository.findAllByCreatedBy(userId);

        for (Attachment attachment : attachments) {
            try {
                fileUploadService.deleteFile(attachment.getUrl());
            } catch (Exception e) {
                // Log and continue
            }
        }

        attachmentRepository.deleteAll(attachments);
    }
}
