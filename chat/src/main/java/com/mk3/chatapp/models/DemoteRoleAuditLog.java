package com.mk3.chatapp.models;

import com.mk3.chatapp.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Entity
@DiscriminatorValue("DEMOTE_ROLE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DemoteRoleAuditLog extends AuditLog {

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_role", nullable = false)
    private Role previousRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_role", nullable = false)
    private Role newRole;
}
