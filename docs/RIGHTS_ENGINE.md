# A — Deterministic rights and pricing contract

## Scope and domain enums

One merchant, one campaign with three immutable asset memberships, USD only, one policy family `CUTLEDGER_CAMPAIGN_V1`. Distribution: `ORGANIC`, `PAID_ADS`. Platforms: `INSTAGRAM`, `YOUTUBE`, `WEBSITE`. Territories: validated ISO 3166-1 alpha-2 sets or the sole sentinel `WORLDWIDE`; normalize UK→GB. TV, cinema, resale, exclusivity, sublicensing, AI training, and any other unmodeled use are unsupported. Unknown or ambiguous requirements never silently disappear.

`CoverageStatus`: `COVERED`, `UPGRADE_REQUIRED`, `UNSUPPORTED`, `NEEDS_CLARIFICATION`, `REVIEW_REQUIRED`.
`RevisionStatus`: `CONFIRMED` (immutable; drafts live in interpretations).
`QuoteStatus`: `OPEN`, `ACCEPTED`, `EXPIRED`, `INVALIDATED`, `FULFILLED`, `CLOSED`.
`EntitlementSource`: `SEEDED`, `PAYPAL_CAPTURE`.
`EntitlementStatus`: `ACTIVE`, `SUSPENDED_REVIEW` (status is separate from immutable grant terms).
`ReasonCode`: `DISTRIBUTION_GAP`, `TERRITORY_GAP`, `PLATFORM_GAP`, `DATE_GAP`, `UNSUPPORTED_USE`, `AMBIGUOUS_INTENT`, `POLICY_MIGRATION_REQUIRED`, `PAYMENT_REVIEW`.

## Merchant policy v1

| Tier / rank | Distribution | Territories | Platforms | Coverage interval from campaign anchor |
|---|---|---|---|---|
| `ORGANIC30` / 0 | ORGANIC | US | all three supported platforms | [anchor, anchor + 30 days) |
| `PAID90` / 1 | ORGANIC, PAID_ADS | US, GB | all three | [anchor, anchor + 90 days) |
| `GLOBAL365` / 2 | ORGANIC, PAID_ADS | WORLDWIDE | all three | [anchor, anchor + 365 days) |

The campaign's original anchor never changes. A later requested start may move inside a licensed interval; upgrading does not restart the clock. Dates are calendar dates in the campaign's fixed `UTC` usage calendar; store monetary/audit timestamps separately as UTC instants. `startDate` inclusive, `endDateExclusive` exclusive. Validate start < end, length 1–365 days, requested start >= anchor, end <= anchor+365. Earlier or longer usage is unsupported. For display: “Nov 15–Feb 12 (90 days)” is equivalent to exclusive Feb 13. A calendar month is not assumed to be 30 days.

Each asset has its own cumulative tier price from PRODUCT_SPEC. There are no per-country multipliers or AI-generated amounts. An upgrade buys the complete selected tier, sometimes broader than requested; show both requested use and purchased scope clearly. Buying GLOBAL365 for a newly requested supported country is permitted; claiming a custom country-specific price is not.

Policies store structured dimensions AND human clause IDs/text generated from the same merchant definition. Suggested immutable clauses: `V1-DIST-ORG`, `V1-DIST-PAID`, `V1-TERR-US`, `V1-TERR-USGB`, `V1-TERR-WORLD`, `V1-PLATFORMS`, `V1-WINDOW-30`, `V1-WINDOW-90`, `V1-WINDOW-365`, `V1-EXCLUSIONS`, `V1-UPGRADE-CREDIT`. Seed validation proves each displayed clause agrees with its structured rule. Policy prose is never parsed by the LLM to infer legal coverage.

## Coverage function

Input: immutable confirmed usage, asset membership, actual ACTIVE grants, relevant policy snapshots. Output: per-asset decision, dimension differences, evidence clause IDs, selected tier if eligible, prior credit, delta, and an overall checkout eligibility flag.

A grant covers usage only if **all** requested distributions, territories, platforms, and the complete date interval fit that single grant. Never combine an organic worldwide grant with a US paid grant to assert worldwide paid rights. In this MVP, tiers are nested, which makes the highest eligible tier sufficient. Still test non-nested historical grants to ensure the implementation does not accidentally union dimensions.

