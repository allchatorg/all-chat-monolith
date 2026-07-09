package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.enums.AuditLogType;
import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.*;
import com.mk3.chatapp.repositories.AuditLogRepository;
import com.mk3.chatapp.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public BanAuditLog logBan(String action, String description, Long targetUserId, BanType banType,
                              ReportType reportType, Long banDurationSeconds, Boolean deleteMessages, Long deleteMessagesDurationSeconds) {
        requireNonNull(action, "action");
        requireNonNull(targetUserId, "targetUserId");
        requireNonNull(banType, "banType");
        requireNonNull(reportType, "reportType");
        requireNonNull(deleteMessages, "deleteMessages");

        BanAuditLog log = BanAuditLog.builder().action(action).description(description).targetUserId(targetUserId)
                .logType(AuditLogType.BAN).banType(banType).reportType(reportType).banDurationSeconds(banDurationSeconds)
                .deleteMessages(deleteMessages).deleteMessagesDurationSeconds(deleteMessagesDurationSeconds).build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public RevokeBanAuditLog logRevokeBan(String action, String description, Long targetUserId) {
        requireNonNull(action, "action");
        requireNonNull(targetUserId, "targetUserId");

        RevokeBanAuditLog log = RevokeBanAuditLog.builder().action(action).description(description)
                .targetUserId(targetUserId).build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public BanAppealAuditLog logBanAppealResolve(String action, String description, Long targetUserId,
                                                 Long appealId, Long banId, String decision) {
        requireNonNull(action, "action");
        requireNonNull(targetUserId, "targetUserId");
        requireNonNull(appealId, "appealId");
        requireNonNull(banId, "banId");
        requireNonNull(decision, "decision");

        BanAppealAuditLog log = BanAppealAuditLog.builder().action(action).description(description)
                .targetUserId(targetUserId).appealId(appealId).banId(banId).decision(decision).build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public WarningAuditLog logWarning(String action, String description, Long targetUserId) {
        requireNonNull(action, "action");
        requireNonNull(targetUserId, "targetUserId");

        WarningAuditLog log = WarningAuditLog.builder().action(action).description(description)
                .targetUserId(targetUserId).build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public MessageDeleteAuditLog logMessageDelete(String action, String description, String deletedMessageId,
                                                  Long targetUserId) {
        requireNonNull(action, "action");
        requireNonNull(deletedMessageId, "deletedMessageId");

        MessageDeleteAuditLog log = MessageDeleteAuditLog.builder().action(action).targetUserId(targetUserId)
                .description(description).deletedMessageId(deletedMessageId).build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public ChangeUsernameAuditLog logChangeUsername(String action, String description, Long targetUserId,
                                                    String previousUsername, String newUsername) {
        requireNonNull(action, "action");
        requireNonNull(targetUserId, "targetUserId");
        requireNonNull(previousUsername, "previousUsername");
        requireNonNull(newUsername, "newUsername");

        ChangeUsernameAuditLog log = ChangeUsernameAuditLog
                .builder()
                .action(action)
                .description(description)
                .targetUserId(targetUserId)
                .previousUsername(previousUsername)
                .newUsername(newUsername)
                .build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public ResolveCaseAuditLog logResolveCase(String action, String description, Long targetUserId, Long reportCaseId) {
        requireNonNull(action, "action");
        requireNonNull(reportCaseId, "reportCaseId");

        ResolveCaseAuditLog log = ResolveCaseAuditLog.builder()
                .action(action)
                .description(description)
                .logType(AuditLogType.RESOLVE_CASE)
                .targetUserId(targetUserId)
                .reportCaseId(reportCaseId)
                .build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public PromoteRoleAuditLog logPromoteRole(String action, String description, Long targetUserId, Role previousRole,
                                              Role newRole) {
        requireNonNull(action, "action");
        requireNonNull(targetUserId, "targetUserId");
        requireNonNull(previousRole, "previousRole");
        requireNonNull(newRole, "newRole");

        PromoteRoleAuditLog log = PromoteRoleAuditLog.builder()
                .action(action)
                .description(description)
                .targetUserId(targetUserId)
                .previousRole(previousRole)
                .newRole(newRole)
                .build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public DemoteRoleAuditLog logDemoteRole(String action, String description, Long targetUserId, Role previousRole,
                                            Role newRole) {
        requireNonNull(action, "action");
        requireNonNull(targetUserId, "targetUserId");
        requireNonNull(previousRole, "previousRole");
        requireNonNull(newRole, "newRole");

        DemoteRoleAuditLog log = DemoteRoleAuditLog.builder()
                .action(action)
                .description(description)
                .targetUserId(targetUserId)
                .previousRole(previousRole)
                .newRole(newRole)
                .build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public ArchiveChatRoomAuditLog logArchiveChatRoom(String action, String description, Long chatRoomId, String chatRoomName) {
        requireNonNull(action, "action");
        requireNonNull(chatRoomId, "chatRoomId");
        requireNonNull(chatRoomName, "chatRoomName");

        ArchiveChatRoomAuditLog log = ArchiveChatRoomAuditLog.builder()
                .action(action)
                .description(description)
                .chatRoomId(chatRoomId)
                .chatRoomName(chatRoomName)
                .build();
        return auditLogRepository.save(log);
    }

    @Override
    @Transactional
    public UnarchiveChatRoomAuditLog logUnarchiveChatRoom(String action, String description, Long chatRoomId, String chatRoomName) {
        requireNonNull(action, "action");
        requireNonNull(chatRoomId, "chatRoomId");
        requireNonNull(chatRoomName, "chatRoomName");

        UnarchiveChatRoomAuditLog log = UnarchiveChatRoomAuditLog.builder()
                .action(action)
                .description(description)
                .chatRoomId(chatRoomId)
                .chatRoomName(chatRoomName)
                .build();
        return auditLogRepository.save(log);
    }

    @Override
    public Optional<AuditLog> getById(Long id) {
        return auditLogRepository.findById(id);
    }

    @Override
    public Page<AuditLog> listAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    @Override
    public Page<AuditLog> listByType(AuditLogType type, Pageable pageable) {
        requireNonNull(type, "type");
        return auditLogRepository.findByLogType(type, pageable);
    }

    @Override
    public Page<AuditLog> findAll(Specification<AuditLog> specification, PageRequest pageRequest) {
        return auditLogRepository.findAll(specification, pageRequest);
    }

    @Override
    public AuditLog findDeletedMessageAuditLog(Long id) {
        return auditLogRepository.findByDeletedMessageId(id.toString()).orElseThrow(
                () -> new IllegalArgumentException("No audit log found for deleted message with id " + id));
    }

    private void requireNonNull(Object value, String name) {
        if (Objects.isNull(value)) {
            throw new IllegalArgumentException(name + " cannot be null");
        }
    }
}
