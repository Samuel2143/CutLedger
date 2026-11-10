#!/usr/bin/env python3
"""Launch a verified JAR from an immutable local copy; keep secrets out of arguments/logs."""
import hashlib
import json
import os
import re
import shutil
import signal
import subprocess
import time
import urllib.request
from pathlib import Path
from localenv import ROOT, read

values = read()
jar = ROOT / 'backend/target/cutledger-0.1.0.jar'
digest = hashlib.sha256(jar.read_bytes()).hexdigest()
runtime = ROOT / '.local/runtime'
runtime.mkdir(exist_ok=True, parents=True)
copy = runtime / ('cutledger-' + digest[:16] + '.jar')
if not copy.exists():
    shutil.copyfile(jar, copy)
port = int(values.get('PORT', '8080'))
listing = subprocess.check_output(['ss', '-ltnp', f'( sport = :{port} )'], text=True)
for pid in set(re.findall(r'pid=(\d+)', listing)):
    process = Path('/proc') / pid
    args = process.joinpath('cmdline').read_bytes().split(b'\0')
    owned = any(str(copy.parent).encode() in arg and b'cutledger-' in arg for arg in args)
    owned = owned or (b'backend/target/cutledger-0.1.0.jar' in args and process.joinpath('cwd').resolve() == ROOT)
    if not owned:
        raise SystemExit('Port is occupied by an unrelated process; nothing was stopped')
    os.kill(int(pid), signal.SIGTERM)
    for _ in range(100):
        if not process.exists():
            break
        time.sleep(0.05)
fd = os.open(ROOT / '.local/app-current.log', os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, 'w') as output:
    process = subprocess.Popen(['java', '-jar', str(copy)], cwd=ROOT, env={**os.environ, **values}, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
pidfile = ROOT / '.local/app.pid'
pidfile.write_text(str(process.pid) + '\n')
pidfile.chmod(0o600)
for _ in range(60):
    if process.poll() is not None:
        raise SystemExit('App startup failed; inspect the ignored local app log without printing secrets')
    try:
        with urllib.request.urlopen(f'http://localhost:{port}/actuator/health/readiness', timeout=2) as response:
            if json.load(response).get('status') == 'UP':
                print(json.dumps({'status': 'READY', 'url': f'http://localhost:{port}', 'jarSha256': digest, 'pid': process.pid}))
                break
    except (OSError, ValueError):
        pass
    time.sleep(0.5)
else:
    raise SystemExit('App readiness timed out; preserve existing data and inspect the private log')
