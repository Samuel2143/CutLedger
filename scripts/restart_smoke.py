#!/usr/bin/env python3
"""Read-only restart check using the private browser-test session, never echoing its cookie."""
import json
import os
import sys
import urllib.request
from localenv import ROOT

state=json.loads((ROOT/'.local/browser-test-state.json').read_text())
cookie=next(item['value'] for item in state['cookies'] if item['name']=='cl_session')
def get(path):
    request=urllib.request.Request('http://localhost:8080/api/v1'+path,headers={'Cookie':'cl_session='+cookie})
    with urllib.request.urlopen(request,timeout=5) as response:return json.load(response)
session=get('/session');campaign=get('/campaigns/'+session['campaignId'])
current={'workspaceId':session['workspaceId'],'campaignId':session['campaignId'],'revisionId':campaign['latestRevision']['id'],'assetIds':sorted(asset['id'] for asset in campaign['assets'])}
before=ROOT/'.local/restart-before.json'
if '--before' in sys.argv:
    fd=os.open(before,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as output:json.dump(current,output)
    print('Saved private restart comparison (no cookie printed)')
else:
    assert current==json.loads(before.read_text())
    assert len(current['assetIds'])==3
    print('PASS: same session workspace, campaign, revision and three prior permissions after process restart')
