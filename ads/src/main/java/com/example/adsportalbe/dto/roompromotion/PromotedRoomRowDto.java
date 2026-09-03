package com.example.adsportalbe.dto.roompromotion;

import java.time.Instant;

/**
 * One row of the promoted-rooms list: a room with at least one APPROVED
 * promotion and the time of its most recent approval (the "bump").
 */
public record PromotedRoomRowDto(Long roomId, Instant promotedAt) {
}
