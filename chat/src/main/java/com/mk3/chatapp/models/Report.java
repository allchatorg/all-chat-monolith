package com.mk3.chatapp.models;

import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.enums.ReportOrigin;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "report")
public class Report extends Base {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "reporter_origin", nullable = false)
    private ReportOrigin reporterOrigin = ReportOrigin.USER;

    @ManyToOne
    @JoinColumn(name = "reported_user_id", nullable = false)
    private User reportedUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private ReportType reportType;

    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne
    @JoinColumn(name = "report_case_id", nullable = false)
    private ReportCase reportCase;

    @Column(name = "description", length = 500)
    private String description;
}
