package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.promotion.PromotedRevenueDailyResponseDto;
import com.example.adsportalbe.dto.promotion.PromotedRevenueSummaryDto;
import com.example.adsportalbe.dto.promotion.PromotionReasonRequestDto;
import com.example.adsportalbe.dto.roompromotion.BanRoomPromotionsSummaryDto;
import com.example.adsportalbe.dto.roompromotion.RoomPromotionDetailDto;
import com.example.adsportalbe.dto.roompromotion.RoomPromotionDto;
import com.example.adsportalbe.dto.roompromotion.RoomPromotionSearchRequestDto;
import com.example.adsportalbe.services.RoomPromotionService;
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
@RequestMapping("/api/v1/ads-portal/admin/room-promotions")
@RequiredArgsConstructor
@PreAuthorize("@security.isAdmin()")
public class AdminRoomPromotionController {

    private final RoomPromotionService roomPromotionService;
    private final SecurityService securityService;

    @GetMapping
    public ResponseEntity<Page<RoomPromotionDto>> searchRoomPromotions(
            @ModelAttribute RoomPromotionSearchRequestDto request) {
        Page<RoomPromotionDto> result = roomPromotionService.searchPromotions(request);
        return ResponseEntity.ok(result);
    }

    // Staff-level (not admin) because moderators can ban users and need this
    // summary in the ban form; the method-level check overrides the class-level one.
    // Super-admin only, matching the promoted-message revenue endpoints
    @GetMapping("/revenue/summary")
    @PreAuthorize("@security.isSuperAdmin()")
    public ResponseEntity<PromotedRevenueSummaryDto> getRoomPromotionRevenueSummary() {
        return ResponseEntity.ok(roomPromotionService.getRevenueSummary());
    }

    @GetMapping("/revenue/daily")
    @PreAuthorize("@security.isSuperAdmin()")
    public ResponseEntity<PromotedRevenueDailyResponseDto> getRoomPromotionRevenueDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
        return ResponseEntity.ok(roomPromotionService.getDailyRevenue(fromDate));
    }

    @GetMapping("/ban-summary/{userId}")
    @PreAuthorize("@security.isStaffMember()")
    public ResponseEntity<BanRoomPromotionsSummaryDto> getBanRoomPromotionsSummary(@PathVariable Long userId) {
        BanRoomPromotionsSummaryDto result = roomPromotionService.getBanPromotionsSummary(userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomPromotionDetailDto> getRoomPromotionById(@PathVariable Long id) {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        RoomPromotionDetailDto result = roomPromotionService.getById(id, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<RoomPromotionDetailDto> approveRoomPromotion(@PathVariable Long id)
            throws StripeException {
        RoomPromotionDetailDto result = roomPromotionService.approve(id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/deny")
    public ResponseEntity<RoomPromotionDetailDto> denyRoomPromotion(
            @RequestBody PromotionReasonRequestDto request) throws StripeException {
        RoomPromotionDetailDto result = roomPromotionService.deny(request.promotionId(), request.reason());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/cancel")
    public ResponseEntity<RoomPromotionDetailDto> cancelRoomPromotion(
            @RequestBody PromotionReasonRequestDto request) throws StripeException {
        RoomPromotionDetailDto result = roomPromotionService.cancelByAdmin(request.promotionId(),
                request.reason());
        return ResponseEntity.ok(result);
    }
}
