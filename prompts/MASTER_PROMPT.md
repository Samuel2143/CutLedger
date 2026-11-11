Implement CutLedger in this existing repository. This is explicit authorization to build the application, not another architecture or brainstorming request. I will do zero manual coding. You must create/edit files, run commands, install appropriate dependencies, execute tests, fix failures, inspect the browser, and make the application operational. I will supply secure credentials, account access, required approvals, and review results.

Start by inspecting the current repository and reading AGENTS.md, START_HERE.md, docs/PROGRESS.md, and docs/IMPLEMENTATION_PLAN.md. Then read the relevant contracts: PRODUCT_SPEC, ARCHITECTURE, DATA_MODEL, API_CONTRACTS, RIGHTS_ENGINE, PAYMENT_FLOW, AI_SPEC and AI_OUTPUT.schema.json, VERIFIED_SOURCES, DEPLOYMENT, TEST_PLAN, DEMO_PLAN, and SPONSOR_USAGE. These files are the agreed specification, not suggestions to redesign. Preserve existing working code and user changes. If implementation already started, resume from verified evidence rather than recreating it.

THE PRODUCT

CutLedger: “When the campaign changes, find and buy only the additional media rights it needs.” A freelance editor has an already-licensed campaign. A client revises distribution, territories, platforms, or duration. AI proposes structured usage edits with literal source evidence. The user confirms them. A deterministic backend compares the new usage with existing entitlements and merchant-owned policies, explains gaps, and quotes only incremental eligible upgrades. The buyer approves through genuine PayPal Sandbox, the server captures and verifies, and the app issues immutable revision-linked permission records. Unsupported usage never receives an invented price or false clearance.

Build the approved single-merchant three-original-asset demo, not a marketplace or a generic chatbot. Merchant: CutLedger Studio. Campaign: Northstar Launch, anchor 2026-11-15. Harbor Lines and Kinetic Shapes initially hold ORGANIC30; Paper Orbit initially holds GLOBAL365. The main brief is: “Keep the visuals, but we're now running this as paid advertising in the US and UK for 90 days.” Expected final assessment: $8 + $12 + $0 = $20. After payment, repeating the same usage costs $0 and creates no order. Television broadcast is unsupported and blocks checkout. Seeded rights must be labeled seeded prior permissions, not fabricated historical payments.

ARCHITECTURE AND STACK

Use one modular Spring Boot/React application and PostgreSQL, served from one origin. Selected baselines: Java 17, Spring Boot 3.5.16, Maven wrapper 3.9.16, React 19.3.0, TypeScript 6.0.3, Vite 8.3.7, patched Node 24 LTS, PostgreSQL 17.11 or the host's supported 17.x patch, matched AG Grid Community/react 36.2.0. These documentation-checked pins still need package resolution and a real build. Record exact dependencies, locks, and image digests in Phase 0. Make only narrow evidence-backed compatibility changes; update the affected specifications rather than silently drifting.

Backend: Spring MVC, Security, Validation, JDBC/JdbcTemplate, Flyway, Actuator, explicit transactions, Java HTTP adapters. Modules: workspace, catalog, campaign, interpretation, rights, quote, payment, entitlement, audit, bootstrap/web. Frontend: typed API client, routed campaign workspace, structured interpretation review, rights comparison, quote, checkout/status/receipt, permissions/activity, consistent reusable UI tokens. No Kafka, Redis, Kubernetes, microservices, or elaborate agent framework. PostgreSQL durable jobs with leases handle reconciliation. Build the schema and indexes in DATA_MODEL, including uniqueness and ownership constraints.

RIGHTS AND DATA RULES

Use fixed nested ORGANIC30, PAID90, GLOBAL365 tiers and exact cumulative prices in PRODUCT_SPEC. Coverage requires all requested dimensions to fit a real grant; never combine unrelated dimensions across grants. The campaign anchor never moves and upgrades do not restart the license window. ISO calendar dates use inclusive start and exclusive end. USD amounts are integer minor units. The backend alone chooses the smallest adequate eligible tier and credits the highest compatible prior grant value. Preserve immutable original grants and policy snapshots. Unsupported, ambiguous, or review-required usage prevents a payable campaign quote.

