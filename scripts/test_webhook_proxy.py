#!/usr/bin/env python3
"""Isolated ingress boundary tests; no provider requests or real credentials."""
import http.client
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from webhook_proxy import PATH, SIGNATURE_HEADERS, WebhookProxy


class Upstream(BaseHTTPRequestHandler):
    received = []

    def log_message(self, *_args):
        pass

    def do_POST(self):
        self.received.append((self.path, dict(self.headers), self.rfile.read(int(self.headers['Content-Length']))))
        self.send_response(204)
        self.end_headers()


class IngressTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.upstream = ThreadingHTTPServer(('127.0.0.1', 0), Upstream)
        cls.proxy = WebhookProxy(('127.0.0.1', 0), cls.upstream.server_port)
        cls.threads = [threading.Thread(target=s.serve_forever, daemon=True) for s in (cls.upstream, cls.proxy)]
        for thread in cls.threads:
            thread.start()

    @classmethod
    def tearDownClass(cls):
        for server in (cls.proxy, cls.upstream):
            server.shutdown()
            server.server_close()
        for thread in cls.threads:
            thread.join()

    def setUp(self):
        Upstream.received.clear()
        self.headers = {name: 'test-only' for name in SIGNATURE_HEADERS}
        self.headers['Content-Type'] = 'application/json'

    def request(self, method='POST', path=PATH, body=b'{"test": true}', headers=None):
        connection = http.client.HTTPConnection('127.0.0.1', self.proxy.server_port, timeout=2)
        try:
            connection.request(method, path, body=body, headers=self.headers if headers is None else headers)
            response = connection.getresponse()
            return response.status, response.read()
        finally:
            connection.close()

    def test_only_exact_signed_post_path_is_forwarded_without_sensitive_headers(self):
        body = b'{ "event": "synthetic-public-fixture" }'
        self.headers.update({'Cookie': 'test-cookie', 'Authorization': 'test-auth', 'Origin': 'https://example.test'})
        self.assertEqual(self.request(body=body), (204, b''))
        self.assertEqual(len(Upstream.received), 1)
        path, headers, received = Upstream.received[0]
        self.assertEqual((path, received), (PATH, body))
        self.assertFalse({'Cookie', 'Authorization', 'Origin'} & headers.keys())
        self.assertTrue(all(headers.get(name) == 'test-only' for name in SIGNATURE_HEADERS))

    def test_workspace_setup_health_and_query_paths_are_not_exposed(self):
        for path in ('/', '/api/v1/session', '/actuator/health', '/configure', PATH+'?other=true', PATH+'/'):
            self.assertEqual(self.request(path=path)[0], 404)
        for method in ('GET', 'HEAD', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'):
            self.assertEqual(self.request(method=method)[0], 404)
        self.assertEqual(Upstream.received, [])

    def test_missing_or_duplicate_signature_headers_fail_before_forwarding(self):
        for name in SIGNATURE_HEADERS:
            headers = {k: v for k, v in self.headers.items() if k != name}
            self.assertEqual(self.request(headers=headers)[0], 400)
        connection = http.client.HTTPConnection('127.0.0.1', self.proxy.server_port, timeout=2)
        try:
            connection.putrequest('POST', PATH)
            for name, value in self.headers.items():
                connection.putheader(name, value)
            connection.putheader(SIGNATURE_HEADERS[0], 'duplicate-test-only')
            connection.putheader('Content-Length', '2')
            connection.endheaders(b'{}')
            response = connection.getresponse()
            self.assertEqual(response.status, 400)
            response.read()
        finally:
            connection.close()
        self.assertEqual(Upstream.received, [])

    def test_oversized_empty_chunked_and_non_json_inputs_fail_before_forwarding(self):
        self.assertEqual(self.request(body=b'x'*262145)[0], 413)
        self.assertEqual(self.request(body=b'')[0], 413)
        self.assertEqual(self.request(headers={**self.headers, 'Transfer-Encoding': 'chunked'})[0], 400)
        self.assertEqual(self.request(headers={**self.headers, 'Content-Type': 'text/plain'})[0], 415)
        self.assertEqual(Upstream.received, [])


if __name__ == '__main__':
    unittest.main()
