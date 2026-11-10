# B — Product and UI specification

## Product promise and scope

CutLedger helps a freelance editor respond when a client expands a campaign's usage after assets have already been licensed. It shows the difference between the rights held and the newly requested use, explains each gap using the demo merchant's policy, and sells only eligible incremental upgrades.

The merchant is **CutLedger Studio**, a fictional demo merchant controlling three original graphics created for this project. This is a Sandbox licensing demonstration, not a marketplace for third-party footage or legal advice. No arbitrary contract uploads, video editing, asset search, team accounts, subscriptions, real card processing, or production payment launch.

The memorable moment: **three assets, two upgrades, one already covered, $20.00 total**. After payment the identical revision costs **$0.00**, and the old permission records remain visible. A subsequent request for television use is blocked with an explanation.

## Demo catalog and campaign

Campaign: `Northstar Launch`. Merchant: `CutLedger Studio`. Original campaign anchor: `2026-11-15`. Initial revision: ORGANIC, US, INSTAGRAM, 30 days (`2026-11-15` through exclusive `2026-12-15`). Display the last included day in human copy and clearly label end-exclusive values in technical views.

| Asset | Original visual to create | ORGANIC30 | PAID90 | GLOBAL365 | Initial entitlement |
|---|---|---:|---:|---:|---|
| Harbor Lines | Original indigo/teal geometric shoreline illustration | 1000 | 1800 | 4000 | ORGANIC30, credited 1000 |
| Kinetic Shapes | Original coral/amber kinetic geometric composition | 2000 | 3200 | 6000 | ORGANIC30, credited 2000 |
| Paper Orbit | Original violet paper-like orbital illustration | 1500 | 3000 | 6000 | GLOBAL365, credited 6000 |

All prices are **fictional USD minor units** defined by this merchant. Initial rights are labeled **seeded prior permissions**, never represented as verified historical PayPal purchases. Original asset files have creator/provenance records and a separate demo license; code is MIT. No stock photos, logos, or unverified third-party asset rights. SVGs must be original static paths without scripts or external references; they are a visual asset task during implementation, not this handoff.

Default client revision: “Keep the visuals, but we're now running this as paid advertising in the US and UK for 90 days.” After confirmation: distribution replaced by PAID_ADS, territory replaced by US+GB, INSTAGRAM inherited, start inherited, end exclusive `2027-02-13`. Paid tiers also permit organic, but the requested use remains exactly what the user confirmed.

Baseline upgrade: Harbor Lines 1800−1000=800; Kinetic Shapes 3200−2000=1200; Paper Orbit 0. Total **2000 cents = $20.00**. Repeating the same request after purchase produces no payable quote. Worldwide/365-day paid usage costs **7000 cents from the initial seed**, or **5000 cents after the $20 purchase**.

## Design system

Use a restrained, modern editorial SaaS style. Warm-white canvas `#F6F7FB`, white cards, ink `#172033`, muted text `#596579`, border `#DDE2EB`, indigo primary `#5152D6`, green `#176B4C`, amber `#915C0B`, red `#B42332`. Verify contrast on actual combinations; never encode coverage only by color. System sans (`Inter` only if properly self-hosted with license), tabular numerals for money, monospace only for receipt references. Base 16px, labels 13–14px, section 20–24px, page title 32px. Line height 1.5.

Spacing on an 8px scale with 4px for compact metadata. Cards radius 12px, inputs 8px, 1px borders, very subtle shadows. Primary actions 44px minimum. Maximum workspace width 1440px. At desktop, main column roughly 2/3 and a sticky 320px summary rail; below 1024px stack summary; below 768px use asset/rights cards and a bottom action bar with no obscured content. Keyboard-visible focus, reduced-motion support, 150–200ms transitions, dialogs focus-trapped with focus restoration. Status language is specific: “Covered by GLOBAL365,” “Upgrade needed,” “Needs clarification,” “Unsupported by this merchant,” “Payment being verified.” Never label pending payment as cleared.

## Routes, content, and actions