Every quote snapshots revision, policy, entitlements, purchased scope, credit, prices, hashes, and expiry. It lasts ten minutes. Changed inputs invalidate uncommitted quotes. One active payment locks campaign confirmation/checkout to prevent credit races. Expiry blocks a new capture commitment; a commitment made while valid remains binding during recovery. A later network response must never create a second payable transaction.

AI IMPLEMENTATION

The original baseline was `gemini-3.8-flash`; use the documented 11 November alternative `gemini-3.5-flash-lite` GenerateContent after its successful real probe (DECISIONS.md). Verify the adopted GenerateContent API envelope/response/schema subset using official docs and a redacted real fixture; do not copy obsolete SDK examples or invent a response field. Build the adapter interface from AI_SPEC. If the model is not accessible, tell me the precise account action or documented alternative and record the change; do not silently substitute keyword parsing or fake output.

AI extracts proposed changes only. It has no payment tools, pricing authority, or coverage authority. Validate schema, evidence substrings, enums, dates, negation, conflicts, unsupported requests, and inherited fields. Preserve uncertainty and user correction provenance. Require explicit user confirmation before a revision is eligible for pricing. Implement quotas, bounded retries, timeouts, duplicate-request protection, and refresh recovery. Run the specified live eval cases and holdouts; report actual counts and failures. Manual structured entry is a labeled fallback, never represented as AI.

PAYPAL IMPLEMENTATION

Use genuine Sandbox Orders v2 creation and hosted buyer approval, followed by explicit return-page capture confirmation and backend capture/verification. Keep OAuth/client secret/model keys server-side. Fix the provider origin to Sandbox and show Sandbox labels. Create/capture request IDs and immutable payloads are persisted before sending. Provider GET verification must match completed capture, order identity, expected merchant, exact amount/currency, and accepted quote references. A frontend success callback or webhook payload alone cannot issue rights.

Follow PAYMENT_FLOW's exact state machine and transaction boundaries. Network calls occur outside DB locks. After verification, insert all upgraded grants, audit events, entitlement-version change, quote fulfillment, and SETTLED payment in one transaction with unique source-payment/asset constraints. A crash between external capture and local commit is recovered through provider reads, never a blind new charge. Enforce endpoint-specific verified idempotency retention; unknown/out-of-window mutation retries require review. Uncertain payment remains locked and is reconciled.

Verify real webhook signatures through PayPal, persist/deduplicate events before acknowledging, and re-read provider resources. Handle duplicated/out-of-order events, pending captures, lost responses, restarts, cancel/capture races, and verified reversals. Implement no public fake-paid endpoint or production fault injection. Test real sandbox approval/capture/webhook and fault-injected edge cases, labeling the latter honestly.

PRODUCT QUALITY

Implement all PRODUCT_SPEC routes and states. The campaign revision and comparison are the centerpiece: original asset previews, evidence-backed changed fields, one already-covered asset, two exact upgrades, source clauses, sticky quote summary, authentic checkout, revision-linked receipt, preserved permission history. Use the specified warm-white/indigo design, typography, spacing, responsive cards/table, visible focus, labeled statuses, and polished loading/error states. No generic chat layout or excessive dashboard. Build original static SVG artwork with documented provenance; no third-party license claims. Inspect 1440×900, 1024×768, and 390×844 in a browser, not just screenshots inferred from source.

SECURITY

Use isolated browser workspaces, hashed opaque HttpOnly/Secure/SameSite=Lax session cookies, CSRF protection and exact Origin validation, ownership checks on every resource, input/body limits, access-code/rate limits, server-controlled money, safe redirect allowlists, parameterized SQL, and redacted logs. No secrets in frontend bundles, commits, logs, or videos. Keep private judge credentials out of public docs. Do not clear financial records to reset a demo. GET return/navigation must never move money.