Order of evaluation:

1. Validate intent completeness and explicit unsupported requirements. Any unsupported/uncertain asset blocks purchasing the entire campaign request. Return available explanatory comparisons, but no payable quote.
2. For each asset, check any ACTIVE grant for full requested coverage, including old policy snapshots. If covered, delta is zero and evidence points to that grant.
3. Otherwise find the smallest current eligible tier fully covering requested use. If none, return UNSUPPORTED.
4. Credit only the highest eligible cumulative amount from an ACTIVE grant for the **same merchant, asset, campaign, policy family, and compatible version**. Credit is a merchant-validated value on the grant, not a sum of all past payments and not client input.
5. `deltaMinor = targetCumulativeMinor − priorCreditedMinor`; require positive delta for an upgrade. Negative or inconsistent values are REVIEW_REQUIRED, never a negative charge. Different policy versions may still provide coverage but do not receive automatic upgrade credit in MVP; require an explicit merchant migration rule, which is out of scope.
6. Total is checked integer addition of positive deltas. Zero total returns a coverage assessment without creating a payable quote or PayPal order.

On fulfillment issue a new immutable grant with full target scope, `creditedMinor=targetCumulativeMinor`, `paidDeltaMinor=delta`, source capture, accepted revision, policy version, and predecessor grant. Earlier grants remain intact and applicable to their original scope. Do not replace or mutate their terms.

## Quotes and concurrency

Quotes last ten minutes from server creation. Snapshot fields include campaign/revision ID and usage hash, campaign version, entitlement version, policy family/version/hash, per-asset target and prior grant IDs, full purchased scope, credit and delta, currency, total, created/expiry timestamps. Compute a stable hash over canonical JSON with sorted set values and fixed key order; include schema version. The browser sends IDs/hashes, never trusted prices.

Before order creation and again at capture commitment, lock the campaign row and check quote OPEN/ACCEPTED as appropriate, expiry, all versions/hashes, ownership, positive total, and no other unresolved payment. A confirmed new revision invalidates all unaccepted open quotes. New grants invalidate competing quotes through the entitlement version. Policy changes invalidate uncommitted quotes; they never rewrite previously purchased grants.

Acceptance sets one `active_payment_id` and freezes the revision. While payment is active, do not confirm another revision, change assets, or start another checkout. This simple serialized campaign rule prevents double credit races. A client can draft text but cannot save a new confirmed revision until the payment resolves.

Expiry means **no new capture commitment after expiry**. A durable capture commitment made while valid remains binding even if a network delay or reconciliation finishes after expiry. It is impossible to retract an already-sent provider operation reliably; the implementation must distinguish these cases. Never let a pending capture expire into a new payable order. See PAYMENT_FLOW for release conditions.

## Acceptance examples

| Test | Expected result |
|---|---|
| Seed + main paid US/GB 90-day revision | Harbor 800, Kinetic 1200, Paper 0; total 2000 |
| Same confirmed usage after verified fulfillment | All COVERED, no order, total 0 |
| Global/365 from seed | Harbor 3000, Kinetic 4000, Paper 0; total 7000 |
| Global/365 after PAID90 upgrade | Harbor 2200, Kinetic 2800, Paper 0; total 5000 |
| Revert to original organic revision | Original grants still cover; no refund or deletion implied |
| TV requested alongside paid Instagram | Entire request unsupported; no partial paid quote |
| Requested interval ends exactly tier boundary | Covered; one day beyond needs next tier or unsupported |
| Different policy version introduced | Old granted scope remains valid; cross-version upgrade requires review |
| Two simultaneous payment initializations | Exactly one active payment; second returns same payment for same quote or 409 |
| Two grants with complementary but non-nested dimensions | No synthetic coverage by combining dimensions |
| Price/client tier tampering | Ignored/rejected; server computes from immutable policy and grant snapshots |
| Expired quote or changed entitlement version | 410 expired or 409 invalidated before capture commitment |

This engine evaluates only this merchant's explicit demo policies. The UI must say “Covered under CutLedger Studio policy v1,” not “legally cleared everywhere.”
