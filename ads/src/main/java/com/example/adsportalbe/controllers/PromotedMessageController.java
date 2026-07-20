package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.promotion.CancelRequestDto;
import com.example.adsportalbe.dto.promotion.PromoteMessageRequestDto;
import com.example.adsportalbe.dto.promotion.PromotedMessageDetailDto;
import com.example.adsportalbe.dto.promotion.PromotedMessageDto;
import com.example.adsportalbe.dto.promotion.PromotionSpendSummaryDto;
import com.example.adsportalbe.enums.PromotedMessageStatus;
import com.example.adsportalbe.services.PromotedMessageService;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.SecurityService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/ads-portal/promoted-messages")
@RequiredArgsConstructor
public class PromotedMessageController {

    private final PromotedMessageService promotedMessageService;
    // Resolve the current user via chat's SecurityService — @AuthenticationPrincipal
    // does NOT work under the merged session auth (see AdController).
    private final SecurityService securityService;

    @PostMapping
    public ResponseEntity<PromotedMessageDetailDto> promoteMessage(@RequestBody PromoteMessageRequestDto request)
            throws StripeException {
        User user = requireUser();
        if (!user.isClaimed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account must be claimed to promote a message");
        }

        PromotedMessageDetailDto result = promotedMessageService.promoteMessage(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<Page<PromotedMessageDto>> getMyPromotedMessages(
            @RequestParam(required = false) PromotedMessageStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = requireUser();
        Page<PromotedMessageDto> result = promotedMessageService.getUserPromotions(user, status, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/summary")
    public ResponseEntity<PromotionSpendSummaryDto> getMyPromotionSpendSummary() {
        User user = requireUser();
        return ResponseEntity.ok(promotedMessageService.getSpendSummary(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotedMessageDetailDto> getPromotedMessageById(@PathVariable Long id) {
        User user = requireUser();
        PromotedMessageDetailDto result = promotedMessageService.getById(id, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PromotedMessageDetailDto> cancelPromotedMessage(@PathVariable Long id)
            throws StripeException {
        User user = requireUser();
        PromotedMessageDetailDto result = promotedMessageService.cancelByUser(id, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/request-cancel")
    public ResponseEntity<PromotedMessageDetailDto> requestCancelPromotedMessage(
            @PathVariable Long id, @RequestBody CancelRequestDto request) {
        User user = requireUser();
        PromotedMessageDetailDto result = promotedMessageService.requestCancelByUser(id, request.reason(), user);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePromotedMessage(@PathVariable Long id) {
        User user = requireUser();
        promotedMessageService.deleteByUser(id, user);
        return ResponseEntity.noContent().build();
    }

    private User requireUser() {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user;
    }
}
