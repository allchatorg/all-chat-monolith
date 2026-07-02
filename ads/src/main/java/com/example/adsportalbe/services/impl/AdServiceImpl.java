package com.example.adsportalbe.services.impl;

import com.example.adsportalbe.dto.ad.*;
import com.example.adsportalbe.dto.payment.PaymentMethodDto;
import com.example.adsportalbe.dto.requests.AdSearchRequestDto;
import com.example.adsportalbe.enums.AdStatus;
import com.example.adsportalbe.mappers.AdMapper;
import com.example.adsportalbe.models.ad.*;
import com.mk3.chatapp.models.identity.User;
import com.example.adsportalbe.models.payment.PaymentReceipt;
import com.example.adsportalbe.repositories.AdFormatRepository;
import com.example.adsportalbe.repositories.AdRepository;
import com.example.adsportalbe.repositories.PaymentReceiptRepository;
import com.example.adsportalbe.services.AdCacheService;
import com.example.adsportalbe.services.AdService;
import com.example.adsportalbe.services.MailService;
import com.example.adsportalbe.services.PaymentService;
import com.example.adsportalbe.specifications.AdSpecification;
import com.example.adsportalbe.utils.Utils;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdServiceImpl implements AdService {

    private final AdRepository adRepository;
    private final PaymentReceiptRepository paymentReceiptRepository;
    private final AdFormatRepository adFormatRepository;
    private final PaymentService paymentService;
    private final MailService mailService;
    private final AdCacheService adCacheService;
    private final AdMapper adMapper;

    @Override
    @Transactional
    public Ad createAd(CreateAdRequestDto request, User user) throws StripeException {
        // 0. Only claimed accounts may create ads (defense-in-depth; the controller
        //    also rejects unclaimed/guest sessions with a 403).
        if (user == null || !user.isClaimed()) {
            throw new IllegalStateException("Account must be claimed to create an ad");
        }

        // 1. Validate Input Basics
        if (request.getTitle() == null || request.getTitle().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }

        if (request.getViewsBought() == null || request.getViewsBought() <= 0) {
            throw new IllegalArgumentException("Views bought must be greater than 0");
        }

        // 2. Fetch Ad Format
        AdFormat format = adFormatRepository.findByType(request.getAdType())
                .orElseThrow(() -> new IllegalArgumentException("Invalid Ad Format Type: " + request.getAdType()));

        // 3. Calculate Price Service-Side to Validate
        double calculatedPrice = calculateAdCost(format, request.getText(), request.getViewsBought());

        // Validate Price deviation (allow small float diff)
        if (Math.abs(calculatedPrice - request.getCalculatedPrice()) > 0.1) {
            throw new IllegalArgumentException("Price mismatch. Server calculated: " + calculatedPrice
                    + ", Client sent: " + request.getCalculatedPrice());
        }

        // 4. Validate Specific Logic
        if (request.getAdType() == AdFormatType.PHOTO
                && (request.getImageUrl() == null || request.getImageUrl().isEmpty())) {
            throw new IllegalArgumentException("Image URL is required for PHOTO ads");
        }
        if (request.getAdType() == AdFormatType.VIDEO
                && (request.getVideoUrl() == null || request.getVideoUrl().isEmpty())) {
            throw new IllegalArgumentException("Video URL is required for VIDEO ads");
        }

        // 5. Authorize Payment
        long amountCents = (long) (calculatedPrice * 100);
        String paymentIntentId = paymentService.authorizePayment(user, request.getStripeId(), amountCents,
                request.getStripeAid());

        // 6. Create Ad Entity
        Ad ad = Ad.builder()
                .title(request.getTitle())
                .owner(user)
                .format(format)
                .imageUrl(request.getImageUrl())
                .videoUrl(request.getVideoUrl())
                .textContent(request.getText())
                .totalViewsBought(request.getViewsBought())
                .servedViews(0)
                .totalCost(calculatedPrice)
                .status(AdStatus.SUBMITTED)
                .submittedAt(Instant.now())
                .build();

        // Snapshot the format (persisted via cascade from Ad.selectedFormats)
        AdFormatSnapshot snapshot = AdFormatSnapshot.builder()
                .originalFormatId(format.getId())
                .type(format.getType())
                .title(format.getTitle())
                .description(format.getDescription())
                .pricePerMille(format.getPricePerMille())
                .build();

        ad.setSelectedFormats(List.of(snapshot));

        // Create Receipt
        PaymentReceipt.PaymentReceiptBuilder receiptBuilder = PaymentReceipt.builder()
                .ad(ad)
                .stripePaymentIntentId(paymentIntentId)
                .amountPaid(calculatedPrice)
                .currency("USD")
                .status("AUTHORIZED")
                .provider("STRIPE");

        PaymentMethodDto paymentMethodDto = paymentService.getPaymentMethod(request.getStripeId());
        if (paymentMethodDto != null) {
            receiptBuilder.cardBrand(paymentMethodDto.getBrand());
            receiptBuilder.cardLast4(paymentMethodDto.getLast4());
            receiptBuilder.cardholderName(paymentMethodDto.getCardholderName());
        }

        PaymentReceipt receipt = receiptBuilder.build();

        ad.setReceipt(receipt);

        return adRepository.save(ad);
    }

    private double calculateAdCost(AdFormat format, String text, int views) {
        double baseCPM = format.getPricePerMille();
        double textCPM = 0;

        List<TextPricingTierRule> textTiers = format.getTextPricingTiers();

        // If not TEXT, we need the TEXT format tiers
        if (format.getType() != AdFormatType.TEXT) {
            AdFormat textFormat = adFormatRepository.findByType(AdFormatType.TEXT).orElse(null);
            if (textFormat != null && textFormat.getTextPricingTiers() != null) {
                textTiers = textFormat.getTextPricingTiers();
            }
        }

        if (textTiers != null && !textTiers.isEmpty() && text != null && !text.isEmpty()) {
            int charCount = text.length();
            // Sort by maxCharacters asc
            List<TextPricingTierRule> sortedTiers = new ArrayList<>(textTiers);
            sortedTiers.sort(Comparator.comparingInt(TextPricingTierRule::getMaxCharacters));

            TextPricingTierRule matchedTier = sortedTiers.stream()
                    .filter(tier -> charCount <= tier.getMaxCharacters())
                    .findFirst()
                    .orElse(null);

            if (matchedTier != null) {
                textCPM = matchedTier.getPricePerMille();
            } else {
                // Exceeds all? use last
                textCPM = sortedTiers.get(sortedTiers.size() - 1).getPricePerMille();
            }
        }

        double totalCPM = baseCPM + textCPM;
        return (views / 1000.0) * totalCPM;
    }

    @Override
    public Page<AdDto> searchAds(AdSearchRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request cannot be null");
        }

        List<Sort.Order> sortOrders = Utils.jsonStringToSortOrder(request.sort());

        // Default sort if none provided or empty
        if (sortOrders.isEmpty()) {
            sortOrders.add(Sort.Order.asc("submittedAt"));
        }

        PageRequest pageRequest = PageRequest.of(
                request.page(),
                request.size(),
                Sort.by(sortOrders));

        Specification<Ad> specification = AdSpecification.getSpecification(request);

        return adRepository.findAll(specification, pageRequest).map(adMapper::toDto);
    }

    @Override
    public List<AdStatusCountDto> getAdStatusCounts() {
        return adRepository.getAdStatusCounts();
    }

    @Override
    public List<AdStatusCountDto> getAdStatusCountsByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        return adRepository.getAdStatusCountsByUserId(userId);
    }

    @Override
    public AdDetailedViewDto getAdById(Long id, User user) {
        Ad ad = adRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ad not found with id: " + id));

        // Access control: non-staff users can only view their own ads. Uses
        // isStaffMember() rather than == Role.USER so UNCLAIMED_USER / GUEST are
        // also scoped (see ads-role-checks-hierarchy).
        if (!user.getRole().isStaffMember()) {
            if (!ad.getOwner().getId().equals(user.getId())) {
                throw new RuntimeException("Access denied: You can only view your own ads");
            }
        }
        return adMapper.toDetailedDto(ad);
    }

    @Override
    @Transactional
    public AdDetailedViewDto rejectAd(Long adId, String rejectionReason) throws StripeException {
        // 1. Fetch ad by ID
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new RuntimeException("Ad not found with id: " + adId));

        // 2. Validate ad is in SUBMITTED status
        if (ad.getStatus() != AdStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only ads with SUBMITTED status can be rejected. Current status: " + ad.getStatus());
        }

        // 3. Update ad status to REJECTED
        ad.setStatus(AdStatus.REJECTED);

        // 4. Set rejection reason
        ad.setRejectionReason(rejectionReason);

        // 5. Cancel payment authorization
        PaymentReceipt receipt = ad.getReceipt();
        if (receipt != null && receipt.getStripePaymentIntentId() != null) {
            try {
                paymentService.cancelPaymentAuthorization(receipt.getStripePaymentIntentId());

                // 6. Update payment receipt status
                receipt.setStatus("CANCELLED");
            } catch (StripeException e) {
                log.error("Failed to cancel payment for ad {}: {}", adId, e.getMessage());
                throw e;
            }
        } else {
            log.warn("No payment receipt found for ad {}", adId);
        }

        // 7. Save the ad
        Ad savedAd = adRepository.save(ad);

        // 8. Send rejection email to ad owner
        try {
            mailService.sendAdRejectionEmail(ad.getOwner(), ad.getTitle(), rejectionReason);
        } catch (Exception e) {
            log.error("Failed to send rejection email for ad {}: {}", adId, e.getMessage());
            // Don't throw - rejection should still succeed even if email fails
        }

        // 9. Return the rejected ad as AdDetailedViewDto
        return adMapper.toDetailedDto(savedAd);
    }

    @Override
    @Transactional
    public AdDetailedViewDto approveAd(Long adId) throws StripeException {
        // 1. Fetch ad by ID
        Ad ad = adRepository.findById(adId)
                .orElseThrow(() -> new RuntimeException("Ad not found with id: " + adId));

        // 2. Validate ad is in SUBMITTED status
        if (ad.getStatus() != AdStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only ads with SUBMITTED status can be approved. Current status: " + ad.getStatus());
        }

        // 3. Capture payment
        PaymentReceipt receipt = ad.getReceipt();
        if (receipt != null && receipt.getStripePaymentIntentId() != null) {
            try {
                paymentService.capturePayment(receipt.getStripePaymentIntentId());

                // 4. Update payment receipt status
                receipt.setStatus("CAPTURED");
                receipt.setPaidAt(Instant.now());
            } catch (StripeException e) {
                log.error("Failed to capture payment for ad {}: {}", adId, e.getMessage());
                throw e;
            }
        } else {
            throw new IllegalStateException("No payment receipt found for ad " + adId);
        }

        // 5. Update ad status to ACTIVE
        ad.setStatus(AdStatus.ACTIVE);

        // 6. Set approval timestamp
        ad.setApprovedAt(Instant.now());

        // 7. Save the ad
        Ad savedAd = adRepository.save(ad);

        // 8. Push to Cache
        try {
            adCacheService.cacheAd(adMapper.toCachedAd(savedAd));
        } catch (Exception e) {
            log.error("Failed to cache ad {} during approval. It will be picked up by reconciliation.", adId, e);
        }

        // 9. Send approval email to ad owner
        try {
            mailService.sendAdApprovalEmail(ad.getOwner(), ad.getTitle());
        } catch (Exception e) {
            log.error("Failed to send approval email for ad {}: {}", adId, e.getMessage());
            // Don't throw - approval should still succeed even if email fails
        }

        // 10. Return the approved ad as AdDetailedViewDto
        return adMapper.toDetailedDto(savedAd);
    }

    @Override
    @Transactional
    public Ad save(Ad ad) {
        return adRepository.save(ad);
    }

    @Override
    @Transactional
    public List<Ad> saveAll(List<Ad> ads) {
        return adRepository.saveAll(ads);
    }

    @Override
    public List<Ad> findAllById(Iterable<Long> ids) {
        return adRepository.findAllById(ids);
    }

    @Override
    public List<Ad> findAllByOwnerId(Long ownerId) {
        return adRepository.findAllByOwnerId(ownerId);
    }

    @Override
    public PurchasedAdsDailyCountDto getPurchasedAdsDailyCounts(LocalDate fromDate) {
        // Default to 3 months ago if no date provided
        LocalDate effectiveFromDate = fromDate != null ? fromDate : LocalDate.now().minusMonths(3);
        LocalDate toDate = LocalDate.now();

        // Convert to Instant for query (start of day for fromDate, end of day for
        // toDate)
        Instant fromInstant = effectiveFromDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant toInstant = toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        List<Ad> approvedAds = adRepository.findApprovedAdsBetweenDates(fromInstant, toInstant);

        // Group ads by date and count
        Map<LocalDate, Long> countsByDate = approvedAds.stream()
                .collect(Collectors.groupingBy(
                        ad -> ad.getApprovedAt().atZone(ZoneId.systemDefault()).toLocalDate(),
                        Collectors.counting()));

        // Convert to list of DailyPurchaseCount, sorted by date
        List<PurchasedAdsDailyCountDto.DailyPurchaseCount> dailyCounts = countsByDate.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> PurchasedAdsDailyCountDto.DailyPurchaseCount.builder()
                        .date(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .collect(Collectors.toList());

        long totalCount = approvedAds.size();

        return PurchasedAdsDailyCountDto.builder()
                .dailyCounts(dailyCounts)
                .totalCount(totalCount)
                .build();
    }

    @Override
    public RevenueDto getDailyRevenueStats() {
        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();

        Instant activeTodayStart = today.atStartOfDay(zoneId).toInstant();
        Instant activeTodayEnd = Instant.now();

        Instant yesterdayStart = today.minusDays(1).atStartOfDay(zoneId).toInstant();
        Instant yesterdayEnd = activeTodayStart.minusSeconds(1);

        Double todayRevenue = paymentReceiptRepository.sumAmountPaidByPaidAtBetween(activeTodayStart, activeTodayEnd);
        Double yesterdayRevenue = paymentReceiptRepository.sumAmountPaidByPaidAtBetween(yesterdayStart, yesterdayEnd);

        return RevenueDto.builder()
                .todayRevenue(todayRevenue != null ? todayRevenue : 0.0)
                .yesterdayRevenue(yesterdayRevenue != null ? yesterdayRevenue : 0.0)
                .build();
    }

    @Override
    public MonthlyRevenueResponseDto getMonthlyRevenueStats() {
        int currentYear = LocalDate.now().getYear();

        // Fetch monthly revenue from repository
        List<Object[]> monthlyData = paymentReceiptRepository.findMonthlyRevenue(currentYear);

        // Create a map for easy lookup
        Map<Integer, Double> revenueByMonth = monthlyData.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).intValue(), // month number
                        row -> row[1] != null ? ((Number) row[1]).doubleValue() : 0.0 // revenue
                ));

        // Month abbreviations
        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        // Build data for all 12 months
        List<MonthlyRevenueDto> data = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            data.add(MonthlyRevenueDto.builder()
                    .month(monthNames[i - 1])
                    .revenue(revenueByMonth.getOrDefault(i, 0.0))
                    .build());
        }

        return MonthlyRevenueResponseDto.builder()
                .data(data)
                .build();
    }

    @Override
    public WeeklyRevenueResponseDto getWeeklyRevenueStats() {
        LocalDate today = LocalDate.now();
        ZoneId zoneId = ZoneId.systemDefault();

        // Calculate start of the last 7 days (including today)
        LocalDate startDate = today.minusDays(6);
        Instant startInstant = startDate.atStartOfDay(zoneId).toInstant();
        Instant endInstant = today.plusDays(1).atStartOfDay(zoneId).toInstant();

        // Fetch daily revenue from repository
        List<Object[]> dailyData = paymentReceiptRepository.findDailyRevenueForDateRange(startInstant, endInstant);

        // Create a map for easy lookup by LocalDate
        Map<LocalDate, Double> revenueByDate = dailyData.stream()
                .collect(Collectors.toMap(
                        row -> {
                            if (row[0] instanceof java.sql.Date) {
                                return ((java.sql.Date) row[0]).toLocalDate();
                            } else if (row[0] instanceof LocalDate) {
                                return (LocalDate) row[0];
                            }
                            return null;
                        },
                        row -> row[1] != null ? ((Number) row[1]).doubleValue() : 0.0));

        // Day abbreviations in order (Mon, Tue, Wed, Thu, Fri, Sat, Sun)
        String[] dayNames = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        // Build data for the last 7 days
        List<WeeklyRevenueDto> data = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            int dayOfWeek = date.getDayOfWeek().getValue(); // 1 (Monday) to 7 (Sunday)

            data.add(WeeklyRevenueDto.builder()
                    .day(dayNames[dayOfWeek - 1])
                    .revenue(revenueByDate.getOrDefault(date, 0.0))
                    .build());
        }

        return WeeklyRevenueResponseDto.builder()
                .data(data)
                .build();
    }
}
