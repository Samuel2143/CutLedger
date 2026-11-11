# A — REST contracts

Base `/api/v1`. JSON uses camelCase, UUID IDs, ISO dates, RFC3339 UTC instants, integer minor units, explicit currency. All responses contain a request trace ID in `X-Request-Id`. Session-scoped endpoints require the session cookie. Mutations require `X-CSRF-Token` and exact allowed Origin, except session creation (access code + Origin) and PayPal webhooks (provider signature).

Client mutation `Idempotency-Key` is a UUID for operations that can create revisions, quotes, payments, or interpretations. Reuse with identical payload returns the same resource; reuse with a different payload returns 409 `IDEMPOTENCY_CONFLICT`. Provider request IDs are different durable server-generated keys. `If-Match: "<campaignVersion>"` is required for revision confirmation and quote/payment creation; stale versions return 409 `STALE_CAMPAIGN`.

## Endpoints

| Method/path | Input | Result / semantics |
|---|---|---|
| POST `/demo-sessions` | `{accessCode}` | 201 sets cookie, returns `{workspaceId,campaignId,csrfToken,serverTime}`. Rate-limited; valid existing session returns 200 same workspace unless explicit fresh-session action with no active payment. |
| GET `/session` | cookie | 200 `{workspaceId,campaignId,csrfToken,expiresAt,serverTime}`; 401 otherwise. CSRF token can be securely rotated and stored hashed. |
| GET `/campaigns/{id}` | — | campaign, version, latest revision, asset previews, existing rights summary, activePaymentId; no secret fields |
| POST `/campaigns/{id}/interpretations` | `{brief,baseRevisionId}` + idempotency key | 202 `{id,status:"PENDING"}`; durable lease-backed interpretation table worker; no new revision yet |
| GET `/interpretations/{id}` | — | PENDING/RUNNING/READY/NEEDS_CLARIFICATION/UNSUPPORTED/FAILED, proposed edits, normalized candidate, evidence, issues, provider model and provenance |
| POST `/campaigns/{id}/revisions` | confirmation below + If-Match/key | 201 immutable revision + new campaign version; rejects unresolved ambiguity, unsupported requirements, or active payment |
| GET `/campaigns/{id}/revisions` | optional cursor | revision summaries |
| GET `/campaigns/{id}/revisions/{revisionId}` | — | normalized usage and confirmation evidence |
| GET `/campaigns/{id}/revisions/{revisionId}/assessment` | — | per-asset coverage, missing dimensions, source clauses, estimated deterministic deltas, canQuote; explicitly not an accepted quote |
| POST `/campaigns/{id}/quotes` | `{revisionId}` + If-Match/key | 201 immutable quote if upgrade required; 200 `{kind:"NO_PURCHASE_REQUIRED",assessment}` if already covered; 422 if unsupported |
| GET `/quotes/{id}` | — | snapshot, current lifecycle, lines, total, expiry, serverTime |
| POST `/quotes/{id}/payments` | `{acceptedSnapshotHash}` + If-Match/key | 202 `{paymentId,state,statusUrl}`; atomically accepts quote, creates payment/job; same quote returns same payment. No client amount. |
| GET `/payments/{id}` | — | state, safe resolution, accepted quote summary, approvalUrl only if usable, canCapture, canCancel, receiptUrl if settled, lastCheckedAt |
| POST `/payments/{id}/capture` | `{returnedOrderId}` + key | 202/200 current payment; validates token against stored order, verifies approval, commits capture if fresh/valid, schedules durable operation. Repeated calls are harmless. |
| POST `/payments/{id}/cancel` | `{}` + key | 200 CANCELLED only if no capture commitment; otherwise 409 `CAPTURE_IN_PROGRESS`. Creates audit, closes quote, unlocks campaign. |
| POST `/payments/{id}/reconcile` | `{}` + key | 202 enqueues status verification only, never creates replacement payment; rate-limited |
| GET `/payments/{id}/receipt` | — | 200 verified record + new grants only if SETTLED; 409 `PAYMENT_NOT_SETTLED` otherwise |
| GET `/campaigns/{id}/entitlements` | optional assetId | immutable grant records, current state, policy/clause links and source revision |
| GET `/campaigns/{id}/activity` | cursor, limit <=50 | redacted business events |
| GET `/policies/{id}` | session | merchant policy version, clauses, tier definitions; no admin mutation endpoint |
| POST `/webhooks/paypal` | provider raw event + signature headers | verified durable enqueue then 204; invalid 400, transient verification/persistence failure 503 |

Use controller validation plus a shared OpenAPI 3.1 file generated/maintained during implementation; this document is the design contract. Provider IDs are opaque strings, never parsed for meaning. Non-owned resources return 404.

## Confirmed revision example

```json
{
  "interpretationId": "a1000000-0000-4000-8000-000000000001",
  "baseRevisionId": "b1000000-0000-4000-8000-000000000001",
  "usage": {
    "schemaVersion": 1,
    "distributions": ["PAID_ADS"],
    "territories": ["US", "GB"],
    "platforms": ["INSTAGRAM"],
    "startDate": "2026-11-15",
    "endDateExclusive": "2027-02-13"
  },
  "resolvedIssueIds": [],
  "issueResolutions": [],
  "excludedRequirementsAcknowledged": [],
  "userEditedFields": [],
  "confirmed": true
}
```

