package com.mk3.chatapp.mappers;

import com.mk3.chatapp.dtos.AttachmentDTO;
import com.mk3.chatapp.dtos.responses.PromotionInfoDTO;
import com.mk3.chatapp.mappers.impl.MessageHistoryTransformerImpl;
import com.mk3.chatapp.models.ChatRoom;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.MessageEditHistory;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageEditHistoryRepository;
import com.mk3.chatapp.services.impl.MessageEditHistoryServiceImpl;
import com.mk3.chatapp.services.impl.MessagesServiceImpl;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageStickerMappingTest {
    @Test
    void generatedMapperAndDtoCopiesPreserveSticker() {
        var mapper = Mappers.getMapper(MessageMapper.class);
        ReflectionTestUtils.setField(mapper, "reactionMapper", Mappers.getMapper(ReactionMapper.class));
        var message = Message.builder().id(1L).content("").stickerId("gondola")
                .sender(User.builder().id(2L).username("sender").build())
                .chatRoom(ChatRoom.builder().id(3L).build()).build();

        var dto = mapper.toMessageResponseDTO(message);

        assertThat(dto.stickerId()).isEqualTo("gondola");
        assertThat(dto.withReplyTo(null).stickerId()).isEqualTo("gondola");
        assertThat(dto.withPromotion(new PromotionInfoDTO(1L, "APPROVED")).stickerId()).isEqualTo("gondola");
    }

    @Test
    void editingArchivesStickerAndHistoryUsesSnapshot() {
        var repository = mock(MessageEditHistoryRepository.class);
        when(repository.save(any(MessageEditHistory.class))).thenAnswer(inv -> inv.getArgument(0));
        var sender = User.builder().id(2L).username("2").build();
        var message = Message.builder().id(1L).content("caption").stickerId("pepe").sender(sender).build();

        var history = new MessageEditHistoryServiceImpl(repository).save("caption", message, List.of(), sender);
        message.setStickerId(null);
        var dto = new MessageHistoryTransformerImpl(Mappers.getMapper(AttachmentMapper.class)).transform(history);

        assertThat(history.getStickerId()).isEqualTo("pepe");
        assertThat(dto.stickerId()).isEqualTo("pepe");
        assertThat(dto.content()).isEqualTo("caption");
        assertThat(Mappers.getMapper(MessageEditHistoryMapper.class).toDto(history).stickerId()).isEqualTo("pepe");
    }

    @Test
    void removingAttachmentFromResponsePreservesSticker() {
        var history = MessageEditHistory.builder().content("").stickerId("pepe")
                .message(Message.builder().id(1L).build()).build();
        var attachmentMapper = mock(AttachmentMapper.class);
        var attachment = new com.mk3.chatapp.models.Attachment();
        history.setAttachments(List.of(attachment));
        when(attachmentMapper.toDto(attachment))
                .thenReturn(new AttachmentDTO(10L, 1L, "image.png", null, null, null, null, null));
        var dto = new MessageHistoryTransformerImpl(attachmentMapper).transform(history);

        var filtered = MessagesServiceImpl.filterAttachments(dto, List.of(10L));

        assertThat(filtered.attachments()).isEmpty();
        assertThat(filtered.stickerId()).isEqualTo("pepe");
    }
}
