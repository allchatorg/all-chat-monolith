# Promoted Messages — Backend Progress

Feature: users pay $0.50 to promote an own message in a chatroom; admin approval (hold → capture / release); badge +
per-room sidebar + WS updates; portal management; revenue split; ban cleanup.
Full plan: `~/.claude/plans/okay-so-i-am-polished-pretzel.md`

## B1 — Entity, repository, refund (ads module)

- [x] `enums/PromotedMessageStatus.java` (PENDING, APPROVED, DENIED, CANCELED)
- [x] `enums/CanceledBy.java` (USER, ADMIN, SYSTEM_BAN)
- [x] `enums/PurchaseType.java` (AD, PROMOTED_MESSAGE)
- [x] `models/promotion/PromotedMessage.java` (table `promoted_messages`, index `(chat_room_id, status)`, unidirectional
  OneToOne receipt)
- [x] `repositories/PromotedMessageRepository.java`
- [x] `specifications/PromotedMessageSpecification.java`
- [x] `PaymentReceipt.purchaseType` (+ default AD via `@PrePersist`, set explicitly in `AdServiceImpl.createAd`)
- [x] `PaymentService.refundPayment(paymentIntentId)` (Stripe Refund API, guard `succeeded`)
- [x] Type-aware monthly/weekly revenue queries in `PaymentReceiptRepository`

## B2 — Service + controllers (ads module)

- [x] DTOs under `dto/promotion/` (PromotedMessageDto, DetailDto, PromoteMessageRequestDto, PromotionReasonRequestDto,
  SearchRequestDto, BanPromotionsSummaryDto)
- [x] `PromotedMessageService(-Impl)` — state machine + ban cleanup + summaries + WS broadcast helper
- [x] `PromotedMessageController` — POST /, GET /, GET /{id}, POST /{id}/cancel, DELETE /{id} under
  `/api/v1/ads-portal/promoted-messages`
- [x] `AdminPromotedMessageController` — search/detail/approve/deny/cancel/delete/ban-summary under
  `/api/v1/ads-portal/admin/promoted-messages` (`@security.isAdmin()`, ban-summary staff)

## B3 — Chat-side port, enrichment, delete-block, WS, sidebar

- [x] chat `services/MessagePromotionPort.java` + ads `services/MessagePromotionAdapter.java`
- [x] chat `dtos/responses/PromotionInfoDTO.java`, `PromotedMessageEventDTO.java`
- [x] `WebSocketMessageType.PROMOTED_MESSAGE_UPDATE` + `WebSocketMessage` @JsonSubTypes entry
- [x] `MessageResponseDTO` record: added trailing `promotion` component (+ `withPromotion`; fixed all manual constructor
  calls incl. tests; MessageMapper ignore mapping)
- [x] Batch enrichment service (`chat services/MessagePromotionEnrichmentService`) applied to message pages (
  previous/next/around/latest/search), edit broadcasts, top-reacted, promoted sidebar
- [x] Delete-block in `ChattingServiceImpl.deleteMessage` (ConflictException → 409)
- [x] `GET /api/v1/chat-rooms/{roomId}/messages/promoted` sidebar endpoint
- [x] `AdminFacadeServiceImpl.banUser` PERMANENT → `cancelPromotionsForBannedUser` (own try/catch)

## B5 — Revenue split

- [x] `MonthlyRevenueDto`/`WeeklyRevenueDto` + `promotedRevenue` (`revenue` stays ad-only)
- [x] `AdServiceImpl` monthly/weekly stats zip both purchase types (daily today/yesterday summary stays total)

## Build

- [x] `./mvnw verify` passes (30 tests, 0 failures)

## API contract summary

User — `/api/v1/ads-portal/promoted-messages` (session `X-Auth-Token`):
| Method | Path | Body → Response |
|---|---|---|
| POST | `/` | `{messageId, paymentMethodId}` → **201** `PromotedMessageDetailDto` (403 if unclaimed, 409 if message
already has an active promotion) |
| GET | `/?status=&page=&size=` | `Page<PromotedMessageDto>`, `submittedAt` desc |
| GET | `/{id}` | `PromotedMessageDetailDto` (owner or staff) |
| POST | `/{id}/cancel` | → `PromotedMessageDetailDto` (PENDING releases hold; APPROVED no refund) |
| DELETE | `/{id}` | 204; only DENIED/CANCELED, else 409 |

