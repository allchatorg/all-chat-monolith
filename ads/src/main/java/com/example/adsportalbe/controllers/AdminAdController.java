package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.ad.*;
import com.example.adsportalbe.dto.requests.AdSearchRequestDto;
import com.mk3.chatapp.models.identity.User;
import com.example.adsportalbe.services.AdService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ads-portal/admin/ads")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAdController {

    private final AdService adService;

    @GetMapping
    public ResponseEntity<Page<AdDto>> searchAds(@ModelAttribute AdSearchRequestDto request) {
        Page<AdDto> result = adService.searchAds(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status-counts")
    public ResponseEntity<List<AdStatusCountDto>> getAdStatusCounts() {
        List<AdStatusCountDto> result = adService.getAdStatusCounts();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status-counts/{userId}")
    public ResponseEntity<List<AdStatusCountDto>> getAdStatusCountsByUserId(@PathVariable Long userId) {
        List<AdStatusCountDto> result = adService.getAdStatusCountsByUserId(userId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/purchased-counts")
    public ResponseEntity<PurchasedAdsDailyCountDto> getPurchasedAdsCounts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
        PurchasedAdsDailyCountDto result = adService.getPurchasedAdsDailyCounts(fromDate);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/revenue/daily-summary")
    public ResponseEntity<RevenueDto> getDailyRevenue() {
        RevenueDto result = adService.getDailyRevenueStats();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/revenue/monthly")
    public ResponseEntity<MonthlyRevenueResponseDto> getMonthlyRevenue() {
        MonthlyRevenueResponseDto result = adService.getMonthlyRevenueStats();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/revenue/weekly")
    public ResponseEntity<WeeklyRevenueResponseDto> getWeeklyRevenue() {
        WeeklyRevenueResponseDto result = adService.getWeeklyRevenueStats();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdDetailedViewDto> getAdById(@PathVariable Long id,
                                                       @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        AdDetailedViewDto result = adService.getAdById(id, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/reject")
    public ResponseEntity<AdDetailedViewDto> rejectAd(@RequestBody AdRejectionRequestDto request)
            throws StripeException {
        AdDetailedViewDto result = adService.rejectAd(request.getAdId(), request.getRejectionReason());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/approve/{id}")
    public ResponseEntity<AdDetailedViewDto> approveAd(@PathVariable Long id) throws StripeException {
        AdDetailedViewDto result = adService.approveAd(id);
        return ResponseEntity.ok(result);
    }
}
