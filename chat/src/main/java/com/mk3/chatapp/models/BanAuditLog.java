package com.mk3.chatapp.models;

import com.mk3.chatapp.enums.BanType;
import com.mk3.chatapp.enums.ReportType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@DiscriminatorValue("BAN")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BanAuditLog extends AuditLog {

    @Column(name = "ban_duration_seconds")
    private Long banDurationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(name = "ban_type")
    @NonNull
    private BanType banType;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type")
    @NonNull
    private ReportType reportType;

    @Column(name = "delete_messages")
    @NonNull
    private Boolean deleteMessages;

    @Column(name = "delete_messages_duration_seconds")
    private Long deleteMessagesDurationSeconds;

}
