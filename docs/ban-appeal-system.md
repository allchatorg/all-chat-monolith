# Ban Appeal System

Lets a banned user contest their ban once, tracks the appeal through an explicit
lifecycle, and gives admins a review queue with full context. Spans both repos:
this backend and `all-chat-frontend-monolith` (pages `/banned`, `/banned/appeal`,
admin `/appeals`).

## Lifecycle

```
            submit                claim                resolve
  (no appeal) ──► PENDING ──► UNDER_REVIEW ──┬──► APPROVED  (ban revoked)
                     │              │        └──► DENIED    (ban stands)
                     └──────────────┴──► EXPIRED  (ban lapsed/revoked mid-appeal)
```

- **One appeal per ban** — DB unique constraint `uk_ban_appeal_ban_id` on
  `ban_appeal.ban_id`; a denied appeal is final for that ban, a new ban grants a
  new appeal right.
- **EXPIRED** is written from two directions so no zombie queue items survive:
  `BanServiceImpl.systemRevokeBan` closes any open appeal on every revocation
  (manual revoke, Quartz `UnbanJob`, appeal approval — approval then overwrites
  with APPROVED as its final step), and `getMyAppeal()` resolves lapsed bans on
  read.
- The banned user sees PENDING and UNDER_REVIEW as one status (`IN_REVIEW`) so
  reviewer activity never leaks.

## Data model

- `ban_appeal` (`models/BanAppeal.java`, extends `Base`): `ban_id` (unique, FK
  `user_ban`), `user_id`, `appeal_text` (50–2000 chars), `what_will_change`
  (optional), `alternate_email` (optional, **unverified**), `status`,
  `reviewer_user_id`/`claimed_at`, `resolved_by_user_id`/`resolved_at`,
  `internal_note` (staff-only), `user_facing_message`.
- `BanAppealAuditLog` (JOINED-inheritance subclass of `audit_logs`, discriminator
  `BAN_APPEAL_RESOLVE`): `appeal_id`, `ban_id`, `decision`. Rendered by
  `AuditLogCustomMapperImpl` as `BanAppealAuditLogDTO`.
- Schema comes from `ddl-auto: create-drop` locally. **Prod note:** if the prod
  schema is managed manually, the `ban_appeal` table, its unique constraint and
  the `ban_appeal_audit_log` join table need explicit DDL.

## API

User-facing (`BanAppealController`, `/api/v1/ban-appeals`) — **no path IDs**;
everything is keyed off the authenticated user, so IDOR is impossible by
construction:

| Method | Path | Returns | Notes |
|---|---|---|---|
| GET | `/my-ban` | `MyBanContextDTO` | masked ban + `appealable` + own appeal; 404 if no active ban |
| POST | `/` | `BanAppealUserViewDTO` | 403 CSAM-ineligible, 409 duplicate |
| GET | `/my-appeal` | `BanAppealUserViewDTO` | 404 if none; resolves EXPIRED on read |

Admin (`BanAppealAdminController`, `/api/v1/admin/ban-appeals`) — all gated at
the service layer with `@PreAuthorize("@security.isAdmin()")` (ADMIN **or**
SUPER_ADMIN; never a `== ADMIN` equality check):

| Method | Path | Notes |
|---|---|---|
| GET | `?status=&openOnly=&page=&pageSize=` | oldest-first (`createdAt ASC`) |
| GET | `/{appealId}` | detail incl. prior bans, notes, banned-by |
| POST | `/{appealId}/claim` | PENDING → UNDER_REVIEW; re-claim is last-writer-wins |
| POST | `/{appealId}/resolve` | body: decision APPROVED/DENIED, required `internalNote`, optional `userFacingMessage` |

Approval calls `BanService.revokeBan(user, description)` (new overload) so the
existing `REVOKE_BAN` audit log references the appeal id, and reuses ban-cache
eviction + Quartz cancellation. Every resolution also writes a
`BAN_APPEAL_RESOLVE` audit log whose description embeds the internal note
(audit logs are staff-only).

## Enforcement model (how a banned user reaches these endpoints)

Banned users can authenticate normally; `AccessRestrictionFilter` blocks them
per-request *after* auth. The filter now has a dedicated
`BANNED_USER_ALLOWED_ENDPOINTS` whitelist — the complete API surface reachable
with an active ban:

- `/api/v1/ban-appeals/**` (GET, POST)
- `/api/v1/users/me` (GET) — lets the frontend see `banned=true` and corral
- `/api/v1/auth/logout` (POST)

