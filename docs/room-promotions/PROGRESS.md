# Room Promotions — Backend Progress

Feature: any claimed user pays a fixed $2.50 to promote any public chat room; admin approval (hold → capture / release);
rooms with ≥1 approved promotion are listed in a global "Promoted" list (newest approval first, never expires, max 25
pages); WS updates; portal management; revenue folded into the promoted series; ban / archive cleanup.
Full plan: `~/.claude/plans/unified-skipping-meteor.md` (Plan A)

## A1 — Enums, entity, repository, specification (ads module)

- [x] `enums/RoomPromotionStatus.java` (PENDING, APPROVED, DENIED, CANCELED)
- [x] `enums/PurchaseType.java` + `ROOM_PROMOTION`
- [x] `models/promotion/RoomPromotion.java` (table `room_promotions`, indexes `(chat_room_id, status)` and
  `(owner_id, chat_room_id, status)`, `@ManyToOne ChatRoom`, denormalized `chatRoomName`, unidirectional OneToOne receipt)
- [x] `dto/roompromotion/PromotedRoomRowDto.java` (`roomId`, `promotedAt`)
- [x] `repositories/RoomPromotionRepository.java` (`findPromotedRooms` constructor-expression JPQL with explicit
  `countQuery`, grouped by room, `order by max(approvedAt) desc`, archived rooms excluded; owner/room/status finders)
- [x] `specifications/RoomPromotionSpecification.java` (status, owner id, email like, chat room id)

## A2 — DTOs (ads module, `dto/roompromotion/`)

- [x] `PromoteRoomRequestDto`, `RoomPromotionDto`, `RoomPromotionDetailDto`, `RoomPromotionSearchRequestDto`,
  `BanRoomPromotionsSummaryDto`
- [x] Reuses `dto/promotion/PromotionReasonRequestDto` and `CancelRequestDto`

## A3 — Service (ads module)

- [x] `RoomPromotionService(-Impl)` — fee `ROOM_PROMOTION_COST_CENTS = 250L`; eligibility (claimed, PUBLIC, not archived,
  not staff-only, not a special room), one PENDING per (owner, room) → 409; hold / capture / release / refund matrix
  identical to promoted messages; ban + archive cleanup hooks; room summary; `getPromotedRooms`
- [x] WS `ROOM_PROMOTION_UPDATE` to `/topic/public-chat` **and** `/topic/user.{ownerId}` (try/catch)
- [x] Notifications `ROOM_PROMOTION_APPROVED / DENIED / CANCELED` (canceled only when `CanceledBy.ADMIN`),
  `referenceType "ROOM_PROMOTION"`

## A4 — Controllers (ads module)

- [x] `RoomPromotionController` — `/api/v1/ads-portal/room-promotions` (POST /, GET /, GET /{id},
  POST /{id}/request-cancel); user via `SecurityService.getCurrentUser()`; 403 when unclaimed or staff
- [x] `AdminRoomPromotionController` — `/api/v1/ads-portal/admin/room-promotions` (`@security.isAdmin()`;
  `/ban-summary/{userId}` is `@security.isStaffMember()`)

## A5 — Chat module: port, DTOs, WS type, notification types, list endpoint

- [x] `services/RoomPromotionPort.java` (+ nested `PromotedRoom`, `RoomPromotionOutcome`, `RoomPromotionsSummary`)
- [x] `dtos/responses/RoomPromotionEventDTO.java`, `dtos/responses/PromotedRoomDTO.java` (+ `from(RoomPopulationDTO, Instant)`)
- [x] `WebSocketMessageType.ROOM_PROMOTION_UPDATE` + `WebSocketMessage` @JsonSubTypes entry
- [x] `NotificationType` + `ROOM_PROMOTION_APPROVED / DENIED / CANCELED`
- [x] `ChatRoomInteractionService(-Impl).getPromotedRooms` (page clamped 0..24, size 1..100 default 8,
  `totalElements ≤ 25 * pageSize`, rows enriched via `RoomActivityService.getRoomPopulation`)
- [x] `GET /api/v1/chat-rooms/promoted?page&pageSize` in `ChatRoomController`
- [x] `RoomPromotionsSummaryDTO` + trailing `roomPromotionPendingCount`, `roomPromotionApprovedCount`,
  `roomPromotionPendingReleaseTotal`, `roomPromotionApprovedRefundTotal`

## A6 — Adapter (ads) + chat hooks

- [x] ads `services/RoomPromotionAdapter.java` implements `RoomPromotionPort`
- [x] `AdminFacadeServiceImpl`: PERMANENT ban → `cancelPromotionsForBannedUser` (own try/catch); archive →
  `cancelActivePromotionsForRoom` (own try/catch); `getRoomPromotionsSummary` fills the four new fields

## A7 — Revenue touch (ads)

- [x] `AdServiceImpl.getMonthlyRevenueStats` / `getWeeklyRevenueStats` fetch `PurchaseType.ROOM_PROMOTION` and add it
  into `promotedRevenue` (no DTO change)

## A8 — Docs + build gate

- [x] This document
- [x] `./mvnw -DskipTests verify` passes

## API contract summary

Enum strings: `RoomPromotionStatus = PENDING|APPROVED|DENIED|CANCELED`; `CanceledBy = USER|ADMIN|SYSTEM_BAN`;
`NotificationType += ROOM_PROMOTION_APPROVED|ROOM_PROMOTION_DENIED|ROOM_PROMOTION_CANCELED`, `referenceType = "ROOM_PROMOTION"`.

User — `/api/v1/ads-portal/room-promotions` (`X-Auth-Token`):

