package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.enums.PromotedMessageStatus;
import com.example.adsportalbe.models.promotion.PromotedMessage;
import com.example.adsportalbe.repositories.PromotedMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotedMessageStickerTest {
    @Mock private PromotedMessageRepository promotedMessageRepository;
    @InjectMocks private PromotedMessageServiceImpl service;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private User owner;
    private Message message;
    private PromotedMessage promotion;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(1L).username("owner").role(Role.USER).build();
        message = Message.builder().id(2L).content("").stickerId("pepe").sender(owner).build();
        promotion = PromotedMessage.builder().id(3L).message(message).owner(owner)
                .chatRoomId(4L).chatRoomName("room").status(PromotedMessageStatus.PENDING).build();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"USER", "ADMIN"})
    void ownerAndAdminDetailsIncludeStickerInJson(Role role) {
        when(promotedMessageRepository.findById(promotion.getId())).thenReturn(Optional.of(promotion));
        User viewer = role == Role.USER ? owner : User.builder().id(99L).role(role).build();

        var detail = service.getById(promotion.getId(), viewer);

        assertThat(detail.messageStickerId()).isEqualTo("pepe");
        assertThat(detail.messageContent()).isEmpty();
        assertThat(json.valueToTree(detail).path("messageStickerId").asText()).isEqualTo("pepe");
    }

    @Test
    void listingPreservesStickerAlongsideCaptionSnippet() {
        message.setContent("caption ".repeat(20));
        when(promotedMessageRepository.findByOwner_Id(eq(owner.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(promotion)));

        var summary = service.getUserPromotions(owner, null, 0, 20).getContent().getFirst();

        assertThat(summary.messageStickerId()).isEqualTo("pepe");
        assertThat(summary.messageContent()).hasSize(81).endsWith("…");
        assertThat(json.valueToTree(summary).path("messageStickerId").asText()).isEqualTo("pepe");
    }

    @Test
    void textOnlyPromotionKeepsNullSticker() {
        message.setStickerId(null);
        message.setContent("ordinary message");
        when(promotedMessageRepository.findById(promotion.getId())).thenReturn(Optional.of(promotion));

        var detail = service.getById(promotion.getId(), owner);

        assertThat(detail.messageStickerId()).isNull();
        assertThat(detail.messageContent()).isEqualTo("ordinary message");
        assertThat(json.valueToTree(detail).path("messageStickerId").isNull()).isTrue();
    }

    @Test
    void stickerDetailsRetainPromotionOwnershipCheck() {
        when(promotedMessageRepository.findById(promotion.getId())).thenReturn(Optional.of(promotion));
        var anotherUser = User.builder().id(99L).role(Role.USER).build();

        assertThatThrownBy(() -> service.getById(promotion.getId(), anotherUser))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Access denied");
    }
}
