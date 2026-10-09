package com.mk3.chatapp.dtos.responses;

import com.mk3.chatapp.enums.Role;

public record UserChatRoomDTO(
        Long id,
        String chatRoomName,
        Role chatRoomRequiredAccessLevel,
        Long chatRoomId,
        RoomPopulationDTO roomPopulation,
        Integer unreadMessagesCount,
        MessageResponseDTO lastReadMessage,
        MessageResponseDTO lastMessage
) {
    @com.fasterxml.jackson.annotation.JsonProperty("vipOnly")
    public boolean vipOnly() {
        return roomPopulation != null && roomPopulation.vipOnly();
    }
}
