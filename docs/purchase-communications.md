# Purchase communications plan and coverage

## Objective and account requirement

Every supported purchase lifecycle update produces an email and a persistent,
real-time chat notification with a link to that purchase. Ads, promoted messages,
and room promotions are the three `PurchaseType` values.

New purchases already require a claimed account in both their controllers and
services. The normal registration and account-claim flows require an email
address. This implementation preserves that eligibility rule; it does not add an
email-verification requirement or change pricing, moderation, or refund policies.

## Implementation plan

1. Audit every purchase mutation, including automatic cancellations from bans,
   message removal, and room archival.
2. Route each committed lifecycle outcome through one communication service that
   persists the owner's chat notification and an email delivery record together.
3. Deliver queued emails outside the purchase request, with bounded SMTP timeouts,
   retries, and protection against concurrent delivery workers.
   Preserve the order of updates for each purchase when an earlier email is retried.
4. Include the review/cancellation reason and distinguish a hold, captured charge,
   released hold, submitted refund, and cancellation without a refund.
5. Extend the shared frontend notification registry and real-time toasts with
   reasons, payment details, and a link to the purchase. Refresh notification
   history on reconnect.
6. Verify with backend compilation/package verification and frontend type and
   production builds. Do not create or run tests under the workspace rules.

## Lifecycle coverage

Every supported cell below emits both an email and an inbox notification. An
online recipient receives the inbox notification over the existing user-specific
WebSocket subscription after the transaction commits. Offline recipients retain
the notification for their next login.

| Outcome | Ads | Promoted messages | Room promotions |
| --- | --- | --- | --- |
| Submitted, awaiting review | Yes | Yes | Yes |
| Approved and payment captured | Yes | Yes | Yes |
| Denied, with staff reason | Yes | Yes | Yes |
| Pending cancellation request acknowledged | No existing action | Yes | Yes |
| Approved purchase canceled by owner | No existing action | Yes | No existing action |
| Canceled by staff | No separate action; pending ads can be rejected | Yes | Yes |
| Pending purchase canceled after permanent ban | Yes | Yes | Yes |
| Promotion canceled after message removal | Not applicable | Yes | Not applicable |
| Promotion canceled after room archival | Not applicable | Yes | Yes |
| Campaign completed | Yes | No expiration event | No expiration event |

Cancellation requests remain pending until staff resolves the purchase. Neither
promotion product has a separate cancellation-request rejection endpoint. Existing
approval, denial, and cancellation actions communicate the eventual resolution.
Failed checkout attempts do not produce purchase confirmations. Notifications
are attached to successful persisted purchase transitions, not merely button clicks.

## Payment wording and consistency

- `AUTHORIZED`: money is held pending review; it has not been captured.
- `CAPTURED`: payment has been charged. A canceled promotion with this receipt
  status explicitly says no refund is being issued.
- `CANCELLED`: the authorization was released. This is not a captured-payment refund.
- `REFUNDED`: a refund was submitted; the customer's bank may still be processing it.
- Missing or unknown receipt data does not invent a successful payment outcome.

Payment operations check the returned Stripe state before persisting success.
Checked Stripe exceptions roll back direct purchase mutations. The existing
best-effort system message-removal path keeps its original transaction semantics;
an item whose payment operation fails does not emit a successful purchase update. Purchase
rows are locked during competing status changes to prevent duplicate resolutions.

## Research supporting the design

- [Spring Framework 6.2 transaction-bound events](https://docs.spring.io/spring-framework/reference/6.2/data-access/transaction/event.html):
  committed notifications use the existing `AFTER_COMMIT` listener so clients
  cannot react to a purchase that later rolls back.
- [Spring Boot 3.5 email configuration](https://docs.spring.io/spring-boot/3.5/reference/io/email.html):
  explicitly bounded connection/read/write timeouts prevent indefinite SMTP blocking.
- [Stripe authorization and capture](https://docs.stripe.com/payments/place-a-hold-on-a-payment-method)
  and [PaymentIntent cancellation](https://docs.stripe.com/api/payment_intents/cancel):
  releasing an authorization is different from refunding captured funds.
- [Stripe refunds](https://docs.stripe.com/refunds): a submitted refund can remain
  pending, so messages must not promise it has reached the customer's bank.

## Operational limits

SMTP delivery and a database commit cannot be atomic: a process crash after SMTP
accepts an email but before its delivery record commits can cause a retry. Delivery
is at least once, not an exactly-once guarantee from the mail provider.

Legacy or manually created claimed accounts with no email still receive a stored
chat notification; their email delivery records must remain visible for recovery.
Banned users may be disconnected from chat and unable to sign in, so email is the
available delivery channel while their notification remains stored.

Build verification does not send real email, charge cards, or exercise a live
WebSocket session. Deployment needs the configured SMTP service, the outbox table,
and the existing chat broker. The project's existing `ddl-auto: create-drop`
configuration deletes database contents on lifecycle restarts; preserving queued
emails across deployments requires a database/schema configuration that preserves
application data.

See [email delivery configuration and recovery](purchase-email-delivery.md) for
worker settings, schema setup, retry behavior, and operational recovery.

## Verification completed

- Backend: `./mvnw -DskipTests verify` passed all five reactor modules on Java 21.
- Frontend: `tsc --noEmit --incremental false` passed.
- Frontend: `npm run build` passed using an isolated source copy to avoid
  conflicting with the running development server's generated `.next` files.
- Both repositories passed `git diff --check`.
- Source review covered lifecycle hooks, cancellation actors, payment wording,
  deduplication, concurrent workers, per-purchase email ordering, and HTML escaping.
- No tests, backend startup, SMTP sends, Stripe transactions, or deployment were
  performed. Live delivery still requires verification in a configured environment.
