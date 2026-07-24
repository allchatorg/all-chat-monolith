package com.mk3.chatapp.mappers.impl;

import com.mk3.chatapp.dtos.responses.*;
import com.mk3.chatapp.enums.AuditLogActorType;
import com.mk3.chatapp.enums.AuditLogType;
import com.mk3.chatapp.mappers.AuditLogCustomMapper;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.mappers.UserMapper;
import com.mk3.chatapp.models.*;
import com.mk3.chatapp.services.MessagesService;
import com.mk3.chatapp.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogCustomMapperImpl implements AuditLogCustomMapper {
    private static final String SYSTEM_AUDITOR = "system";

    private final UserService userService;
    private final UserMapper userMapper;
    private final MessagesService messagesService;
    private final MessageMapper messageMapper;

    @Override
    public AuditLogDTO toAuditLogDTO(AuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }

        AuditLogActorType createdByType = resolveCreatedByType(auditLog);
        UserDTO createdBy = resolveCreatedBy(auditLog, createdByType);

        AuditLogType type = auditLog.getLogType();

        switch (type) {
            case BAN -> {
                BanAuditLog banLog = (BanAuditLog) auditLog;
                return new BanAuditLogDTO(
                        banLog.getId(),
                        banLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        banLog.getAction(),
                        banLog.getDescription(),
                        banLog.getLogType(),
                        userMapper.toDto(userService.findById(banLog.getTargetUserId())),
                        banLog.getBanType(),
                        banLog.getReportType(),
                        banLog.getBanDurationSeconds(),
                        banLog.getDeleteMessages(),
                        banLog.getDeleteMessagesDurationSeconds());
            }

            case REVOKE_BAN -> {
                RevokeBanAuditLog revokeLog = (RevokeBanAuditLog) auditLog;
                return new RevokeBanAuditLogDTO(
                        revokeLog.getId(),
                        revokeLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        revokeLog.getAction(),
                        revokeLog.getDescription(),
                        revokeLog.getLogType(),
                        userMapper.toDto(userService.findById(revokeLog.getTargetUserId())));
            }

            case BAN_APPEAL_RESOLVE -> {
                BanAppealAuditLog appealLog = (BanAppealAuditLog) auditLog;
                return new BanAppealAuditLogDTO(
                        appealLog.getId(),
                        appealLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        appealLog.getAction(),
                        appealLog.getDescription(),
                        appealLog.getLogType(),
                        userMapper.toDto(userService.findById(appealLog.getTargetUserId())),
                        appealLog.getAppealId(),
                        appealLog.getBanId(),
                        appealLog.getDecision());
            }

            case WARNING -> {
                WarningAuditLog warningLog = (WarningAuditLog) auditLog;
                return new WarningAuditLogDTO(
                        warningLog.getId(),
                        warningLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        warningLog.getAction(),
                        warningLog.getDescription(),
                        warningLog.getLogType(),
                        userMapper.toDto(userService.findById(warningLog.getTargetUserId())));
            }

            case MESSAGE_DELETE -> {
                MessageDeleteAuditLog messageDeleteLog = (MessageDeleteAuditLog) auditLog;
                return new MessageDeleteAuditLogDTO(
                        auditLog.getId(),
                        auditLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        auditLog.getAction(),
                        auditLog.getDescription(),
                        auditLog.getLogType(),
                        userMapper.toDto(userService.findById(auditLog.getTargetUserId())),
                        resolveDeletedMessage(messageDeleteLog.getDeletedMessageId()));
            }

            case RESOLVE_CASE -> {
                ResolveCaseAuditLog resolveLog = (ResolveCaseAuditLog) auditLog;
                return new ResolveCaseAuditLogDTO(
                        resolveLog.getId(),
                        resolveLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        resolveLog.getAction(),
                        resolveLog.getDescription(),
                        resolveLog.getLogType(),
                        resolveLog.getTargetUserId() != null
                                ? userMapper.toDto(userService.findById(resolveLog.getTargetUserId()))
                                : null,
                        resolveLog.getReportCaseId());
            }

            case PROMOTE_ROLE -> {
                PromoteRoleAuditLog promoteLog = (PromoteRoleAuditLog) auditLog;
                return new RoleChangeAuditLogDTO(
                        promoteLog.getId(),
                        promoteLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        promoteLog.getAction(),
                        promoteLog.getDescription(),
                        promoteLog.getLogType(),
                        userMapper.toDto(userService.findById(promoteLog.getTargetUserId())),
                        promoteLog.getPreviousRole(),
                        promoteLog.getNewRole());
            }

            case DEMOTE_ROLE -> {
                DemoteRoleAuditLog demoteLog = (DemoteRoleAuditLog) auditLog;
                return new RoleChangeAuditLogDTO(
                        demoteLog.getId(),
                        demoteLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        demoteLog.getAction(),
                        demoteLog.getDescription(),
                        demoteLog.getLogType(),
                        userMapper.toDto(userService.findById(demoteLog.getTargetUserId())),
                        demoteLog.getPreviousRole(),
                        demoteLog.getNewRole());
            }

            case NCMEC_REPORT -> {
                com.mk3.chatapp.models.ncmec.NcmecReportAuditLog ncmecLog = (com.mk3.chatapp.models.ncmec.NcmecReportAuditLog) auditLog;
                return new NcmecReportAuditLogDTO(
                        ncmecLog.getId(),
                        ncmecLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        ncmecLog.getAction(),
                        ncmecLog.getDescription(),
                        ncmecLog.getLogType(),
                        ncmecLog.getTargetUserId() != null
                                ? userMapper.toDto(userService.findById(ncmecLog.getTargetUserId()))
                                : null,
                        ncmecLog.getNcmecReportId(),
                        ncmecLog.getReportCaseId(),
                        ncmecLog.getXmlContent(),
                        ncmecLog.getStatus());
            }

            case ARCHIVE_CHATROOM -> {
                ArchiveChatRoomAuditLog archiveLog = (ArchiveChatRoomAuditLog) auditLog;
                return new ChatRoomAuditLogDTO(
                        archiveLog.getId(),
                        archiveLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        archiveLog.getAction(),
                        archiveLog.getDescription(),
                        archiveLog.getLogType(),
                        null,
                        archiveLog.getChatRoomId(),
                        archiveLog.getChatRoomName()
                );
            }

            case UNARCHIVE_CHATROOM -> {
                UnarchiveChatRoomAuditLog unarchiveLog = (UnarchiveChatRoomAuditLog) auditLog;
                return new ChatRoomAuditLogDTO(
                        unarchiveLog.getId(),
                        unarchiveLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        unarchiveLog.getAction(),
                        unarchiveLog.getDescription(),
                        unarchiveLog.getLogType(),
                        null,
                        unarchiveLog.getChatRoomId(),
                        unarchiveLog.getChatRoomName()
                );
            }

            case REQUIRE_ID_VERIFICATION -> {
                RequireIdVerificationAuditLog requireLog = (RequireIdVerificationAuditLog) auditLog;
                return new IdVerificationAuditLogDTO(
                        requireLog.getId(),
                        requireLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        requireLog.getAction(),
                        requireLog.getDescription(),
                        requireLog.getLogType(),
                        userMapper.toDto(userService.findById(requireLog.getTargetUserId())),
                        requireLog.getReportCaseId());
            }

            case ID_VERIFICATION_PASSED -> {
                IdVerificationPassedAuditLog passedLog = (IdVerificationPassedAuditLog) auditLog;
                return new IdVerificationAuditLogDTO(
                        passedLog.getId(),
                        passedLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        passedLog.getAction(),
                        passedLog.getDescription(),
                        passedLog.getLogType(),
                        userMapper.toDto(userService.findById(passedLog.getTargetUserId())),
                        passedLog.getReportCaseId());
            }

            case ID_VERIFICATION_FAILED -> {
                IdVerificationFailedAuditLog failedLog = (IdVerificationFailedAuditLog) auditLog;
                return new IdVerificationAuditLogDTO(
                        failedLog.getId(),
                        failedLog.getCreatedAt().toString(),
                        createdBy,
                        createdByType,
                        failedLog.getAction(),
                        failedLog.getDescription(),
                        failedLog.getLogType(),
                        userMapper.toDto(userService.findById(failedLog.getTargetUserId())),
                        failedLog.getReportCaseId());
            }

            default -> {
                throw new IllegalArgumentException("Unknown AuditLogType " + type);
            }
        }
    }

    private AuditLogActorType resolveCreatedByType(AuditLog auditLog) {
        return SYSTEM_AUDITOR.equals(auditLog.getCreatedBy()) || auditLog.getCreatedBy() == null
                ? AuditLogActorType.SYSTEM
                : AuditLogActorType.USER;
    }

    private UserDTO resolveCreatedBy(AuditLog auditLog, AuditLogActorType createdByType) {
        if (createdByType == AuditLogActorType.SYSTEM) {
            return null;
        }

        Long createdById = Long.parseLong(auditLog.getCreatedBy());
        return userMapper.toDto(userService.findById(createdById));
    }

    private MessageResponseDTO resolveDeletedMessage(String deletedMessageId) {
        try {
            Long messageId = Long.parseLong(deletedMessageId);
            return messageMapper.toMessageResponseDTO(messagesService.findById(messageId));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
