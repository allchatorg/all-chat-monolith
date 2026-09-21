package com.mk3.chatapp.events;

import com.mk3.chatapp.dtos.responses.ProBadgeDTO;

public record ProBadgeChangedEvent(ProBadgeDTO badge) {
}
