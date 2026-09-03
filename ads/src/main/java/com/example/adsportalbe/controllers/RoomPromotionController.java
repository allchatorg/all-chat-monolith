package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.promotion.CancelRequestDto;
import com.example.adsportalbe.dto.roompromotion.PromoteRoomRequestDto;
import com.example.adsportalbe.dto.roompromotion.RoomPromotionDetailDto;
import com.example.adsportalbe.dto.roompromotion.RoomPromotionDto;
import com.example.adsportalbe.enums.RoomPromotionStatus;
import com.example.adsportalbe.services.RoomPromotionService;
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
@RequestMapping("/api/v1/ads-portal/room-promotions")
@RequiredArgsConstructor
public class RoomPromotionController {

    private final RoomPromotionService roomPromotionService;
    // Resolve the current user via chat's SecurityService — @AuthenticationPrincipal
    // does NOT work under the merged session auth (see AdController).
    private final SecurityService securityService;

    @PostMapping
    public ResponseEntity<RoomPromotionDetailDto> promoteRoom(@RequestBody PromoteRoomRequestDto request)
            throws StripeException {
        User user = requireUser();
        if (!user.isClaimed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account must be claimed to promote a room");
        }
        if (user.getRole().isStaffMember()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Staff members cannot promote rooms");
        }

        RoomPromotionDetailDto result = roomPromotionService.promoteRoom(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<Page<RoomPromotionDto>> getMyRoomPromotions(
            @RequestParam(required = false) RoomPromotionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = requireUser();
        Page<RoomPromotionDto> result = roomPromotionService.getUserPromotions(user, status, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoomPromotionDetailDto> getRoomPromotionById(@PathVariable Long id) {
        User user = requireUser();
        RoomPromotionDetailDto result = roomPromotionService.getById(id, user);
        return ResponseEntity.ok(result);
    }


    @PostMapping("/{id}/request-cancel")
    public ResponseEntity<RoomPromotionDetailDto> requestCancelRoomPromotion(
            @PathVariable Long id, @RequestBody CancelRequestDto request) {
        User user = requireUser();
        RoomPromotionDetailDto result = roomPromotionService.requestCancelByUser(id, request.reason(), user);
        return ResponseEntity.ok(result);
    }

    private User requireUser() {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user;
    }
}
