package com.mk3.chatapp.configs;

import com.mk3.chatapp.enums.AttachmentTypeEnum;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.MimeType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.AttachmentType;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Tag;
import com.mk3.chatapp.repositories.AttachmentTypeRepository;
import com.mk3.chatapp.repositories.ChatRoomRepository;
import com.mk3.chatapp.repositories.TagRepository;
import com.mk3.chatapp.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@RequiredArgsConstructor
@Component
@Slf4j
@Profile("prod")
public class BootstrapProd implements CommandLineRunner {

    private final ChatRoomRepository chatRoomRepository;
    private final AttachmentTypeRepository attachmentTypeRepository;
    private final TagRepository tagRepository;

    private final RoomActivityService roomActivityService;
    private final BanService banService;
    private final ChatRoomService chatRoomService;
    private final MessageSource messageSource;
    private final MessagesService messagesService;
    private final MessageBatchEnteringService messageBatchEnteringService;

    @Override
    @Transactional
    public void run(String... args) {
        if (isFirstStart()) {
            loadInitialData();
            chatRoomService.createStaffChatRooms();
        }

        chatRoomService.createDefaultPublicChatRooms();
        messageBatchEnteringService.createBulkTestMessages();
        roomActivityService.resetRoomPopulationData();
        initializeRoomsMessageCountsInRedis();
        reScheduleBans();
        fillRedisWithChatRoomsMetadata();

        log.info("Successfully started the application in PROD mode.");
    }

    private boolean isFirstStart() {
        return tagRepository.count() == 0;
    }

    private void fillRedisWithChatRoomsMetadata() {
        chatRoomRepository.findAll().forEach(room -> {
            // Private chats have no name, so there is no metadata to store for them.
            if (room.getType() == ChatRoomType.PRIVATE) {
                return;
            }
            roomActivityService.storeRoomMetadata(room.getId().toString(), room.getName());
            if (room.isArchived()) {
                roomActivityService.markRoomAsArchived(room.getId().toString());
            }
        });
    }

    public void loadInitialData() {

        log.info("⏳ Bootstrapping attachment types and tags...");

        List<String> tagNames = List.of("explicit", "gore/harm", "gross", "epilepsy", "jumpscare");
        Map<String, Tag> tagsMap = new HashMap<>();

        ChatRoom homeRoom = ChatRoom.builder()
                .name("Home")
                .build();
        chatRoomRepository.save(homeRoom);

        for (String tagName : tagNames) {
            Tag tag = tagRepository.findByNameIgnoreCase(tagName)
                    .orElseGet(() -> tagRepository.save(
                            Tag.builder()
                                    .name(tagName)
                                    .restrictedToAdults(tagName.equals("explicit") || tagName.equals("gore/harm"))
                                    .build()));
            tagsMap.put(tagName, tag);
        }

        saveAttachmentType(
                AttachmentTypeEnum.IMAGE,
                Set.of(
                        MimeType.PNG, MimeType.JPEG, MimeType.JPG,
                        MimeType.BMP, MimeType.SVG, MimeType.WEBP),
                10L * 1024 * 1024,
                Set.of(
                        tagsMap.get("explicit"),
                        tagsMap.get("gore/harm"),
                        tagsMap.get("gross")));

        saveAttachmentType(
                AttachmentTypeEnum.VIDEO,
                Set.of(
                        MimeType.MP4, MimeType.AVI, MimeType.MOV, MimeType.WEBM,
                        MimeType.MPEG, MimeType.GIF),
                10L * 1024 * 1024,
                Set.of(
                        tagsMap.get("explicit"),
                        tagsMap.get("gore/harm"),
                        tagsMap.get("gross"),
                        tagsMap.get("epilepsy"),
                        tagsMap.get("jumpscare")));

        saveAttachmentType(
                AttachmentTypeEnum.AUDIO,
                Set.of(MimeType.MP3, MimeType.OGG),
                10L * 1024 * 1024, // 10 MB
                Collections.emptySet());

        saveAttachmentType(
                AttachmentTypeEnum.FLASH,
                Set.of(MimeType.SWF),
                10L * 1024 * 1024,
                Set.of(
                        tagsMap.get("explicit"),
                        tagsMap.get("gore/harm"),
                        tagsMap.get("gross"),
                        tagsMap.get("epilepsy"),
                        tagsMap.get("jumpscare")));

        log.info("✅ Bootstrapping completed.");
    }

    private void reScheduleBans() {
        banService.reScheduleBans();
    }

    private void saveAttachmentType(
            AttachmentTypeEnum typeEnum,
            Set<MimeType> mimeTypes,
            Long maxFileSize,
            Set<Tag> tags) {
        if (attachmentTypeRepository.findByFileType(typeEnum).isPresent()) {
            log.info("⚠️ AttachmentType {} already exists. Skipping.", typeEnum);
            return;
        }

        AttachmentType attachmentType = AttachmentType.builder()
                .fileType(typeEnum)
                .acceptedMimeTypes(mimeTypes)
                .maxFileSizeBytes(maxFileSize)
                .availableTags(tags)
                .build();

        attachmentTypeRepository.save(attachmentType);
        log.info("✅ Created AttachmentType: {}", typeEnum);
    }

    private void initializeRoomsMessageCountsInRedis() {
        List<ChatRoom> chatRooms = chatRoomRepository.findAll();

        for (ChatRoom room : chatRooms) {
            var count = messagesService.getMessageCount(room.getId(), Role.GUEST);
            roomActivityService.setMessageCount(room.getId().toString(), count);
        }
    }

}
