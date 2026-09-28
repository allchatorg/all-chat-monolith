package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.enums.FontPreset;
import com.mk3.chatapp.mappers.impl.MessageHistoryTransformerImpl;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.MessageEditHistory;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.impl.MessagesServiceImpl;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FontMappingTest {
    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);
    private final MessageMapper messageMapper = Mappers.getMapper(MessageMapper.class);

    @Test
    void generatedUserMappersExposeEffectiveFontsAndKeepHiddenMembershipPrivate() {
        var user = sender();
        assertThat(userMapper.toDto(user).usernameFont()).isEqualTo(FontPreset.INTER);
        assertThat(userMapper.toDto(user).messageFont()).isEqualTo(FontPreset.OPEN_SANS);
        assertThat(userMapper.toDto(user).proActive()).isNull();
        assertThat(userMapper.toOwnerDto(user).proActive()).isTrue();
        assertThat(userMapper.toOwnerDto(user).usernameFont()).isEqualTo(FontPreset.INTER);
        assertThat(userMapper.toMinimalDto(user).fontRevision()).isEqualTo(9);
        user.setProPaidThrough(Instant.now().minusSeconds(1));
        assertThat(userMapper.toDto(user).usernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(userMapper.toOwnerDto(user).messageFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(userMapper.toMinimalDto(user).usernameFont()).isEqualTo(FontPreset.DEFAULT);
    }

    @Test
    void currentProfileFontsReachHistoricalMessagesRepliesAndCopies() {
        var sender = sender();
        var parent = message(sender);
        var message = message(sender);
        message.setReplyTo(parent);
        ReflectionTestUtils.setField(messageMapper, "reactionMapper", mock(ReactionMapper.class));
        var dto = messageMapper.toMessageResponseDTO(message);
        assertThat(dto.senderUsernameFont()).isEqualTo(FontPreset.INTER);
        assertThat(dto.senderMessageFont()).isEqualTo(FontPreset.OPEN_SANS);
        assertThat(dto.senderFontRevision()).isEqualTo(9);
        assertThat(dto.replyTo().senderUsernameFont()).isEqualTo(FontPreset.INTER);
        assertThat(dto.replyTo().senderMessageFont()).isEqualTo(FontPreset.OPEN_SANS);
        assertThat(dto.withPromotion(null).senderFontRevision()).isEqualTo(9);
        assertThat(dto.withReplyTo(null).senderMessageFont()).isEqualTo(FontPreset.OPEN_SANS);
        sender.setUsernameFont(FontPreset.OPEN_SANS);
        sender.setFontRevision(10);
        assertThat(messageMapper.toMessageResponseDTO(message).senderUsernameFont()).isEqualTo(FontPreset.OPEN_SANS);
        sender.setProPaidThrough(Instant.now().minusSeconds(1));
        assertThat(messageMapper.toMessageResponseDTO(message).senderUsernameFont()).isEqualTo(FontPreset.DEFAULT);
        assertThat(messageMapper.toMessageResponseDTO(message).replyTo().senderMessageFont()).isEqualTo(FontPreset.DEFAULT);
    }

    @Test
    void historyAndAttachmentFilteringPreserveFontMetadata() {
        var sender = sender();
        var message = message(sender);
        var transformer = new MessageHistoryTransformerImpl(mock(AttachmentMapper.class));
        var history = MessageEditHistory.builder().message(message).content("earlier text").attachments(List.of()).build();
        var result = transformer.transform(history);
        assertThat(result.senderUsernameFont()).isEqualTo(FontPreset.INTER);
        assertThat(result.senderMessageFont()).isEqualTo(FontPreset.OPEN_SANS);
        assertThat(result.senderFontRevision()).isEqualTo(9);
        var attachment = new AttachmentDTO(1L, 1L, "a", 1L, "url", null, null, null);
        var withAttachment = new com.mk3.chatapp.dtos.responses.MessageResponseDTO(result.id(), result.content(), result.chatRoomId(),
                result.chatRoomName(), result.senderId(), result.senderUsername(), result.senderRole(), result.senderCountryCode(),
                result.senderIdVerificationStatus(), result.bannedUser(), result.deleted(), result.createdAt(), result.editedAt(),
                result.color(), List.of(attachment), result.reactions(), result.replyTo(), result.promotion(), result.senderProBadgeVisible(),
                result.senderProBadgeRevision(), result.senderUsernameFont(), result.senderMessageFont(), result.senderFontRevision());
        var filtered = MessagesServiceImpl.filterAttachments(withAttachment, List.of(1L));
        assertThat(filtered.attachments()).isEmpty();
        assertThat(filtered.senderFontRevision()).isEqualTo(9);
        assertThat(filtered.senderMessageFont()).isEqualTo(FontPreset.OPEN_SANS);
    }

    private static User sender() {
        return User.builder().id(7L).username("tester").proPaidThrough(Instant.now().plusSeconds(3600)).showProBadge(false)
                .usernameFont(FontPreset.INTER).messageFont(FontPreset.OPEN_SANS).fontRevision(9).build();
    }

    private static Message message(User sender) {
        var result = Message.builder().id(1L).sender(sender).content("hello")
                .chatRoom(ChatRoom.builder().id(5L).name("room").build()).build();
        result.setCreatedAt(Instant.parse("2020-01-01T00:00:00Z"));
        return result;
    }
}
