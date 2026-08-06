# Notification System — Backend Progress

Generic, Facebook-style notification system. V1 delivers WARNING notifications;
the design is generic so future types (server announcements, report-case
resolutions, ad updates) are one enum value + one `createAndSend` call.

## Checklist

- [x] `enums/NotificationType.java` — `WARNING` (+ future values documented)
- [x] `models/Notification.java` — entity `notifications` table, `readAt` nullable = unread, soft delete via `Base.deleted`
- [x] `repositories/NotificationRepository.java` — per-user lookups + bulk `markAllRead`
- [x] `dtos/responses/NotificationDTO.java` + `UnreadCountDTO.java`
- [x] `mappers/NotificationMapper.java` — MapStruct, `instantToString`
- [x] `events/NotificationCreatedEvent.java`
- [x] `services/NotificationService.java` + `impl/NotificationServiceImpl.java` — `createAndSend` producer entry point + user-facing list/read/unread/delete/read-all/unread-count
- [x] `listeners/NotificationDeliveryListener.java` — post-commit `@TransactionalEventListener` → STOMP `/topic/user.{id}`
- [x] `enums/WebSocketMessageType.java` — add `NOTIFICATION` (deprecate `WARN_USER`)
- [x] `models/WebSocketMessage.java` — register `NotificationDTO` in `@JsonSubTypes`
- [x] `controllers/NotificationController.java` — `/api/v1/notifications` (GET paged, GET /unread-count, PATCH /{id}/read, PATCH /{id}/unread, PATCH /read-all, DELETE /{id})
- [x] `AdminFacadeServiceImpl.warnUser` — replace `WARN_USER` broadcast with `notificationService.createAndSend(...)` (covers both admin + report-case warn paths)
- [x] Verify: `./mvnw -DskipTests verify`

## Contract (frozen once frontend starts)

`NotificationDTO`: `{ id, type, title, body, metadata, referenceType, referenceId, readAt, createdAt }`
(timestamps ISO strings, `readAt: null` = unread, `metadata` = opaque JSON string or null)

WS payload on `/topic/user.{userId}`:
`{ "type": "NOTIFICATION", "chatRoomName": null, "data": { "@type": "NOTIFICATION", ...NotificationDTO } }`
