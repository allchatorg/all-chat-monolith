package com.mk3.chatapp.services;

import com.mk3.chatapp.enums.AuditLogType;
import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public interface AuditLogService {

    BanAuditLog logBan(
            String action,
            String description,
            Long targetUserId,
            BanType banType,
            ReportType reportType,
            Long banDurationSeconds,
            Boolean deleteMessages,
            Long deleteMessagesDurationSeconds);

    RevokeBanAuditLog logRevokeBan(String action,
                                   String description,
                                   Long targetUserId);

    BanAppealAuditLog logBanAppealResolve(String action,
                                          String description,
                                          Long targetUserId,
                                          Long appealId,
                                          Long banId,
                                          String decision);

    WarningAuditLog logWarning(String action,
                               String description,
                               Long targetUserId);

    MessageDeleteAuditLog logMessageDelete(
            String action,
            String description,
            String deletedMessageId,
            Long targetUserId);

    ChangeUsernameAuditLog logChangeUsername(
            String action,
            String description,
            Long targetUserId,
            String previousUsername,
            String newUsername);

    ResolveCaseAuditLog logResolveCase(
            String action,
            String description,
            Long targetUserId,
            Long reportCaseId);

    PromoteRoleAuditLog logPromoteRole(
            String action,
            String description,
            Long targetUserId,
            Role previousRole,
            Role newRole);

    DemoteRoleAuditLog logDemoteRole(
            String action,
            String description,
            Long targetUserId,
            Role previousRole,
            Role newRole);

    ArchiveChatRoomAuditLog logArchiveChatRoom(
            String action,
            String description,
            Long chatRoomId,
            String chatRoomName);

    UnarchiveChatRoomAuditLog logUnarchiveChatRoom(
            String action,
            String description,
            Long chatRoomId,
            String chatRoomName);

    Optional<AuditLog> getById(Long id);

    Page<AuditLog> listAll(Pageable pageable);

    Page<AuditLog> listByType(AuditLogType type, Pageable pageable);

    Page<AuditLog> findAll(Specification<AuditLog> specification, PageRequest pageRequest);

    AuditLog findDeletedMessageAuditLog(Long id);
}
