package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.dtos.requests.BanRequestDTO;
import com.mk3.chatapp.models.AuditLog;
import com.mk3.chatapp.models.Ban;
import com.mk3.chatapp.models.identity.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service

public interface BanService {
    AuditLog banUser(BanRequestDTO banRequestDTO, User currentUser);

    void revokeBan(User user);

    void systemRevokeBan(User user);

    Page<BanResponseDTO> findActiveBans(String username, Long userId, int page, int pageSize);

    Optional<Ban> findUserActiveBan(User user);

    void reScheduleBans();
}
