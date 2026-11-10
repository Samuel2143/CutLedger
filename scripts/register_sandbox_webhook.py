#!/usr/bin/env python3
"""Register the verified webhook-only temporary endpoint; never print provider payloads."""
import base64, hashlib, json, urllib.request
from datetime import datetime, timezone
from localenv import ROOT, read, save
values=read()
endpoint=json.loads((ROOT/'.local/webhook-tunnel.json').read_text())['webhookUrl']
if not endpoint.startswith('https://') or not endpoint.endswith('/api/v1/webhooks/paypal'):
    raise SystemExit('Invalid isolated webhook endpoint')
def call(path,body=None,token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    else: headers['Authorization']='Basic '+base64.b64encode((values['PAYPAL_CLIENT_ID']+':'+values['PAYPAL_CLIENT_SECRET']).encode()).decode(); headers['Content-Type']='application/x-www-form-urlencoded'
    request=urllib.request.Request('https://api-m.sandbox.paypal.com'+path,data=body,headers=headers)
    with urllib.request.urlopen(request,timeout=20) as response:
        return json.loads(response.read(262144))
token=call('/v1/oauth2/token',b'grant_type=client_credentials')['access_token']
catalog=call('/v1/notifications/webhooks-event-types',token=token)
requested={'CHECKOUT.ORDER.APPROVED','PAYMENT.CAPTURE.COMPLETED','PAYMENT.CAPTURE.PENDING','PAYMENT.CAPTURE.DENIED','PAYMENT.CAPTURE.REFUNDED','PAYMENT.CAPTURE.REVERSED'}
available={item['name'] for item in catalog['event_types']}
if not requested<=available: raise SystemExit('Required event types missing; no registration made')
existing=call('/v1/notifications/webhooks',token=token).get('webhooks',[])
matching=[item for item in existing if item['url']==endpoint]
if len(matching)>1: raise SystemExit('Duplicate endpoint registrations require review')
webhook=matching[0] if matching else call('/v1/notifications/webhooks',json.dumps({'url':endpoint,'event_types':[{'name':name} for name in sorted(requested)]}).encode(),token)
actual={item['name'] for item in webhook['event_types']}
if not requested<=actual: raise SystemExit('Registered event list does not match')
values['PAYPAL_WEBHOOK_ID']=webhook['id']; save(values)
evidence={'observedAt':datetime.now(timezone.utc).isoformat(),'status':'REGISTERED_SANDBOX','webhookReferenceHash':hashlib.sha256(webhook['id'].encode()).hexdigest(),'endpointHash':hashlib.sha256(endpoint.encode()).hexdigest(),'events':sorted(actual),'runtimeReload':'PENDING','realDeliveryAndSignature':'NOT_RUN','created':not bool(matching)}
(ROOT/'docs/evidence/sandbox-webhook-registration.json').write_text(json.dumps(evidence,indent=2)+'\n')
print(json.dumps(evidence))
