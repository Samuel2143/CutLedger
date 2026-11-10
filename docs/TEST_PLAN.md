# G — Verification and release gates

The implementation agent must execute these checks and record results in VERIFICATION. This is a planned test matrix, not evidence that tests have passed. Use JUnit/AssertJ, actual PostgreSQL Testcontainers for persistence/concurrency, a provider HTTP test server for fault injection, frontend component tests, and Playwright browser tests. Freeze compatible test dependency versions in Phase 0. Real Sandbox tests are opt-in with secrets and separate from deterministic CI; never charge a real account.

## Required test layers

| Layer | What it proves | What it does not prove |
|---|---|---|
| Pure domain tests | Scope, dates, credit math, quote validity | Provider integration or UI behavior |
| PostgreSQL integration | Locks, uniqueness, atomic fulfillment, leases, restart state | That PayPal accepts actual requests |
| Provider contract/fault tests | Parsing, mismatch handling, timeouts, stable keys, retries | Actual seller access or real webhook authenticity |
| Live AI evaluation | Named model works and handles selected cases | General legal understanding or universal accuracy |
| Real Sandbox transactions | Actual create/approve/capture/verify and callback flow | Production account approval or real-money readiness |
| Browser tests and visual inspection | User interaction, recovery, responsiveness | Backend safety unless corresponding API assertions are included |

## Domain and quote tests

1. Main seed fixture returns 800 + 1200 + 0 = 2000; currency USD and no floating point.
2. Same usage after fulfillment returns zero and no order; subsequent worldwide upgrade totals 5000, direct-from-seed worldwide totals 7000.
3. Every purchased tier covers every requested dimension; property-style generated subsets/date boundaries test this invariant.
4. Old grants and policy snapshots are unchanged after revision/upgrade. Reverting usage is still covered without another purchase.
5. Complementary non-nested grants are not combined into invented coverage.
6. Unsupported TV/training/resale, unknown platform, unresolved territory, invalid dates, and ambiguous confirmation cannot create a payable quote.
7. Inclusive start/exclusive end boundaries; leap-year/month arithmetic; 30/90/365 day windows; altered start never extends a tier's fixed anchor window.
8. Quote expires at exactly server expiresAt; changed revision, entitlement version, or precommit policy invalidates it. Committed capture remains valid after expiry.
9. Client-supplied prices/credit/currency/tier/provider ID cannot override server snapshot.
10. Concurrent acceptance of same quote returns same payment; different quotes cannot both claim the campaign. No second quote captures using already-consumed credit.

## Payment fault matrix

| ID | Fault or event | Required persisted/UI outcome |
|---|---|---|
| PAY01 | Happy actual Sandbox purchase | One completed capture, two new grants for final fixture, SETTLED receipt |
| PAY02 | Double click create/capture; identical key | Same payment/order and one grant set; no second monetary operation |
| PAY03 | Same idempotency key with changed body | 409; no provider call |
| PAY04 | Browser closes after approval | Resume known payment; explicit capture commitment still required |
| PAY05 | Browser closes after capture dispatch | Worker verifies/fulfills without browser |
| PAY06 | Create response lost | Same create key/payload inside verified retention; otherwise review, no blind replacement |
| PAY07 | Capture succeeded but response lost | GET verifies existing capture; exactly one fulfillment |
| PAY08 | Process dies after provider capture before DB commit | Restart worker issues missing grants once |
| PAY09 | Crash during multi-asset grant inserts | Transaction rolls back; recovery inserts all or none, never partial cleared campaign |
| PAY10 | Duplicate workers after lease expiry | Stable operation identity and DB uniqueness prevent duplication |
| PAY11 | Provider pending | No grants, no new checkout, eventual reconciliation |
| PAY12 | Verified definitive decline | No grants; FAILED only when no unknown capture remains |
| PAY13 | Amount/currency/payee/order/custom reference mismatch | REVIEW_REQUIRED, no grants, no replacement checkout |
| PAY14 | Expired/stale quote before commitment | Reject, never dispatch capture |
| PAY15 | Quote expires after capture commitment | Complete same accepted operation, never demand second payment |
| PAY16 | Cancel races with capture | One transaction wins; either cancelled before commit or capture remains locked; never both cancellation release and capture |
| PAY17 | Cancel races with order-create response | Cancel remains terminal; late order stored only for audit, no capture |
| PAY18 | Invalid webhook signature / malformed headers | 400, no financial state mutation |
| PAY19 | Verification service unavailable | 503, provider can retry; no trusted event stored as verified |
| PAY20 | Same verified webhook twice | One event record/effect; duplicate acknowledged |
| PAY21 | Pending event arrives after completed event | No downgrade of SETTLED |
| PAY22 | Valid event for unknown order | No synthesized payment/grant; sanitized audit only |
| PAY23 | Capture request retried after retention window | GET/manual review only, no new mutation identity |
| PAY24 | Verified refund/reversal | Suspend affected paid grants; preserve original grants and history |
| PAY25 | Fake browser success / forged return token | No fulfillment; ownership and stored order validation fail safely |

