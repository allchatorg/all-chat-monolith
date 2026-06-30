package com.mk3.chatapp.tasks;

import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.models.WebSocketMessage;
import com.mk3.chatapp.services.RoomActivityService;
import com.mk3.chatapp.services.WebSocketBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeartbeatCleanupTask {

    // Online: safety net only — catches missed WebSocket disconnects
    private static final long ONLINE_SAFETY_NET_TIMEOUT_MS = 300_000L; // 5 minutes
    // Active: heartbeat-driven, tight timeout
    private static final long ACTIVE_TIMEOUT_MS = 45_000L;

    private final RedisTemplate<String, String> redisTemplate;
    private final WebSocketBroadcastService webSocketBroadcastService;
    private final RoomActivityService roomActivityService;

    /**
     * Runs every 30 seconds to purge stale users from online/active sorted sets.
     * When stale entries are removed, broadcasts updated population counts to the
     * room.
     */
    @Scheduled(fixedRate = 30_000)
    public void cleanupStaleUsers() {
        long now = System.currentTimeMillis();
        long onlineCutoff = now - ONLINE_SAFETY_NET_TIMEOUT_MS;
        long activeCutoff = now - ACTIVE_TIMEOUT_MS;

        Set<String> onlineKeys = redisTemplate.keys("room:*:online");
        if (onlineKeys == null || onlineKeys.isEmpty()) {
            return;
        }

        for (String onlineKey : onlineKeys) {
            // Extract roomId from key pattern "room:{id}:online"
            String[] parts = onlineKey.split(":");
            if (parts.length < 3)
                continue;
            String roomId = parts[1];

            String activeKey = String.format("room:%s:active", roomId);

            // Remove users whose last heartbeat is older than the cutoff
            Long removedOnline = redisTemplate.opsForZSet().removeRangeByScore(onlineKey, 0, onlineCutoff);
            Long removedActive = redisTemplate.opsForZSet().removeRangeByScore(activeKey, 0, activeCutoff);

            // If any users were cleaned up, broadcast updated counts
            if ((removedOnline != null && removedOnline > 0) ||
                    (removedActive != null && removedActive > 0)) {

                log.debug("Cleaned up stale users in room {}: {} online, {} active removed",
                        roomId, removedOnline, removedActive);

                var population = roomActivityService.getRoomPopulation(roomId);
                var webSocketMessage = WebSocketMessage.builder()
                        .type(WebSocketMessageType.POPULARITY_UPDATE)
                        .chatRoomName(population.roomName())
                        .data(population)
                        .build();

                webSocketBroadcastService.broadcastToChatRoom(population.roomName(), webSocketMessage);
            }
        }
    }
}
