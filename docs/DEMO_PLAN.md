# B/G — Demonstration and submission

## 2:50 demonstration script

Record the actual deployed application using a fresh isolated demo session and a genuine sandbox buyer. Use the original three assets and exact dataset in PRODUCT_SPEC. Prefer one continuous application run. A clearly disclosed edit may omit credential entry or waiting time; do not splice an unrelated successful payment into a failed run. Never expose buyer password, API secret, access code, or full private account details.

| Time | Screen/action | Narration |
|---|---|---|
| 0:00–0:15 | Already-licensed Northstar campaign; three assets and current rights visible | “The edit is approved. Then the client changes where the campaign will run. Buying every asset again wastes money; assuming the old license still covers it is risky.” |
| 0:15–0:28 | Paste main client revision and press Interpret | “CutLedger asks a narrower question: what additional rights does this revision need? Paid ads, US and UK, ninety days.” |
| 0:28–0:48 | Real AI result; evidence chips, inherited Instagram/start, confirm usage | “AI translates the request into proposed usage changes and points to the words it used. I confirm them before anything can be purchased.” |
| 0:48–1:13 | Comparison: two upgrade rows, one green covered row; filter and clause drawer | “This asset already has worldwide annual permission. These two need a wider tier. The merchant's structured policies explain the gap; the model does not decide coverage or price.” |
| 1:13–1:28 | Quote rail: $8 + $12 = $20; prior credit and exact revision | “We credit the rights already held. Only twenty additional dollars are due, bound to this exact revision and quote.” |
| 1:28–1:58 | Actual Sandbox approval, return, confirm capture, verification | “PayPal Sandbox handles buyer approval. Our server captures and independently verifies the amount, seller, and order before issuing any permission.” |
| 1:58–2:18 | Real settled receipt; two new grants, preserved originals; repeat assessment | “The new permission record is linked to the campaign revision. Previous grants remain. Ask for the same usage again: zero additional charge.” |
| 2:18–2:38 | New brief: “Also include television broadcast.” → unsupported state | “If the merchant doesn't offer a right, CutLedger doesn't invent a price or call it cleared. This request is blocked.” |
| 2:38–2:50 | Return to clear summary / product mark | “CutLedger: when the campaign changes, find and buy only the additional media rights it needs. Built with real PayPal Sandbox payments, AI interpretation, and an auditable rights ledger.” |

Target 2:50, leaving ten seconds below the limit. If provider sign-in is slow, record a truthful edited version with “Sign-in time omitted”; retain the uncut private evidence. Never speed through unreadable prices or source clauses. Sponsor names can appear in a small ending credit if actually used; the product story comes first.

## Sample content and test access

- Merchant/visuals/prices: PRODUCT_SPEC, fixed policy v1 and original project artwork only.
- Default brief: “Keep the visuals, but we're now running this as paid advertising in the US and UK for 90 days.”
- Unsupported brief: “Keep the paid Instagram campaign, and also include television broadcast.”
- Covered brief after purchase: repeat the default brief verbatim.
- Before recording, create a fresh session so the first quote is $20, not $0. Do not erase real previous payment records to reset the demo.
- Use an access-code gate and independent browser workspace for each judge. Provide the exact hosted URL, private access code, separate sandbox buyer login instructions, expected $20 result, and “Sandbox only; no real funds.” Never provide seller secret/API keys to judges.
- Test the instructions in a private browser. A judge should need no local installation for the hosted path. README still provides complete local setup for reproducibility.
- Keep all credential entry out of the recording and public documentation. If Devpost lacks a private credential field, prepare a safe account-specific testing alternative before submission; do not make the public GitHub README a password store.

## Recording checklist

- [ ] Deployed commit and database state correspond to the submitted source.
- [ ] Actual Gemini response, actual Sandbox order/capture, actual persisted grants; no fixture endpoint in production.
- [ ] 1440×900 or similar readable viewport, 100–125% zoom, dark private account details hidden, no noisy desktop notifications.
- [ ] Microphone clear, no copyrighted music/stock media; original graphics provenance documented.
- [ ] Price, source clause, Sandbox badge, receipt status, and unsupported message are legible.
- [ ] Payment succeeds before recording takes; keep an authentic fallback recording from the same verified build if the provider has a later outage.
- [ ] Final duration <3:00, public video link opens signed out, captions/narration in English.
- [ ] Video description accurately labels Sandbox and any timing edit; no secret in frames, captions, or description.

## Devpost description structure

1. **One sentence:** “CutLedger finds and sells only the additional media rights a campaign revision needs.”
2. **Problem:** editors have existing rights when client distribution/territory/duration changes; avoid repurchasing everything or guessing.
3. **What it does:** natural-language revision → evidence-backed confirmation → deterministic policy comparison → incremental PayPal purchase → revision-linked permission record.
4. **Demonstrated result:** the sample merchant's fictional catalog produces $8 + $12, one already covered asset, and $0 for repeating the same use. Unsupported television is blocked. Do not imply these are market license prices.
5. **Technical implementation:** monolith, actual model and version, deterministic engine, immutable quote, server-verified Sandbox capture, durable recovery, verified webhooks, preserved grants.
6. **Sponsor use:** specific APIMatic work and AG Grid interactions, with actual evidence; Studio only if implemented.
7. **Challenges and lessons:** only challenges actually encountered and resolved; include a concrete recovery test if observed.
8. **Limitations:** demo merchant's own original assets, fixed supported tiers, Sandbox only, no third-party or general legal clearance claims.
9. **Links/instructions:** deployed app, public repository/commit, public video, private judge access and setup as required by the platform. Disclose AI development assistance and actual technologies according to current entry fields.

## README checklist

- Product promise, screenshot, live demo link, honest status and supported scope.
- Architecture and data flow; exact runtime versions; prerequisites and reproducible clean start/test commands.
- Secret template variable descriptions; PayPal seller/buyer/webhook setup; model access gate; no real secrets.
- Seed provenance and fictional cumulative prices; policy terms; old-grant preservation; date/quote semantics.
- Actual AI role/evaluation summary and payment verification/recovery explanation.
- Test commands and genuine results linked to VERIFICATION; clearly label mocked tests versus live evidence.
- Public code license (MIT), separate original asset terms/provenance, third-party dependency notices where necessary.
- Actual sponsor-tool usage; clear limitations; safe judge instructions; production-money explicitly out of scope.
- Deployment/migrations/retention guidance and no public reset/mark-paid endpoints.

## Final submission verification

- [ ] Recheck [official rules](https://paypalaihackathon.devpost.com/rules), current entry fields, personal eligibility, and deadline conversion.
- [ ] Public repo opens signed out; license file present; committed source matches deployed build and video.
- [ ] Hosted app opens in a fresh browser and completes the judge journey with supplied private instructions.
- [ ] Paid resources and credentials remain usable through announced judging/results period; no free database expiry mid-judging.
- [ ] Video public and under three minutes; correct URL in Devpost; all other links open.
- [ ] Sponsor tracks selected only for actual use and required evidence included; no invented claim of award eligibility confirmation.
- [ ] All required Devpost fields completed, not just project description. Review terms and final submission action as owner where required.
- [ ] Submit by Nov 12, 18:00 IST. Observe and record confirmation/status and entry URL. A saved draft is not a submitted entry.
- [ ] Preserve submitted commit and evidence; later material changes require organizer permission under the rules.

Create the Devpost draft and upload a video draft early when access allows. Do not spend the final buffer adding features or rewriting architecture.
