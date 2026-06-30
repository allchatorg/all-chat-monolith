package com.mk3.chatapp.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum BanType {
    TEMPORARY("TEMPORARY"),
    PERMANENT("PERMANENT");

    @Getter
    private final String banType;
}
