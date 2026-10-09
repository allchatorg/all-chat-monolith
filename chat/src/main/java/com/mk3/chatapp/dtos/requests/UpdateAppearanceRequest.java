package com.mk3.chatapp.dtos.requests;

import jakarta.validation.constraints.NotNull;

public record UpdateAppearanceRequest(@NotNull Boolean showVipBadge) {
}
