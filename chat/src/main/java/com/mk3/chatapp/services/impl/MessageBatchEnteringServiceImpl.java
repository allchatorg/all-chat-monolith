package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.UserChatRoom;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.ChatRoomRepository;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.services.MessageBatchEnteringService;
import com.mk3.chatapp.services.UserCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageBatchEnteringServiceImpl implements MessageBatchEnteringService {

    private final UserCreationService userCreationService;
    private final ChatRoomRepository chatRoomRepository;
    private final UserChatRoomRepository userChatRoomRepository;
    private final MessageRepository messageRepository;

    @Override
    @Transactional
    public void createBulkTestMessages() {
        if (messageRepository.count() > 0) {
            log.info("Messages already exist, skipping bulk generation.");
            return;
        }

        int roomCount = 3;
        int messagesPerRoom = 10_000;
        int totalMessages = roomCount * messagesPerRoom;

        log.info("⏳ Starting bulk message insertion ({} messages across {} chatrooms)...",
                totalMessages, roomCount);
        long startTime = System.currentTimeMillis();

        // Create test users for messages
        List<User> testUsers = userCreationService.createTestUsers(10);

        // Create 3 test chatrooms
        List<ChatRoom> testRooms = createTestChatrooms(roomCount);

        // Link users to all test chatrooms
        linkUsersToRooms(testUsers, testRooms);

        // Insert numbered messages per room
        int batchSize = 1000; // Insert in batches for better performance

        for (int roomIndex = 0; roomIndex < testRooms.size(); roomIndex++) {
            ChatRoom room = testRooms.get(roomIndex);
            log.info("⏳ Inserting {} numbered messages into room '{}' ({}/{})...",
                    messagesPerRoom, room.getName(), roomIndex + 1, roomCount);

            insertMessagesForRoom(room, testUsers, messagesPerRoom, batchSize);

            log.info("✅ Completed room '{}' ({}/{})", room.getName(), roomIndex + 1, roomCount);
        }

        long endTime = System.currentTimeMillis();
        double durationSeconds = (endTime - startTime) / 1000.0;
        log.info("✅ Bulk message insertion completed! Total time: {:.2f} seconds", durationSeconds);
        log.info("📊 Insertion rate: {:.0f} messages/second", totalMessages / durationSeconds);
    }

    private List<ChatRoom> createTestChatrooms(int count) {
        List<ChatRoom> rooms = new ArrayList<>();
        String[] roomNames = {"Test Room Alpha", "Test Room Beta", "Test Room Gamma"};

        for (int i = 0; i < count; i++) {
            ChatRoom room = ChatRoom.builder()
                    .name(roomNames[i])
                    .build();
            rooms.add(chatRoomRepository.save(room));
        }

        log.info("✅ Created {} test chatrooms", count);
        return rooms;
    }

    private void linkUsersToRooms(List<User> users, List<ChatRoom> rooms) {
        for (User user : users) {
            for (ChatRoom room : rooms) {
                userChatRoomRepository.save(
                        UserChatRoom.builder()
                                .user(user)
                                .chatRoom(room)
                                .build());
            }
        }
        log.info("✅ Linked {} users to {} rooms", users.size(), rooms.size());
    }

    private void insertMessagesForRoom(ChatRoom room, List<User> users, int totalMessages, int batchSize) {
        Random random = new Random();
        List<Message> messageBatch = new ArrayList<>();

        // Generate messages with timestamps spread over the last 30 days
        Instant now = Instant.now();
        Instant startTime = now.minus(30, ChronoUnit.DAYS);
        long timeRangeMillis = ChronoUnit.MILLIS.between(startTime, now);

        for (int i = 0; i < totalMessages; i++) {
            // Select random user
            User sender = users.get(random.nextInt(users.size()));

            // Generate random timestamp within the range
            long randomOffset = (long) (random.nextDouble() * timeRangeMillis);
            Instant messageTime = startTime.plusMillis(randomOffset);

            String content = String.valueOf(i + 1);

            Message message = Message.builder()
                    .chatRoom(room)
                    .sender(sender)
                    .content(content)
                    .build();

            // Override the createdAt timestamp (since it's auto-generated)
            message.setCreatedAt(messageTime);

            messageBatch.add(message);

            // Save batch when it reaches the batch size
            if (messageBatch.size() >= batchSize) {
                messageRepository.saveAll(messageBatch);
                messageBatch.clear();

                // Log progress every 10k messages
                if ((i + 1) % 10_000 == 0) {
                    log.info("   Progress: {}/{} messages ({:.1f}%)",
                            i + 1, totalMessages, ((i + 1) * 100.0) / totalMessages);
                }
            }
        }

        // Save any remaining messages
        if (!messageBatch.isEmpty()) {
            messageRepository.saveAll(messageBatch);
        }
    }
}
