package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.BanResponseDTO;
import com.mk3.chatapp.models.Ban;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {UserMapper.class})
public interface BanMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(expression = "java(ban.getUser() != null ? ban.getUser().getApplicationUsername() : \"Perma Deleted User\")", target = "username")
    BanResponseDTO toDto(Ban ban);

    Ban toEntity(BanResponseDTO banResponseDTO);

    /**
     * User-facing view of a ban: masks CSAM-related report types/descriptions and
     * omits user identifiers. Shared by AccessRestrictionFilter and the ban appeal flow
     * so banned users always see identical, sanitized ban details.
     */
    default BanResponseDTO toUserFacingDto(Ban ban) {
        var userFacingReportType = ban.getReportType().toUserFacingReportType();
        String userFacingDescription = ban.getReportType().toUserFacingDescription(ban.getDescription());

        return new BanResponseDTO(
                ban.getId(),
                null,
                null,
                ban.getIpAddress(),
                ban.getUserAgent(),
                userFacingDescription,
                ban.getExpiresAt() == null ? null : ban.getExpiresAt().toString(),
                true,
                ban.getType(),
                userFacingReportType
        );
    }
}
