package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.ChatRoomNoiseLevelEnum;

public record RoomPopulationDTO(
        Long roomId,
        String roomName,
        long activeUsersCount,
        long onlineUsersCount,
        Long totalMessagesCount,
        ChatRoomNoiseLevelEnum noiseLevel,
        boolean archived
) {
}
