package com.mk3.chatapp.models;

import com.mk3.chatapp.enums.RequiredVerificationEnum;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ip")
public class Ip extends Base {
    @Id
    private String ip;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RequiredVerificationEnum requiredVerification = RequiredVerificationEnum.NONE;

    @Builder.Default
    private Long riskLevel = 0L;
}
