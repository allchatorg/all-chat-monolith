package com.mk3.chatapp.models.ncmec;

import com.mk3.chatapp.models.AuditLog;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "ncmec_report_audit_log")
@DiscriminatorValue("NCMEC_REPORT")
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class NcmecReportAuditLog extends AuditLog {

    @Column(name = "ncmec_report_id")
    private Long ncmecReportId;

    @Column(name = "report_case_id")
    private Long reportCaseId;

    @Column(name = "xml_content", columnDefinition = "TEXT")
    private String xmlContent;

    @Column(name = "status")
    private String status;
}
