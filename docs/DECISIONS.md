# Architecture decisions

| ID | Decision | Reason |
|---|---|---|
| ADR-001 | Modular Spring/React monolith + PostgreSQL | One deploy, familiar backend, durable transactions without extra infrastructure |
| ADR-002 | Fixed nested merchant-owned tiers | Precise incremental pricing and testable scope within the deadline |
| ADR-003 | Gemini proposed edits; Java coverage/pricing | AI handles language while financial/legal-policy decisions stay deterministic |
| ADR-004 | Hosted PayPal approval via REST | Genuine checkout with fewer frontend SDK/version dependencies |
| ADR-005 | Immutable quote and capture commitment before dispatch | Clear expiry semantics and recovery when the provider response is lost |
| ADR-006 | Campaign serialized during active payment | Prevents competing quotes from spending the same entitlement credit |
| ADR-007 | Browser-isolated judge sessions | No shared mutable demo state or full account system needed |
| ADR-008 | AG Grid Community core; Studio optional | Useful comparison first; sponsor complexity cannot threaten release |
| ADR-009 | Paid persistent hosting through judging | Avoid sleeping web service and expiring free database during evaluation |

Implementation deviations must append date, observed incompatibility, source or failing test, narrow change, files updated, and verification. A model availability issue does not authorize silent fake extraction; a payment failure does not authorize mocked checkout.


## Adopted accessible structured extraction endpoint
 accessible structured extraction endpoint

Gemini 3.8 Flash Interactions returned repeated HTTP 503 and later exceeded the 20-second bound, including a minimal diagnostic; model GET succeeding did not prove inference availability. The documented Gemini 3.6 Flash GenerateContent alternative produced a real structured response in 2,650 ms (419 reported tokens). `InterpretationValidatorTest.savedRealGemini36ResponsePassesCanonicalSchemaAndLiteralEvidence` passes the complete canonical schema, exact literal evidence, GB normalization, inherited platform and 90-day result.

Adopt **gemini-3.6-flash** through POST `/v1beta/models/gemini-3.6-flash:generateContent`, fixed Google origin, no tools, `store:false`. Live discovery and probe confirm `generationConfig.responseFormat.text` with MIME enum `APPLICATION_JSON` and schema; response text is `candidates[0].content.parts[].text` excluding thought parts, exactly one STOP candidate. Keep backend canonical bounds; provider projection omits decoder-complexity bounds. This is the explicit alternative permitted by the master prompt, not an accuracy gate waiver. All live evals remain required. Historical 3.8 evidence stays intact. Sources and public fixture: `evidence/gemini-generate-content-contract.json`, `evidence/gemini-alternative-generate-content-public-fixture.json`.

### Interpretation cost and recovery limits

Workspace submissions:20/hour; actual model dispatches:60/workspace/day and configured global200/day, all durable. Each interpretation permits3dispatches total including leased restart recovery, at most1transient retry and1schema repair. Honor numeric Retry-After/GoogleRetryInfo up to24h without blocking request threads; display the retry date. Recovered work whose base revision changed or campaign entered checkout is failed before another model call. Expired workspaces are never processed. Private input is never shared across workspace caches.

## Public source boundary

The owner requested all live payment evidence be omitted from publication. The public repository begins with a source snapshot and excludes private implementation Git history, live payment/account diagnostics, operational chronology and provider identity records. No financial records are deleted from the local application.
