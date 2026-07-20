package com.mk3.chatapp.dtos.responses;

// status is a plain string (PENDING/APPROVED/...) — the enum lives in the ads
// module and chat must not depend on it
public record PromotionInfoDTO(
        Long id,
        String status
) {
}
