package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.RoomPopulationDTO;
import com.mk3.chatapp.dtos.responses.TopReactedMessageDTO;
import com.mk3.chatapp.enums.ChatRoomNoiseLevelEnum;
import com.mk3.chatapp.enums.RoomPopularitySort;
import com.mk3.chatapp.services.RoomActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomActivityServiceImpl implements RoomActivityService {

    // Sorted Sets: score = last heartbeat timestamp (millis)
    private static final String KEY_ROOM_ONLINE_USERS = "room:%s:online";
    private static final String KEY_ROOM_ACTIVE_USERS = "room:%s:active";
    private static final String KEY_LEADERBOARD_ONLINE = "leaderboard:online";
    private static final String KEY_LEADERBOARD_ACTIVE = "leaderboard:active";
    private static final String KEY_ROOM_METADATA = "room:%s:metadata";
    private static final String KEY_ROOM_MESSAGE_COUNT = "room:%s:message_count";
    private static final String KEY_ARCHIVED_ROOMS = "rooms:archived";

    // Active: heartbeat-driven, tight timeout
    private static final long ACTIVE_TIMEOUT_MS = 45_000L; // 45 seconds (stricter for "actively viewing")

    private final RedisTemplate<String, String> redisTemplate;

    // =======================
    // ROOM METADATA
    // =======================

    @Override
    public void storeRoomMetadata(String roomId, String roomName) {
        String metadataKey = String.format(KEY_ROOM_METADATA, roomId);
        redisTemplate.opsForValue().set(metadataKey, roomName);
    }

    private String getRoomName(String roomId) {
        String metadataKey = String.format(KEY_ROOM_METADATA, roomId);
        String roomName = redisTemplate.opsForValue().get(metadataKey);
        return roomName != null ? roomName : roomId; // fallback to ID if not found
    }

    // =======================
    // MESSAGE TRACKING
    // =======================

    @Override
    public void addMessage(String roomId) {
        String messagesKey = "room:" + roomId + ":messages";
        long now = System.currentTimeMillis();

        // Add a dummy member with timestamp as score
        redisTemplate.opsForZSet().add(messagesKey, String.valueOf(now), now);

        // Remove old messages (older than 1 hour)
        long oneHourAgo = now - 3_600_000L;
        redisTemplate.opsForZSet().removeRangeByScore(messagesKey, 0, oneHourAgo);

        // Increment total message count
        incrementMessageCount(roomId);
    }

    @Override
    public long getMessagesLastHour(String roomId) {
        String messagesKey = "room:" + roomId + ":messages";
        long oneHourAgo = System.currentTimeMillis() - 3_600_000L;
        Long count = redisTemplate.opsForZSet().count(messagesKey, oneHourAgo, Double.MAX_VALUE);
        return count != null ? count : 0L;
    }

    private ChatRoomNoiseLevelEnum determineNoiseLevel(long messagesLastHour) {
        if (messagesLastHour <= 100) {
            return ChatRoomNoiseLevelEnum.QUIET;
        } else if (messagesLastHour <= 1000) {
            return ChatRoomNoiseLevelEnum.CONVERSATIONAL;
        } else {
            return ChatRoomNoiseLevelEnum.NOISY;
        }
    }

    // =======================
    // MESSAGE COUNT TRACKING
    // =======================

    /**
     * Increments the total message count for a room
     */
    private void incrementMessageCount(String roomId) {
        String countKey = String.format(KEY_ROOM_MESSAGE_COUNT, roomId);
        redisTemplate.opsForValue().increment(countKey);
    }

    /**
     * Gets the total message count for a room
     */
    public long getMessageCount(String roomId) {
        String countKey = String.format(KEY_ROOM_MESSAGE_COUNT, roomId);
        String count = redisTemplate.opsForValue().get(countKey);
        return count != null ? Long.parseLong(count) : 0L;
    }

    /**
     * Sets the message count for a room (useful for initialization)
     */
    public void setMessageCount(String roomId, long count) {
        String countKey = String.format(KEY_ROOM_MESSAGE_COUNT, roomId);
        redisTemplate.opsForValue().set(countKey, String.valueOf(count));
    }

    /**
     * Resets the message count for a room to zero
     */
    public void resetMessageCount(String roomId) {
        String countKey = String.format(KEY_ROOM_MESSAGE_COUNT, roomId);
        redisTemplate.delete(countKey);
    }

    // =======================
    // HEARTBEAT
    // =======================

    @Override
    public void recordHeartbeat(String userId, String activeRoomId, List<String> allUserRoomIds) {
        long now = System.currentTimeMillis();

        for (String roomId : allUserRoomIds) {
            if (isRoomArchived(roomId)) continue;
            // Refresh "online" timestamp in every room the user belongs to
            String onlineKey = String.format(KEY_ROOM_ONLINE_USERS, roomId);
            redisTemplate.opsForZSet().add(onlineKey, userId, now);

            String activeKey = String.format(KEY_ROOM_ACTIVE_USERS, roomId);
            if (roomId.equals(activeRoomId)) {
                // Only the actively-viewed room gets the "active" refresh
                redisTemplate.opsForZSet().add(activeKey, userId, now);
            } else {
                // Remove from active in other rooms
                redisTemplate.opsForZSet().remove(activeKey, userId);
            }
        }
    }

    // =======================
    // USER STATE TRACKING
    // =======================

    @Override
    public RoomPopulationDTO userJoinedRoom(String roomId, String userId) {
        if (isRoomArchived(roomId)) return getArchivedRoomPopulation(roomId);
        long now = System.currentTimeMillis();
        String onlineUsersKey = String.format(KEY_ROOM_ONLINE_USERS, roomId);
        redisTemplate.opsForZSet().add(onlineUsersKey, userId, now);
        return updateAndGetPopulation(roomId);
    }

    @Override
    public RoomPopulationDTO userLeftRoom(String roomId, String userId) {
        if (isRoomArchived(roomId)) return getArchivedRoomPopulation(roomId);
        String onlineUsersKey = String.format(KEY_ROOM_ONLINE_USERS, roomId);
        String activeUsersKey = String.format(KEY_ROOM_ACTIVE_USERS, roomId);

        redisTemplate.opsForZSet().remove(onlineUsersKey, userId);
        redisTemplate.opsForZSet().remove(activeUsersKey, userId);

        return updateAndGetPopulation(roomId);
    }

    @Override
    public RoomPopulationDTO userBecomesActiveInRoom(String roomId, String userId) {
        if (isRoomArchived(roomId)) return getArchivedRoomPopulation(roomId);
        long now = System.currentTimeMillis();
        String activeUsersKey = String.format(KEY_ROOM_ACTIVE_USERS, roomId);
        redisTemplate.opsForZSet().add(activeUsersKey, userId, now);
        return updateAndGetPopulation(roomId);
    }

    @Override
    public RoomPopulationDTO userBecomesInactiveInRoom(String roomId, String userId) {
        if (isRoomArchived(roomId)) return getArchivedRoomPopulation(roomId);
        String activeUsersKey = String.format(KEY_ROOM_ACTIVE_USERS, roomId);
        redisTemplate.opsForZSet().remove(activeUsersKey, userId);
        return updateAndGetPopulation(roomId);
    }

    // =======================
    // ROOM POPULATION
    // =======================

    private boolean isRoomArchived(String roomId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(KEY_ARCHIVED_ROOMS, roomId));
    }

    private RoomPopulationDTO getArchivedRoomPopulation(String roomId) {
        long totalMessages = getMessageCount(roomId);
        long messagesLastHour = getMessagesLastHour(roomId);
        return new RoomPopulationDTO(Long.parseLong(roomId), getRoomName(roomId), 0L, 0L, totalMessages,
                determineNoiseLevel(messagesLastHour), true);
    }

    @Override
    public void markRoomAsArchived(String roomId) {
        redisTemplate.opsForSet().add(KEY_ARCHIVED_ROOMS, roomId);
        redisTemplate.opsForZSet().remove(KEY_LEADERBOARD_ONLINE, roomId);
        redisTemplate.opsForZSet().remove(KEY_LEADERBOARD_ACTIVE, roomId);
        redisTemplate.delete(String.format(KEY_ROOM_ONLINE_USERS, roomId));
        redisTemplate.delete(String.format(KEY_ROOM_ACTIVE_USERS, roomId));
    }

    @Override
    public void markRoomAsUnarchived(String roomId) {
        redisTemplate.opsForSet().remove(KEY_ARCHIVED_ROOMS, roomId);
    }

    private RoomPopulationDTO updateAndGetPopulation(String roomId) {
        long now = System.currentTimeMillis();
        String onlineUsersKey = String.format(KEY_ROOM_ONLINE_USERS, roomId);
        String activeUsersKey = String.format(KEY_ROOM_ACTIVE_USERS, roomId);

        // Online: count ALL members (event-driven via connect/disconnect)
        Long onlineCount = redisTemplate.opsForZSet().zCard(onlineUsersKey);
        // Active: count only users whose last heartbeat is within the timeout window
        Long activeCount = redisTemplate.opsForZSet().count(activeUsersKey, now - ACTIVE_TIMEOUT_MS, Double.MAX_VALUE);

        onlineCount = onlineCount != null ? onlineCount : 0L;
        activeCount = activeCount != null ? activeCount : 0L;

        // Update leaderboards
        redisTemplate.opsForZSet().add(KEY_LEADERBOARD_ONLINE, roomId, onlineCount);
        redisTemplate.opsForZSet().add(KEY_LEADERBOARD_ACTIVE, roomId, activeCount);

        String roomName = getRoomName(roomId);

        long messagesLastHour = getMessagesLastHour(roomId);
        ChatRoomNoiseLevelEnum noiseLevel = determineNoiseLevel(messagesLastHour);
        long totalMessages = getMessageCount(roomId);

        return new RoomPopulationDTO(Long.parseLong(roomId), roomName, activeCount, onlineCount, totalMessages,
                noiseLevel, false);
    }

    @Override
    public RoomPopulationDTO getRoomPopulation(String roomId) {
        if (isRoomArchived(roomId)) return getArchivedRoomPopulation(roomId);
        long now = System.currentTimeMillis();
        String onlineUsersKey = String.format(KEY_ROOM_ONLINE_USERS, roomId);
        String activeUsersKey = String.format(KEY_ROOM_ACTIVE_USERS, roomId);

        // Online: count ALL members (event-driven via connect/disconnect)
        Long onlineCount = redisTemplate.opsForZSet().zCard(onlineUsersKey);
        // Active: count only users whose last heartbeat is within the timeout window
        Long activeCount = redisTemplate.opsForZSet().count(activeUsersKey, now - ACTIVE_TIMEOUT_MS, Double.MAX_VALUE);

        onlineCount = onlineCount != null ? onlineCount : 0L;
        activeCount = activeCount != null ? activeCount : 0L;

        String roomName = getRoomName(roomId);

        long messagesLastHour = getMessagesLastHour(roomId);
        ChatRoomNoiseLevelEnum noiseLevel = determineNoiseLevel(messagesLastHour);
        long totalMessages = getMessageCount(roomId);

        return new RoomPopulationDTO(Long.parseLong(roomId), roomName, activeCount, onlineCount, totalMessages,
                noiseLevel, false);
    }

    // =======================
    // LEADERBOARDS
    // =======================

    @Override
    public List<RoomPopulationDTO> getTopActiveRooms(int topN) {
        return getTopRoomsFromLeaderboard(KEY_LEADERBOARD_ACTIVE, topN);
    }

    @Override
    public List<RoomPopulationDTO> getTopOnlineRooms(int topN) {
        return getTopRoomsFromLeaderboard(KEY_LEADERBOARD_ONLINE, topN);
    }

    private List<RoomPopulationDTO> getTopRoomsFromLeaderboard(String leaderboardKey, int topN) {
        Set<String> roomIds = redisTemplate.opsForZSet().reverseRange(leaderboardKey, 0, topN - 1);

        if (roomIds == null || roomIds.isEmpty()) {
            return Collections.emptyList();
        }

        return roomIds.stream().map(this::getRoomPopulation).filter(room -> room.onlineUsersCount() > 0)
                .collect(Collectors.toList());
    }

    // =======================
    // CLEANUP
    // =======================

    @Override
    public void resetRoomPopulationData() {
        Set<String> keysToDelete = redisTemplate.keys("room:*:online");

        Set<String> activeKeys = redisTemplate.keys("room:*:active");
        keysToDelete.addAll(activeKeys);

        Set<String> metadataKeys = redisTemplate.keys("room:*:metadata");
        keysToDelete.addAll(metadataKeys);

        Set<String> leaderboardKeys = redisTemplate.keys("leaderboard:*");
        keysToDelete.addAll(leaderboardKeys);

        Set<String> messageKeys = redisTemplate.keys("room:*:messages");
        keysToDelete.addAll(messageKeys);

        Set<String> messageCountKeys = redisTemplate.keys("room:*:message_count");
        keysToDelete.addAll(messageCountKeys);

        Set<String> reactionKeys = redisTemplate.keys("room:*:top_reactions");
        keysToDelete.addAll(reactionKeys);

        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
    }

    @Override
    public Page<RoomPopulationDTO> getChatRoomLeaderboard(int page, int pageSize, List<String> roomsToExclude,
                                                          RoomPopularitySort roomPopularitySort, ChatRoomNoiseLevelEnum chatRoomNoiseLevelEnum) {
        // Normalize inputs
        if (roomsToExclude == null)
            roomsToExclude = Collections.emptyList();
        if (roomPopularitySort == null)
            roomPopularitySort = RoomPopularitySort.ACTIVE;

        String leaderboardKey = roomPopularitySort == RoomPopularitySort.ACTIVE ? KEY_LEADERBOARD_ACTIVE
                : KEY_LEADERBOARD_ONLINE;

        return getPaginatedRoomsFromLeaderboard(leaderboardKey, page, pageSize, roomsToExclude, roomPopularitySort,
                chatRoomNoiseLevelEnum);
    }

    private Page<RoomPopulationDTO> getPaginatedRoomsFromLeaderboard(String leaderboardKey, int page, int pageSize,
                                                                     List<String> specialRooms, RoomPopularitySort popularitySort,
                                                                     ChatRoomNoiseLevelEnum chatRoomNoiseLevelEnum) {
        if (page < 0)
            page = 0;
        if (pageSize <= 0)
            pageSize = 10;
        if (pageSize > 100)
            pageSize = 100;

        Set<String> excludedRooms = (specialRooms == null) ? Collections.emptySet()
                : specialRooms.stream().map(String::toLowerCase).collect(Collectors.toSet());

        Set<String> allRoomIds = redisTemplate.opsForZSet().reverseRange(leaderboardKey, 0, -1);
        if (allRoomIds == null || allRoomIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, pageSize), 0);
        }

        List<RoomPopulationDTO> allRooms = allRoomIds.stream().map(this::getRoomPopulation)
                .filter(room -> room.onlineUsersCount() > 0)
                .filter(room -> !excludedRooms.contains(room.roomName().toLowerCase()))
                .filter(room -> chatRoomNoiseLevelEnum == null || room.noiseLevel().equals(chatRoomNoiseLevelEnum))
                .toList();

        long total = allRooms.size();

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allRooms.size());
        List<RoomPopulationDTO> rooms = startIndex >= allRooms.size() ? Collections.emptyList()
                : allRooms.subList(startIndex, endIndex);

        PageRequest pageable = PageRequest.of(page, pageSize, Sort.unsorted());
        return new PageImpl<>(rooms, pageable, total);
    }

    // =======================
    // REACTION TRACKING
    // =======================

    @Override
    public void incrementReactionCount(Long roomId, Long messageId) {
        String key = String.format("room:%s:top_reactions", roomId);
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(messageId), 1);
    }

    @Override
    public void decrementReactionCount(Long roomId, Long messageId) {
        String key = String.format("room:%s:top_reactions", roomId);
        Double newScore = redisTemplate.opsForZSet().incrementScore(key, String.valueOf(messageId), -1);
        if (newScore != null && newScore <= 0) {
            redisTemplate.opsForZSet().remove(key, String.valueOf(messageId));
        }
    }

    @Override
    public void removeMessageReactions(Long roomId, Long messageId) {
        String key = String.format("room:%s:top_reactions", roomId);
        redisTemplate.opsForZSet().remove(key, String.valueOf(messageId));
    }

    @Override
    public Page<TopReactedMessageDTO> getTopReactedMessages(Long roomId, int page, int pageSize) {
        if (page < 0)
            page = 0;
        if (pageSize <= 0)
            pageSize = 10;
        if (pageSize > 100)
            pageSize = 100;

        String key = String.format("room:%s:top_reactions", roomId);

        Long totalElements = redisTemplate.opsForZSet().zCard(key);
        if (totalElements == null || totalElements == 0) {
            return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, pageSize), 0);
        }

        int startIndex = page * pageSize;
        int endIndex = startIndex + pageSize - 1; // Redis ZREVRANGE is inclusive

        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, startIndex, endIndex);

        List<TopReactedMessageDTO> resultList = (tuples != null)
                ? tuples.stream()
                .map(tuple -> new TopReactedMessageDTO(Long.parseLong(tuple.getValue()), roomId,
                        tuple.getScore() != null ? tuple.getScore().longValue() : 0L))
                .toList()
                : Collections.emptyList();

        return new PageImpl<>(resultList, PageRequest.of(page, pageSize, Sort.unsorted()), totalElements);
    }

}
