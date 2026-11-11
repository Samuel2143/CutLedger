#!/usr/bin/env python3
"""Loopback-only credential entry. No secret echo, logs, URLs, or browser storage."""
import secrets
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import parse_qs
from localenv import ROOT, read, save

def initialize():
    values = read()
    for line in (ROOT / '.env.example').read_text().splitlines():
        if line and not line.startswith('#'):
            key, value = line.split('=', 1)
            values.setdefault(key, value)
    for key in ('DB_PASSWORD', 'SESSION_PEPPER'):
        if not values[key]:
            values[key] = secrets.token_urlsafe(32)
    save(values)

nonce = secrets.token_urlsafe(32)
fields = {
    'PAYPAL_CLIENT_ID': 'Sandbox seller app client ID',
    'PAYPAL_CLIENT_SECRET': 'Sandbox seller app client secret',
    'PAYPAL_MERCHANT_ID': 'Sandbox business seller merchant ID',
    'GEMINI_API_KEY': 'Gemini API key (gemini-3.5-flash-lite access)',
    'PAYPAL_WEBHOOK_ID': 'Sandbox webhook ID (optional until hosted)',
}

class Entry(BaseHTTPRequestHandler):
    # no-referrer makes browsers send Origin:null on native form POSTs.
    # same-origin retains the exact local origin without sending referrers elsewhere.
    referrer_policy = 'same-origin'

    @property
    def origin(self):
        return f'http://localhost:{self.server.server_port}'

    def log_message(self, *_):
        pass

    def respond(self, status, body):
        self.send_response(status)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Security-Policy', "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'")
        self.send_header('Referrer-Policy', self.referrer_policy)
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(body.encode())

    def do_GET(self):
        if self.path != '/' or self.headers.get('Host') != f'localhost:{self.server.server_port}':
            return self.respond(404, 'Not found')
        inputs = ''.join(f'<label>{label}<input type="password" name="{key}" autocomplete="off" placeholder="{"Saved — leave blank to keep" if read().get(key) else "Not configured"}"></label>' for key, label in fields.items())
        self.respond(200, f'''<!doctype html><html lang="en"><meta name="viewport" content="width=device-width,initial-scale=1"><title>CutLedger · Private setup</title>
<style>body{{font:16px/1.5 system-ui;background:#f6f7fb;color:#172033;max-width:620px;margin:48px auto;padding:24px}}label{{display:block;margin:20px 0}}input{{display:block;box-sizing:border-box;width:100%;padding:12px;border:1px solid #a6afbe;border-radius:8px;margin-top:6px}}button{{padding:14px 24px;background:#5152d6;color:white;border:0;border-radius:8px;font:inherit}}small{{color:#596579}}</style>
<h1>Private integration setup</h1><p>CutLedger · PayPal Sandbox only</p><p>Enter seller credentials here, never in chat. Values are stored only in the ignored, owner-readable <code>.env.local</code>. Saved values are never sent back to this page.</p>
<form method="post" action="/"><input type="hidden" name="nonce" value="{nonce}">{inputs}<button>Save credentials locally</button></form><p><small>No buyer password is needed here. Keep the separate personal Sandbox buyer login for the hosted approval step.</small></p></html>''')

    def do_POST(self):
        if self.path != '/' or self.headers.get('Host') != f'localhost:{self.server.server_port}' or self.headers.get('Origin') != self.origin:
            return self.respond(403, f'Origin rejected. Credentials were not saved. Reload {self.origin}/ and try again.')
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= 16384:
                return self.respond(413, 'Body too large')
            body = parse_qs(self.rfile.read(length).decode(), strict_parsing=True)
            if not secrets.compare_digest(body.get('nonce', [''])[0], nonce):
                return self.respond(403, 'Form expired; reload')
            updated = read()
            for key in fields:
                value = body.get(key, [''])[0].strip()
                if value:
                    updated[key] = value
            save(updated)
            required = ['PAYPAL_CLIENT_ID', 'PAYPAL_CLIENT_SECRET', 'PAYPAL_MERCHANT_ID', 'GEMINI_API_KEY']
            missing = [fields[key] for key in required if not updated.get(key)]
            status = '<p>All four required integration fields are present.</p>' if not missing else '<p>Still missing: ' + ', '.join(missing) + '.</p>'
            self.respond(200, '<title>Saved</title><h1>Credentials saved locally</h1>' + status + '<p>Stored on this computer; provider validity is checked separately. Return to Codex and say “credentials saved.” No values are displayed or logged.</p><a href="/">Return to setup</a>')
        except (ValueError, UnicodeError):
            self.respond(400, 'Invalid form')

if __name__ == '__main__':
    initialize()
    if '--init' in sys.argv:
        print('Initialized private .env.local (values are never printed)')
        raise SystemExit()
    print('Private setup: http://localhost:8091 (loopback only)', flush=True)
    HTTPServer(('127.0.0.1', 8091), Entry).serve_forever()
