"""Credential-form regression tests: isolated temporary store, synthetic values only."""
import json
import subprocess
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from http.server import HTTPServer

import localenv
import configure

class SetupTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.previous = localenv.ENV
        localenv.ENV = Path(self.directory.name) / '.env.local'
        localenv.save({'PAYPAL_CLIENT_ID': 'synthetic-prior-id'})
        self.server = HTTPServer(('127.0.0.1', 0), configure.Entry)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.origin = f'http://localhost:{self.server.server_port}'

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        localenv.ENV = self.previous
        self.directory.cleanup()

    def get(self):
        with urllib.request.urlopen(self.origin + '/') as response:
            return response.headers, response.read().decode()

    def post(self, fields, origin='correct'):
        fields = {'nonce': configure.nonce, **fields}
        headers = {'Content-Type': 'application/x-www-form-urlencoded'}
        if origin is not None:
            headers['Origin'] = self.origin if origin == 'correct' else origin
        request = urllib.request.Request(self.origin + '/', data=urllib.parse.urlencode(fields).encode(), headers=headers)
        try:
            with urllib.request.urlopen(request) as response:
                return response.status, response.read().decode()
        except urllib.error.HTTPError as response:
            with response:
                return response.code, response.read().decode()

    def test_valid_save_reports_presence_without_echoing_values(self):
        fields = {key: 'synthetic-' + key.lower() for key in configure.fields}
        status, body = self.post(fields)
        self.assertEqual(status, 200)
        self.assertIn('All four required integration fields are present', body)
        self.assertEqual(localenv.read(), fields)
        self.assertEqual(localenv.ENV.stat().st_mode & 0o777, 0o600)
        for value in fields.values():
            self.assertNotIn(value, body)
            self.assertNotIn(value, self.get()[1])

    def test_blank_fields_preserve_previously_saved_values_and_report_missing(self):
        status, body = self.post({'PAYPAL_CLIENT_ID': '', 'PAYPAL_CLIENT_SECRET': ''})
        self.assertEqual(status, 200)
        self.assertEqual(localenv.read()['PAYPAL_CLIENT_ID'], 'synthetic-prior-id')
        self.assertIn('Still missing:', body)

    def test_cross_origin_null_and_missing_origins_still_cannot_save(self):
        before = localenv.ENV.read_bytes()
        for origin in ('https://evil.example', 'null', None):
            status, body = self.post({'GEMINI_API_KEY': 'synthetic-key'}, origin)
            self.assertEqual(status, 403)
            self.assertIn('Credentials were not saved', body)
            self.assertEqual(localenv.ENV.read_bytes(), before)

    def test_invalid_nonce_or_multiline_input_cannot_change_store(self):
        before = localenv.ENV.read_bytes()
        self.assertEqual(self.post({'nonce': 'incorrect', 'GEMINI_API_KEY': 'synthetic-key'})[0], 403)
        self.assertEqual(self.post({'GEMINI_API_KEY': 'synthetic\nvalue'})[0], 400)
        self.assertEqual(localenv.ENV.read_bytes(), before)

    def browser_submit(self):
        # Never point this fixture at the real credential server or capture request bodies.
        module = (localenv.ROOT / 'frontend/node_modules/playwright/index.mjs').as_uri()
        script = '''
          const {chromium}=await import(process.argv[1]);
          const browser=await chromium.launch({headless:true,executablePath:process.env.CUTLEDGER_BROWSER_PATH});
          try {
            const page=await browser.newPage(); let origin;
            page.on('request',request=>{if(request.method()==='POST')origin=request.headers()['origin']});
            await page.goto(process.argv[2]);
            await page.getByLabel('Gemini API key (gemini-3.6-flash access)').fill('synthetic-browser-key');
            const [response]=await Promise.all([page.waitForNavigation(),page.getByRole('button',{name:'Save credentials locally'}).click()]);
            console.log(JSON.stringify({origin,status:response.status()}));
          } finally {await browser.close()}
        '''
        output = subprocess.check_output(['node', '--input-type=module', '-e', script, module, self.origin], text=True, timeout=30)
        return json.loads(output)

    def test_real_browser_form_saves_with_exact_origin(self):
        result = self.browser_submit()
        self.assertEqual(result, {'origin': self.origin, 'status': 200})
        self.assertEqual(localenv.read()['GEMINI_API_KEY'], 'synthetic-browser-key')

    def test_real_browser_reproduces_previous_no_referrer_failure(self):
        class PreviousPolicy(configure.Entry):
            referrer_policy = 'no-referrer'
        self.server.RequestHandlerClass = PreviousPolicy
        before = localenv.ENV.read_bytes()
        self.assertEqual(self.browser_submit(), {'origin': 'null', 'status': 403})
        self.assertEqual(localenv.ENV.read_bytes(), before)

if __name__ == '__main__':
    unittest.main()
