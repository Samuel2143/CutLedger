# CutLedger

**When the campaign changes, buy only the additional media rights it needs.**

CutLedger helps creative teams buy eligible media-rights upgrades from a participating asset studio. Gemini turns a revised brief into evidence-backed proposed edits. The user reviews those edits; deterministic merchant policies decide coverage and price. PayPal Sandbox handles the purchase, and independently verified payment controls permission issuance.

![Rights comparison](docs/evidence/comparison-1440x900.png)

## Example

Northstar Launch already has permissions for three original visuals. The client changes the campaign to paid Instagram advertising in the US and UK for 90 days.

| Asset | Existing permission | Additional price |
| --- | --- | ---: |
| Harbor Lines | ORGANIC30 | $8 |
| Kinetic Shapes | ORGANIC30 | $12 |
| Paper Orbit | GLOBAL365 | $0 — already covered |
| **Total** | **$50 cumulative tier value − $30 eligible prior credit** | **$20** |

The prices and policies belong to a fictional demonstration catalog. Prior permissions remain in the ledger. After the eligible upgrades are purchased, the same use needs no further purchase. Unsupported television use remains blocked.

## Run locally

The application runs locally; no paid hosting is required. The supported installation path is Linux x64 with Python 3.12+, Docker Engine and Docker Compose. The installer supplies the pinned Java 17.0.21+9 and Node 24.22.2 distributions. Other operating systems require equivalent toolchains and have not been verified.

```bash
git clone https://github.com/Samuel2143/CutLedger.git
cd CutLedger
python3 scripts/setup_toolchain.py
python3 scripts/configure.py --init
python3 scripts/configure.py
```

While the last command runs, open **http://localhost:8091** and save:

- A PayPal Sandbox business-seller REST app client ID and secret.
- The matching seller merchant ID.
- A Gemini API key with access to `gemini-3.5-flash-lite`.

The setup utility stores values in ignored `.env.local` with user-only file permissions. Saved values are never returned to the browser. Its presence check confirms storage, not provider validity. Keep credentials, buyer passwords and private briefs out of Git, screenshots and logs.

Stop the setup utility with Ctrl+C, then start the application:

```bash
bash scripts/start.sh
```

Open **http://localhost:8080**. The first build downloads pinned dependencies and container images. The app serves React and the API from one origin, with PostgreSQL on a persistent volume. Default ports are 8080 for the app, 55432 for the fresh-install database and 8091 for temporary setup.

```bash
docker compose -p cutledger ps
curl --fail http://localhost:8080/actuator/health/readiness
```

Use a separate **Personal Sandbox buyer**, eligible to pay the selected seller, for PayPal approval. Seller and buyer account-country restrictions can affect checkout. No real funds move. Judge testing access must be delivered privately; a missing credential produces a visible unavailable integration, never a simulated success.

## Walk through the demo

1. Open the sample campaign in a fresh browser workspace. Initial permissions are explicitly labeled seeded permissions.
2. Select **Use example brief**, then **Interpret revision**. Review the literal evidence for paid advertising, US/UK and 90 days, along with inherited fields.
3. Confirm the intended usage. Compare held permissions with requested use, filter the AG Grid comparison and inspect policy evidence. Filtering does not change the server total.
4. Review the **$20** quote and eligible prior credit. Accepting the ten-minute quote prepares a real Sandbox order.
5. Approve with the separate Personal Sandbox buyer, return to CutLedger and explicitly confirm capture. Browser navigation alone never captures money or issues permissions.
6. Wait for independent server verification. The completed purchase issues two new permissions while retaining previous grants. The same confirmed use then costs **$0 additional**.
7. Try “Also use this in television commercials.” The unsupported request stays blocked and cannot be cleared by a checkbox.

The first three steps require no payment. Pending or uncertain payments keep checkout locked until reconciliation resolves them. Do not start replacement purchases or erase financial records to reset a demonstration.

The private narrated demo is 2:05; a public video link will be added after publication. Its existing Sandbox footage is labeled as recorded prior UI, and omitted hosted approval/intermediate edits are disclosed.

## Architecture and safeguards

