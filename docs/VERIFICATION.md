# Public verification

Run `bash scripts/check.sh` with Docker for the deterministic/HTTP/PostgreSQL suite. See evidence/phase3-backend-tests.json for the recorded local regression result; payment-provider faults are synthetic test fixtures. Production image readiness passed as UID10001 with a read-only root against an isolated credential-free smoke database. Three browser regression tests passed comparison/filter/sort/evidence/manual confirmation/mobile/refresh/offline recovery.

Gemini uses the public synthetic fixture and complete canonical validator. The first40sample live AI evaluation failed mainly on429/503; its report is retained. AI quality and hosted release remain gates.

All live payment evidence, provider/account references, outcome records and financial test chronology are omitted at the owner’s request. This public source snapshot contains no private implementation Git history. No live deployment, video or submission confirmation is claimed.

Source-only snapshot fresh npm install, production frontend build and full Maven verify: PASS69tests,0failures/errors/skips. Includes30actualPostgreSQL integration tests and synthetic webhook outage/unknown-order/out-of-order/crashed-worker cases. No live financial evidence is included.
