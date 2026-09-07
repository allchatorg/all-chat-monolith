# Purchase email delivery

Purchase lifecycle changes save the in-app notification and a `purchase_email_outbox` row in the purchase transaction. The existing notification event pushes to the user's chat WebSocket after commit. SMTP runs on a separate scheduler, never on the purchase request. Template values, including rejection reasons, are rendered with escaped `th:text`; an equivalent plain-text part is included.

Each `(purchase type, purchase ID, notification type)` has one event key. Lifecycle services lock existing purchases before transitions; the outbox's unique key prevents duplicate event creation. Delivered rows remain in the table for deduplication. Do not remove them without preserving the event keys elsewhere.

The worker selects the earliest undelivered event for each purchase using PostgreSQL `FOR UPDATE SKIP LOCKED`, sends it while holding the lock in an independent transaction, and records the result. An earlier event that is backing off or locked by another worker blocks later emails for that purchase, so confirmation cannot arrive after cancellation. Other purchases continue independently. Failed rows remain queued and retry after 30 seconds, doubling up to 6 hours, with no retry limit. The worker processes at most 10 rows per run by default and uses a dedicated scheduling thread. Connection, read and write SMTP timeouts default to 5, 10 and 10 seconds.

SMTP delivery is at least once: if SMTP accepts a message and the application crashes before recording success, the worker may resend it. `delivered_at` means the SMTP server accepted the message; it does not prove delivery to the recipient's inbox. Bounces and spam filtering are outside this transport's visibility. No actual emails or purchase payments were sent during implementation.

## Configuration

| Property | Default |
| --- | --- |
| `app.purchase-email.enabled` | `true` |
| `app.purchase-email.batch-size` | `10` (clamped to 1–100) |
| `app.purchase-email.fixed-delay-ms` | `5000` |
| `app.purchase-email.initial-delay-ms` | `15000` |
| `SPRING_MAIL_CONNECTION_TIMEOUT_MS` | `5000` |
| `SPRING_MAIL_READ_TIMEOUT_MS` | `10000` |
| `SPRING_MAIL_WRITE_TIMEOUT_MS` | `10000` |

## Schema and recovery

The repository currently uses Hibernate `ddl-auto: create-drop`, including its production profile. That existing setting destroys all tables, including the outbox, on application restart. Production durability across restarts requires a persistent schema policy and deployment of the outbox table/unique constraint/index before this worker runs. [The manual PostgreSQL schema addition](sql/purchase-email-outbox.sql) is included for environments with a persistent schema policy; it is not applied automatically. This change does not launch the backend or alter the repository's global schema policy.

Normal purchases require claimed accounts, whose claim flow requires an email. A legacy owner with no email still receives the chat notification. Its outbox row is retained with `last_failure_code = 'MISSING_EMAIL'`, a null recipient and a null `next_attempt_at`; a warning includes only the user ID and event key. This also holds later emails for the same purchase until recovery, preserving event order. Fix the account's email through the usual verified account flow, then intentionally readdress only the affected undelivered outbox rows to that verified address and set `next_attempt_at = CURRENT_TIMESTAMP`, clearing `last_failure_code`. Old queued emails are never automatically redirected when an account changes its email.

Operators can inspect delivery health without retrieving email addresses, message bodies or payment data:

```sql
SELECT last_failure_code, COUNT(*) AS undelivered_count,
       MIN(created_at) AS oldest_update, MIN(next_attempt_at) AS next_retry
FROM purchase_email_outbox
WHERE delivered_at IS NULL
GROUP BY last_failure_code;
```

An SMTP outage leaves all failures queued. After repairing mail configuration, normal retries resume; operators may intentionally bring an affected row's `next_attempt_at` forward. Do not reset successful rows, because that sends the email again. Logs record outbox IDs, attempt counts, retry dates and failure classes only, never raw SMTP responses, rejection reasons, payment identifiers or exceptions.

## Payment wording

Both channels receive the same snapshot text. An `AUTHORIZED` receipt describes a temporary hold, `CAPTURED` a charge, `CANCELLED` the release of a hold, and `REFUNDED` a submitted refund with possible bank processing time. A captured cancellation or denial explicitly states that no refund has been issued. Missing or unknown receipt states make no invented claims. Amounts appear only when the amount and currency are usable.

## References

- [PostgreSQL SELECT locking documentation](https://www.postgresql.org/docs/current/sql-select.html) describes `SKIP LOCKED` for multiple consumers of queue-like tables.
- [Spring Boot 3.5 email documentation](https://docs.spring.io/spring-boot/3.5/reference/io/email.html) recommends SMTP timeouts because some defaults are infinite.
