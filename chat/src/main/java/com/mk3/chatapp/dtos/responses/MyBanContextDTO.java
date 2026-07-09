package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.dtos.BanResponseDTO;

public record MyBanContextDTO(
        BanResponseDTO ban,
        boolean appealable,
        BanAppealUserViewDTO appeal
) {
}
