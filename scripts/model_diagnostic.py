#!/usr/bin/env python3
"""Bounded, public-input connectivity/envelope diagnostic; never an extraction eval."""
import datetime
import json
import time
import urllib.error
import urllib.request
from localenv import ROOT, read

values = read()
headers = {'x-goog-api-key': values['GEMINI_API_KEY'], 'Content-Type': 'application/json'}
schema = {'type': 'object', 'additionalProperties': False, 'required': ['schemaVersion', 'ok'],
          'properties': {'schemaVersion': {'type': 'integer', 'enum': [1]}, 'ok': {'type': 'boolean'}}}
envelope = {'model': 'gemini-3.8-flash', 'store': False, 'input': 'Return schemaVersion 1 and ok true.',
            'generation_config': {'thinking_level': 'low', 'max_output_tokens': 128},
            'response_format': {'type': 'text', 'mime_type': 'application/json', 'schema': schema}}
result = {'observedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'model': 'gemini-3.8-flash',
          'scope': 'Minimal envelope diagnostic only; not usage extraction or eval'}
calls = [('modelGet', 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash', None),
         ('minimalInteraction', 'https://generativelanguage.googleapis.com/v1beta/interactions', envelope)]
for kind, url, body in calls:
    start = time.monotonic()
    try:
        request = urllib.request.Request(url, headers=headers, data=json.dumps(body).encode() if body else None)
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.loads(response.read(65536))
            status = response.status
        record = {'httpStatus': status, 'latencyMs': round((time.monotonic()-start)*1000), 'observedFields': sorted(payload)}
        if body:
            record['providerStatus'] = payload.get('status')
            record['steps'] = [{'type': step.get('type'), 'contentTypes': [content.get('type') for content in step.get('content', [])]} for step in payload.get('steps', [])]
            text = ''.join(content.get('text', '') for step in payload.get('steps', []) if step.get('type') == 'model_output' for content in step.get('content', []) if content.get('type') == 'text')
            # Persist only a parsed two-field public result, not arbitrary model text.
            try:
                parsed = json.loads(text)
                record['minimalSchemaMatch'] = parsed == {'schemaVersion': 1, 'ok': True}
            except json.JSONDecodeError:
                record['minimalSchemaMatch'] = False
        result[kind] = record
    except urllib.error.HTTPError as error:
        result[kind] = {'httpStatus': error.code, 'latencyMs': round((time.monotonic()-start)*1000)}
        error.close()
    except Exception as error:
        result[kind] = {'errorClass': type(error).__name__, 'latencyMs': round((time.monotonic()-start)*1000)}
(ROOT/'docs/evidence/gemini-minimal-diagnostic.json').write_text(json.dumps(result, indent=2)+'\n')
print(json.dumps(result))
