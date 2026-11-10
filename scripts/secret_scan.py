#!/usr/bin/env python3
"""Scan publishable candidates and bundle for known local secrets and key signatures."""
import re
import subprocess
from pathlib import Path
from localenv import ROOT, read

paths = subprocess.check_output(['git','ls-files','--cached','--others','--exclude-standard','-z'],cwd=ROOT).decode().split('\0')
secrets = [value for key,value in read().items() if value and len(value)>=16 and any(word in key for word in ('SECRET','API_KEY','PEPPER','PASSWORD','CODE_HASH'))]
signatures = [re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),re.compile(rb'AIza[0-9A-Za-z_-]{35}')]
failures=[]
for file in [ROOT/p for p in paths if p]+list((ROOT/'frontend/dist').rglob('*')):
    if not file.is_file() or file.suffix in ('.png','.zip'):continue
    body=file.read_bytes()
    if any(secret.encode() in body for secret in secrets) or any(pattern.search(body) for pattern in signatures):
        failures.append(str(file.relative_to(ROOT)))
if failures:
    print('FAIL: possible secrets in files (values redacted): '+', '.join(sorted(set(failures))))
    raise SystemExit(1)
print('PASS: publishable candidates and frontend bundle contain no known configured secrets or recognized private-key/API-key signatures')
