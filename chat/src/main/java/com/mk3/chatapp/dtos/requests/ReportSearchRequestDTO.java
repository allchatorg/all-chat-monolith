package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.ReportType;

import java.util.List;

public record ReportSearchRequestDTO(
        Boolean resolved,
        List<ReportType> reportTypes,
        String reportedUserUsernameOrId,
        Integer page,
        Integer size,
        String sort
) {
}
