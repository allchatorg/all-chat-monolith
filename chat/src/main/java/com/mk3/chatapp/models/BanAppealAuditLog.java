package com.mk3.chatapp.models;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@DiscriminatorValue("BAN_APPEAL_RESOLVE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BanAppealAuditLog extends AuditLog {

    @Column(name = "appeal_id")
    private Long appealId;

    @Column(name = "ban_id")
    private Long banId;

    @Column(name = "decision")
    private String decision;
}