Fault-injected provider responses must be labeled as such. PAY01, duplicate user action, cancellation, hosted return, and a real verified webhook need actual sandbox evidence. A simulator webhook does not replace the real signature-verification gate. Do not brute-force actual financial failures; use deterministic test-server fixtures for rare mismatch and network cases.

## Security and browser tests

- Two fresh judge sessions: every cross-workspace campaign/quote/interpretation/payment/receipt lookup returns 404, including guessed IDs and direct deep links.
- CSRF missing/incorrect token or untrusted Origin cannot mutate; webhook route remains authenticated with provider signature.
- Access-code brute force and interpretation quota return 429; global cap survives restart. Request sizes and input lengths enforced server-side.
- Browser bundle/repository/history candidate scan has no secret, private buyer credential, raw provider payload, or test-only fault endpoint. Logged failures redact headers and PII.
- XSS strings in client briefs render as text, not HTML; malicious policy/asset metadata is not injected unsafely. SVG originals have no script/external references.
- Confirmed-usage form requires explicit acknowledgement; unresolved/unsupported states disable checkout on UI and server.
- Refresh each route during idle, AI running, approval, capture pending, settled, and error states. No automatic re-extraction or new payment on reload.
- Browser Back from PayPal cannot resurrect a cancelled/stale payment. Closing/returning to tab resumes server status.
- Inspect desktop/mobile viewports, keyboard flow, visible focus, labels, live regions for status, contrast, reduced motion, no overflow. Grid behavior and mobile cards share the same assessment.

## AI tests

Execute AI_SPEC's 18 scenarios, including >=36 live samples and four withheld paraphrases, plus deterministic validator tests. Record sample counts, exact-match/issue/evidence results, observed latency, and limitations. A safe failure with a clear retry/clarification message is preferable to an invented success. Every accepted edited field has evidence or explicit user correction provenance.

## Release checklist

- [ ] P0/P1 defects resolved. P0 = duplicate charge, unverified grant, secret exposure, cross-session access, data loss. P1 = broken main journey, unsupported use purchasable, unrecoverable pending payment, wrong price/quote binding, missing genuine integration.
- [ ] All domain/payment/security blocking tests pass on actual PostgreSQL.
- [ ] Real hosted AI extraction and actual hosted $20 Sandbox purchase verified.
- [ ] Same revision costs zero afterward; unsupported broadcast blocked.
- [ ] Actual webhook authenticated, durable dedup/recovery tested.
- [ ] Clean clone reproduces build and documented setup; production image runs.
- [ ] Browser console clean; responsive screenshots inspected; all route refreshes safe.
- [ ] Public repository and runtime contain no secrets or public test switches.
- [ ] Deployment persists through restart and is funded/retained through judging.
- [ ] README, license, original asset provenance, sponsor claims, demo instructions accurate.
- [ ] Video <3:00, public, readable, authentic; Devpost fields and private access tested.
- [ ] Final submission confirmation observed; draft status never called submitted.

Optional Studio, additional dashboards, PDF receipt generation, and more countries/platforms are not release blockers and must be removed or left out if they threaten these gates.
