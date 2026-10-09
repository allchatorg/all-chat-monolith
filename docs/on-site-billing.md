# On-site VIP billing rollout

Deploy the backend before the frontend. The backend keeps the hosted Checkout and Customer Portal routes for existing clients; the new frontend uses embedded Checkout and authenticated billing APIs. Existing Stripe customers, subscriptions, prices, and webhooks are reused. No database migration is required.

## Configuration

- Keep `STRIPE_API_KEY`, `STRIPE_VIP_MONTHLY_PRICE_ID`, `STRIPE_VIP_WEBHOOK_SECRET`, and the existing VIP enable/live flags configured. Yearly purchases and plan switching still require the yearly flag and price.
- Use the corresponding public key in frontend `NEXT_PUBLIC_STRIPE_KEY`. Never expose the secret key to the frontend.
- Customer Portal configuration IDs are only needed by legacy portal routes, not the new on-site controls.
- `APP_FRONTEND_URL` must identify the canonical frontend origin, including its scheme. Legacy billing returns and authentication returns must reach the same origin used to sign in. Do not transfer session tokens between origins.
- Keep the existing subscription/invoice/checkout/schedule webhook events and include `customer.subscription.pending_update_applied` and `customer.subscription.pending_update_expired`.
- Verify the effective production `spring.jpa.hibernate.ddl-auto` setting before restarting. The base configuration uses `update`, but the checked-in dev and prod profiles override it with `create-drop`, recreating application tables on startup and dropping them on shutdown. Environment overrides may change that. Do not start against a persistent database merely to run a billing smoke check.

## Billing behavior

Card entry reuses the existing Stripe CardElement UI. Customer-bound off-session SetupIntents verify cards; VIP renewal defaults are subscription-specific. Ads and VIP use the same customer and enforce the same protected-card removal rules. Card metadata, not card numbers or security codes, passes through Allchat APIs.

Embedded card checkout completes inside the VIP dialog. Payment confirmation comes from the backend and Stripe webhooks, not a URL flag. Existing hosted attempts are reconciled before replacement. An incomplete subscription is recovered by paying its existing invoice instead of creating another subscription.

Monthly-to-yearly changes show a Stripe-calculated preview before confirmation and only apply after payment. Yearly-to-monthly changes start at renewal. Pending upgrade payments, invoice retries, cancellation, and schedule removal operate on the existing Stripe objects.

Existing subscribers who are banned or awaiting verification retain card, invoice, recovery, and cancellation access. These maintenance permissions do not allow new purchases or upgrades.

## Manual acceptance in an isolated Stripe test environment

Use a non-staff claimed user for purchases; staff receive VIP through their role. Do not use real card details or live payments for these checks.

1. Complete embedded monthly checkout, close and reopen the dialog, and verify entitlement and invoice state after webhook processing. Repeat with a decline and a Stripe test card requiring 3DS.
2. Save a card in Ads, view it in VIP, verify it for renewal, and select it. Verify that both screens prevent removing a card still needed for current or scheduled billing. Replace the card on a scheduled plan and verify the next phase uses the replacement.
3. Download an owned invoice and retry an unpaid invoice. Confirm that the existing invoice/PaymentIntent is used, and another user's IDs cannot be read or changed.
4. With yearly billing enabled, preview and confirm both plan directions. Repeat confirmation, fail the upgrade payment, retry payment, and cancel while an upgrade is pending. Confirm no duplicate subscription or charge.
5. Exercise pending hosted sessions, delayed/duplicate webhooks, refresh after payment, and a lost checkout response. Confirm ambiguous attempts cannot start a second subscription.
6. Expire the session during a billing return. Verify the login destination survives and no guest account substitutes for the subscriber. Simulate a temporary profile-loading failure and verify the saved session is retained and retryable.
7. Verify blocked/restricted accounts can still manage existing charges, while purchase and upgrade eligibility stays enforced. Check mobile layout, light/dark themes, and keyboard access to Stripe's 3DS dialog.

For the reported sign-in 500, correlate the failed request path/time and `X-Request-Id` with backend logs. A generic HTML response alone cannot identify its underlying Redis, database, or session exception.

## Compile checks

Backend: `./mvnw -Dmaven.test.skip=true compile`.

Frontend: `npx tsc --noEmit --incremental false` and/or `npm run build`.

Do not add or run test suites for this change.
