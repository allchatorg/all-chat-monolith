package com.mk3.chatapp.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "last_session_info")
@Getter
@Setter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class LastSessionInfo extends Base {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;
}
