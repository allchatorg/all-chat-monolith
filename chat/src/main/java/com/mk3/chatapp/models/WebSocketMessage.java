package com.mk3.chatapp.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mk3.chatapp.dtos.responses.*;
import com.mk3.chatapp.enums.WebSocketMessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketMessage {

    private WebSocketMessageType type;
    private String chatRoomName;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = MessageResponseDTO.class, name = "NEW_MESSAGE"),
            @JsonSubTypes.Type(value = RoomPopulationDTO.class, name = "POPULARITY_UPDATE"),
            @JsonSubTypes.Type(value = RoomPopulationListDTO.class, name = "LEADERBOARD_UPDATE_ONLINE"),
            @JsonSubTypes.Type(value = RoomPopulationListDTO.class, name = "LEADERBOARD_UPDATE_ACTIVE"),
            @JsonSubTypes.Type(value = ReactionSocketResponse.class, name = "MESSAGE_REACTION_UPDATE"),
            @JsonSubTypes.Type(value = MessageResponseDTO.class, name = "MESSAGE_EDIT"),
            @JsonSubTypes.Type(value = ReportNotificationDTO.class, name = "REPORT_NOTIFICATION"),
            @JsonSubTypes.Type(value = ChatRoomDTO.class, name = "CHATROOM_ARCHIVED"),
            @JsonSubTypes.Type(value = ChatRoomDTO.class, name = "CHATROOM_UNARCHIVED"),
            @JsonSubTypes.Type(value = MessagingAvailabilityDTO.class, name = "MESSAGE_SENDING_AVAILABILITY_UPDATE"),
            @JsonSubTypes.Type(value = MessageResponseDTO.class, name = "PRIVATE_NEW_MESSAGE"),
            @JsonSubTypes.Type(value = MessageResponseDTO.class, name = "PRIVATE_MESSAGE_EDIT"),
            @JsonSubTypes.Type(value = MessageResponseDTO.class, name = "PRIVATE_MESSAGE_DELETE"),
            @JsonSubTypes.Type(value = PromotedMessageEventDTO.class, name = "PROMOTED_MESSAGE_UPDATE"),
            @JsonSubTypes.Type(value = IdVerificationRequiredDTO.class, name = "ID_VERIFICATION_REQUIRED"),
            @JsonSubTypes.Type(value = IdVerificationResultDTO.class, name = "ID_VERIFICATION_RESULT"),
    })
    private Object data;
}
