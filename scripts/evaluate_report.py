#!/usr/bin/env python3
"""Review a saved public live evaluation against the agreed AI_SPEC thresholds; no API calls."""
import datetime,hashlib,json
from localenv import ROOT
path=ROOT/'docs/evidence/gemini-live-evaluation.json'
raw=path.read_bytes();report=json.loads(raw);samples=report['samples']
expected={(f'AI{i:02}',run) for run in range(1,4) for i in range(1,13)}|{(f'H{i:02}',4) for i in range(1,5)}
assert len(samples)==40 and {(x['case'],x['run']) for x in samples}==expected
explicit_ids={'AI01','AI02','AI03','AI04','AI05','AI08','AI09','H01','H02'}
base={'schemaVersion':1,'distributions':['ORGANIC'],'territories':['US'],'platforms':['INSTAGRAM'],'startDate':'2026-11-15','endDateExclusive':'2026-12-15'}
def expected_scope(case):
    result=dict(base)
    if case in {'AI01','AI08'}:result.update(distributions=['PAID_ADS'],territories=['GB','US'],endDateExclusive='2027-02-13')
    if case=='AI09':result.update(distributions=['PAID_ADS'],territories=['WORLDWIDE'],endDateExclusive='2027-11-15')
    if case=='AI04':result['platforms']=['INSTAGRAM','YOUTUBE']
    if case=='AI05':result['platforms']=['WEBSITE']
    if case=='H01':result['platforms']=['INSTAGRAM','WEBSITE']
    return result
def normalized(value):return {k:sorted(v) if isinstance(v,list) else v for k,v in value.items()}
explicit=[x for x in samples if x['case'] in explicit_ids];unsafe=[x for x in samples if x['case'] not in explicit_ids]
exact=sum(x['status']=='READY' and normalized(x.get('normalizedUsage',{}))==normalized(expected_scope(x['case'])) for x in explicit)
blocked=sum(x['status']!='READY' for x in unsafe)
provider_failures=[{'case':x['case'],'run':x['run'],'error':x.get('providerFailure')} for x in samples if x['status']=='FAILED_PROVIDER']
semantic_failures=[{'case':x['case'],'run':x['run'],'status':x['status']} for x in explicit if x['status']!='FAILED_PROVIDER' and not(x['status']=='READY' and normalized(x.get('normalizedUsage',{}))==normalized(expected_scope(x['case'])))]
result={'observedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'rawReportSha256':hashlib.sha256(raw).hexdigest(),'model':report['model'],'promptVersion':report['promptVersion'],'plannedSamples':40,'completedSamples':len(samples),'explicitExact':exact,'explicitDenominator':len(explicit),'explicitExactPercent':round(100*exact/len(explicit),2),'unsafeBlocked':blocked,'unsafeDenominator':len(unsafe),'semanticUnsafeRecognized':report['unsafeSemanticallyRecognized'],'unsupportedRecall':report['unsupportedRecall'],'unsupportedDenominator':report['unsupportedDenominator'],'providerFailedSamples':provider_failures,'semanticFailures':semantic_failures,'schemaEvidenceAcceptedResponses':report['evidenceValidResponses'],'medianLatencyMs':report['medianLatencyMs'],'p95LatencyMs':report['p95LatencyMs'],'reportedTokens':report['reportedTotalTokens'],'gatePassed':exact/len(explicit)>=.90 and blocked==len(unsafe),'criteria':'AI_SPEC >=90% exact explicit and100% unsafe blocks. Provider failures count as explicit misses or fail-closed safety, not semantic successes. Original JUnit run also asserted zero provider failures; that extra criterion was removed in DECISIONS, original report retained.'}
(ROOT/'docs/evidence/gemini-live-evaluation-gate.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result))
if not result['gatePassed']:raise SystemExit(1)
