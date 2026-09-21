package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.requests.CreateMessageRequestDTO;
import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.enums.ChatRoomType;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.mappers.MessageEditHistoryMapper;
import com.mk3.chatapp.mappers.MessageMapper;
import com.mk3.chatapp.models.Attachment;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import com.mk3.chatapp.repositories.UserChatRoomRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessagesServiceImplStickerTest {
    @Mock private MessageRepository messageRepository;
    @Mock private WebSocketBroadcastService webSocketBroadcastService;
    @Mock private MessageMapper messageMapper;
    @Mock private RoomActivityService roomActivityService;
    @Mock private MessageEditHistoryService messageEditHistoryService;
    @Mock private MessageEditHistoryMapper messageEditHistoryMapper;
    @Mock private AttachmentService attachmentService;
    @Mock private ChatRoomService chatRoomService;
    @Mock private UserChatRoomRepository userChatRoomRepository;
    @Mock private MessagePromotionEnrichmentService messagePromotionEnrichmentService;
    @Mock private UserRepository userRepository;
    @InjectMocks private MessagesServiceImpl service;

    private User sender;
    private ChatRoom room;

    @BeforeEach
    void setUp() {
        sender = User.builder().id(1L).role(Role.USER).username("sender").showProBadge(false).build();
        room = ChatRoom.builder().id(5L).name("room").type(ChatRoomType.PUBLIC).build();
        var entitlement = new ProBadgeService(userRepository, null, null);
        ReflectionTestUtils.setField(service, "proStickerService", new ProStickerService(entitlement));
    }

    private CreateMessageRequestDTO request(String content, String stickerId) {
        return new CreateMessageRequestDTO(content, room.getId(), null, null, stickerId);
    }

    private void activePro() {
        sender.setProPaidThrough(Instant.now().plusSeconds(3600));
        when(userRepository.findEligibleProPaidThrough(sender.getId()))
                .thenReturn(Optional.of(sender.getProPaidThrough()));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest
    @EnumSource(ChatRoomType.class)
    void activeProWithHiddenBadgeCanSendStickerOnlyInEitherRoomType(ChatRoomType type) {
        activePro();
        room.setType(type);

        Message result = service.saveMessage(request(null, "pepe"), sender, room);

        assertThat(sender.isProBadgeVisible()).isFalse();
        assertThat(sender.isProActive()).isTrue();
        assertThat(result.getStickerId()).isEqualTo("pepe");
        assertThat(result.getContent()).isEmpty();
        verify(userRepository).findEligibleProPaidThrough(sender.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wojak", "soyjak", "chud", "chad-1", "chad-2", "virgin", "doomer", "coomer",
            "bloomer", "zoomer", "npc", "grug", "pepe", "apu-apustaja", "honkler", "spurdo", "gondola"})
    void everyCatalogStickerCanAccompanyTextAndAnAttachment(String stickerId) {
        activePro();
        var attachment = new AttachmentDTO(10L, null, "image.png", 12L, "/image.png", null, null, null);
        var request = new CreateMessageRequestDTO("hello", room.getId(), List.of(attachment), null, stickerId);

        Message result = service.saveMessage(request, sender, room);

        assertThat(result.getStickerId()).isEqualTo(stickerId);
        assertThat(result.getContent()).isEqualTo("hello");
    }

    @ParameterizedTest
    @EnumSource(ChatRoomType.class)
    void unpaidUserCannotSendStickerEvenWithForgedCachedProState(ChatRoomType type) {
        room.setType(type);
        sender.setShowProBadge(true);
        sender.setProPaidThrough(Instant.now().plusSeconds(3600));
        when(userRepository.findEligibleProPaidThrough(sender.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveMessage(request("hello", "pepe"), sender, room))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void expiredPaidThroughCannotSendSticker() {
        when(userRepository.findEligibleProPaidThrough(sender.getId()))
                .thenReturn(Optional.of(Instant.now().minusSeconds(1)));

        assertThatThrownBy(() -> service.saveMessage(request("", "pepe"), sender, room))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(messageRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "PEPE", "../pepe", "https://example.com/sticker.png", "pepe ", "unknown"})
    void invalidStickerIdsFailBeforeEntitlementOrPersistence(String stickerId) {
        assertThatThrownBy(() -> service.saveMessage(request("hello", stickerId), sender, room))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(userRepository);
        verify(messageRepository, never()).save(any());
    }

    @Test
    void ordinaryMessagesDoNotRequireProAndEmptyMessagesStillFail() {
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.saveMessage(request("hello", null), sender, room).getStickerId()).isNull();
        assertThatThrownBy(() -> service.saveMessage(request(null, null), sender, room))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void captionCanBeClearedWithoutRemovingExistingStickerAfterProExpires() {
        var message = editableMessage();
        stubEditBroadcast(message);

        Message updated = service.editMessage(message.getId(), null, sender);

        assertThat(updated.getContent()).isEmpty();
        assertThat(updated.getStickerId()).isEqualTo("pepe");
        verify(messageEditHistoryService).save("caption", message, List.of(), sender);
        verifyNoInteractions(userRepository);
    }

    @Test
    void lastAttachmentCanBeRemovedWhenStickerRemains() {
        var message = editableMessage();
        message.setContent("");
        var attachment = new Attachment();
        attachment.setId(10L);
        message.setAttachments(new ArrayList<>(List.of(attachment)));
        stubEditBroadcast(message);

        assertThat(service.removeAttachmentFromMessage(message.getId(), 10L, sender).getStickerId()).isEqualTo("pepe");

        verify(attachmentService).softDeleteAttachments(List.of(10L));
    }

    @Test
    void unrelatedAttachmentCannotBeRemovedFromStickerMessage() {
        var message = editableMessage();
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.removeAttachmentFromMessage(message.getId(), 10L, sender))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("does not belong");
        verifyNoInteractions(attachmentService);
    }

    @Test
    void removingLastAttachmentWithoutTextOrStickerFailsBeforeDeletingIt() {
        var message = editableMessage();
        message.setContent("");
        message.setStickerId(null);
        var attachment = new Attachment();
        attachment.setId(10L);
        message.setAttachments(new ArrayList<>(List.of(attachment)));
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.removeAttachmentFromMessage(message.getId(), 10L, sender))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cannot be left empty");
        verifyNoInteractions(attachmentService);
    }

    @Test
    void deletedStickerMessagesCannotBeEdited() {
        var message = editableMessage();
        message.setDeleted(true);
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.editMessage(message.getId(), "new caption", sender))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Removed");
        verify(messageRepository, never()).save(any());
    }

    private Message editableMessage() {
        return Message.builder().id(11L).chatRoom(room).sender(sender).content("caption")
                .stickerId("pepe").attachments(new ArrayList<>()).build();
    }

    private void stubEditBroadcast(Message message) {
        var dto = new MessageResponseDTO(message.getId(), message.getContent(), room.getId(), room.getName(),
                sender.getId(), sender.getUsername(), sender.getRole(), null, null, false, false,
                null, null, null, List.of(), List.of(), null, null, false, 0, "pepe");
        when(messageRepository.findById(message.getId())).thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageMapper.toMessageResponseDTO(message)).thenReturn(dto);
        when(messagePromotionEnrichmentService.enrich(any(MessageResponseDTO.class))).thenAnswer(inv -> inv.getArgument(0));
        when(chatRoomService.findById(room.getId())).thenReturn(room);
    }
}
