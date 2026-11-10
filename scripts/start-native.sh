#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/toolchain.sh
python3 scripts/configure.py --init
python3 scripts/local_postgres.py
npm ci --prefix frontend --no-audit --no-fund
npm --prefix frontend run build
python3 scripts/run.py backend/mvnw -f backend/pom.xml -Dmaven.repo.local="$PWD/.local/m2" -B -ntp package -DskipTests
exec python3 scripts/launch_app.py
