package com.mk3.chatapp.services;

import com.mk3.chatapp.dtos.responses.TypingUpdateDTO;
import com.mk3.chatapp.enums.WebSocketMessageType;
import com.mk3.chatapp.events.TypingAccessChangedEvent;
import com.mk3.chatapp.events.IdVerificationRequiredEvent;
import com.mk3.chatapp.models.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Ephemeral state, scoped to the same process as Spring's simple message broker. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TypingService {
    public static final String DESTINATION = "/app/chat.typing";
    public static final String TOPIC_PREFIX = "/topic/chat-typing.";
    private static final long LEASE_MS = 10_000;
    private static final long REFRESH_MS = 4_000;

    private final TypingAccessService accessService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Watch> watches = new HashMap<>();
    private final Map<String, Activity> sessions = new HashMap<>();
    private final Map<RoomUser, Map<String, Activity>> users = new HashMap<>();
    private final Map<RoomUser, Long> lastBroadcast = new HashMap<>();
    private long accessRevision;

    private record Watch(String sessionId, String subscriptionId, Long userId, Long roomId, String ipAddress) {}
    private record RoomUser(Long roomId, Long userId) {}
    private record Activity(RoomUser key, String username, long expiresAt) {}

    public boolean subscribe(String sessionId, String subscriptionId, Long userId, Long roomId, String ipAddress) {
        long revision;
        synchronized (this) { revision = accessRevision; }
        if (sessionId == null || subscriptionId == null || username(userId, roomId, ipAddress) == null) return false;
        synchronized (this) {
            if (revision != accessRevision) return false;
            stopSession(sessionId);
            watches.put(sessionId, new Watch(sessionId, subscriptionId, userId, roomId, ipAddress));
        }
        return true;
    }

    public synchronized void unsubscribe(String sessionId, String subscriptionId) {
        var watch = watches.get(sessionId);
        if (watch != null && Objects.equals(watch.subscriptionId(), subscriptionId)) {
            watches.remove(sessionId);
            stopSession(sessionId);
        }
    }

    // Outbound authorization is an O(1) memory lookup, never a query per recipient or heartbeat.
    public synchronized boolean canReceive(String sessionId, String subscriptionId, String destination) {
        var watch = watches.get(sessionId);
        return watch != null && Objects.equals(watch.subscriptionId(), subscriptionId)
                && (TOPIC_PREFIX + watch.roomId()).equals(destination);
    }

    public void update(String sessionId, Long userId, Long roomId, boolean typing) {
        Watch watch;
        long revision;
        synchronized (this) {
            revision = accessRevision;
            watch = watches.get(sessionId);
            if (watch == null || !Objects.equals(watch.userId(), userId) || !Objects.equals(watch.roomId(), roomId)) return;
            if (!typing) {
                stopSession(sessionId);
                return;
            }
            var previous = sessions.get(sessionId);
            if (previous != null && previous.expiresAt() - LEASE_MS > System.currentTimeMillis() - REFRESH_MS) return;
        }

        String username = username(userId, roomId, watch.ipAddress());
        synchronized (this) {
            if (watches.get(sessionId) != watch || revision != accessRevision) return;
            if (username == null) {
                watches.remove(sessionId);
                stopSession(sessionId);
                return;
            }
            long now = System.currentTimeMillis();
            var key = new RoomUser(roomId, userId);
            var activity = new Activity(key, username, now + LEASE_MS);
            sessions.put(sessionId, activity);
            users.computeIfAbsent(key, ignored -> new HashMap<>()).put(sessionId, activity);
            if (now - lastBroadcast.getOrDefault(key, 0L) >= REFRESH_MS) broadcast(key, username, now);
        }
    }

    @EventListener
    public synchronized void disconnected(SessionDisconnectEvent event) {
        watches.remove(event.getSessionId());
        stopSession(event.getSessionId());
    }

    @Scheduled(fixedDelay = 1_000)
    public synchronized void expire() {
        long now = System.currentTimeMillis();
        sessions.entrySet().stream().filter(entry -> entry.getValue().expiresAt() <= now)
                .map(Map.Entry::getKey).toList().forEach(this::stopSession);
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void accessChanged(TypingAccessChangedEvent event) {
        java.util.List<Watch> affected;
        synchronized (this) {
            accessRevision++;
            affected = watches.values().stream()
                    .filter(watch -> event.userId() == null || event.userId().equals(watch.userId()))
                    .filter(watch -> event.chatRoomId() == null || event.chatRoomId().equals(watch.roomId())).toList();
        }
        for (var watch : affected) {
            if (username(watch.userId(), watch.roomId(), watch.ipAddress()) != null) continue;
            synchronized (this) {
                if (watches.get(watch.sessionId()) != watch) continue;
                watches.remove(watch.sessionId());
                stopSession(watch.sessionId());
            }
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void verificationRequired(IdVerificationRequiredEvent event) {
        // The existing notification is itself sent after commit; listen to its original domain event.
        accessChanged(new TypingAccessChangedEvent(event.userId(), null));
    }

    private String username(Long userId, Long roomId, String ipAddress) {
        try {
            return accessService.authorizedUsername(userId, roomId, ipAddress);
        } catch (RuntimeException exception) {
            log.debug("Typing access unavailable for user {} in room {}", userId, roomId, exception);
            return null;
        }
    }

    private void stopSession(String sessionId) {
        var activity = sessions.remove(sessionId);
        if (activity == null) return;
        var members = users.get(activity.key());
        members.remove(sessionId);
        if (members.isEmpty()) users.remove(activity.key());
        broadcast(activity.key(), activity.username(), System.currentTimeMillis());
    }

    // All state mutation and publication share the same monitor, ordering starts/stops across tabs.
    private void broadcast(RoomUser key, String username, long now) {
        var members = users.get(key);
        long expiry = members == null ? now : members.values().stream()
                .mapToLong(Activity::expiresAt).max().orElse(now);
        long remaining = Math.max(0, expiry - now);
        if (remaining > 0) lastBroadcast.put(key, now);
        else lastBroadcast.remove(key);
        try {
            messagingTemplate.convertAndSend(TOPIC_PREFIX + key.roomId(), WebSocketMessage.builder()
                    .type(WebSocketMessageType.TYPING_UPDATE)
                    .data(new TypingUpdateDTO(key.roomId(), key.userId(), username, remaining > 0, remaining)).build());
        } catch (RuntimeException exception) {
            // Missing typing updates expire on clients; they must never fail a message or access change.
            log.debug("Unable to publish typing activity", exception);
        }
    }
}
