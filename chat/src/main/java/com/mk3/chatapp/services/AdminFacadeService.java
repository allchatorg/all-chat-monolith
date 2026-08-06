package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.dtos.WarnUserRequestDTO;
import com.mk3.chatapp.dtos.requests.*;
import com.mk3.chatapp.dtos.responses.AuditLogDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.dtos.responses.RoomPromotionsSummaryDTO;
import com.mk3.chatapp.dtos.responses.UserAdminViewDTO;
import com.mk3.chatapp.dtos.responses.UserDTO;
import com.mk3.chatapp.models.AuditLog;
import org.springframework.data.domain.Page;

public interface AdminFacadeService {
    AuditLog banUser(BanRequestDTO banRequestDTO);

    void revokeBan(Long userId);

    UserDTO getUserAdminDetails(Long userId);

    Page<MessageResponseDTO> getUserMessages(MessageSearchRequestDTO request);

    AuditLog warnUser(WarnUserRequestDTO warnRequestDTO);

    Page<BanResponseDTO> findActiveBans(String userNameOrId, int page, int pageSize);

    void updateUserRole(RoleUpdateRequest request);

    Page<UserDTO> searchUsers(UserSearchRequestDTO request);

    UserAdminViewDTO getUserAdminViewDetails(Long userId);

    Page<AuditLogDTO> searchAuditLogs(AuditLogSearchRequestDTO request);

    void archiveChatRoom(Long roomId);

    void unarchiveChatRoom(Long roomId);

    RoomPromotionsSummaryDTO getRoomPromotionsSummary(Long roomId);

    void requireIdVerification(Long userId, Long reportCaseId);

    void clearIdVerificationRequirement(Long userId);
}
