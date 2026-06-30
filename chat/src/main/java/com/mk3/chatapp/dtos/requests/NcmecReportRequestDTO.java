package com.mk3.chatapp.dtos.requests;

import com.mk3.chatapp.enums.NcmecFileAnnotation;
import com.mk3.chatapp.enums.NcmecIncidentType;
import com.mk3.chatapp.enums.NcmecReportAnnotation;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NcmecReportRequestDTO(@NotNull Long reportCaseId, @NotNull NcmecIncidentType incidentType,
                                    List<NcmecReportAnnotation> reportAnnotations,
                                    List<NcmecFileAnnotation> fileAnnotations) {
}
