package com.mk3.chatapp.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProStickerService {
    private static final Set<String> STICKER_IDS = Set.of(
            "wojak", "soyjak", "chud", "chad-1", "chad-2", "virgin", "doomer", "coomer",
            "bloomer", "zoomer", "npc", "grug", "pepe", "apu-apustaja", "honkler", "spurdo", "gondola");

    private final ProBadgeService proBadgeService;

    public void validateForSend(String stickerId, Long senderId) {
        if (stickerId == null) return;
        if (!STICKER_IDS.contains(stickerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown sticker");
        }
        if (!proBadgeService.hasActiveEntitlement(senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "allchat Pro is required to send this sticker");
        }
    }
}
