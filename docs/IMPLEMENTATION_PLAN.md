# C — Cumulative execution roadmap

The owner does no coding. Codex writes files, runs commands, verifies behavior, repairs failures, and produces a usable deployed application. Read the current PROGRESS record before starting. No phase is complete merely because its code exists. Under the master prompt, proceed automatically after each passed gate; under an individual phase prompt, stop after that phase's report.

## Schedule and scope control

Planning baseline: 10 November 2026 afternoon IST. Rough focused effort: Phase 0 2h, Phase 1 5h, Phase 2 4h, Phase 3 4h, Phase 4 6h, Phase 5 5h, Phase 6 5h; about 31 focused hours plus access delays and rest. These are estimates, not promises. Start provider access and deployment scaffolding in Phase 0 so accounts do not become last-day surprises.

| Milestone | Target IST | Cut line |
|---|---|---|
| Dependency/access probes and local boot | Nov 10, 15:30 | Avoid optional libraries and SDK experiments |
| Real minimal Sandbox capture + persisted grant | Nov 10, 21:00 | Core payment access is a critical-path blocker |
| Rights engine + hosted AI extraction | Nov 11, 09:00–12:00 | No new dimensions beyond fixed scope |
| Polished main journey | Nov 11, 18:00 | Use Community grid; skip Studio if behind |
| Feature freeze | Nov 11, 20:00 | Reliability, deployment, docs, video only afterward |
| Hosted full journey / recording candidate | Nov 12, 12:00 | Remove optional features rather than weaken correctness |
| Video uploaded and Devpost draft complete | Nov 12, 15:00 | Keep three hours for submission fixes |
| Submit and verify confirmation | Nov 12, 18:00 | 7.5-hour safety margin before official cutoff |

Recompute targets from actual start time. A later start reduces optional polish/Studio, not required payment tests. If payment or model access fails, report the blocker immediately and continue independent backend/UI work with clearly labeled test doubles. Do not call an incomplete live integration release-ready.

## Phase 0 — Environment and scaffolding

**Objective:** one reproducible local application and early evidence that critical accounts work.

**Build:** inspect repository/AGENTS; preserve specifications; initialize project/build tooling only as needed; create Maven wrapper, Boot app, React/Vite/TS app, local PostgreSQL compose, Flyway baseline, typed config, `.env.example` and ignore rules, initial session endpoint, health/readiness, same-origin production assembly, CI commands, Docker skeleton. Resolve exact dependency/image versions and document them. Install/read APIMatic's PayPal Context Plugin using current documented instructions and record actual discovery/use; no badge-only claim. Probe PayPal OAuth and Gemini structured output securely when keys are available. Prepare Render resource proposal without paid provisioning.

**Modules:** backend/bootstrap and workspace, frontend/app, root build/container/config, docs/DECISIONS, docs/VERIFICATION.

**Dependencies:** local Java/Node/Docker and network/package access; credentials for live probes. Scaffolding continues if credentials are pending, but probe gate remains blocked.

**Automated checks:** backend startup test; PostgreSQL migration test; frontend typecheck/build; container health smoke; secret-pattern scan of tracked candidate files. No placeholder test that only asserts true.

**Manual check:** browser opens shell, session persists across refresh, unavailable integrations clearly labeled, no secret output. Owner enters keys in secure configuration; Codex reruns probes.

**Acceptance/DoD:** clean documented startup works, required versions recorded, migrations run against real Postgres, provider probe outcomes recorded honestly, no integration compatibility uncertainty hidden. Account-dependent failures are explicit blockers.

**Deliverable/report:** runnable skeleton, exact commands/versions, actual probe status, redacted evidence, precise outstanding owner action, next phase. No claim of payment integration yet.

## Phase 1 — Real PayPal vertical slice

**Objective:** prove the riskiest integration before building broad UI.

**Build:** merchant+one asset+tier seed, minimal confirmed revision/quote, isolated workspace, complete durable payment identity, server order creation, hosted approval, capture commitment, provider GET verification, atomic persisted grant, basic receipt, duplicate request handling, early reconciliation worker, basic authenticated webhook ingestion. Use the same final states/tables, not throwaway in-memory payment code. Temporary minimal UI is acceptable; fake payments are not.

