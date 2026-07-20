package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.responses.MessageResponseDTO;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMapperReplyTest {

    private static final MessageResponseDTO BASE_DTO = new MessageResponseDTO(
            1L, "a reply", 5L, "room", 2L, "replier", null, null,
            false, false, null, null, null, List.of(), List.of(), null, null);

    // The generated mapping is exercised by the application context; here we test
    // the reply-visibility contract implemented in the interface default methods.
    private final MessageMapper mapper = message -> BASE_DTO;

    private Message parent(boolean deleted, boolean quarantined) {
        var sender = User.builder().id(7L).username("original-sender").displayColor("#ff0000").build();
        var parent = Message.builder()
                .id(99L)
                .content("original content")
                .sender(sender)
                .chatRoom(ChatRoom.builder().id(5L).build())
                .build();
        parent.setDeleted(deleted);
        parent.setQuarantined(quarantined);
        return parent;
    }

    private Message replyTo(Message parent) {
        return Message.builder().id(1L).content("a reply").replyTo(parent).build();
    }

    @Test
    void visibleParent_contentIncludedForEveryone() {
        var message = replyTo(parent(false, false));

        var regular = mapper.toMessageResponseDTO(message, false).replyTo();
        var staff = mapper.toMessageResponseDTO(message, true).replyTo();

        assertThat(regular.content()).isEqualTo("original content");
        assertThat(regular.deleted()).isFalse();
        assertThat(regular.id()).isEqualTo(99L);
        assertThat(regular.senderId()).isEqualTo(7L);
        assertThat(regular.senderUsername()).isEqualTo("original-sender");
        assertThat(regular.color()).isEqualTo("#ff0000");
        assertThat(staff.content()).isEqualTo("original content");
    }

    @Test
    void deletedParent_contentHiddenFromRegularUsers() {
        var message = replyTo(parent(true, false));

        var replyInfo = mapper.toMessageResponseDTO(message, false).replyTo();

        assertThat(replyInfo.content()).isNull();
        assertThat(replyInfo.deleted()).isTrue();
        assertThat(replyInfo.senderUsername()).isEqualTo("original-sender");
    }

    @Test
    void deletedParent_contentVisibleToStaff() {
        var message = replyTo(parent(true, false));

        var replyInfo = mapper.toMessageResponseDTO(message, true).replyTo();

        assertThat(replyInfo.content()).isEqualTo("original content");
        assertThat(replyInfo.deleted()).isTrue();
    }

    @Test
    void quarantinedParent_contentHiddenFromEveryone() {
        var message = replyTo(parent(false, true));

        var regular = mapper.toMessageResponseDTO(message, false).replyTo();
        var staff = mapper.toMessageResponseDTO(message, true).replyTo();

        assertThat(regular.content()).isNull();
        assertThat(regular.deleted()).isTrue();
        assertThat(staff.content()).isNull();
        assertThat(staff.deleted()).isTrue();
    }

    @Test
    void parentWithAttachment_flagsAttachment() {
        var parent = parent(false, false);
        parent.setAttachments(new java.util.ArrayList<>(List.of(new com.mk3.chatapp.models.Attachment())));
        var message = replyTo(parent);

        assertThat(mapper.toMessageResponseDTO(message, false).replyTo().hasAttachment()).isTrue();
    }

    @Test
    void deletedParentWithAttachment_attachmentHiddenFromRegularUsers() {
        var parent = parent(true, false);
        parent.setAttachments(new java.util.ArrayList<>(List.of(new com.mk3.chatapp.models.Attachment())));
        var message = replyTo(parent);

        assertThat(mapper.toMessageResponseDTO(message, false).replyTo().hasAttachment()).isFalse();
        assertThat(mapper.toMessageResponseDTO(message, true).replyTo().hasAttachment()).isTrue();
    }

    @Test
    void noParent_replyToStaysNull() {
        var message = Message.builder().id(1L).content("a reply").build();

        assertThat(mapper.toMessageResponseDTO(message, true).replyTo()).isNull();
        assertThat(mapper.toMessageResponseDTO(message, false).replyTo()).isNull();
    }
}
