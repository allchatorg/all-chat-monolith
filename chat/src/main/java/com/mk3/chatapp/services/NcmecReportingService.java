package com.mk3.chatapp.services;

import com.mk3.chatapp.enums.NcmecFileAnnotation;
import com.mk3.chatapp.enums.NcmecIncidentType;
import com.mk3.chatapp.enums.NcmecReportAnnotation;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.ReportCase;
import com.mk3.chatapp.models.identity.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface NcmecReportingService {
    Long openReport(
            ReportCase reportCase,
            NcmecIncidentType incidentType,
            List<NcmecReportAnnotation> reportAnnotations,
            List<NcmecFileAnnotation> fileAnnotations,
            User initiatingUser);

    void uploadFile(Long reportId, Attachment attachment);

    void finishReport(Long reportId);

    void cancelReport(Long reportId);
}
