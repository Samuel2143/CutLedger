#!/usr/bin/env python3
"""Prepare owner-only hosting secrets without displaying values or changing local sessions.
No network calls or paid resources. Import app.env only into the approved Render service.
"""
import argparse,json,os,secrets,subprocess
from pathlib import Path
from urllib.parse import urlsplit
from localenv import ROOT,read
parser=argparse.ArgumentParser();parser.add_argument('--origin',required=True);args=parser.parse_args()
origin=urlsplit(args.origin)
if origin.scheme!='https' or not origin.hostname or origin.username or origin.password or origin.query or origin.fragment or origin.path not in ('','/'):
    raise SystemExit('A plain HTTPS origin is required')
folder=ROOT/'.local/render';folder.mkdir(parents=True,exist_ok=True,mode=0o700);folder.chmod(0o700)
def private(path,value):
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    with os.fdopen(fd,'w') as out:out.write(value)
    path.chmod(0o600)
code=folder/'judge-access.txt';pepper=folder/'session-pepper.txt';hashed=folder/'judge-access.bcrypt'
if not code.exists():private(code,secrets.token_urlsafe(24))
if not pepper.exists():private(pepper,secrets.token_urlsafe(48))
if not hashed.exists():
    helper=folder/'HashAccess.java';private(helper,'''import java.nio.file.*;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class HashAccess { public static void main(String[] paths) throws Exception {Files.writeString(Path.of(paths[1]),new BCryptPasswordEncoder(12).encode(Files.readString(Path.of(paths[0]))));} }
''');private(hashed,'')
    crypto=ROOT/'.local/m2/org/springframework/security/spring-security-crypto/6.5.11/spring-security-crypto-6.5.11.jar'
    logging=ROOT/'.local/m2/org/springframework/spring-jcl/6.2.19/spring-jcl-6.2.19.jar'
    subprocess.run(['java','--class-path',str(crypto)+os.pathsep+str(logging),str(helper),str(code),str(hashed)],check=True,capture_output=True)
v=read();names=['PAYPAL_CLIENT_ID','PAYPAL_CLIENT_SECRET','PAYPAL_MERCHANT_ID','PAYPAL_WEBHOOK_ID','GEMINI_API_KEY']
if not all(v.get(key) for key in names):raise SystemExit('Required saved integration fields missing; no secret bundle exported')
cloud={key:v[key] for key in names};cloud.update(SPRING_PROFILES_ACTIVE='demo',PUBLIC_BASE_URL=args.origin.rstrip('/'),SESSION_PEPPER=pepper.read_text(),DEMO_ACCESS_CODE_HASH=hashed.read_text(),PAYPAL_ENV='sandbox',AI_MODEL='gemini-3.5-flash-lite',AI_DAILY_REQUEST_LIMIT='200')
lines=[]
for key,value in cloud.items():
    if any(c in value for c in ('\n','\r','\x00')):raise SystemExit('Invalid single-line configuration')
    lines.append(key+'='+("'"+value+"'" if key=='DEMO_ACCESS_CODE_HASH' else value))
private(folder/'app.env','\n'.join(lines)+'\n')
print(json.dumps({'status':'PREPARED_PRIVATE_ONLY','folder':str(folder),'keys':sorted(cloud),'databaseUrl':'NOT_INCLUDED: paste approved managed database internal URL directly in hosting UI','webhook':'Temporary existing real Sandbox ID; replace with hosted registration after the origin is live','publicOrigin':cloud['PUBLIC_BASE_URL'],'secretValuesPrinted':False}))
