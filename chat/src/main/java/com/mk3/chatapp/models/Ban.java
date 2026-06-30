package com.mk3.chatapp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "user_ban")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Where(clause = "deleted = false")
public class Ban extends Base implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    @JsonIgnore
    private User user;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "description")
    private String description;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    private BanType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type")
    private ReportType reportType;
}
