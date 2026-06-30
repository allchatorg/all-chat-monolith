package com.example.adsportalbe.dto;

import com.mk3.chatapp.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDto {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private Long totalPurchasedAdsCount;
    private Double totalSpent;
    private Instant createdAt;

}
