# allchat Pro operations

Pro is one recurring product: USD 5/month, with USD 50/year behind the
`ALLCHAT_PRO_YEARLY_BILLING_ENABLED` feature flag (default `false`). A confirmed paid subscription unlocks higher account limits, username and message
font presets, 17 characters for stickers/custom emojis/reactions, and an optional
public username badge. Hiding the badge never removes paid benefits. Roles and
moderation privileges never grant Pro; existing staff room/upload exemptions remain.

## Included benefits

| Benefit | Basic | allchat Pro |
| --- | --- | --- |
| Joined public chatrooms | 20 guest, 25 claimed, 50 email-verified | 100 |
| Visible characters per message | 500 | 2,500 |
| Raw formatting/emoji storage cap | 2,000 | 10,000 |
| Per-file upload | Attachment-type limit (currently 10 MB) | 100 MB |
| Total chat uploads over the previous hour | 25 MB | 500 MB |
| Username and message font presets | Default | Five saves per day, resetting at midnight UTC |
| Exclusive stickers, inline custom emojis, and reactions | Read existing content | Send all 17 catalog characters |
| Optional public Pro badge | No | Show or hide in Appearance |

Upload sizes use 1,024 × 1,024 bytes per displayed MB. Each complete inline emoji
counts as one UTF-16 character for the visible limit, while its serialized marker
still consumes raw storage. Both limits apply to sends and edits.

Checkout return parameters grant no access. Backend-confirmed payment activates
all benefits. Cancellation retains benefits through the paid-through time.
Expiration restores Basic limits, keeps existing joined rooms and readable paid
content, and resets saved fonts to Default. New room joins must fit the current
limit; new paid stickers/emojis/reactions and font saves require active Pro.
Existing custom reactions remain removable, and edits may retain, move, or remove
existing inline emojis without increasing the count of an identity.

See [font behavior](pro-fonts.md) and [sticker contracts](sticker-messages.md).

## Database deployment

The base, `dev`, and `prod` configurations use Hibernate `ddl-auto: create-drop`.
The `dev` and `prod` profiles use Quartz schema initialization `always`; `prod`
is the default active profile.
Every profile uses a disposable database: Hibernate recreates entity tables on
startup and drops them on shutdown, and Quartz recreates its job tables on
startup. Users, messages, billing projections, reporting history, queued emails,
and scheduled jobs are reset. No manual SQL migrations are required.

Deploy the backend before the matching frontend. Older clients can ignore the
additive sticker/font fields; older frontends may display inline markers literally.

The billing guard remains in place: Checkout is unavailable under the current
`prod` configuration, including with Stripe test keys. Disposable schemas permit
Checkout only with test keys and `dev` active without `prod`. Live billing still
requires persistent storage (`validate`, `none`, or `update`) and its explicit
enablement flag.

Allow 100 MiB files and multipart overhead through any reverse proxy. Application
multipart caps are 100 MB per file and 110 MB per request. Keep the updated ClamAV
stream/file caps (110M), scan cap (400M), and oversized-scan alerts from `compose.yml`
so accepted files receive complete scanning. Advertising retains its own upload cap.

## Local development

Local Stripe test purchases work with `create-drop` when `dev` is active and
`prod` is not active. Use a valid `sk_test_` or `rk_test_` payment key and the
test-mode Stripe settings below, set `ALLCHAT_PRO_ENABLED=true`, and leave
`ALLCHAT_PRO_LIVE_ENABLED=false`. This exception does not permit live charges
against a disposable database.

The saved IntelliJ `AllChatApplication` configuration selects `dev` and loads
the backend's `env` file. Add the Stripe settings there and restart that
configuration. Command-line launches must export the settings and explicitly
select `dev`. Hibernate creates the Pro tables and columns automatically.

Resetting the local database does not delete Stripe test customers or
subscriptions. Cancel leftover test subscriptions in the Stripe Dashboard's
test environment when resetting development data.

## Stripe setup (test mode first)

Use the existing payment account/key (`STRIPE_API_KEY`), not the independent
Stripe Identity key. Product, prices, customers, portals, and webhook secret must
all belong to the same Stripe account and mode.

Create one **allchat Pro** product with a monthly recurring price. Configure its
yearly price before enabling yearly billing:

| Environment variable | Required price |
| --- | --- |
| `STRIPE_PRO_MONTHLY_PRICE_ID` | USD, 500 cents, every month |
| `STRIPE_PRO_YEARLY_PRICE_ID` | USD, 5000 cents, every year; required when yearly billing is enabled or existing yearly subscribers must be reconciled |

Quantity is one. No trial, coupon, promotion code, or additional product is
offered by this integration. Set an explicit tax behavior on both prices;
their tax behavior must agree for portal changes. This feature does not add a
new automatic-tax integration. Checkout explicitly requests USD and disables
Adaptive Pricing so account-level localization cannot change the billing currency.

Create the billing Customer Portal configuration. The separate switch
configuration is required only when enabling yearly billing:

* `STRIPE_PRO_BILLING_PORTAL_CONFIGURATION_ID`: payment method updates, billing
  information, and invoice history; subscription updates/cancellation disabled.
  allchat supplies cancellation directly, including for restricted accounts.
* `STRIPE_PRO_SWITCH_PORTAL_CONFIGURATION_ID`: price changes only, limited to the
  two prices of the same allchat Pro product, with
  `proration_behavior=always_invoice`. Set
  `subscription_update.schedule_at_period_end.conditions` to **only**
  `shortening_interval`. Disable portal cancellation, quantity changes, and promotion codes.

Do not use `decreasing_item_amount` as a scheduling condition: the discounted
annual price could otherwise defer the monthly-to-yearly upgrade. The annual
upgrade must show the prorated charge before confirmation; the monthly change
must show next renewal as its effective date. Configure branding and the return
link to the deployment's `/?pro=subscriptions` URL. Checkout returns add
`checkout=success` or `checkout=canceled`; portal returns add `billing=updated`.
The chat loads normally and opens the subscription modal over it. The modal
retains the return result while the URL parameters are removed, so closing it
leaves the user in chat and payment-confirmation polling continues until the
backend confirms access. Previously issued `/pro/return` and
`/settings/subscriptions` result links forward into the same chat flow.
Restricted accounts retain access to billing without initializing unavailable
chat features. Return parameters never grant Pro access themselves.

