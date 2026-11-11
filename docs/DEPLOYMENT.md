# A/G — Deployment and external setup

## Current delivery decision

On 11 November 2026 the owner chose **local operation for now**. No Render resources were provisioned and no paid approval is active. Use [LOCAL_DEMO](LOCAL_DEMO.md) for the judge-run path. The [official rules](https://paypalaihackathon.devpost.com/rules) permit complete setup/run instructions instead of a hosted demo. The following topology is a deferred plan, not a deployed service.

## Deferred hosting topology

One Render Docker web service serving Spring + compiled React, and one managed PostgreSQL database in the same available region. Use an always-on paid service and a database retained through at least December 21, 2026. Render free web services sleep and free PostgreSQL has a limited lifetime; a database created November 10 with a 30-day expiry could disappear during judging. [Render free limits](https://render.com/docs/free). Do not silently fall back to ephemeral disk or an expiring database.

Use a multi-stage Docker build: patched Node 24 builds frontend; Maven/Java 17 builds backend including frontend static files; a small Java 17 runtime executes the jar as nonroot. Pin verified base image digests after the first build. Bind `0.0.0.0:${PORT}`; [Render web service requirements](https://render.com/docs/web-services). Serve SPA routes through a fallback only for browser routes, never `/api` or missing static assets. Build and run locally from the same container before deployment.

The account-displayed prepared proposal uses a 2 GB web service plus 256 MB PostgreSQL and 5 GB storage at $32.50/month base; see HOSTING_PROPOSAL. It was not approved. Measure memory/CPU and cap JVM memory sensibly if hosting is reopened. Before creating a paid resource, Codex must prepare the resource names, region, plan sizes, exact displayed costs, retention through judging, and budget estimate, then request one approval unless already authorized. A provisional planning allowance of $50 for hosting/model usage is a suggestion, not spending permission or a guaranteed sufficient budget. Billing alerts are not assumed to be hard caps; enforce the app's AI quota too.

## Human setup sequence — no coding

1. **PayPal first:** sign into developer dashboard, create/select sandbox business seller app, obtain client ID/secret and merchant ID, and prepare a separate funded personal sandbox buyer. Use actual country/account information for real registration; sandbox test accounts are test fixtures. Enter secrets only into Codex-prepared ignored fields or hosting secret UI. Never pay the seller with its own account.
2. **AI:** create/select a Gemini API project/key with appropriate access and billing/quota. Codex runs one structured-output probe and records actual response shape, available model, and usage. If permission fails, the owner enables access; no fake adapter may pass this gate.
3. **Repository/hosting:** sign into GitHub and Render or authorize the existing connections. Codex prepares public repository contents, license, secret scan, Dockerfile and Render blueprint. Authorize the concrete paid hosting configuration if needed. Do not ask the owner to write YAML or edit Java.
4. **Webhook:** after the public HTTPS origin exists, Codex supplies the exact endpoint URL and subscribable event list. Configure on the same sandbox app and securely store the webhook ID. Use a trusted HTTPS development tunnel for Phase 1 webhook testing if available; final webhook verification must use the hosted origin.
5. **Judge access:** Codex generates a private demo access code and separate sandbox buyer instructions. Put them in Devpost's private testing instructions if available. If no private field exists, confirm a safe distribution mechanism; do not place seller credentials, API secrets, or buyer passwords in the public README. Public video may show the flow with sign-in cut or credentials obscured, with disclosure of the edit.

## Deployment acceptance

- Public HTTPS origin serves SPA deep links and same-origin APIs, static assets cached with hashed filenames; API/payment responses `Cache-Control: no-store`.
- Database survives service restart/redeploy. Flyway succeeds before readiness. Back up/restore guidance is documented; do not destroy the database to fix migration drift.
- `PUBLIC_BASE_URL` matches checkout return/cancel URLs and allowed Origin. Secure cookies work across top-level PayPal return. No permissive wildcard CORS.
- Readiness passes and all private endpoints require ownership. Access gate works in a fresh private browser.
- Actual hosted $20 Sandbox checkout settles, issues exactly two grants, survives refresh, and receives a verified real webhook. Record observed result, not just a health check.
- Hosted AI probe and one ambiguous/unsupported test run through the real provider. No local-only fixture hiding a missing credential.
- Record deploy URL, repository URL, commit SHA, actual versions, timestamp, resource retention date, health result, and redacted payment evidence in VERIFICATION.
- Keep service and API quota available through judging. Freeze the submitted commit; do not materially alter the entry after deadline without organizer permission. Fixing operational outages should preserve the submitted functionality and be documented.

## Safe release procedure

Build → unit/domain tests → PostgreSQL integration/payment fault tests → frontend build/tests → local container smoke → deploy candidate → hosted judge journey → inspect logs/DB integrity → tag submission commit → capture video and submit. If a migration or release breaks payment recovery, roll application code back only if compatible with the forward database schema; never roll back by deleting financial rows. No public `/reset`, `/mark-paid`, debug fault switch, actuator environment endpoint, or development credentials.
