package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.dtos.WarnUserRequestDTO;

public record ReportCaseWarnUserReq(WarnUserRequestDTO warnUserRequestDTO, Long reportCaseId) {
}