Everything else still returns the ban-shaped 403 (`BanResponseDTO`). `/ws/**`
is deliberately **excluded**: the handshake keeps 403-ing, `UserInterceptor`
drops STOMP SEND frames from banned users, and sessions are expired at ban time
— so a banned user can never chat over a lingering socket.

`/api/v1/ban-appeals/**` was also added to the existing verification whitelist
(`ENDPOINT_ALLOWED_METHODS`) so flagged-IP email/phone verification demands
never block the appeal flow. The two maps are separate on purpose: merging them
would loosen the verification gate for banned-only routes and vice versa.

A `BANNED` role was considered and rejected: role is a single leveled field
(a banned MODERATOR would need a restore column), role changes don't propagate
to live sessions, and the whitelist decision needs the `Ban` record anyway.

## Security properties

- **IDOR-free by construction** on user endpoints (no client-supplied ids).
- **CSAM bans are not appealable** (`ReportType.isCsamRelated()`); server
  returns 403 with a generic message. This also protects
  `BanServiceImpl.systemRevokeBan`, which refuses to revoke CSAM bans.
- Hidden from the banned user: banning admin identity, internal notes, raw CSAM
  report types (masking shared with the filter via `BanMapper.toUserFacingDto`),
  reviewer identity/activity.
- **Alternate email is unverified**: it receives only a fixed generic notice
  ("a decision has been made — check the account's email or log in"), never the
  decision, the account identity, or interpolated text.
- Email subjects are fixed strings; user/admin text is only rendered in bodies
  through Thymeleaf `th:text` (escaped).
- Soft self-review prevention: the admin UI warns when the viewer issued the
  ban (`bannedByUserId`), without hard-blocking (small admin team).

## Rate limits (`RateLimitFilter`)

| Rule | Scope | Window | Limit |
|---|---|---|---|
| `ban_appeal_submit` (POST `/api/v1/ban-appeals`) | user-or-IP | 1 day | 5 attempts (successes capped at 1/ban by the unique constraint) |
| `ban_appeals_controller` (`/api/v1/ban-appeals/**`) | user-or-IP | 1 hour | 300 |
| admin endpoints | covered by existing `admin_controller` rule | 1 hour | 600 |

Config keys: `rate-limit.sensitive.ban-appeal-submit-per-user-per-day`,
`rate-limit.controllers.ban-appeals-per-hour`.

## Emails (`MailSenderService`)

| Trigger | To | Template |
|---|---|---|
| Appeal submitted | account email (skipped if null — guests) | `BAN_APPEAL_RECEIVED_TEMPLATE` |
| Decision | account email | `BAN_APPEAL_DECISION_TEMPLATE` (approved/denied + optional staff message) |
| Decision (notice only) | alternate email, if provided | `BAN_APPEAL_ALTERNATE_NOTICE_TEMPLATE` |

Mail failures are logged and never roll back the appeal/resolution; there is no
retry queue in this codebase. Guests with no email rely on the status screen.

## Frontend integration (`all-chat-frontend-monolith`)

- `src/lib/api.ts` no longer deletes the session token on a ban-shaped 403 —
  the banned user keeps a session for the appeal endpoints; the redirect to
  `/banned` is loop-guarded. The `?ban=` query param remains as a fallback.
- `AuthGuard` corrals authenticated users with `user.banned === true` to routes
  under `/banned` only.
- Pages: `/banned` (ban info + Appeal/Status/Logout buttons), `/banned/appeal`
  (form or status view), admin `/appeals` (queue, ADMIN+ sidebar item) and
  `/appeals/[id]` (detail with ban card, appeal, prior bans, user messages via
  the existing `/api/v1/admin/user-messages`, audit logs, claim/resolve dialog).

## Gap analysis / future work

- Submit-time audit log entry (currently the appeal row is the submission trail).
- Hard reviewer locking (optimistic `@Version`) instead of last-writer-wins claim.
- Turnstile captcha on the appeal form (component + backend verification already
  exist for auth).
- Alternate-email verification flow.
- Time-window filter on `MessageSearchRequestDTO` to show messages *around the
  ban*; message deletion at ban time may already have removed evidence.
- Re-appeal cooldown for permanent bans (deliberately excluded for now: one
  appeal per ban, final).
- Prod DDL/migration story once `ddl-auto` stops being `create-drop`.
