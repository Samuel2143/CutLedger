#!/usr/bin/env python3
"""Run a command with private local env; never shell-source secrets."""
import os
import sys
from localenv import read

if len(sys.argv) < 2:
    raise SystemExit('Usage: python3 scripts/run.py COMMAND [ARGS...]')
os.execvpe(sys.argv[1], sys.argv[1:], {**os.environ, **read()})
