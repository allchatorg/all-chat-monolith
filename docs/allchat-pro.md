# allchat Pro operations

Pro is one recurring product: USD 5/month, with USD 50/year behind the
`ALLCHAT_PRO_YEARLY_BILLING_ENABLED` feature flag (default `false`). The only launch
entitlement is a public username badge, controlled by the owner's saved
appearance preference. Roles and moderation privileges never grant Pro.

## Database deployment

The `prod` profile (the default active profile) uses Hibernate `validate` and
Quartz schema initialization `never`. The base and `dev` configuration files use
`create-drop`. Live billing always requires persistent storage (`validate`,
`none`, or `update`), including when a development profile is active.
Use the following procedure when deploying against an existing persistent database.

1. Back up the existing PostgreSQL database and quiesce writes.
2. Check the **running old process's** Hibernate configuration. A process started
   with `create-drop` can drop the schema when stopped, even if the next binary
   uses `validate`. Stop it under the backup/restore procedure; restore the
   existing schema/data afterward if necessary. Do not rely on changing the new
   configuration to prevent the old process's shutdown DDL.
3. Apply [sql/allchat-pro.sql](sql/allchat-pro.sql) to the existing schema. It is
   additive and includes defaults for existing users. It does not baseline a new
   database or repair unrelated application/Quartz schema drift.
4. Start the new binary with production schema validation, billing disabled.
   Validation must pass before accepting traffic. Retain the existing Quartz
   tables; production must not initialize/drop them on restart.
5. Confirm account/customer mappings survive a restart before enabling Checkout.

The migration can be reapplied, but `IF NOT EXISTS` is not a schema repair tool.
An incompatible pre-existing column/table must be resolved before startup.
Leave the additive schema in place when rolling back code. Do not roll back to
a binary/configuration that re-enables `create-drop`.

## Local development

Local Stripe test purchases work with `create-drop` when `dev` is active and
`prod` is not active. Use a valid `sk_test_` or `rk_test_` payment key and the
test-mode Stripe settings below, set `ALLCHAT_PRO_ENABLED=true`, and leave
`ALLCHAT_PRO_LIVE_ENABLED=false`. This exception does not permit live charges
against a disposable database.

The saved IntelliJ `AllChatApplication` configuration selects `dev` and loads
the backend's `env` file. Add the Stripe settings there and restart that
configuration. Command-line launches must export the settings and explicitly
select `dev`. Hibernate creates the Pro tables and columns, so this disposable
development setup does not need the SQL migration above.

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
link to the deployment's `/settings/subscriptions` page.

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
contain only user ID, effective visibility, and a monotonic revision.
An independent database-only job expires public badges every 30 seconds, so
Stripe outages cannot block expiry notifications. Effective entitlement is also
checked against the current time whenever a user/message DTO is mapped.

The existing websocket broker is in-process. Reconnect/mount lookup repairs
missed badge events; immediate cross-instance delivery would require the app's
broader shared-broker infrastructure. Persisted preferences continue to apply
after expiration and resubscription.

## Verification

Repository policy forbids creating/running automated tests. Compile with
`./mvnw -DskipTests verify`; frontend verification uses TypeScript and a production
build. Manually verify in Stripe test mode before live enablement:

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
  payment confirmation, expiration, and restart with the same customer mapping.
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
| Storage/restart | The additive SQL applied successfully to the isolated database. The final jar started with Hibernate `validate` and Quartz initialization `never`; customer/subscription state, hidden preference, badge revisions, and processed webhook IDs survived restart. |
| UI/builds | Desktop/mobile, light/dark, mobile scrolling/close control, initial focus, Escape dismissal, price selection, and appearance controls were inspected. TypeScript, the Next.js production build, and Maven verification with tests skipped passed. |

The disposable paid subscriptions were canceled after verification. Repeat the
deployment backup/restore procedure against the target environment before rollout.
Public webhook transport still needs verification on the deployed endpoint; the
checks above signed real test-event payloads locally. The complete identity-surface
layout matrix (including every DM/search/reaction variant, long names, and staff
combinations), live-mode configuration, and provider-outage/load scenarios remain
release checks; the table records only what was exercised here.
