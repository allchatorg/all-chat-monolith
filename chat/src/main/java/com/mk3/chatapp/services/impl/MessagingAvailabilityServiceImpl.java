package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.MessagingAvailabilityDTO;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.services.ChatRoomService;
import com.mk3.chatapp.services.MessagingAvailabilityService;
import com.mk3.chatapp.services.RoomActivityService;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import com.mk3.chatapp.utils.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagingAvailabilityServiceImpl implements MessagingAvailabilityService {
    private static final String MESSAGING_BLOCKED_KEY = "messaging:availability:blocked";
    private static final String DISABLED_REASON =
            "Messaging is temporarily disabled until a moderator is online.";

    private final ChatRoomService chatRoomService;
    private final RoomActivityService roomActivityService;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public MessagingAvailabilityDTO getCurrentAvailability() {
        boolean hasOnlineStaff = hasOnlineStaff();
        return toAvailability(!hasOnlineStaff);
    }

    @Override
    public MessagingAvailabilityDTO refreshAndBroadcastIfChanged() {
        MessagingAvailabilityDTO availability = getCurrentAvailability();
        String currentBlockedValue = Boolean.toString(availability.messagingBlocked());
        String previousBlockedValue = redisTemplate.opsForValue().get(MESSAGING_BLOCKED_KEY);

        if (!currentBlockedValue.equals(previousBlockedValue)) {
            redisTemplate.opsForValue().set(MESSAGING_BLOCKED_KEY, currentBlockedValue);
            broadcastAvailability(availability);
        }

        return availability;
    }

    private boolean hasOnlineStaff() {
        try {
            var moderatorsRoom = chatRoomService.findByName(Constants.MODERATORS_CHATROOM_NAME);
            var population = roomActivityService.getRoomPopulation(moderatorsRoom.getId().toString());
            return population.onlineUsersCount() > 0;
        } catch (Exception ex) {
            log.warn("Unable to resolve moderator online presence. Failing messaging availability closed.", ex);
            return false;
        }
    }

    private MessagingAvailabilityDTO toAvailability(boolean messagingBlocked) {
        return new MessagingAvailabilityDTO(
                messagingBlocked,
                messagingBlocked ? DISABLED_REASON : null
        );
    }

    private void broadcastAvailability(MessagingAvailabilityDTO availability) {
        var webSocketMessage = WebSocketMessage.builder()
                .type(WebSocketMessageType.MESSAGE_SENDING_AVAILABILITY_UPDATE)
                .data(availability)
                .build();

        webSocketBroadcastService.broadcastToPublicChat(webSocketMessage);
    }
}
