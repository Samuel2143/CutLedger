#!/usr/bin/env python3
"""Start checksum-pinned temporary HTTPS ingress to the webhook-only local proxy."""
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from localenv import ROOT

tool = ROOT / '.local/tools/cloudflared-2026.10.7'
expected = '1f99fb518b83dec2ba8d870a7cc991d82a77e6196295a3d2754ba17ab9f91872'
if not tool.exists() or hashlib.sha256(tool.read_bytes()).hexdigest() != expected:
    raise SystemExit('Install the official checksum-pinned tunnel tool before starting')
processes = []
checks = []


def launch(args, log_name):
    path = ROOT / '.local' / log_name
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, 'w') as output:
        process = subprocess.Popen(args, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=output,
                                   stderr=subprocess.STDOUT, start_new_session=True)
    processes.append(process)
    return process


try:
    proxy = launch([sys.executable, str(ROOT/'scripts/webhook_proxy.py')], 'webhook-proxy.log')
    tunnel = launch([str(tool), 'tunnel', '--no-autoupdate', '--protocol', 'http2',
                     '--metrics', '127.0.0.1:2092', '--url', 'http://127.0.0.1:8092'], 'webhook-tunnel.log')
    endpoint = None
    for _ in range(60):
        if any(process.poll() is not None for process in processes):
            raise RuntimeError('Temporary ingress process stopped; inspect its private local log')
        log = (ROOT/'.local/webhook-tunnel.log').read_text()
        match = re.search(r'https://[a-z0-9-]+\.trycloudflare\.com', log)
        if match:
            endpoint = match.group()
            break
        time.sleep(0.5)
    if endpoint is None:
        raise RuntimeError('Temporary endpoint was not assigned')
    # Read-only public-path check proves the workspace is not published through this ingress.
    reachable = False
    for _ in range(120):
        try:
            with urllib.request.urlopen(endpoint+'/', timeout=5):
                raise RuntimeError('Ingress unexpectedly exposes the root path')
        except urllib.error.HTTPError as error:
            with error:
                checks.append({'httpStatus': error.code})
                if error.code == 404:
                    reachable = True
                    break
        except urllib.error.URLError as error:
            checks.append({'networkError': type(error.reason).__name__})
        time.sleep(0.5)
    if not reachable:
        raise RuntimeError('Temporary endpoint did not pass the public root-path isolation check')
    metadata = {'observedAt': datetime.now(timezone.utc).isoformat(), 'origin': endpoint,
                'webhookUrl': endpoint+'/api/v1/webhooks/paypal', 'proxyPid': proxy.pid,
                'tunnelPid': tunnel.pid, 'rootHttpStatus': 404}
    path = ROOT/'.local/webhook-tunnel.json'
    path.write_text(json.dumps(metadata, indent=2)+'\n')
    path.chmod(0o600)
    print(json.dumps({'status': 'READY_WEBHOOK_ONLY', 'rootHttpStatus': 404,
                      'endpointHash': hashlib.sha256(endpoint.encode()).hexdigest()}))
except Exception as error:
    for process in processes:
        process.terminate()
    (ROOT/'docs/evidence/webhook-tunnel-checks.json').write_text(json.dumps({'status':'FAIL','checks':checks},indent=2)+'\n')
    raise SystemExit(str(error)) from None
