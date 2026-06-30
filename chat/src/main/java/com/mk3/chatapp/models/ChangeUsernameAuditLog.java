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
@DiscriminatorValue("CHANGE_USERNAME")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ChangeUsernameAuditLog extends AuditLog {

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "previous_username", nullable = false)
    private String previousUsername;

    @Column(name = "new_username", nullable = false)
    private String newUsername;
}
