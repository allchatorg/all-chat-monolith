package com.mk3.chatapp.models.vip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "processed_stripe_event")
@Getter
@NoArgsConstructor
public class ProcessedStripeEvent {
    @Id
    @Column(name = "event_id")
    private String eventId;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public ProcessedStripeEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }
}
