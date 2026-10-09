package com.mk3.chatapp.dtos.responses;

public record AppearanceDTO(boolean showVipBadge, boolean vipActive, boolean vipBadgeVisible,
                            long vipBadgeRevision) {
}
