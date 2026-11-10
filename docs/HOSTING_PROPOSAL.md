# Prepared Render configuration — no resources purchased

Observed 10 November 2026. Account-specific availability, final invoice and permission remain unverified. This proposal does not authorize provisioning.

| Resource | Configuration | Official displayed monthly cost |
|---|---|---:|
| `cutledger-demo` | One Docker web service, Singapore, 1 CPU / 2 GB (`1c-2g`, legacy `standard`) | USD 25.00 |
| `cutledger-db` | Managed PostgreSQL 17, Singapore, 0.1 CPU / 256 MB (`0.1c-256mb`, legacy `basic-256mb`), 100 connections | USD 6.00 |
| Database storage | 5 GB, no automatic storage growth proposed | USD 1.50 (USD 0.30/GB) |
| Workspace | Hobby, one owner | USD 0.00 |
| **Base total** | One app and one persistent database | **USD 32.50/month** |

The public [Render pricing page](https://render.com/pricing) was fetched and its rendered HTML tables inspected: web `1c-2g` = $25/month; Postgres `0.1c-256mb` = $6/month; expandable storage = $0.30/GB. No 1 GB web option was present in the inspected web compute table, so 2 GB is the smallest listed web size above 512 MB. The blueprint uses the supported legacy plan identifiers pending validation in the owner's account. Region/plan selection is not yet observed in an authenticated dashboard.

Retain resources from approximately November 10 through December 21, 2026, inclusive (42 days). Using a 30-day month only as an estimate, base retention costs about $45.50. Actual prorating, taxes, outbound bandwidth, pipeline overages, foreign exchange and account billing can differ. No disks, Redis, extra services, autoscaling, paid workspace upgrade, replicas or high availability are proposed. Disable automatic spend expansion where the account permits it. Database storage can only grow on some plans; do not auto-grow without approval.

The app caps AI requests independently. Model calls are billed separately by Google; the required initial probe is one bounded synthetic request, not a general inference budget approval. A later concrete AI eval budget must cover the specified live samples.

`render.yaml` and the Dockerfile are prepared. Provisioning is blocked until the local release gates pass, account access is available, and the owner approves the exact account-displayed configuration and spending scope. No approval request is being made for an incomplete deployment candidate.
