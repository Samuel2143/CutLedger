# Run and judge CutLedger locally

The owner selected local operation on 11 November 2026. There is no public application URL. The [official rules](https://paypalaihackathon.devpost.com/rules) allow complete setup/run instructions as the functional-demo access path. Public video and final entry remain outstanding.

## Prerequisites and access

Use Linux x64 with Python3.12+, Docker Engine and Docker Compose. Allow ports8080 (app),55432 (fresh-install PostgreSQL) and8091 (temporary private setup). The optional installer supplies the frozen Java17 and Node24 toolchain. Other platforms need the equivalent pinned toolchain; they have not been verified here.

The real AI and checkout require a Gemini key with `gemini-3.5-flash-lite` access and a PayPal Sandbox business seller REST application's client ID, secret and matching merchant ID. Buyer approval uses a **separate Personal Sandbox account**. Use a Sandbox buyer eligible to pay the selected seller; account-country restrictions can affect approval even when both accounts hold USD balances. All funds are Sandbox funds.

Judges should request the entrant's private testing instructions through Devpost before testing if they do not have their own compatible test accounts. Private access delivery has not yet been verified. Never publish keys or buyer passwords in GitHub, screenshots or video. A missing key produces a visible unavailable integration, not a simulated response. Do not treat missing private access as a successful full-journey test.

## Fresh installation

Clone the public source into a new directory:

```bash
git clone https://github.com/Samuel2143/CutLedger.git
cd CutLedger
python3 scripts/setup_toolchain.py
python3 scripts/configure.py --init
python3 scripts/configure.py
```

While the last command is running, open http://localhost:8091 in a local browser. Enter the real seller credentials and Gemini key and save. “All four required integration fields are present” confirms local storage; provider validity is checked separately. Stop only the setup utility with Ctrl+C, then start the app:

```bash
bash scripts/start.sh
```

Open http://localhost:8080. The first build downloads pinned dependencies/images. `docker compose -p cutledger ps` shows the app and database; readiness is http://localhost:8080/actuator/health/readiness. If ports are occupied, stop the unrelated listener or use an isolated machine; do not overwrite a running financial database. Existing owner checkout uses its preserved55433 database and native JAR; do not start a competing Compose app there.

## Five-minute judge journey

1. Open the sample campaign in a fresh browser workspace. See Harbor Lines and Kinetic Shapes with seeded ORGANIC30 permissions, and Paper Orbit with seeded GLOBAL365. Seeded permissions are labeled; they are not historical purchases.
2. Select **Use example brief**, then **Interpret revision**. This calls the real hosted model. Wait for the status;429/503/timeouts fail closed with a visible retry/manual fallback. Do not clear quotas to force a response.
3. Inspect the literal quotes for paid advertising, US/UK and90days. Instagram and the original start date are inherited. Review the proposal and explicitly confirm usage.
4. Verify the server comparison: Harbor Lines$8, Kinetic Shapes$12, Paper Orbit$0; total$20. Filter/sort the Community grid and open each policy source. The pinned total remains the full campaign total when rows are filtered.
5. Review the immutable ten-minute quote and its prior credit. Accepting it prepares a genuine Sandbox order. The user signs in to the separate personal Sandbox buyer, approves, returns, and explicitly confirms capture. Browser navigation alone never captures or issues rights.
6. Wait for server verification. A completed receipt should link exactly two new grants to the revision and preserve the three seeded grants. Repeating the same confirmed usage should cost$0 and offer no purchase. If payment is pending/review-required, keep the campaign locked and let reconciliation run; never create a replacement charge to test recovery.
7. Interpret “Also use this in television commercials.” Confirm the unsupported message and disabled checkout. Unsupported use cannot be cleared by a checkbox; revise the original brief.

The first four steps can be reviewed without making any payment. A quote expires after ten minutes. Prepare a new checkout only after the buyer is ready; do not abandon uncertain payment state or delete ledger rows.

## Webhooks and persistence

A fully verified webhook test additionally needs a publicly reachable HTTPS endpoint forwarding only POST `/api/v1/webhooks/paypal` to the local app. The supplied `scripts/start_webhook_tunnel.py` and `scripts/register_sandbox_webhook.py` are development utilities; registration changes the selected Sandbox app and requires account authorization. Store the returned webhook ID privately and restart the app. The temporary endpoint can disappear after restart; check delivery before claiming success. No full-app tunnel is necessary for normal localhost approval/return. Server capture verification still reads PayPal independently.

Named PostgreSQL volumes retain financial/permission records across restart. Use a fresh browser workspace to obtain a fresh seed campaign. Never run `docker compose down -v`, truncate payment tables or restore over an existing ledger to reset a demo.

## Reproduce checks

```bash
source scripts/toolchain.sh
bash scripts/check.sh
# With the local app running and browser installed:
CUTLEDGER_BROWSER_PATH=/usr/bin/brave-browser npm --prefix frontend run test:e2e
```

The backend suite uses isolated PostgreSQL/Testcontainers and test-only provider fixtures for rare failures. It does not place a payment. Live model probes/evaluation are opt-in and consume the real project's free quota. See VERIFICATION for actual executed results and limitations.
