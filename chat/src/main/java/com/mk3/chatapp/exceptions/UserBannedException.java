package com.mk3.chatapp.exceptions;

import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.models.Ban;
import lombok.Getter;

import java.util.Objects;

@Getter
public class UserBannedException extends RuntimeException {
    private final BanResponseDTO banResponse;

    public UserBannedException(Ban banResponse) {
        super("User is banned");
        ReportType userFacingReportType = banResponse.getReportType().toUserFacingReportType();
        String userFacingDescription = banResponse.getReportType().toUserFacingDescription(banResponse.getDescription());

        this.banResponse = new BanResponseDTO(
                banResponse.getId(),
                null,
                null,
                banResponse.getIpAddress(),
                banResponse.getUserAgent(),
                userFacingDescription,
                Objects.isNull(banResponse.getExpiresAt()) ? null : banResponse.getExpiresAt().toString(),
                true,
                banResponse.getType(),
                userFacingReportType

        );
    }
}