**Modules:** catalog, quote, payment, entitlement, audit, migrations; frontend/checkout minimal; provider adapter contract fixtures.

**Dependencies:** Phase 0 build and PayPal credentials; real personal buyer; tunnel/hosted URL for real webhook gate. Do not defer the crash-safe design until Phase 5.

**Automated checks:** stable key/payload, amount/currency/merchant mismatch rejection, duplicate capture/fulfillment uniqueness, webhook verification adapter behavior, failure-after-capture restart recovery using fault injection, transaction rollback. Provider tests use doubles for injected faults only.

**Manual check:** actual seller/buyer Sandbox approve/capture, GET-verified provider references and grant in DB; refresh and double-click; cancel pre-capture. Receive actual webhook if HTTPS endpoint available; missing webhook access remains a tracked gate.

**Acceptance/DoD:** one genuine payment produces one grant; refresh/retry never creates a second capture/grant; uncertain state is recoverable. No unverified success UI. Record exact provider request/response fields and endpoint retry retention.

**Deliverable/report:** working vertical slice, redacted order/capture evidence, verification query counts, successful tests, webhook status, access gaps. Continue only with known payment invariants preserved.

## Phase 2 — Core rights engine

**Objective:** make incremental rights the reliable differentiator.

**Build:** complete three-asset catalog, original policy clauses, fixed nested tiers, seeded entitlement provenance, normalized usage, immutable numbered revisions, pure coverage engine, tier selection/credit arithmetic, per-dimension reason codes, ten-minute quote snapshots/hashes, campaign payment lock and invalidation, entitlement history and old-policy retention.

**Modules:** catalog, campaign, rights, quote, entitlement, API DTOs, seed migrations; minimal comparison view.

**Dependencies:** Phase 1 persistence and payment identity. Replace the one-asset fixture with final three-asset fixtures without changing verified payment semantics.

**Automated checks:** all RIGHTS_ENGINE examples including 2000/0/7000/5000; exact date edges; unsupported uses; no union across unrelated grants; stale versions; old-policy preservation; concurrent quote acceptance; price tampering; no purchase for zero total.

**Manual check:** main revision compares three assets and two upgrades; clause text matches dimensions; same revision after actual fulfillment is free; history still contains seed and upgrades.

**Acceptance/DoD:** pricing and coverage are backend-only, deterministic, independently tested, and bound to immutable quotes. No unsupported row can coexist with enabled campaign checkout.

**Deliverable/report:** domain tests and results, sample assessment/quote, migration changes, observed calculations, all invariant failures fixed.

## Phase 3 — AI interpretation

**Objective:** turn messy client language into evidence-backed edits users can confirm.

**Build:** real Gemini adapter using probed current API, complete output schema, untrusted-input prompt, durable interpretation lifecycle/lease, normalization, evidence spans, uncertainty/conflict/unsupported handling, explicit confirmation with correction provenance, limits/retries/cache/quota, manual entry visibly distinguished from AI. No coverage/pricing authority in the model.

**Modules:** interpretation, campaign confirmation DTOs, schema resource, frontend structured review, eval fixtures/reports.

**Dependencies:** Phase 2 usage contracts and live Gemini access from Phase 0. Save redacted provider fixtures, not the key or private prompt content.

**Automated checks:** schema/normalization/evidence/date/negation tests; malformed provider outputs; prompt injection; input limits/quota; duplicate interpretation; confirmation unresolved issues. Execute the live evaluation suite with holdouts and record actual denominators/latency/errors.

**Manual check:** real default brief → evidence chips → confirmation → exact $20 assessment; one contradiction and one unsupported use visibly blocked; refresh retrieves result without another paid model request.

**Acceptance/DoD:** real provider works, explicit-case extraction target met, every unsafe checkout test blocked, no fabricated evidence accepted, user confirmation required.

**Deliverable/report:** actual model/API/prompt/schema versions, evaluation table, failures/fixes, measured latency and known limitations; never claim generalized accuracy from a few examples.

## Phase 4 — Professional frontend

**Objective:** make the complete judge journey immediately understandable and polished.

