package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.ChatRoomNoiseLevelEnum;

import java.time.Instant;

/**
 * A promoted room for the "Promoted" tab: the live population stats of
 * {@link RoomPopulationDTO} flattened together with the time of the room's
 * most recent approved promotion.
 */
public record PromotedRoomDTO(
        Long roomId,
        String roomName,
        long activeUsersCount,
        long onlineUsersCount,
        Long totalMessagesCount,
        ChatRoomNoiseLevelEnum noiseLevel,
        boolean archived,
        Instant promotedAt
) {
    public static PromotedRoomDTO from(RoomPopulationDTO population, Instant promotedAt) {
        return new PromotedRoomDTO(
                population.roomId(),
                population.roomName(),
                population.activeUsersCount(),
                population.onlineUsersCount(),
                population.totalMessagesCount(),
                population.noiseLevel(),
                population.archived(),
                promotedAt);
    }
}
