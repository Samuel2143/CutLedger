#!/usr/bin/env python3
"""Install frozen local Node/JDK distributions after verifying recorded SHA-256."""
import hashlib
import json
import platform
import tarfile
import urllib.request
from pathlib import Path
from localenv import ROOT

if platform.system()!='Linux' or platform.machine()!='x86_64':
    raise SystemExit('Portable Linux x64 installer only; install Java 17.0.21+9 and Node 24.22.2 for your platform, then use the Maven wrapper.')
tools=ROOT/'.local/tools';tools.mkdir(parents=True,exist_ok=True)
pins=json.loads((ROOT/'docs/evidence/native-toolchain.json').read_text())
for pin in pins.values():
    if (tools/pin['directory']).exists():
        continue
    temporary=ROOT/'.local'/pin['archive']
    with urllib.request.urlopen(pin['url'],timeout=30) as response,temporary.open('wb') as output:
        while chunk:=response.read(1048576):output.write(chunk)
    if hashlib.sha256(temporary.read_bytes()).hexdigest()!=pin['sha256']:
        temporary.unlink();raise SystemExit('Distribution checksum mismatch; installation stopped')
    with tarfile.open(temporary) as archive:archive.extractall(tools,filter='data')
    temporary.unlink()
    print('Installed checksum-verified '+pin['directory'])
