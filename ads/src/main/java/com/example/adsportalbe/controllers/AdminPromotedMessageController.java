package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.promotion.*;
import com.example.adsportalbe.services.PromotedMessageService;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.SecurityService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/ads-portal/admin/promoted-messages")
@RequiredArgsConstructor
@PreAuthorize("@security.isAdmin()")
public class AdminPromotedMessageController {

    private final PromotedMessageService promotedMessageService;
    private final SecurityService securityService;

    @GetMapping
    public ResponseEntity<Page<PromotedMessageDto>> searchPromotedMessages(
            @ModelAttribute PromotedMessageSearchRequestDto request) {
        Page<PromotedMessageDto> result = promotedMessageService.searchPromotions(request);
        return ResponseEntity.ok(result);
    }

    // Staff-level (not admin) because moderators can ban users and need this
    // summary in the ban form; the method-level check overrides the class-level one.
    @GetMapping("/ban-summary/{userId}")
    @PreAuthorize("@security.isStaffMember()")
    public ResponseEntity<BanPromotionsSummaryDto> getBanPromotionsSummary(@PathVariable Long userId) {
        BanPromotionsSummaryDto result = promotedMessageService.getBanPromotionsSummary(userId);
        return ResponseEntity.ok(result);
    }

    // Super-admin only, matching the ad revenue endpoints in AdminAdController
    @GetMapping("/revenue/summary")
    @PreAuthorize("@security.isSuperAdmin()")
    public ResponseEntity<PromotedRevenueSummaryDto> getPromotedRevenueSummary() {
        return ResponseEntity.ok(promotedMessageService.getRevenueSummary());
    }

    @GetMapping("/revenue/daily")
    @PreAuthorize("@security.isSuperAdmin()")
    public ResponseEntity<PromotedRevenueDailyResponseDto> getPromotedRevenueDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
        return ResponseEntity.ok(promotedMessageService.getDailyRevenue(fromDate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PromotedMessageDetailDto> getPromotedMessageById(@PathVariable Long id) {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        PromotedMessageDetailDto result = promotedMessageService.getById(id, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<PromotedMessageDetailDto> approvePromotedMessage(@PathVariable Long id)
            throws StripeException {
        PromotedMessageDetailDto result = promotedMessageService.approve(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/deny")
    public ResponseEntity<PromotedMessageDetailDto> denyPromotedMessage(
            @RequestBody PromotionReasonRequestDto request) throws StripeException {
        PromotedMessageDetailDto result = promotedMessageService.deny(request.promotionId(), request.reason());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel")
    public ResponseEntity<PromotedMessageDetailDto> cancelPromotedMessage(
            @RequestBody PromotionReasonRequestDto request) throws StripeException {
        PromotedMessageDetailDto result = promotedMessageService.cancelByAdmin(request.promotionId(),
                request.reason());
        return ResponseEntity.ok(result);
    }
}
