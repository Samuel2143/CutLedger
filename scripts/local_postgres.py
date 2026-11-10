#!/usr/bin/env python3
"""Own unprivileged PostgreSQL 17 cluster, no system-database mutations."""
import os
import subprocess
import sys
from pathlib import Path
from localenv import ROOT, read

base = ROOT / '.local' / 'postgres'
data = base / 'data'
binary = Path(os.environ.get('POSTGRES_BIN', '/usr/lib/postgresql/17/bin'))
values = read()
if not values.get('DB_PASSWORD'):
    raise SystemExit('Run scripts/configure.py first to initialize private settings')
base.mkdir(parents=True, exist_ok=True, mode=0o700)
if sys.argv[-1] == 'stop':
    subprocess.run([str(binary / 'pg_ctl'), '-D', str(data), '-m', 'fast', 'stop'], check=True)
    raise SystemExit()
if not (data / 'PG_VERSION').exists():
    password_file = base / 'init-password'
    password_file.touch(mode=0o600)
    password_file.write_text(values['DB_PASSWORD'])
    try:
        subprocess.run([str(binary / 'initdb'), '-D', str(data), '-U', values['DB_USERNAME'], '--pwfile', str(password_file), '--auth-host=scram-sha-256', '--auth-local=scram-sha-256', '--encoding=UTF8', '--locale=C.UTF-8'], check=True)
    finally:
        password_file.unlink(missing_ok=True)
status = subprocess.run([str(binary / 'pg_ctl'), '-D', str(data), 'status'], stdout=subprocess.DEVNULL)
if status.returncode:
    subprocess.run([str(binary / 'pg_ctl'), '-D', str(data), '-l', str(base / 'server.log'), '-o', f'-h 127.0.0.1 -p 55432 -k "{base}"', '-w', 'start'], check=True)
env = {**os.environ, 'PGPASSWORD': values['DB_PASSWORD']}
args = [str(binary / 'psql'), '-h', '127.0.0.1', '-p', '55432', '-U', values['DB_USERNAME'], '-d', 'postgres', '-tAc']
exists = subprocess.check_output(args + ["SELECT 1 FROM pg_database WHERE datname='cutledger'"], env=env, text=True).strip()
if not exists:
    subprocess.run([str(binary / 'createdb'), '-h', '127.0.0.1', '-p', '55432', '-U', values['DB_USERNAME'], 'cutledger'], env=env, check=True)
print('Private PostgreSQL ready on 127.0.0.1:55432')