| Route | Main content and actions | Recovery / errors |
|---|---|---|
| `/` | CutLedger mark, one-sentence promise, Sandbox badge, access-code field, “Open sample campaign.” Existing session offers “Resume campaign.” | Incorrect code is a generic error. Existing session is reused; do not reseed on refresh. Missing server config shows a useful unavailable message. |
| `/campaigns/:id?tab=workspace` | Campaign title, revision number, three visual asset cards, existing-rights summary, client brief input, example brief chip, “Interpret revision,” structured review panel, rights grid, quote rail. | Persist submitted brief/interpretation IDs server-side. Unsaved text warns before navigation. Load stored revision and active payment after refresh; do not re-call AI automatically. |
| `/campaigns/:id?tab=permissions` | Per-asset original and upgraded grant timeline, scope, source policy, revision link, seeded/paid provenance. Open receipt and policy clause. | Empty purchase state still shows initial seeded rights. Suspended grants visibly require review. |
| `/campaigns/:id?tab=activity` | Brief submitted, intent confirmed, quote accepted, payment verified, grant issued; timestamps and revision links. | Paginated bounded list; no raw provider payloads. |
| `/checkout/:paymentId` | Exact accepted revision, quote lines, total, Sandbox label, state, hosted PayPal approval button when safe. “Check payment status.” | Refresh resumes existing payment. Uncertain capture removes new-pay action. Cancel is allowed only before capture commitment. |
| `/checkout/return?paymentId=...` | “Approve capture” confirmation of accepted total, or immediate status if already committed. Verify session and returned token matches stored order, then POST capture on explicit button. | GET never moves money. Missing session asks to reopen original browser; never uses query ID as authentication. Forged token rejected. |
| `/checkout/cancel?paymentId=...` | Cancellation explanation and server state; POST “Cancel this checkout” if pre-capture. | A provider return alone is not definitive payment status. If already capture-committed, show verification in progress. |
| `/receipts/:paymentId` | Verified Sandbox capture reference, paid delta, new immutable grants, revision snapshot, policy versions, “Return to campaign,” print-friendly view. | A pending payment shows status rather than a receipt. Receipt is workspace-protected. |

No full admin portal. Merchant policies are seeded and versioned by migrations/config fixtures. “Start fresh sample” creates a separate workspace/campaign only after warning that it does not erase old records; prevent it while a payment is unresolved. Prefer returning to the original workspace rather than distributing one shared mutable judge account.

## Exact interaction sequence

1. Open the isolated seeded campaign; initial permissions are visible before AI runs.
2. Paste the client revision and select Interpret. Show progressive loading text; keep original rights visible. AI failure permits retry or explicit manual usage entry, clearly labeled, never simulated extraction.
3. Show proposed changes with evidence chips. Distinguish inherited fields. Clicking evidence highlights the literal source text; ambiguous/conflicting fields require an explicit choice. Unsupported requests remain visible and block checkout even if other dimensions are valid.
4. The user reviews distribution, territories, platforms, start date, and end date/duration. They may correct fields. Show “Your edit” provenance. Confirmation saves a new immutable revision only when normalized fields are complete and contradiction-free. If unsupported use is removed, require editing the brief and reinterpreting, or explicitly acknowledge a supported subset as a different revision; never silently drop a clause.
5. Rights comparison returns one row per asset. Columns: Asset; Rights held; Requested change; Result; Additional cost; Policy evidence. A “Needs upgrade” filter, sort by cost/status, pinned totals, and clause drawer help make the decision. All data is from the backend.
6. Quote rail shows 2 upgrades, 1 covered, $20.00, expiry countdown based on server time, revision number, and “Accept quote & continue to PayPal.” Acceptance locks the campaign and initializes one durable payment. New revisions are disabled while this payment is active; users can still inspect history.
7. Redirect to genuine PayPal Sandbox approval. Return to a server-bound confirmation, capture, and verification state. Show a non-optimistic progress message; poll status at 2 seconds initially, back off to 10 seconds, stop when tab hidden or terminal, resume on focus.
8. Once the backend has verified and issued grants, show “Permissions updated,” $20.00 incremental total, two new grants, and three covered assets for that revision. Receipt must say Sandbox and reference merchant-defined demo policies.
9. Run the same revision: all covered, total $0.00, primary action “View permission record”; no PayPal order is created. Run “Also include television broadcast”: blocked, no invented quote.

## Required state treatments

| Condition | UI behavior |
|---|---|
| Loading campaign | Layout-matched skeleton, no fake values or success badges |
| AI busy | Disable duplicate Interpret for this brief; cancel navigation safely, server result remains retrievable |
| AI schema/evidence failure | “Could not reliably interpret this revision”; retry or manual confirmation, never empty success |
| Ambiguous/conflicting intent | Highlight unresolved fields; focused clarification controls; disable confirmation |
| Unsupported requirement | Explain unsupported dimension and merchant scope; disable quote/payment for entire request |
| Covered | Zero additional charge; show grant and clause proving coverage |
| Expired quote | “Price confirmation expired”; request a new server quote; no reuse of old checkout commitment |
| Concurrent tab / stale version | 409 banner with Reload current revision; preserve unsaved local draft separately |
| Capture pending/unknown | “Payment is being checked. Do not start another checkout.” Resume status only |
| Definitive decline | No grant; show sanitized reason and safe next action, new quote only after payment is conclusively closed |
| Offline / server unavailable | Preserve last-known view with timestamp and stale banner; disable payment actions |
| Empty audit | Helpful “Your campaign activity will appear here” state |
| Reversal/manual review | Remove current coverage claim attributable to affected grant; preserve historical record |

## Visual acceptance

Browser screenshots at 1440×900, 1024×768, 390×844. No clipped money, sideways page scrolling, hidden primary actions, overflowing clause text, or color-only state. Keyboard-only journey reaches all confirmation and policy controls. A screen reader can identify table status and payment progress. Judges should understand the $20 difference within the first 25 seconds without narration explaining the interface.