SPONSORS

Use APIMatic's PayPal Context Plugin during the actual integration after inspecting its current installation instructions; record what it contributed and the files/tests it affected. Do not claim sponsor use from installation alone. AG Grid Community should provide useful rights comparison renderers, filters, sorting, pinned totals, keyboard interaction, and a source-clause drawer backed by server data. AG Studio is optional, only after core gates pass, before feature freeze, and within three hours; omit it if not clearly useful. Never imply plain AG Grid is Studio. Maintain actual evidence in SPONSOR_USAGE.

EXECUTION AND VERIFICATION

Work cumulatively through phases 0–6 in IMPLEMENTATION_PLAN:
0. Reproducible environment, local app/DB, exact versions, secure config, early PayPal/Gemini probes.
1. Real minimal PayPal vertical slice with verification, durable state, idempotency, and persisted grant.
2. Full deterministic rights engine, catalog, revisions, quotes, credit and history.
3. Real AI extraction, evidence/confirmation, ambiguity handling, live evals.
4. Complete polished responsive frontend and meaningful AG Grid comparison.
5. Payment/security/restart/concurrency hardening, sponsor evidence, browser recovery tests.
6. Approved deployment, public repo/license/README, hosted judge journey, video/submission materials and verified submission status.

Do not stop after writing a plan or scaffolding. Continue autonomously between passed phases, giving concise progress reports. Stop dependent work only for genuinely missing credentials/access/approval; continue independent work while waiting. Never ask me to edit code, write SQL, run migrations, or debug configuration manually. Prepare secure fields and exact account steps instead. Do not bypass approval for paid resources: prepare the exact resource/cost proposal and request only the necessary approval unless already given. Prepare publication/submission artifacts completely before any required final owner action.

Use the planned domain, PostgreSQL transaction/concurrency, provider fault, live AI, real Sandbox, and browser tests. Run commands and inspect outcomes. Fix failures and rerun relevant checks. Never invent test counts, payment success, screenshots, deployment URLs, or submission confirmation. Distinguish PASS, FAIL, BLOCKED, and NOT_RUN. Do not mark a phase VERIFIED without its required evidence. Document limits rather than presenting unsupported features as complete.

Maintain docs/PROGRESS.md as the resume point, docs/VERIFICATION.md for actual commands/results/redacted provider evidence, docs/DECISIONS.md for narrow deviations, and sponsor evidence. Preserve accepted contracts and keep names/enums/API examples consistent. After each phase report: completed functionality, files/modules, actual verification, remaining blockers, and next task. If interrupted or context resets, read these records and resume without repeating proven work.

DEPLOYMENT AND DEADLINE

Deploy one Docker web service and persistent managed PostgreSQL on Render after concrete account/cost approval. Use same-origin HTTPS, actual hosted webhooks, safe secrets, correct return URLs, durable storage, and clean-clone setup. Prefer paid always-on resources retained through December 21 judging/results, not a free database that expires mid-judging. Record actual prices before provisioning; this prompt is not permission for unbounded charges.

Target submission: November 12, 2026, 18:00 IST. Official cutoff: November 13, 2026, 01:30 IST. Freeze new features November 11 at 20:00 IST. Recompute remaining time when you start. Cut optional Studio/analytics before risking payment reliability, deployed usability, or the video. Prepare the truthful <3-minute script in DEMO_PLAN, public licensed repository, original asset provenance, private judge instructions, and Devpost draft. A real final submission requires an observed confirmation, not a saved draft.

Begin now with repository inspection and Phase 0 implementation. Ask only for the first genuinely blocking account/access action, and keep working on everything independent of it. Finish with an operational, verified application and concrete evidence, or an exact account-action blocker with the rest of the authorized work completed.
