package com.mk3.chatapp.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.models.identity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notifications_user_read", columnList = "user_id, read_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Where(clause = "deleted = false")
public class Notification extends Base implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    /**
     * Opaque JSON payload for type-specific detail rendering on the frontend.
     * Never queried server-side.
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Optional pointer to the domain object this notification is about, for
     * types whose details modal needs to deep-link or fetch live data.
     */
    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    /**
     * Null means unread.
     */
    @Column(name = "read_at")
    private Instant readAt;
}
