#!/usr/bin/env python3
"""Development ingress for one signed webhook path, never the workspace or setup UI."""
import http.client
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PATH = '/api/v1/webhooks/paypal'
SIGNATURE_HEADERS = ('PAYPAL-AUTH-ALGO', 'PAYPAL-CERT-URL', 'PAYPAL-TRANSMISSION-ID',
                     'PAYPAL-TRANSMISSION-SIG', 'PAYPAL-TRANSMISSION-TIME')
MAX_BODY = 262144


class WebhookProxy(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address=('127.0.0.1', 8092), upstream_port=8080):
        self.upstream_port = upstream_port
        self.slots = threading.BoundedSemaphore(4)
        super().__init__(address, Handler)

    def process_request(self, request, client_address):
        if not self.slots.acquire(blocking=False):
            self.shutdown_request(request)
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.slots.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.slots.release()

    def handle_error(self, request, client_address):
        pass  # Never print incoming bodies, signature headers or socket details.


class Handler(BaseHTTPRequestHandler):
    server_version = 'CutLedgerWebhookIngress'
    sys_version = ''

    def setup(self):
        super().setup()
        self.connection.settimeout(45)

    def log_message(self, *_args):
        pass

    def reply(self, status):
        self.send_response(status)
        self.send_header('Content-Length', '0')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Connection', 'close')
        self.end_headers()
        self.close_connection = True

    def do_GET(self):
        self.reply(404)

    do_HEAD = do_GET
    do_PUT = do_GET
    do_DELETE = do_GET
    do_OPTIONS = do_GET
    do_PATCH = do_GET

    def do_POST(self):
        if self.path != PATH:
            self.reply(404)
            return
        lengths = self.headers.get_all('Content-Length', [])
        if self.headers.get('Transfer-Encoding') or len(lengths) != 1 or not lengths[0].isdigit():
            self.reply(400)
            return
        length = int(lengths[0])
        if not 1 <= length <= MAX_BODY:
            self.reply(413)
            return
        if any(len(self.headers.get_all(name, [])) != 1 or not self.headers.get(name) for name in SIGNATURE_HEADERS):
            self.reply(400)
            return
        if self.headers.get('Content-Type', '').split(';')[0].strip().lower() != 'application/json':
            self.reply(415)
            return
        connection = http.client.HTTPConnection('127.0.0.1', self.server.upstream_port, timeout=45)
        try:
            body = self.rfile.read(length)
            if len(body) != length:
                self.reply(400)
                return
            headers = {name: self.headers[name] for name in SIGNATURE_HEADERS}
            headers['Content-Type'] = 'application/json'
            # Body bytes stay untouched for provider signature verification. Cookies,
            # Authorization and arbitrary forwarding headers never reach the app.
            connection.request('POST', PATH, body=body, headers=headers)
            response = connection.getresponse()
            self.reply(response.status if response.status in (204, 400, 413, 415, 429, 503) else 503)
            response.close()
        except (OSError, http.client.HTTPException):
            self.reply(503)
        finally:
            connection.close()


if __name__ == '__main__':
    WebhookProxy().serve_forever()
