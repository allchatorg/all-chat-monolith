package com.mk3.chatapp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mk3.chatapp.enums.BanAppealStatus;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "ban_appeal",
        uniqueConstraints = @UniqueConstraint(name = "uk_ban_appeal_ban_id", columnNames = "ban_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Where(clause = "deleted = false")
public class BanAppeal extends Base implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ban_id", nullable = false)
    @JsonIgnore
    private Ban ban;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "appeal_text", columnDefinition = "TEXT", nullable = false)
    private String appealText;

    @Column(name = "what_will_change", columnDefinition = "TEXT")
    private String whatWillChange;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BanAppealStatus status = BanAppealStatus.PENDING;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "internal_note", columnDefinition = "TEXT")
    private String internalNote;

    @Column(name = "user_facing_message", columnDefinition = "TEXT")
    private String userFacingMessage;
}