**Build:** all PRODUCT_SPEC routes, original asset artwork/provenance, tokenized design, responsive shell, brief/review flow, AG Grid Community rights comparison, clause drawer, sticky quote rail, hosted approval/return/cancel pages, live payment status, receipt and permission timeline, empty/loading/error/stale/offline states, keyboard accessibility. The real backend drives every value and status.

**Modules:** frontend features/components/styles/assets, backend view DTOs only as necessary, browser tests/screenshots.

**Dependencies:** functional phases 1–3; current grid version/license verified. Community grid is implemented now because it is the central UI, not postponed for sponsor decoration.

**Automated checks:** component behavior for confirmation/quote disable rules; Playwright main flow using labeled test provider fixtures in CI; keyboard navigation, route refresh, mobile layout; axe checks if compatible and useful. Also run one real local/hosted journey separately.

**Manual check:** inspect 1440×900, 1024×768, 390×844; click every evidence link; deliberate slow/error responses; tab through checkout; verify formatted totals and seed/paid labels.

**Acceptance/DoD:** no dead buttons, console errors, overflow, clipped prices, optimistic cleared state, or unexplained spinner. Judges can see the three/two/one/$20 difference immediately.

**Deliverable/report:** screenshots, observed browser results, accessibility corrections, implemented state matrix, real integration status; report optional visual work separately.

## Phase 5 — Reliability and sponsor evidence

**Objective:** harden the already-real money flow and substantiate sponsor use.

**Build:** complete reconciliation/webhook cases, retry-retention enforcement, lease recovery, reversal review, session ownership/CSRF/rate limits, safe logs, concurrent-tab handling, secret scan, deployment readiness. Finish APIMatic usage evidence and substantive AG Grid comparison features. Only if all core gates pass and before freeze, attempt AG Studio in a branch/isolated module with a strict three-hour cap; otherwise omit it honestly.

**Modules:** payment/worker/security/observability, grid polish, tests, sponsor/verification docs.

**Dependencies:** phases 1–4; real HTTPS webhook endpoint; no introduction of a separate orchestration framework.

**Automated checks:** entire payment fault matrix, actual PostgreSQL locking races, invalid/replayed/out-of-order webhooks, stale/expired quote, forged amount/order/merchant, unauthorized workspace, CSRF, restart and duplicate workers; inspect bundle for secrets.

**Manual check:** hosted/local real Sandbox repeat capture action, browser return recovery, actual verified webhook; screenshot useful grid interaction and evidence of APIMatic-informed implementation.

**Acceptance/DoD:** no unresolved P0/P1 safety defect; uncertain transactions never open replacement checkout; all actual sponsor claims trace to installed/used technology.

**Deliverable/report:** release-blocking checklist, failure-recovery evidence, sponsor usage record, remaining limitations and optional feature decisions. No fabricated adversarial provider result.

## Phase 6 — Deployment and submission

**Objective:** a judge can use the hosted application, inspect the code, and understand the demo without help.

**Build:** final Docker/Render config, approved paid resources, migrations/secret config/HTTPS, hosted webhook registration, private judge instructions, public clean repository/MIT license, README, asset provenance, demo script/video assets, Devpost draft, final submission checklist. Prepare all materials before requesting any remaining account action.

**Modules:** deployment files, README/LICENSE, docs/demo/submission/verification; only necessary bug fixes in application code.

**Dependencies:** Phase 5 release gates, repository/hosting/YouTube/Devpost access, concrete spending approval where required. Owner may need to perform sign-in/consent or final platform action; no manual coding.

**Automated checks:** clean clone/build/test, production container smoke, dependency/secret review, public URL/deep-link/ownership checks, final hosted browser journey, actual AI and Sandbox payment smoke.

**Manual check:** fresh private browser reproduces judge steps; video <3:00, readable and public, authentic payment references; repo public/license present; private credentials not public; Devpost link fields open; owner eligibility and entry confirmation verified.

**Acceptance/DoD:** actual deployed URL and submitted repository commit verified, all release blockers passed, video and Devpost fields prepared/published as authorized, final submission confirmation observed or explicitly pending owner action. Do not say “submitted” for a saved draft.

**Deliverable/report:** URLs, commit/tag, deployment/provider evidence, final test results, video duration, submission status/confirmation, access/retention notes. If blocked, provide exact prepared artifact and one concrete remaining human action.