The runtime's Stripe Java SDK remains at 24.12.0 (API 2023-10-16). It supports
portal flows, but not a typed `schedule_at_period_end` configuration setter.
Provision the portal settings through the Stripe Dashboard/current configuration
API; runtime requests use the supplied configuration IDs. See
[portal configuration](https://docs.stripe.com/customer-management/configure-portal)
and [portal limitations](https://docs.stripe.com/customer-management#limitations).

Configure a signed POST webhook at `/api/v1/pro/webhook` and put its signing
secret in `STRIPE_PRO_WEBHOOK_SECRET`. Subscribe to:

* `checkout.session.completed`, `checkout.session.expired`,
  `checkout.session.async_payment_succeeded`, `checkout.session.async_payment_failed`
* `customer.subscription.created`, `customer.subscription.updated`,
  `customer.subscription.deleted`
* `invoice.paid`, `invoice.payment_failed`, `invoice.payment_action_required`
* `subscription_schedule.created`, `subscription_schedule.updated`,
  `subscription_schedule.released`, `subscription_schedule.canceled`,
  `subscription_schedule.completed`, `subscription_schedule.aborted`

Pin the webhook endpoint version to **2023-10-16**, matching the SDK. For local
manual verification, Stripe CLI can forward these test events to the local
webhook endpoint; use that listener's signing secret, not a Dashboard endpoint's
secret. The webhook bypasses session/IP restrictions and verifies its signature
against the raw body. Other Pro APIs require the current account session.

Set `APP_FRONTEND_URL` to the trusted frontend origin. Return URLs are constructed
on the server and never taken from user-submitted redirect URLs.

Set `ALLCHAT_PRO_ENABLED=true` only after the database and all Stripe settings
are ready. `ALLCHAT_PRO_LIVE_ENABLED` defaults to `false`; leave it false during
test-mode verification. Live enablement requires separate live-mode resources,
signing secret, and explicitly setting both flags. Turning sales off does not
remove an existing subscriber's ability to cancel or manage billing.
Keep the price IDs, portal IDs, payment key, and webhook secret configured when
turning sales off; the lifecycle projection still needs them for existing owners.

### Yearly billing rollout

`ALLCHAT_PRO_YEARLY_BILLING_ENABLED=false` keeps monthly sales available without
a yearly price or switch portal configuration. The subscription API returns
`yearlyBillingEnabled` so the frontend can hide the yearly purchase option.
Yearly checkout requests are also rejected by the server. Switching is disabled
while the flag is off because the configured switch portal exposes both prices;
the regular billing portal still requires subscription updates to be disabled.
Set the flag to `true` and restart after both prices and the switch portal have
been configured and verified to make yearly checkout and plan switching available.

Existing yearly subscriptions keep their plan, paid access, renewal, cancellation,
reactivation, and billing management. Keep their yearly price ID configured even
when the flag is off. Scheduled changes already agreed to are preserved and can
still be undone. Reconciliation expires known unfinished yearly Checkout sessions
when yearly billing is disabled; they cannot be continued through the API. An
incomplete subscription can still be canceled before starting a monthly purchase.
A create request whose Stripe response was lost remains pending until its saved
Checkout deadline, to avoid creating a duplicate subscription. When disabling a
previously enabled rollout, also remove yearly offers from the Stripe switch
portal and expire any outstanding annual payment links: already issued hosted
Stripe pages operate outside the application flag until closed or reconciled.

## Billing state and recovery

Stripe is the billing authority; PostgreSQL stores the entitlement projection,
one subscription record per user, pending Checkout attempts, and processed event
IDs. Customers are shared with advertising through the chat-owned customer
service. Concurrent mutations serialize on the user row and provider creation
uses idempotency keys.

Checkout return parameters never grant Pro. The return page refreshes status and
shows payment confirmation in progress while the backend verifies the payment.
An unfinished Checkout retains its selected interval. If the first payment fails,
`canContinueCheckout` allows the owner to reopen the same verified session; its
interval stays fixed until that incomplete subscription is canceled. Incomplete,
unpaid subscriptions are canceled immediately because Stripe does not allow
end-of-period updates on them.
Already-paid time is honored after a renewal failure, but unpaid time does not
grant entitlement. End-of-period cancellation retains paid access. Account
deletion first expires pending Checkout and confirms remote cancellation; an
unconfirmed cancellation prevents deletion.

The Stripe portal cannot mutate a subscription with a scheduled change. allchat's
native cancellation first **releases** that schedule, then sets
`cancel_at_period_end`; it never calls schedule cancellation. Undoing a scheduled
change releases it while preserving any independent cancellation. If a provider
operation partially succeeds, the UI must show an error and refresh actual
state; retry is safe.

Monitor failed webhook responses and reconciliation warnings. Stripe retries
failed deliveries; duplicate event IDs are ignored after successful processing.
Subscription reconciliation repairs missed/out-of-order provider updates.
Billing metadata is never sent to public websocket topics. Public badge events
contain user ID, effective badge visibility and revision, plus effective font
presets and their independent revision; they never contain billing metadata.
An independent database-only job expires public badges every 30 seconds, so
Stripe outages cannot block expiry notifications. Effective entitlement is also
checked against the current time whenever a user/message DTO is mapped.

The existing websocket broker is in-process. Reconnect/mount lookup repairs
missed badge events; immediate cross-instance delivery would require the app's
broader shared-broker infrastructure. The saved badge visibility preference survives expiration and resubscription;
font choices reset on expiry and must be selected again.

## Verification

### Subscription reporting deployment and recovery

Deploy the backend before the dashboard frontend. Hibernate creates the
`pro_subscription_payment` ledger and `pro_reporting_state` checkpoint from their
entities. Both are reset by the configured `create-drop` application lifecycle.

On the existing `/api/v1/pro/webhook` Stripe endpoint, add `charge.refunded`,
`charge.refund.updated`, `refund.created`, `refund.updated`, and `refund.failed`
alongside the existing `invoice.paid` and subscription events. Keep the API key,
webhook secret, and original monthly/yearly price IDs configured even when
sales are disabled. The API key requires read access to events, invoices,
invoice lines, subscriptions, charges, and payment intents. The collector reads
Stripe only; it never creates charges or refunds.

The ledger records positive USD captured payments on verified allchat Pro
invoices. It excludes free, unpaid, manually marked paid, and
customer-credit-only invoices. Cash captured is capped at the invoice's
actual amount paid. Subscription metadata must identify `allchat_feature=pro`
and its `allchat_user_id`, the customer must match, and nonzero invoice lines
must reference the subscription and configured Pro prices. Keep price IDs
available while pending invoices may still need recovery. Ledger entries retain
their verified account and invoice references after account deletion, subscription
replacement, or price retirement.

Successful refunds reduce revenue on the original invoice payment date, before
Stripe fees; payment counts remain unchanged. The current charge is re-read
under a reporting lock, so duplicate or reordered payment/refund events cannot
apply the same refund twice. Revenue uses integer cents internally and USD
decimal amounts in API responses. Reporting days use the server's default time
zone, matching the dashboard's existing daily and weekly revenue boundaries;
use the same time zone across application instances and the database.

An independent scheduler processes up to 25 events per minute by default
(`app.pro.reporting-delay-ms`). It saves a stable window of at most one day and
a continuation cursor transactionally, overlaps completed windows by five
minutes, and leaves one minute for Stripe event indexing. The recovery checkpoint
is initialized once per database and stored with the ledger. A failed page rolls
back both ledger updates and cursor advancement and is retried. The scheduler
has its own thread so entitlement reconciliation cannot starve financial
recovery. Verified webhook reporting identifiers are published before lifecycle
deduplication and dispatched only after the billing transaction commits, on a
separate single-thread worker with a bounded queue of 100 events. Neither Stripe
financial reads nor the reporting lock run on the billing webhook thread.
Worker failures mark reporting unavailable; rejected work during saturation or
shutdown is logged. The database-backed event scanner recovers missed queue work
independently of lifecycle-event deduplication while its ledger and checkpoint
remain available. An application restart resets both under `create-drop`; the
scanner does not restore deleted account mappings or guarantee recovery of the
deleted financial history.

`GET /api/v1/admin/pro/statistics?days=7|30|90` (default 90) is super-admin only
and reads local data. It returns current membership counts, net revenue,
daily initial/renewal/other payment counts, and synchronization metadata.
Revenue includes `today`, `yesterday`, and `total`; total is the net sum of the
ledger. Empty date ranges return zero revenue and payment counts.
Active membership ignores public badge preferences and
includes scheduled cancellations until paid entitlement expires. Deleted
accounts are excluded from membership counts but retain their revenue.

Users Management shows all recorded purchasers, including users who only bought
message promotions, room promotions, or subscriptions. `totalSpent` combines
captured ad and promotion receipts with subscription payments minus recorded
refunds, in USD. Pending holds, canceled authorizations, and refunded receipts do
not add to spending. Users with pending or fully refunded purchases remain in the
list with zero spending. The list, its sorting and pagination, and user details
use the same database calculation; subscription amounts reflect the local ledger.

Synchronization status is `CATCHING_UP` until the first scan completes and recovery
reaches within ten minutes of the present, `CURRENT` after it does, and `UNAVAILABLE` when Stripe
is unconfigured or a recovery attempt fails. `lastSynchronizedAt` is the event
time through which a completed scan has reconciled. Stripe's event API retains
only 30 days; an older unscanned interval sets persistent `INCOMPLETE`, which
takes priority over the other statuses and remains set after later successful
scans. Investigate reporting warnings promptly. A known gap requires a separately
reviewed financial repair from Stripe records; do not clear the flag to hide
missing revenue. The dashboard labels partial reporting and
suppresses unavailable totals instead of treating request failures as zero.

Do not add new tests without an explicit request. Run the existing suites with
`./mvnw verify`; frontend verification uses `npm run test:stickers`,
`npm run test:fonts`, TypeScript, and a production build. A JVM that blocks Mockito
self-attachment can use the installed `mockito-core` JAR via `-DargLine=-javaagent:...`.
The PostgreSQL font test remains opt-in against its disposable test database.
Manually verify in Stripe test mode before live enablement:

* Monthly/yearly Checkout, abandonment, authentication/payment failure, and a
  duplicate click/tab. There must be at most one subscription per account.
* With yearly billing disabled: monthly-only configuration and checkout,
  rejection of direct yearly checkout/switch requests, expiration of unfinished
  yearly Checkout, and continued management/renewal of existing yearly plans.
* Renewal, cancellation through the paid period, reactivation, annual upgrade
  with displayed credit, monthly switch at renewal, and undoing the switch.
* Cancel while a switch is scheduled; retry after provider failure. Confirm that
  settings reflects Stripe's actual state and never announces false success.
* Duplicate/reordered webhook deliveries, missed-event reconciliation, delayed
  payment confirmation, and expiration.
* Restricted subscribers can view/cancel/manage payment details but cannot buy,
  upgrade, or reactivate. Account deletion cannot leave a recurring charge.
* Two connected users: hide/show a badge; inspect old messages, replies,
  reactions, searches, and DM lists; reconnect; expire/resubscribe while hidden.
* Existing ads payment/customer reuse and light/dark, narrow-screen, keyboard,
  long-username, and staff-badge layouts.

Record what was actually exercised; successful compilation alone does not prove
provider configuration, production schema readiness, or payment lifecycle behavior.

### Development verification — September 18, 2026

Manual checks used synthetic accounts, Stripe test resources/cards, a separate
PostgreSQL database and Redis instance, and isolated application ports. The
existing development backend and database were not restarted or changed.
No automated tests were created or run.

| Check | Observed result |
| --- | --- |
| Monthly and yearly hosted Checkout | Paid USD 5 and USD 50 invoices activated the matching plans. An annual card decline granted no access; retrying with a successful test card activated Pro. |
| Pending/repeated Checkout | Repeated requests reused the same session. Incomplete annual recovery retained that session and rejected a monthly retry. Expiring unfinished Checkout cleared its pending state. |
| Cancellation/reactivation | Monthly cancellation preserved paid access and showed the exact end date. Reactivation restored renewal. Annual cancellation also preserved paid access. |
| Plan switches | Stripe displayed a USD 45 immediate annual upgrade after crediting the unused monthly payment. The monthly downgrade was deferred to annual renewal with no charge that day. |
| Scheduled changes | Keep yearly released the schedule. Canceling a newly scheduled change released it before setting end-of-period cancellation; provider state confirmed both operations. |
| Renewal/failure/recovery | A historical Stripe test clock produced a paid renewal, then a failed renewal whose future billing period did not grant access beyond the earlier paid invoice. Paying the failed invoice restored Pro. |
| Webhooks | Locally signed actual Stripe events returned 200, duplicate IDs produced one database record, and invalid signatures returned 400. Older creation/failure events did not roll back current yearly/recovered state. |
| Restricted accounts | Verification-required and cached-ban requests could read/cancel/manage billing; checkout/reactivation remained blocked. |
| Badge consistency | Two browser sessions observed hide/show changes on existing messages and replies without reload. Hidden state survived reload/reconnect and an ended subscription followed by a new annual purchase. |
| Expiry | With provider reconciliation deferred for a disposable fixture, the independent job changed visibility to false and incremented its revision within 30 seconds of expiry. |
| Account deletion | A simulated missing billing customer caused cleanup to fail and retained the account/credentials. Retrying with the correct mapping canceled recurring charges, cleared pending billing, and then deleted the account. |
| Ads integration | The existing payment-method endpoint returned the same saved test card and retained the shared Stripe customer ID. No advertising charge was created. |
| Storage/restart | This earlier isolated run used persistent Hibernate and Quartz settings; customer/subscription state, hidden preference, badge revisions, and processed webhook IDs survived restart. The current disposable configuration resets that state. |
| UI/builds | Desktop/mobile, light/dark, mobile scrolling/close control, initial focus, Escape dismissal, price selection, and appearance controls were inspected. TypeScript, the Next.js production build, and Maven verification with tests skipped passed. |

The disposable paid subscriptions were canceled after verification.
Public webhook transport still needs verification on the deployed endpoint; the
checks above signed real test-event payloads locally. The complete identity-surface
layout matrix (including every DM/search/reaction variant, long names, and staff
combinations), live-mode configuration, and provider-outage/load scenarios remain
release checks; the table records only what was exercised here.