If manually entering usage, `interpretationId:null`, `origin:"MANUAL"`, and explicit confirmation are required. Do not represent this as AI. User edits need field-level provenance, and confirmation must reference the exact interpretation/base revision. Unsupported clauses cannot be discarded by sending an empty issues list: server checks stored issues and resolution records. For MVP unsupported interpretation must be revised/reinterpreted or explicitly replaced by a separate manual supported-use revision acknowledging the excluded use.

The server assigns stable issue IDs when validating model output. Each resolution has `{issueId,field,resolvedValue,acknowledged:true}`; its resolved value must match the final normalized usage, and every required issue must be resolved. `resolvedIssueIds` is a convenience list checked against those records, never sufficient alone. `excludedRequirementsAcknowledged` contains exact server-issued unsupported requirement IDs for a separately confirmed supported-subset manual revision; retain the originating interpretation and excluded requirements in its confirmation evidence, show “Excluded from this permission” in the receipt, and never describe the original full brief as cleared. Ordinary AI confirmation must have an empty exclusion list and no unsupported requirement.

## Assessment and quote examples

```json
{
  "revisionId": "b2000000-0000-4000-8000-000000000002",
  "canQuote": true,
  "currency": "USD",
  "totalDeltaMinor": 2000,
  "rows": [
    {"assetSlug":"harbor-lines","status":"UPGRADE_REQUIRED","heldTier":"ORGANIC30","targetTier":"PAID90","deltaMinor":800,"reasonCodes":["DISTRIBUTION_GAP","TERRITORY_GAP","DATE_GAP"],"clauseIds":["V1-DIST-PAID","V1-TERR-USGB","V1-WINDOW-90","V1-UPGRADE-CREDIT"]},
    {"assetSlug":"kinetic-shapes","status":"UPGRADE_REQUIRED","heldTier":"ORGANIC30","targetTier":"PAID90","deltaMinor":1200,"reasonCodes":["DISTRIBUTION_GAP","TERRITORY_GAP","DATE_GAP"],"clauseIds":["V1-DIST-PAID","V1-TERR-USGB","V1-WINDOW-90","V1-UPGRADE-CREDIT"]},
    {"assetSlug":"paper-orbit","status":"COVERED","heldTier":"GLOBAL365","targetTier":null,"deltaMinor":0,"reasonCodes":[],"clauseIds":["V1-DIST-PAID","V1-TERR-WORLD","V1-WINDOW-365"]}
  ]
}
```

```json
{
  "id": "c1000000-0000-4000-8000-000000000001",
  "status": "OPEN",
  "revisionId": "b2000000-0000-4000-8000-000000000002",
  "campaignVersion": 2,
  "entitlementVersion": 1,
  "snapshotHash": "<64 lowercase SHA-256 hex characters>",
  "currency": "USD",
  "totalMinor": 2000,
  "createdAt": "2026-11-10T09:00:00Z",
  "expiresAt": "2026-11-10T09:10:00Z",
  "serverTime": "2026-11-10T09:00:00Z",
  "lines": [
    {"assetSlug":"harbor-lines","priorCreditMinor":1000,"targetCumulativeMinor":1800,"deltaMinor":800,"targetTier":"PAID90"},
    {"assetSlug":"kinetic-shapes","priorCreditMinor":2000,"targetCumulativeMinor":3200,"deltaMinor":1200,"targetTier":"PAID90"}
  ]
}
```

Responses additionally include complete purchased-scope snapshots and IDs as specified in DATA_MODEL; abbreviated examples omit repeated scope for readability, not implementation permission. All IDs/hashes above are illustrative, not actual transactions.

## Errors

Use `application/problem+json` with `{type,title,status,code,detail,traceId,fieldErrors,retryAfterSeconds}`. No exception stack or raw provider response. Status mapping: 400 malformed; 401 no session; 403 CSRF/access gate; 404 not found/not owned; 409 stale/conflict/active payment; 410 quote expired; 422 unsupported or unresolved usage; 429 quota; 502 provider/schema failure; 503 unavailable. Payment timeout responses include the existing payment resource and reconciliation instructions; they never tell the client to create a new order blindly.

## Current implementation additions — 10 November 2026

Payment views include an integer `version`. Clients retain the greatest observed version to prevent a delayed status response from regressing a committed/settled display. The backend remains authoritative and never issues a grant from a client state. `quoteId` retrieves the complete immutable accepted snapshot; amount/currency and ownership are server-derived.

Assessment rows also expose `heldScope` (nullable immutable grant snapshot), `requestedScope`, and `policyVersionId` for the evidence used by that row. Covered historical rights point to their actual grant's policy version; eligible upgrades point to the current policy. Filtering, sorting and column widths are presentation preferences; the pinned total and quote use the backend's complete campaign assessment.

Current running revision confirmation supports MANUAL and AI_CONFIRMED. AI interpretation creation/polling is durable; stored base/hash, issues, exact issue field resolutions and correction provenance are enforced server-side. Unsupported AI confirmation is rejected. An explicit manual subset can acknowledge every stored unsupported requirement ID; those exclusions remain in immutable confirmation evidence. The UI offers editing/reinterpreting the brief or a separately labeled manual revision with exact requirement exclusions and actual field resolutions. Browser messages alone never settle payments. Live payment evidence is omitted from publication.

Campaign responses expose `latestInterpretationId` scoped to the current accepted base and runtime model, so refresh can recover an interpretation even when the creation response was lost before the client saved its ID. Stored conflicts/unsupported requirements cannot be bypassed by nulling the interpretation ID during manual confirmation. Receipt and permission history expose immutable `excludedRequirements` from the accepted revision; they never describe the original unsupported full brief as cleared.
