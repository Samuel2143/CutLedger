# CutLedger repository instructions

## Authorization and objective

This repository currently contains a specification handoff. Documentation preparation alone does not authorize application implementation. Once the owner explicitly asks to implement, build CutLedger through the phases in `docs/IMPLEMENTATION_PLAN.md`; do not stop at advice or scaffold-only output. The owner does zero coding.

Read `START_HERE.md`, then the relevant domain contracts before editing. Preserve working features and user changes. Use the existing checkout. Do not introduce Kafka, Redis, microservices, Kubernetes, a generalized agent framework, or a new product concept. Do not create parallel agents unless the owner explicitly requests them.

## Non-negotiable invariants

1. Only the deterministic backend decides coverage and money. AI supplies evidence-backed proposed edits.
2. Every checkout binds one accepted immutable revision, quote, policy snapshot, and entitlement version.
3. Pending or uncertain money movement blocks replacement checkout until reconciliation resolves it.
4. Only server-verified completed capture with matching amount, currency, merchant, and order identity can issue entitlements. Webhooks and browser messages alone are insufficient.
5. Durable request IDs, uniqueness constraints, and transactional fulfillment enforce idempotency. Never blindly retry with a new payment key.
6. Historical grants remain immutable. Unsupported use never becomes covered. Previously held eligible rights receive deterministic credit.
7. No payment or hosted-model simulation in the final running demo. Test doubles belong only to tests and visibly labeled development fixtures.
8. No secrets, tokens, buyer passwords, raw payment payloads, or private briefs in commits/logs/screenshots.

## Execution

- Follow phases 0–6, update `docs/PROGRESS.md` after every meaningful milestone, and continue automatically under the master prompt. Individual phase prompts stop after their requested phase.
- Resolve access blockers early; continue independent authorized work while waiting. Ask for account actions, never user code edits. Obtain approval for a concrete paid resource configuration before spending unless already authorized.
- Freeze exact dependency versions, lockfiles, and image digests after the first successful build. Do not opportunistically upgrade the stack later.
- Run meaningful tests appropriate to changed behavior; never claim an unrun check passed. Tests must check externally observable invariants, not merely duplicate implementation logic.
- Inspect desktop and mobile UI in a browser. Accessibility, error states, and refresh recovery are release requirements.
- Keep `docs/DECISIONS.md`, `docs/VERIFICATION.md`, and `docs/SPONSOR_USAGE.md` current as implementation proceeds. Evidence is a command/result/date or a redacted observed provider reference, not a narrative assertion.
- Report completed work, verification evidence, remaining blockers, and the next action. Mark blocked requirements explicitly. Do not invent deployment, screenshots, tests, video publication, or submission confirmation.

## Delivery

Use a public MIT-licensed code repository when authorized; document the separate merchant demo asset terms. Keep credentials and judge-only access instructions private. Finish the deployed verified app, README, evidence, and submission draft by 12 November 2026, 18:00 IST. Never trade payment correctness for sponsor breadth. No feature expansion after 11 November, 20:00 IST.
