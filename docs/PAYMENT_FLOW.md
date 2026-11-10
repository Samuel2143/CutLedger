# A — PayPal integration and consistency contract

## Real integration and chosen approval experience

Use PayPal Sandbox Orders v2 and Payments v2 directly from the backend. The browser follows a provider-returned **hosted approval URL**, then returns to CutLedger. This avoids mixing incompatible generations of the JavaScript SDK while retaining a genuine PayPal buyer approval and capture flow. Do not introduce a fake wallet modal. Show Sandbox labels on quote, checkout, verification, and receipt.

Provider API base is fixed in code/config validation to `https://api-m.sandbox.paypal.com`. Credentials never reach the browser. Cache OAuth access tokens only server-side until shortly before expiry. Use finite connect/read timeouts and redact Authorization. Official references: [Orders](https://developer.paypal.com/api/orders/v2/), [capture](https://developer.paypal.com/api/orders/v2/orders-capture), [Payments](https://developer.paypal.com/api/payments/v2/), [idempotency](https://developer.paypal.com/api/rest/reference/idempotency/), [webhooks](https://developer.paypal.com/api/rest/webhooks/rest/).

## State machine

`PaymentState` is exactly: `CREATING_ORDER`, `AWAITING_APPROVAL`, `CAPTURE_COMMITTED`, `CAPTURE_PENDING`, `RECONCILING`, `SETTLED`, `FAILED`, `CANCELLED`, `REVIEW_REQUIRED`.

| State | Meaning | Permitted next states |
|---|---|---|
| CREATING_ORDER | Durable identity and immutable create payload exist; order call queued/in flight | AWAITING_APPROVAL, RECONCILING, FAILED, CANCELLED |
| AWAITING_APPROVAL | Known provider order, awaiting buyer approval and capture commitment | CAPTURE_COMMITTED, CANCELLED, FAILED, REVIEW_REQUIRED |
| CAPTURE_COMMITTED | Valid quote accepted for capture before expiry; durable job exists | CAPTURE_PENDING, RECONCILING, SETTLED, FAILED, REVIEW_REQUIRED |
| CAPTURE_PENDING | Provider explicitly reports pending capture | RECONCILING, SETTLED, FAILED, REVIEW_REQUIRED |
| RECONCILING | Create/capture outcome uncertain; exact operation identity retained | AWAITING_APPROVAL, CAPTURE_PENDING, SETTLED, FAILED, REVIEW_REQUIRED; CANCELLED only if capture was never committed |
| SETTLED | Provider verified and all local entitlements issued atomically | REVIEW_REQUIRED only on verified reversal/refund/inconsistency |
| FAILED | Definitive provider refusal/no completed capture, no unresolved operation | Terminal; a new quote/payment may be created |
| CANCELLED | Server rejected further capture before commitment | Terminal; late callbacks cannot revive it |
| REVIEW_REQUIRED | Mismatch, reversal, or uncertainty that cannot be safely resolved automatically | Privileged operator resolution only; no public “mark paid” endpoint |

Use a separate `resolution` code (`DECLINED`, `EXPIRED_BEFORE_CAPTURE`, `AMOUNT_MISMATCH`, `MERCHANT_MISMATCH`, `IDEMPOTENCY_WINDOW_UNKNOWN`, `REFUNDED`, `REVERSED`, etc.). `RECONCILING` does not erase whether capture was committed: `capture_committed_at` is immutable. Never release a campaign lock for unresolved capture or REVIEW_REQUIRED merely because a timer expired.

## Ordered workflow

1. **Initialize.** Transaction locks campaign and quote; verifies ownership, revision/hash/version, policy, entitlement version, ten-minute TTL, positive total, no active payment. Persist payment with one create UUID and one different capture UUID (36-character UUID strings), exact create payload and hash, expected amount/currency/merchant, and CREATE_ORDER job. Mark quote ACCEPTED and campaign active. Commit before HTTP.
2. **Create provider order.** Worker records attempt before dispatch and calls POST `/v2/checkout/orders`, `intent:CAPTURE`, one purchase unit. Use `reference_id=quoteId`, `custom_id=paymentId`, exact amount (`2000` → `"20.00"`), currency USD, and expected seller `payee.merchant_id`. Use `payment_source.paypal.experience_context` with allowlisted return/cancel URLs, `shipping_preference:NO_SHIPPING`, `user_action:PAY_NOW`. Current provider payload must pass the Phase 1 sandbox probe; deprecated `application_context` is not the design default. The official [create reference](https://developer.paypal.com/api/orders/v2/orders-create) describes hosted payer-action links and experience context.
3. **Persist approval.** Save provider order ID and accepted `payer-action`/`approve` URL. Validate HTTPS and an explicit PayPal Sandbox host allowlist based on observed documented responses, never arbitrary redirect domains or substring matching. Transition to AWAITING_APPROVAL only if the local payment is still eligible; cancellation racing with a create response must remain cancelled.
4. **Buyer approves.** Browser navigates to PayPal and signs in as a separate personal sandbox buyer. Return URL contains internal payment ID; PayPal supplies order token. The cookie binds ownership. GET return performs no capture. Display the accepted total and a clear “Confirm capture” action. This extra confirmation is deliberate and should take one click in the demo.
5. **Commit capture.** POST capture verifies returned order ID equals stored ID, GETs the provider order to confirm approval and expected identity, then locks campaign → quote → payment and rechecks current quote validity and all versions. Persist `CAPTURE_COMMITTED`, immutable `capture_committed_at`, and CAPTURE job in one transaction. A repeated call returns existing state, not another job. If expiry occurs before this transaction, reject, mark quote EXPIRED and pre-capture payment CANCELLED with EXPIRED_BEFORE_CAPTURE, and release the campaign lock. If commitment preceded expiry, finish/reconcile that same operation even after expiry.
6. **Capture.** Worker POSTs `/v2/checkout/orders/{orderId}/capture` using the persisted capture request ID. Store returned provider references, but issue nothing from HTTP 2xx alone. A timeout/connection loss becomes RECONCILING. A pending provider result becomes CAPTURE_PENDING. A definitive decline can become FAILED only after confirming no completed or unresolved capture.
7. **Verify independently.** GET the order and its capture (Payments v2). Require exactly one intended full capture, status COMPLETED, exact USD amount, expected payee merchant, expected order ID and purchase-unit reference/custom IDs, and no prior refund/reversal. If the capture response lacks payee fields, verify them on its associated order; do not invent a field. Compare decimal amounts using exact decimal parsing, never floating point. Persist a minimal verification snapshot and verification time.
8. **Fulfill atomically.** In one transaction lock campaign → quote → payment; revalidate immutable quote/line identity, insert each new entitlement with unique `(source_payment_id,asset_id)`, append audit events, increment campaign entitlement version, mark quote FULFILLED and payment SETTLED, clear active payment. If already settled, return existing receipt. Any insert failure rolls back all local fulfillment. The provider transaction and database cannot share an atomic transaction; reconciliation repairs the gap.
9. **Render receipt.** Client sees success only after GET payment reports SETTLED. Receipt references the verified capture and accepted revision and differentiates prior seeded rights from new paid grants.

## Idempotency and crash recovery

Stable provider request IDs prevent repeated operations within PayPal's endpoint-specific retention; they do **not** provide a perpetual distributed exactly-once guarantee. Before first integration, record the documented retention for create and capture, and any account-specific settings, in VERIFICATION. If not verified, allow only one mutation dispatch and use GET verification afterward; do not assume a universal six-hour window. [PayPal idempotency guidance](https://developer.paypal.com/api/rest/reference/idempotency/) explicitly makes support/retention API-dependent.

- Before retrying any mutation, inspect the stored operation and GET known order/capture first. Retry only the identical body and identical key, only inside a verified retention window, and only when that retry is safe under the provider contract.
- If order creation timed out before an order ID was returned, retry the same create key/payload only inside that verified window. If recovery is impossible, REVIEW_REQUIRED; do not initialize a replacement payment automatically.
- Before sending capture, record an attempt and timestamp. A crash after this record but before network dispatch is indistinguishable from some lost responses: recovery first checks provider state, then may repeat the same key inside its retention. Outside the safe window, stop mutation retries.
- If capture succeeded but local transaction failed, GET verification plus the unique grant constraint completes fulfillment after restart. It does not capture again.
- Jobs use `SELECT ... FOR UPDATE SKIP LOCKED`, bounded batches, a 60-second lease longer than a 20-second request timeout, and transactionally renewed leases when needed. Even an accidental duplicate worker must be harmless because operation identity is stable. Never hold a database transaction across HTTP.
- Poll unresolved provider status at roughly 5, 15, 30, 60 seconds, then bounded intervals up to five minutes with jitter. Reconciliation can continue for 24 hours using reads; mutation retries remain limited by verified idempotency retention. Exceeding operational limits escalates to REVIEW_REQUIRED, not FAILED.
- Cancellation is accepted only before capture commitment. It prevents future capture even if PayPal approval arrives later. It does not pretend to revoke a capture already sent. A late create result may be stored for audit but cannot resurrect cancelled state.
- A missing browser, closed tab, app restart, or lost webhook must not prevent a committed payment from being verified and fulfilled. Conversely, ORDER.APPROVED webhooks do not trigger capture without the local explicit commitment.

## Webhook authentication and processing

Register the actual hosted HTTPS `/api/v1/webhooks/paypal` endpoint on the same sandbox REST app. Subscribe to `CHECKOUT.ORDER.APPROVED`, `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.PENDING`, `PAYMENT.CAPTURE.DENIED`, `PAYMENT.CAPTURE.REFUNDED`, and `PAYMENT.CAPTURE.REVERSED` if offered by the app/event catalog; record the actual supported subscription list. A missing reversal event requires periodic verification of recently settled demo captures, not a claim of complete reversal coverage.

Read PayPal transmission ID/time/signature/auth algorithm/certificate URL headers and the untouched event object. POST them with the configured webhook ID to `/v1/notifications/verify-webhook-signature`. Require SUCCESS. Do not fetch arbitrary inbound certificate URLs in application code. Validate body size and header presence first. Invalid signature: 400, no state change. Verification unavailable: 503 so the event is retried. PayPal's simulator events cannot prove postback verification of a real app event; [official webhook documentation](https://developer.paypal.com/api/rest/webhooks/rest/) distinguishes this limitation.

After verification, atomically insert event ID with uniqueness and enqueue verification for the matching known provider order/capture. Return 204 only after durable persistence; a duplicate returns 204 with no duplicate effects. Unknown events are retained as sanitized audit metadata and do not create payments or entitlements. Do not trust amount/status from the webhook as final proof; GET the provider resource. Events may be duplicated, delayed, or out of order. A late pending event cannot downgrade a settled payment.

If a verified refund/reversal is discovered, record it, transition payment to REVIEW_REQUIRED, and suspend only grants originating from that payment. Earlier grants remain valid according to their terms. Current assessments must not assert coverage using suspended grants. Refund initiation and dispute-management UI are out of scope.

## Required real Sandbox evidence

Use a genuine business seller app and a distinct personal buyer. Record redacted order/capture references, timestamps, accepted quote hash, expected/verified merchant and total, database grant count, and actual webhook event ID in private verification evidence. Public evidence contains only redacted IDs/screenshots. Required observed runs: successful $20 capture; repeated capture click with one charge/grant set; buyer cancellation; browser closed/reopened after approval; refresh after capture; restart after provider capture before fulfillment using a test-only failure hook; eventual webhook delivery and duplicate handling; failed/declined capture where safely supported. Fault-injected HTTP timeouts and mismatches supplement real tests and are labeled simulated faults, not real provider failures.

The final hosted product must have no publicly accessible failure hooks, mock success switches, admin fulfillment endpoint, or buyer credentials in client code. Financial reliability is a release blocker.
