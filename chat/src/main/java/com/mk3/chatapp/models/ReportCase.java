package com.mk3.chatapp.models;

import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "report_case")
public class ReportCase extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "message_id", unique = true, nullable = false)
    private Message message;

    @OneToMany
    @JoinColumn(name = "report_case_id")
    @Builder.Default
    private List<Report> reports = new ArrayList<>();

    @OneToMany
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<AuditLog> auditLogs = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "resolver_id")
    private User resolver;

    @Column(name = "needs_attention_at")
    private Instant needsAttentionAt;

    @Column(name = "resolution_date")
    private Instant resolutionDate;

    @Column(name = "csam_case")
    private boolean csamCase;
}
