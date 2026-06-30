package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.RequiredVerificationEnum;

public record IpDetailsResponseDTO(
        RequiredVerificationEnum requiredVerification
) {
}
