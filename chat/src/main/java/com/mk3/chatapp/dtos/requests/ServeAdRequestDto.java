package com.mk3.chatapp.dtos.requests;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServeAdRequestDto {
    private Long userId;
    private String ipAddress;
}
