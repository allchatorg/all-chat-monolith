package com.mk3.chatapp.enums;

public enum BanAppealStatus {
    PENDING,
    UNDER_REVIEW,
    APPROVED,
    DENIED,
    EXPIRED;

    public boolean isOpen() {
        return this == PENDING || this == UNDER_REVIEW;
    }
}
