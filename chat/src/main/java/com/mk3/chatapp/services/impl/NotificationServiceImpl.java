package com.mk3.chatapp.services.impl;

import com.mk3.chatapp.dtos.responses.NotificationDTO;
import com.mk3.chatapp.enums.NotificationType;
import com.mk3.chatapp.events.NotificationCreatedEvent;
import com.mk3.chatapp.exceptions.ForbiddenException;
import com.mk3.chatapp.exceptions.NotFoundException;
import com.mk3.chatapp.mappers.NotificationMapper;
import com.mk3.chatapp.models.Notification;
import com.mk3.chatapp.models.identity.User;
import com.mk3.chatapp.repositories.NotificationRepository;
import com.mk3.chatapp.services.NotificationService;
import com.mk3.chatapp.services.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public NotificationDTO createAndSend(User recipient, NotificationType type, String title,
                                         String body, String metadata, String referenceType, Long referenceId) {
        Notification notification = Notification.builder()
                .user(recipient)
                .type(type)
                .title(title)
                .body(body)
                .metadata(metadata)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .build();

        NotificationDTO dto = notificationMapper.toDto(notificationRepository.save(notification));
        // Delivered by NotificationDeliveryListener after commit, so a client
        // reacting to the push can never read an uncommitted row.
        eventPublisher.publishEvent(new NotificationCreatedEvent(recipient.getId(), dto));
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationDTO> getMyNotifications(int page, int size) {
        User currentUser = requireCurrentUser();
        if (page < 0 || size <= 0 || size > 100) {
            throw new IllegalArgumentException("Invalid pagination parameters");
        }

        var pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return notificationRepository.findAllByUser_Id(currentUser.getId(), pageRequest)
                .map(notificationMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository.countByUser_IdAndReadAtIsNull(requireCurrentUser().getId());
    }

    @Override
    @Transactional
    public NotificationDTO markRead(Long notificationId) {
        Notification notification = findOwned(notificationId);
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        return notificationMapper.toDto(notification);
    }

    @Override
    @Transactional
    public NotificationDTO markUnread(Long notificationId) {
        Notification notification = findOwned(notificationId);
        notification.setReadAt(null);
        return notificationMapper.toDto(notification);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        findOwned(notificationId).setDeleted(true);
    }

    @Override
    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllRead(requireCurrentUser().getId(), Instant.now());
    }

    private Notification findOwned(Long notificationId) {
        return notificationRepository.findByIdAndUser_Id(notificationId, requireCurrentUser().getId())
                .orElseThrow(() -> new NotFoundException("Notification not found"));
    }

    private User requireCurrentUser() {
        User currentUser = securityService.getCurrentUser();
        if (currentUser == null) {
            throw new ForbiddenException("Authentication required");
        }
        return currentUser;
    }
}
