package com.mk3.chatapp.events;

import com.mk3.chatapp.dtos.responses.VipBadgeDTO;

public record VipBadgeChangedEvent(VipBadgeDTO badge) {
}
