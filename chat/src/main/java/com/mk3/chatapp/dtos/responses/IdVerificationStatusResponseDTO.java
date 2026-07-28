package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.IdVerificationStatus;

public record IdVerificationStatusResponseDTO(
        IdVerificationStatus status) {
}
