#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 scripts/configure.py --init
python3 scripts/run.py docker compose -p cutledger up --build -d
