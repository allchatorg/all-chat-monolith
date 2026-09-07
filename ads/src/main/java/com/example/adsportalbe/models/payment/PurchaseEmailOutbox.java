package com.example.adsportalbe.models.payment;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** A committed purchase update, retained until SMTP accepts it (and afterwards for audit/deduplication). */
@Entity
@Table(name = "purchase_email_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_purchase_email_event", columnNames = "event_key"),
        indexes = {
                @Index(name = "idx_purchase_email_due", columnList = "delivered_at,next_attempt_at,id"),
                @Index(name = "idx_purchase_email_order", columnList = "purchase_reference,delivered_at,id")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseEmailOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_key", nullable = false, length = 160)
    private String eventKey;

    @Column(name = "recipient_user_id", nullable = false)
    private Long recipientUserId;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "purchase_reference", nullable = false, length = 100)
    private String purchaseReference;

    @Column(name = "details_path", nullable = false)
    private String detailsPath;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null on missing-email records: keep them visible for operator recovery without futile retries. */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** A fixed failure category or exception class, never an SMTP response or exception message. */
    @Column(name = "last_failure_code", length = 100)
    private String lastFailureCode;
}
