package com.mk3.chatapp.models.vip;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Entity
@Table(name = "vip_reporting_state")
@Check(constraints = "id = 1")
@Getter
@Setter
@NoArgsConstructor
public class VipReportingState {
    public static final long SINGLETON_ID = 1L;
    @Id
    private Long id;
    @Column(name = "last_synchronized_at", nullable = false)
    private Instant lastSynchronizedAt;
    @Column(name = "synchronized_once", nullable = false)
    private boolean synchronizedOnce;
    @Column(name = "window_start")
    private Instant windowStart;
    @Column(name = "window_end")
    private Instant windowEnd;
    @Column(name = "event_cursor")
    private String eventCursor;
    @Column(name = "incomplete", nullable = false)
    private boolean incomplete;
    @Column(name = "synchronization_failed", nullable = false)
    private boolean synchronizationFailed;
}