| Method | Path | Body → Response |
|---|---|---|
| POST | `/` | `{chatRoomId, paymentMethodId}` → **201** `RoomPromotionDetailDto`; 403 unclaimed or staff; 400 private/archived/staff/special room; 409 already PENDING for this room |
| GET | `/?status=&page=0&size=10` | `Page<RoomPromotionDto>` sorted `submittedAt desc` |
| GET | `/{id}` | `RoomPromotionDetailDto` (owner or staff) |
| POST | `/{id}/request-cancel` | `{reason}`; PENDING only → detail |

Admin — `/api/v1/ads-portal/admin/room-promotions` (`@security.isAdmin()`):

| Method | Path | Notes |
|---|---|---|
| GET | `/?status=&email=&userId=&chatRoomId=&page=&size=&sort=` | `Page<RoomPromotionDto>`; `sort` JSON string as messages; default `submittedAt asc` |
| GET | `/{id}` | `RoomPromotionDetailDto` |
| POST | `/{id}/approve` | captures payment, sets `approvedAt` |
| POST | `/deny` | `{promotionId, reason}`; releases hold; 400 blank reason |
| POST | `/cancel` | `{promotionId, reason}`; PENDING release / APPROVED no refund |
| GET | `/ban-summary/{userId}` | `@security.isStaffMember()`; `BanRoomPromotionsSummaryDto` |
| GET | `/revenue/summary` | `@security.isSuperAdmin()`; `PromotedRevenueSummaryDto` (ROOM_PROMOTION receipts) |
| GET | `/revenue/daily?fromDate=` | `@security.isSuperAdmin()`; `PromotedRevenueDailyResponseDto` |

`RoomPromotionDto = {id, chatRoomId, chatRoomName, status, amount, currency, submittedAt, approvedAt, email, userId, cancelRequested}`;
`RoomPromotionDetailDto` adds `chatRoomArchived, canceledBy, reason, resolvedAt, cardBrand, cardLast4, receiptStatus, cancelRequestReason, cancelRequestedAt`.

Chat — `GET /api/v1/chat-rooms/promoted?page=0&pageSize=8` → `Page<PromotedRoomDTO>`
(`roomId, roomName, activeUsersCount, onlineUsersCount, totalMessagesCount, noiseLevel, archived, promotedAt`); index clamped 0..24, `totalElements ≤ 25*pageSize`.

WS — type `ROOM_PROMOTION_UPDATE` on `/topic/public-chat` and `/topic/user.{ownerId}`,
`data = {chatRoomId, chatRoomName, promotionId, status, ownerId, approvedAt|null}`.

Archive summary — `RoomPromotionsSummaryDTO` (chat, `GET .../rooms/{id}/promotions-summary` unchanged) now carries the
four trailing `roomPromotion*` fields next to the existing promoted-message fields.

## Deviations / notes

- **Revenue (2026-09-03)**: room promotions now have their own dashboard revenue endpoints (above, reusing the
  promoted-message DTO records) and their own `roomPromotedRevenue` series in `MonthlyRevenueDto`/`WeeklyRevenueDto`
  (`promotedRevenue` is message-only again — the earlier fold-in was replaced).

- **Archive refund window (user decision, 2026-09-03)**: on archive, room promotions are refunded only when PENDING
  (hold released) or APPROVED within the last **24 hours of `approvedAt`** (`RoomPromotionPort.ARCHIVE_REFUND_WINDOW_HOURS`);
  older APPROVED ones are canceled without refund (`canceledWithoutRefund` in the outcome). Promoted messages keep
  their always-refund rule. `RoomPromotionsSummaryDTO` gained `roomPromotionApprovedRefundableCount`,
  `roomPromotionApprovedNonRefundableCount`, `roomPromotionApprovedNonRefundableTotal`, `roomPromotionRefundWindowHours`
  (`roomPromotionApprovedRefundTotal` = refundable only) so the archive dialog can show the breakdown.

- **Post-review changes (user decisions)**: staff members (`Role.isStaffMember()`) get 403 on POST for both room
  promotions AND promoted messages (frontend hides the buttons too); the owner's `POST /{id}/cancel` for APPROVED
  room promotions was removed (no refund → pointless). PENDING still goes through `request-cancel`; admin cancel unchanged.
  Promoted messages keep their owner APPROVED cancel.

- `promoteRoom` validation is ordered exactly as planned (unclaimed → `IllegalStateException`, which the controller
  pre-empts with a 403 `ResponseStatusException`; null id / private / archived / staff / special → `IllegalArgumentException`
  → 400 via the ads `GlobalExceptionHandler`; duplicate PENDING → ads `ConflictException` → 409). Room lookup uses
  `ChatRoomService.findById`, which throws a plain `RuntimeException` (500) for an unknown id — parity with the rest of
  chat, not changed.
- `findPromotedRooms` keeps the constructor-expression + `order by max(rp.approvedAt) desc` JPQL form with an explicit
  `countQuery` (valid Hibernate 6 HQL; validated by careful reading — the app was not started, per workspace rules). The
  `Page<Object[]>` fallback was not needed. The service passes an unsorted `PageRequest`.
- `AdminFacadeServiceImplBanRefundTest` was not extended (no-tests rule); it has no mock for `RoomPromotionPort`, so the
  injected port is null there — the NPE is swallowed by the dedicated try/catch, same documented deviation as promoted
  messages.
- `RoomPromotionServiceImpl` depends on chat's `ChatRoomService` (its impl depends only on `ChatRoomRepository` and
  `RoomActivityService`), so no circular bean chain is introduced.
- Out of scope, as planned: expiry/scheduler, room revenue summary endpoints, spend-summary card, per-user admin sub-tab,
  audit-log type, tests, Flyway.
