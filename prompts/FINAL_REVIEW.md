Perform an independent final review of the implemented CutLedger application as a skeptical senior engineer and hackathon judge. This is a review and authorized defect-fix request, not a redesign. Do not trust previous completion claims. Inspect the current files, git state, deployed app if available, and actual evidence. Read AGENTS.md, START_HERE.md, PROGRESS, all domain/payment/API/AI contracts, TEST_PLAN, DEMO_PLAN, and VERIFICATION.

First establish what actually exists and runs. Compare the submitted/deployed commit with the repository. Re-run targeted meaningful checks and inspect the real browser. Clearly separate observed results, inference, and missing evidence. Do not use the author's test names as proof without checking what the assertions cover.

Prioritize:
1. Can a timeout, double click, concurrent tab, webhook replay, lease collision, process restart, cancellation race, or expired quote cause duplicate charging or grant issuance? Is the provider idempotency window actually verified? Does uncertainty block replacement checkout?
2. Can forged price/currency/order/merchant/reference, frontend success, unsigned webhook, or cross-workspace ID yield a permission? Does completion require independent provider verification and atomic multi-asset fulfillment?
3. Are original entitlements/policy snapshots preserved, credit correct, fixed dates respected, non-nested grants not combined, and unsupported use blocked? Independently check $20, repeat $0, direct global $70, later global $50.
4. Does the real model handle negation, conflicts, ambiguity, evidence, and prompt injection without pricing authority? Can stored unresolved issues be bypassed at confirmation? Are claimed live eval results genuine?
5. Is the judge journey understandable within 25 seconds, polished on desktop/mobile, accessible, and safe on refresh/error? Are all prices/statuses server-derived? Does the public app actually use real Sandbox and hosted AI?
6. Are secrets/credentials/test hooks absent from public source/runtime/video? Are session/CSRF/ownership/rate limits effective? Will hosting/database persist through judging?
7. Are sponsor claims accurate, code licensed, original assets documented, README reproducible, video public and under three minutes, and Devpost submitted rather than merely drafted?

Write findings with severity, concrete reproduction, affected file/line, impact, and minimal fix. P0/P1 payment/security/main-journey issues are release blockers. Implement safe minimal fixes, add meaningful regression tests, rerun affected checks, and inspect changed UI. Do not add speculative features or rewrite working architecture. Never perform real-money transactions; use Sandbox and labeled fault injection. Ask only for access that genuinely blocks verification.

Update docs/FINAL_REVIEW.md, PROGRESS, and VERIFICATION with observed evidence and remaining risks. Final verdict must be one of READY, READY_WITH_NONBLOCKING_LIMITS, or NOT_READY, with the actual deployment/submission status. Missing real payment/model/webhook evidence means NOT_READY for the claimed completed entry. Do not manufacture a favorable verdict to meet the deadline.
