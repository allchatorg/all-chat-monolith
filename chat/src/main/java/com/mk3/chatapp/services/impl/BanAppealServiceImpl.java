package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.requests.BanAppealRequestDTO;
import com.mk3.chatapp.dtos.requests.BanAppealResolutionRequestDTO;
import com.mk3.chatapp.dtos.responses.BanAppealAdminDetailDTO;
import com.mk3.chatapp.dtos.responses.BanAppealAdminListDTO;
import com.mk3.chatapp.dtos.responses.BanAppealUserViewDTO;
import com.mk3.chatapp.dtos.responses.MyBanContextDTO;
import com.mk3.chatapp.enums.BanAppealDecision;
import com.mk3.chatapp.enums.BanAppealStatus;
import com.mk3.chatapp.exceptions.ConflictException;
import com.mk3.chatapp.exceptions.ForbiddenException;
import com.mk3.chatapp.exceptions.NotFoundException;
import com.mk3.chatapp.mappers.BanMapper;
import com.mk3.chatapp.models.Ban;
import com.mk3.chatapp.models.BanAppeal;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.BanAppealRepository;
import com.mk3.chatapp.repositories.BanRepository;
import com.mk3.chatapp.repositories.UserRepository;
import com.mk3.chatapp.services.AuditLogService;
import com.mk3.chatapp.services.BanAppealService;
import com.mk3.chatapp.services.BanService;
import com.mk3.chatapp.services.MailSenderService;
import com.mk3.chatapp.services.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BanAppealServiceImpl implements BanAppealService {

    private static final String USER_STATUS_IN_REVIEW = "IN_REVIEW";

    private final BanAppealRepository banAppealRepository;
    private final BanRepository banRepository;
    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final BanService banService;
    private final AuditLogService auditLogService;
    private final MailSenderService mailSenderService;
    private final BanMapper banMapper;

    @Override
    @Transactional(readOnly = true)
    public MyBanContextDTO getMyBanContext() {
        User currentUser = securityService.getCurrentUser();

        Ban ban = banRepository.findByUserAndActiveIs(currentUser, true)
                .orElseThrow(() -> new NotFoundException("No active ban found"));

        BanAppeal appeal = banAppealRepository.findByBan_Id(ban.getId()).orElse(null);
        boolean appealable = appeal == null && !ban.getReportType().isCsamRelated();

        return new MyBanContextDTO(
                banMapper.toUserFacingDto(ban),
                appealable,
                appeal != null ? toUserView(appeal) : null);
    }

    @Override
    @Transactional
    public BanAppealUserViewDTO submitAppeal(BanAppealRequestDTO request) {
        User currentUser = securityService.getCurrentUser();

        Ban ban = banRepository.findByUserAndActiveIs(currentUser, true)
                .orElseThrow(() -> new NotFoundException("No active ban found"));

        if (ban.getReportType().isCsamRelated()) {
            throw new ForbiddenException("This ban is not eligible for appeal.");
        }

        if (banAppealRepository.findByBan_Id(ban.getId()).isPresent()) {
            throw new ConflictException("An appeal has already been submitted for this ban.");
        }

        BanAppeal appeal = BanAppeal.builder()
                .ban(ban)
                .user(currentUser)
                .appealText(request.appealText().trim())
                .whatWillChange(normalize(request.whatWillChange()))
                .status(BanAppealStatus.PENDING)
                .build();

        try {
            appeal = banAppealRepository.saveAndFlush(appeal);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("An appeal has already been submitted for this ban.");
        }

        try {
            mailSenderService.sendBanAppealReceivedEmail(currentUser);
        } catch (Exception e) {
            log.warn("Failed to send ban appeal received email for appeal {}", appeal.getId(), e);
        }

        return toUserView(appeal);
    }

    @Override
    @Transactional
    public BanAppealUserViewDTO getMyAppeal() {
        User currentUser = securityService.getCurrentUser();

        BanAppeal appeal = banAppealRepository.findFirstByUser_IdOrderByCreatedAtDesc(currentUser.getId())
                .orElseThrow(() -> new NotFoundException("No appeal found"));

        // A temporary ban may have lapsed (or been revoked) while the appeal sat in the
        // queue; resolve it as EXPIRED on read so the user never sees a stale "in review".
        if (appeal.getStatus().isOpen() && isBanLapsed(appeal.getBan())) {
            appeal.setStatus(BanAppealStatus.EXPIRED);
            appeal.setResolvedAt(Instant.now());
            appeal = banAppealRepository.save(appeal);
        }

        return toUserView(appeal);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@security.isAdmin()")
    public Page<BanAppealAdminListDTO> listAppeals(BanAppealStatus status, boolean openOnly, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.ASC, "createdAt"));

        Page<BanAppeal> appeals;
        if (status != null) {
            appeals = banAppealRepository.findAllByStatus(status, pageable);
        } else if (openOnly) {
            appeals = banAppealRepository.findAllByStatusIn(
                    List.of(BanAppealStatus.PENDING, BanAppealStatus.UNDER_REVIEW), pageable);
        } else {
            appeals = banAppealRepository.findAll(pageable);
        }

        return appeals.map(this::toAdminListDto);
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("@security.isAdmin()")
    public BanAppealAdminDetailDTO getAppeal(Long appealId) {
        return toAdminDetailDto(findAppeal(appealId));
    }

    @Override
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public BanAppealAdminDetailDTO claimAppeal(Long appealId) {
        BanAppeal appeal = findAppeal(appealId);

        if (!appeal.getStatus().isOpen()) {
            throw new ConflictException("This appeal has already been resolved.");
        }

        User currentUser = securityService.getCurrentUser();
        appeal.setStatus(BanAppealStatus.UNDER_REVIEW);
        appeal.setReviewerUserId(currentUser.getId());
        appeal.setClaimedAt(Instant.now());

        return toAdminDetailDto(banAppealRepository.save(appeal));
    }

    @Override
    @Transactional
    @PreAuthorize("@security.isAdmin()")
    public BanAppealAdminDetailDTO resolveAppeal(Long appealId, BanAppealResolutionRequestDTO request) {
        BanAppeal appeal = findAppeal(appealId);

        if (!appeal.getStatus().isOpen()) {
            throw new ConflictException("This appeal has already been resolved.");
        }

        Ban ban = appeal.getBan();
        User targetUser = appeal.getUser();

        if (isBanLapsed(ban)) {
            appeal.setStatus(BanAppealStatus.EXPIRED);
            appeal.setResolvedAt(Instant.now());
            banAppealRepository.save(appeal);
            throw new ConflictException("The ban has already lapsed; the appeal was marked as expired.");
        }

        boolean approved = request.decision() == BanAppealDecision.APPROVED;

        if (approved) {
            // Reuses cache eviction, Quartz cancellation and the REVOKE_BAN audit log;
            // systemRevokeBan flips this appeal to EXPIRED, overwritten below.
            banService.revokeBan(targetUser,
                    "Revoked ban for userId " + targetUser.getId() + " via approved ban appeal #" + appeal.getId());
        }

        User currentUser = securityService.getCurrentUser();
        appeal.setStatus(approved ? BanAppealStatus.APPROVED : BanAppealStatus.DENIED);
        appeal.setResolvedByUserId(currentUser.getId());
        appeal.setResolvedAt(Instant.now());
        appeal.setInternalNote(request.internalNote().trim());
        appeal.setUserFacingMessage(normalize(request.userFacingMessage()));
        appeal = banAppealRepository.save(appeal);

        auditLogService.logBanAppealResolve(
                approved ? "APPROVE_BAN_APPEAL" : "DENY_BAN_APPEAL",
                "Ban appeal #" + appeal.getId() + (approved ? " approved: " : " denied: ") + appeal.getInternalNote(),
                targetUser.getId(),
                appeal.getId(),
                ban.getId(),
                appeal.getStatus().name());

        sendDecisionEmail(targetUser, appeal, approved);

        return toAdminDetailDto(appeal);
    }

    private void sendDecisionEmail(User targetUser, BanAppeal appeal, boolean approved) {
        try {
            mailSenderService.sendBanAppealDecisionEmail(targetUser, approved, appeal.getUserFacingMessage());
        } catch (Exception e) {
            log.warn("Failed to send ban appeal decision email for appeal {}", appeal.getId(), e);
        }
    }

    private BanAppeal findAppeal(Long appealId) {
        return banAppealRepository.findById(appealId)
                .orElseThrow(() -> new NotFoundException("Appeal not found: " + appealId));
    }

    private boolean isBanLapsed(Ban ban) {
        return !ban.isActive() || (ban.getExpiresAt() != null && ban.getExpiresAt().isBefore(Instant.now()));
    }

    private BanAppealUserViewDTO toUserView(BanAppeal appeal) {
        BanAppealStatus status = appeal.getStatus();
        // PENDING and UNDER_REVIEW both render as IN_REVIEW so reviewer activity never leaks.
        String userStatus = status.isOpen() ? USER_STATUS_IN_REVIEW : status.name();
        boolean resolved = status == BanAppealStatus.APPROVED || status == BanAppealStatus.DENIED;

        return new BanAppealUserViewDTO(
                appeal.getId(),
                userStatus,
                appeal.getCreatedAt(),
                appeal.getResolvedAt(),
                appeal.getAppealText(),
                appeal.getWhatWillChange(),
                resolved ? appeal.getUserFacingMessage() : null);
    }

    private BanAppealAdminListDTO toAdminListDto(BanAppeal appeal) {
        Ban ban = appeal.getBan();
        User user = appeal.getUser();
        Long bannedByUserId = parseUserId(ban.getCreatedBy());

        return new BanAppealAdminListDTO(
                appeal.getId(),
                appeal.getStatus(),
                appeal.getCreatedAt(),
                user.getId(),
                user.getApplicationUsername(),
                ban.getId(),
                ban.getType(),
                ban.getReportType(),
                ban.getDescription(),
                bannedByUserId,
                resolveUsername(bannedByUserId),
                appeal.getReviewerUserId(),
                resolveUsername(appeal.getReviewerUserId()),
                appeal.getResolvedAt());
    }

    private BanAppealAdminDetailDTO toAdminDetailDto(BanAppeal appeal) {
        Ban ban = appeal.getBan();

        List<Ban> priorBans = banRepository.findAllByUser_IdOrderByCreatedAtDesc(appeal.getUser().getId()).stream()
                .filter(b -> !b.getId().equals(ban.getId()))
                .toList();

        return new BanAppealAdminDetailDTO(
                toAdminListDto(appeal),
                appeal.getAppealText(),
                appeal.getWhatWillChange(),
                appeal.getInternalNote(),
                appeal.getUserFacingMessage(),
                appeal.getResolvedByUserId(),
                resolveUsername(appeal.getResolvedByUserId()),
                ban.getCreatedAt(),
                ban.getExpiresAt(),
                ban.isActive(),
                priorBans.size(),
                priorBans.stream().map(banMapper::toDto).toList());
    }

    private String resolveUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(User::getApplicationUsername)
                .orElse("Unknown");
    }

    private Long parseUserId(String auditorId) {
        if (auditorId == null || auditorId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(auditorId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
