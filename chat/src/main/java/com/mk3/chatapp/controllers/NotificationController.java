package com.mk3.chatapp.controllers;

import com.mk3.chatapp.dtos.responses.NotificationDTO;
import com.mk3.chatapp.dtos.responses.UnreadCountDTO;
import com.mk3.chatapp.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Deliberately id-free ownership: every lookup is keyed off the authenticated
 * user, so one user can never read or mutate another user's notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "${app.FRONT_END.URL}", allowCredentials = "true")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.getMyNotifications(page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountDTO> getUnreadCount() {
        return ResponseEntity.ok(new UnreadCountDTO(notificationService.getUnreadCount()));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationDTO> markRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PatchMapping("/{id}/unread")
    public ResponseEntity<NotificationDTO> markUnread(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markUnread(id));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }
}
