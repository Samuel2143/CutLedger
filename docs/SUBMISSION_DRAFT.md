# CutLedger — prepared submission copy (not submitted)

**Name:** CutLedger

**Tagline:** When the campaign changes, find and buy only the additional media rights it needs.

**Repository:** https://github.com/Samuel2143/CutLedger — public, MIT licensed.

**Try it:** Local build; [complete judge setup](LOCAL_DEMO.md). No hosted application URL. Private test-access delivery is pending.

**Video:** NOT_PUBLISHED. A private64-second product draft exists, without payment footage. It is not the final end-to-end demo.

**Draft:** Saved in Devpost; project overview, story, source and technical answers persist. Status2/5stepsdone. Video and personal/feedback fields are incomplete. **Final submission:** NOT_OBSERVED. Final terms remain unchecked.

## Inspiration

An editor has already licensed the visuals. Then the client changes the campaign from organic posts to paid advertising, adds countries or extends its duration. Buying the assets again wastes money; assuming the old permissions still apply leaves a gap. CutLedger makes that difference visible and purchasable.

## What it does

An editor pastes a client revision. Gemini proposes structured usage changes and shows literal source evidence. The editor confirms or corrects the proposal before it affects a campaign revision. A deterministic backend compares that immutable revision against permissions already held and the merchant's supported policies.

The three-asset Northstar example needs two upgrades and already covers one asset: $8+$12+$0=$20. Prior eligible rights receive credit. Each quote binds the revision, policy and entitlement version for ten minutes. PayPal Sandbox hosts buyer approval; explicit capture confirmation lets the server capture and independently verify the order, merchant, currency and amount before it atomically issues revision-linked permissions. Historical grants remain immutable. Covered usage needs no new purchase, and unsupported television use blocks checkout rather than receiving an invented price.

## How we built it

One Java17/SpringBoot3.5.16 modular application serves React19.3.0/TypeScript6.0.3/Vite8.3.7 from the same origin. PostgreSQL17.11 stores revisions, quotes, grants, durable provider operations and leased jobs. There are no brokers or separate agent services.

Gemini3.5Flash-Lite GenerateContent provides real structured extraction. The server validates the full canonical schema, exact evidence substrings, dates, enums, inherited fields and unresolved issues. Durable quotas, bounded retries, dispatch pacing and refresh recovery constrain cost and failure. The model has no pricing authority or payment tools.

PayPal Sandbox Ordersv2 is central to acquiring the additional rights. Durable operation identities are stored before sending. An uncertain result keeps the campaign locked; recovery reads provider resources instead of blindly sending another monetary operation. Signature-verified webhooks trigger independent provider verification. Database transactions and uniqueness constraints prevent partial or duplicate grant issuance.

AGGridCommunity36.2.0 presents held/requested scope, changed dimensions, sorting, filtering, a pinned server total and merchant-policy evidence. Mobile cards use the same backend result. APIMatic's PayPal Context Plugin informed operation lookup, request/header construction and retry boundaries in the Java adapter and its tests. AGStudio and Enterprise are not used.

## Challenges

Provider availability and quota were real engineering constraints. The original model path repeatedly failed; documented alternatives were evaluated before explicitly adopting the accessible free-tier Flash-Lite model. Failed requests remain in the benchmark report. The application fails closed and offers a visibly labeled manual fallback; it never invents an AI success.

Payment uncertainty was another design constraint. A provider may capture successfully while the application loses the response or crashes before committing grants. The ledger separates durable monetary intent, external verification and atomic fulfillment so recovery can complete the same purchase safely.

## What we accomplished

The local release passed73backend tests, including31realPostgreSQL integration tests, plus provider-contract, canonical-validation, domain and configuration tests. Four browser regressions and a separate real-model browser journey passed. Desktop, tablet and mobile states were inspected. Payment faults in automated tests use explicitly synthetic provider fixtures; there is no runtime mock-paid endpoint. Live payment evidence is omitted from public materials at the owner's request.

The40sample real-model benchmark met the agreed narrow thresholds:21/23explicit exact matches (91.3%) and17/17unsafe checkout blocks. Two provider503failedsamples and one overcautious duration clarification remain disclosed. All38returned responses passed canonical schema/evidence validation. These are small synthetic benchmark results, not general accuracy or legal-understanding claims.

## What we learned

A useful AI feature can have a deliberately narrow job. Evidence-backed proposals make a brief editable, while deterministic policies, immutable snapshots and explicit confirmation keep money and coverage auditable. Clear uncertainty is more valuable than an optimistic success state.

## What's next

Complete the public end-to-end video and verified private judge access, then submit after the owner's required platform consent. Hosting is deferred by the owner; local setup is the delivery path. Broader catalogs, arbitrary legal-text interpretation and production-money use are outside this entry.

## Built with

Java17, SpringBoot3.5.16, PostgreSQL17.11, React19.3.0, TypeScript6.0.3, Vite8.3.7, Gemini3.5Flash-Lite, PayPalSandbox REST, AGGridCommunity36.2.0, APIMaticPayPalContextPlugin0.4.1, Playwright1.65.0, Docker.

## Disclosures and final fields

Single merchant, original demo artwork, fictional fixed tiers and Sandbox funds only. Merchant-policy comparison is not general legal clearance. Code is MIT; original graphics have separate demo asset terms. Codex assisted implementation, testing and documentation; Gemini performs runtime extraction. No public live-payment evidence, credentials or private briefs are included.

Before submission: insert the verified public YouTube URL, deliver private testing access using an approved private channel, review the actual entry fields/eligibility and obtain owner consent for platform terms. A saved draft must never be described as submitted.
