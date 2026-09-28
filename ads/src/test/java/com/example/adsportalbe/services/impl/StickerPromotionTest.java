package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.dto.promotion.PromoteMessageRequestDto;
import com.example.adsportalbe.services.PaymentService;
import com.mk3.chatapp.enums.Role;
import com.mk3.chatapp.models.Message;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StickerPromotionTest {
    @Mock private MessageRepository messages;
    @Mock private PaymentService payments;
    @InjectMocks private PromotedMessageServiceImpl service;

    @Test
    void standaloneStickersAreRejectedBeforeAnyPaymentAuthorization() {
        var user = User.builder().id(1L).role(Role.USER).claimed(true).build();
        var message = Message.builder().id(8L).sender(user).content("").stickerId("pepe").build();
        when(messages.findById(message.getId())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.promoteMessage(new PromoteMessageRequestDto(8L, "payment-method"), user))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Sticker messages cannot be promoted");
        verifyNoInteractions(payments);
    }
}