Admin — `/api/v1/ads-portal/admin/promoted-messages` (`@security.isAdmin()`):
| Method | Path | Notes |
|---|---|---|
| GET | `/?status=&email=&userId=&page=&size=&sort=` | default `submittedAt` asc |
| GET | `/{id}` | |
| POST | `/{id}/approve` | captures payment |
| POST | `/deny` | `{promotionId, reason}`; releases hold |
| POST | `/cancel` | `{promotionId, reason}`; PENDING release / APPROVED **refund** |
| DELETE | `/{id}` | releases/refunds per status, broadcasts CANCELED, deletes row |
| GET | `/ban-summary/{userId}` | `@security.isStaffMember()`; `BanPromotionsSummaryDto` |

Chat: `GET /api/v1/chat-rooms/{roomId}/messages/promoted?page=&pageSize=` → `Page<MessageResponseDTO>` (approvedAt
desc).
`MessageResponseDTO` gained trailing `promotion: {id, status} | null` (status string: PENDING/APPROVED — only active
promotions are attached).
WS: `PROMOTED_MESSAGE_UPDATE` on `/topic/chat-room.{roomName}` with data
`{messageId, chatRoomId, chatRoomName, promotionId, status, ownerId}` (status is the enum name as string; admin delete
broadcasts `CANCELED`).
Revenue: `MonthlyRevenueDto`/`WeeklyRevenueDto` now `{month|day, revenue, promotedRevenue}`; daily summary endpoint
unchanged (total).

## Deviations / notes

- **Post-merge contract alignment with the frontend**: `PromotedMessageDto.messageSnippet` renamed to `messageContent` (
  still truncated), detail DTO `senderUsername` renamed to `messageSenderUsername` and gained `messageDeleted` (from
  `Message.deleted`) — matches `src/portal/models/promoted-message.ts` in the frontend repo.
- **NEW_MESSAGE broadcasts are not enriched** — a just-created message cannot have a promotion yet; enrichment on that
  hot path would add a pointless query per message send. MESSAGE_EDIT broadcasts are enriched.
- `MessagePromotionEnrichmentService` is a concrete `@Service` in chat `services/` (no interface), mirroring
  `SecurityService`; one batched `IN` query per page via the port.
- `PromotedMessageService` also exposes `hasActivePromotion` / `getActivePromotions` / `getApprovedPromotedMessageIds` —
  consumed only by `MessagePromotionAdapter` (the port impl).
- The ads module reads chat's `Message` via chat's `MessageRepository` directly (not `MessagesService`) to avoid a
  Spring circular-bean chain (`MessagesServiceImpl → enrichment → adapter → PromotedMessageServiceImpl`).
- 409s: duplicate promotion and delete-of-active use the ads `ConflictException` (handled by ads
  `GlobalExceptionHandler`); the chat delete-block uses chat's `ConflictException` (ResponseStatusException CONFLICT).
- `refundPayment` guards PI status `succeeded` and throws `IllegalStateException` otherwise (mirrors `capturePayment`).
- Old untyped `findMonthlyRevenue`/`findDailyRevenueForDateRange` repo queries were replaced by the `...ByType`
  versions (AdServiceImpl was their only caller). `sumAmountPaidByPaidAtBetween` intentionally stays untyped (headline
  total).
- Verified `receipt.getAd()` usages: none dereference `receipt.ad`; all flows reaching a receipt via `ad.getReceipt()`
  are ad-scoped, so promotion receipts with `ad = null` are safe.
- `AdminFacadeServiceImplBanRefundTest` was not extended (no-tests rule); it has no mock for the new
  `MessagePromotionPort`, so the injected port is null there — the resulting NPE is swallowed by the dedicated
  try/catch, tests still pass.
- Broadcast failures inside `PromotedMessageServiceImpl` are caught and logged so a WS problem never fails a payment
  transition.