One Spring Boot application serves the React frontend and REST API. PostgreSQL stores immutable revisions, quotes, grants, durable provider operations and leased jobs. There are no message brokers or separate agent services.

| Component | Version / role |
| --- | --- |
| Java / Spring Boot | 17 / 3.5.16 |
| React / TypeScript / Vite | 19.3.0 / 6.0.3 / 8.3.7 |
| PostgreSQL | 17.11 |
| Gemini | 3.5 Flash-Lite GenerateContent structured extraction |
| PayPal | Sandbox Orders v2, capture verification and signed webhooks |
| AG Grid Community | 36.2.0; comparison, filters, sorting and pinned total |

- **AI proposes; the backend decides.** Canonical validation checks schema, enums, exact evidence, dates and unresolved requirements. A pending or failed replacement interpretation cannot silently discard an earlier unresolved warning. Manual entry remains a labeled fallback.
- **Checkout binds accepted state.** Each quote fixes one accepted revision, policy snapshot and entitlement version. Draft changes have no authority over a committed purchase.
- **Verification precedes fulfillment.** Only a server-verified completed capture matching order, amount, currency and merchant can issue permissions. Webhooks trigger verification; browser messages alone cannot settle a purchase.
- **Uncertainty is durable.** Stable request IDs, uniqueness constraints, transactions and reconciliation prevent duplicate purchases or partial permission issuance.
- **History stays intact.** Previous grants are immutable, broader eligible rights remain held, and unsupported use never becomes covered.
- **Sessions are scoped.** Opaque HttpOnly/SameSite cookies, hashed tokens, exact-origin and CSRF checks, workspace isolation, bounded bodies and persistent quotas protect the application.

AG Grid Community provides the actual comparison workspace and policy-evidence interaction; mobile cards use the same backend result. APIMatic's PayPal Context Plugin informed request/header construction and retry boundaries in the Java adapter. It is development tooling, not a runtime payment processor. AG Studio and Enterprise are not used.

## Verify

With Docker available:

```bash
bash scripts/check.sh
```

The backend uses isolated PostgreSQL/Testcontainers and synthetic HTTP fixtures for provider faults. Tests do not place payments or call the live model. The corrected release passed **74 backend tests**, including **32 PostgreSQL integration tests**, and **7 browser checks**.

With the application running:

```bash
source scripts/toolchain.sh
(cd frontend && npx playwright install chromium)
npm --prefix frontend run test:e2e
```

For an existing compatible browser, set `CUTLEDGER_BROWSER_PATH` to its executable path. Private setup and webhook-proxy regressions can also run independently:

```bash
python3 scripts/test_setup.py -v
python3 -W error::ResourceWarning scripts/test_webhook_proxy.py -v
```

The 40-sample real-model evaluation achieved 21/23 exact explicit matches and 17/17 unsafe checkout blocks. Two provider failures and one overcautious clarification remain limitations; these are small synthetic-benchmark results, not general accuracy claims. Runtime integrations use real providers; simulated payment/model adapters are restricted to tests.

## Webhooks and persistence

Local capture independently verifies PayPal even without a public application URL. To test signed delivery, expose only HTTPS `POST /api/v1/webhooks/paypal`, register that endpoint on the selected Sandbox app, save the returned webhook ID privately and restart the app. `scripts/start_webhook_tunnel.py` and `scripts/register_sandbox_webhook.py` support this optional setup. Registration changes the selected Sandbox app; a temporary tunnel may expire.

PostgreSQL volumes retain the ledger across restart. **Do not run `docker compose down -v`, truncate payment tables or restore over an existing ledger to reset the demo.** Use a fresh browser workspace for a new seeded campaign. Failed provider calls remain visible; bounded retries and quotas do not enable paid billing.

## License and scope

Application code is [MIT licensed](LICENSE). The three original SVGs have separate [demo artwork terms](ASSET_TERMS.txt). Merchant-defined policies, prices and seeded permissions are fictional. Sandbox purchases demonstrate software behavior and do not transfer real commercial media rights. CutLedger does not interpret arbitrary third-party licenses or provide general legal clearance.
