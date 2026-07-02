package com.example.adsportalbe.controllers;

import com.example.adsportalbe.dto.ServeAdRequestDto;
import com.example.adsportalbe.dto.ServedAdDto;
import com.example.adsportalbe.dto.ad.*;
import com.example.adsportalbe.dto.requests.AdSearchRequestDto;
import com.example.adsportalbe.models.ad.Ad;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.services.SecurityService;
import com.example.adsportalbe.services.AdService;
import com.example.adsportalbe.services.AdStatisticsService;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/ads-portal/ads")
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;
    private final AdStatisticsService adStatisticsService;
    // Resolve the current user via chat's SecurityService (auth name -> DB lookup).
    // @AuthenticationPrincipal does NOT work under the merged session auth: the Redis
    // session stores the principal as the user-id name, not a com.mk3.chatapp User,
    // so binding it as a User parameter always yields null. See SecurityService.
    private final SecurityService securityService;

    @PostMapping
    public ResponseEntity<CreateAdResponseDto> createAd(@RequestBody CreateAdRequestDto request)
            throws StripeException {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        // Only claimed accounts may create ads. Guest / unclaimed sessions must
        // claim their account first (the frontend surfaces a claim popup); this is
        // the server-side backstop so the flow can't be bypassed via the API.
        if (!user.isClaimed()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account must be claimed to create an ad");
        }

        Ad ad = adService.createAd(request, user);
        PaymentReceipt receipt = ad.getReceipt();

        CreateAdResponseDto.ReceiptDto receiptDto = receipt == null ? null
                : CreateAdResponseDto.ReceiptDto.builder()
                        .id(receipt.getId())
                        .amountPaid(receipt.getAmountPaid())
                        .currency(receipt.getCurrency())
                        .status(receipt.getStatus())
                        .cardBrand(receipt.getCardBrand())
                        .cardLast4(receipt.getCardLast4())
                        .build();

        CreateAdResponseDto response = CreateAdResponseDto.builder()
                .adId(ad.getId())
                .title(ad.getTitle())
                .status(ad.getStatus())
                .submittedAt(ad.getSubmittedAt())
                .receipt(receiptDto)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<AdDto>> searchAds(@ModelAttribute AdSearchRequestDto request) {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        // For non-staff users, enforce userId validation. Uses isStaffMember()
        // rather than an exact == Role.USER check so UNCLAIMED_USER / GUEST are
        // also scoped to their own ads (see ads-role-checks-hierarchy).
        if (!user.getRole().isStaffMember()) {
            // Check if userId is provided
            if (request.userId() == null) {
                return ResponseEntity.status(403)
                        .body(null); // Regular users must provide userId
            }

            // Verify the userId matches the authenticated user
            if (!request.userId().equals(user.getId())) {
                return ResponseEntity.status(403)
                        .body(null); // Regular users can only query their own ads
            }
        }
        // For admins, no restrictions - they can query any userId or no userId

        Page<AdDto> result = adService.searchAds(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status-counts")
    public ResponseEntity<List<AdStatusCountDto>> getAdStatusCounts() {
        List<AdStatusCountDto> result = adService.getAdStatusCounts();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status-counts-by-user")
    public ResponseEntity<List<AdStatusCountDto>> getAdStatusCountsByUserId() {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        Long userId = user.getId();
        List<AdStatusCountDto> result = adService.getAdStatusCountsByUserId(userId);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdDetailedViewDto> getAdById(@PathVariable Long id) {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        AdDetailedViewDto result = adService.getAdById(id, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/serve")
    public ResponseEntity<ServedAdDto> serveAd(@RequestBody ServeAdRequestDto request) {
        // Optional: validation for request.getUserId() vs authenticated user if needed
        // But the requirements say "accept a userid" which implies it might be used by
        // a system or the user itself.
        // Given the optional IP, I'll pass it through.

        log.info("Serving ad for user {} from IP {}", request.getUserId(), request.getIpAddress());

        ServedAdDto servedAd = adStatisticsService.serveAd(request.getUserId(), request.getIpAddress());


        log.info("Served ad: {}", servedAd);

        if (servedAd == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(servedAd);
    }

    @GetMapping("/{id}/daily-stats")
    public ResponseEntity<AdDailyStatsResponseDto> getAdDailyStats(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        AdDailyStatsResponseDto result = adStatisticsService.getAdDailyStats(id, fromDate, user);
        return ResponseEntity.ok(result);
    }

    /**
     * Gets a summary of the authenticated user's ad views including today's views,
     * yesterday's views, total views bought, and total served views across all ads.
     */
    @GetMapping("/my-stats/summary")
    public ResponseEntity<UserAdViewsSummaryDto> getUserAdViewsSummary() {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        UserAdViewsSummaryDto result = adStatisticsService.getUserAdViewsSummary(user);
        return ResponseEntity.ok(result);
    }

    /**
     * Gets a day-by-day breakdown of total views for all of the authenticated
     * user's ads.
     * Optionally filtered by fromDate.
     */
    @GetMapping("/my-stats/daily")
    public ResponseEntity<UserAdViewsDailyBreakdownDto> getUserAdViewsDailyBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
        User user = securityService.getCurrentUser();
        if (user == null) {
            throw new RuntimeException("User not found");
        }

        UserAdViewsDailyBreakdownDto result = adStatisticsService.getUserAdViewsDailyBreakdown(user, fromDate);
        return ResponseEntity.ok(result);
    }
}
