package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.IdVerificationStatus;
import com.mk3.chatapp.enums.Role;

import java.time.Instant;
import java.util.List;

public record UserAdminViewDTO(
                Long id,
                String username,
                String email,
                boolean isOver18,
                boolean claimed,
                boolean verified,
                boolean banned,
                IdVerificationStatus idVerificationStatus,
                Role role,
                Long totalUploadUsage,
                List<String> previousUsernames,
                Instant createdAt,
                Instant lastLoginAt,
                String countryCode) {
}
