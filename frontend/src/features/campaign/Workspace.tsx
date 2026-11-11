import {lazy,Suspense,useCallback,useEffect,useRef,useState} from 'react';
import {InterpretationReview} from './InterpretationReview';
import type {Interpretation} from './InterpretationReview';
import {api,money,mutate,navigate} from '../../api';
import type {Assessment,Campaign,Diagnostics,Grant,Payment,Quote,RightsRow,Session,Usage} from '../../api';
const RightsComparison=lazy(()=>import('./RightsComparison').then(module=>({default:module.RightsComparison})));

export function Workspace({campaign,session,diagnostics,onRefresh}:{campaign:Campaign;session:Session;diagnostics:Diagnostics|null;onRefresh:()=>Promise<void>}){
  const [aiRecord,setAiRecord]=useState<Interpretation|null>(null);
  const [assessment,setAssessment]=useState<Assessment|null>(null),[quote,setQuote]=useState<Quote|null>(null);
  const [error,setError]=useState(''),[busy,setBusy]=useState(false),[manualDirty,setManualDirty]=useState(false),[aiDirty,setAiDirty]=useState(false),[tick,setTick]=useState(Date.now());
  const [evidence,setEvidence]=useState<{row:RightsRow;clauses:Record<string,string>}|null>(null);
  const dirty=manualDirty||aiDirty;
  useEffect(()=>{if(!dirty)return;const unload=(event:BeforeUnloadEvent)=>{event.preventDefault()};const navigation=(event:Event)=>{if(!window.confirm('Leave this unconfirmed draft? Your last confirmed revision stays unchanged.'))event.preventDefault()};addEventListener('beforeunload',unload);addEventListener('cl-before-navigate',navigation);return()=>{removeEventListener('beforeunload',unload);removeEventListener('cl-before-navigate',navigation)}},[dirty]);
  const dialog=useRef<HTMLDialogElement>(null);
  const quoteKey=`cl-quote:${campaign.id}:${campaign.latestRevision.id}`;
  useEffect(()=>{
    let active=true;setAssessment(null);setError('');setQuote(null);
    api<Assessment>(`/campaigns/${campaign.id}/revisions/${campaign.latestRevision.id}/assessment`).then(value=>{if(active)setAssessment(value)}).catch(e=>{if(active)setError(e.message)});
    const saved=sessionStorage.getItem(quoteKey);
    if(saved)api<Quote>(`/quotes/${saved}`).then(value=>{if(active&&value.status==='OPEN'&&value.entitlementVersion===campaign.entitlementVersion)setQuote(value);else sessionStorage.removeItem(quoteKey)}).catch(()=>{sessionStorage.removeItem(quoteKey)});
    return()=>{active=false};
  },[campaign.id,campaign.latestRevision.id,campaign.entitlementVersion,quoteKey]);
  useEffect(()=>{if(!quote)return;const timer=setInterval(()=>setTick(Date.now()),1000);return()=>clearInterval(timer)},[quote]);
  const [clockOffset,setClockOffset]=useState(0);
  useEffect(()=>{if(quote)setClockOffset(Date.now()-Date.parse(quote.serverTime))},[quote]);
  const seconds=quote?Math.max(0,Math.ceil((Date.parse(quote.expiresAt)-(tick-clockOffset))/1000)):0;
  async function createQuote(){
    setError('');setBusy(true);
    try{
      const result=await mutate<Quote|{kind:'NO_PURCHASE_REQUIRED'}>(session,`/campaigns/${campaign.id}/quotes`,{revisionId:campaign.latestRevision.id},campaign.version,quote?.id??'initial');
      if('kind'in result){await onRefresh();return}
      setQuote(result);sessionStorage.setItem(quoteKey,result.id);setTick(Date.now());
    }catch(e){setError((e as Error).message)}finally{setBusy(false)}
  }
  async function checkout(){
    if(!quote)return;setBusy(true);setError('');
    try{const payment=await mutate<Payment>(session,`/quotes/${quote.id}/payments`,{acceptedSnapshotHash:quote.snapshotHash},campaign.version);navigate(`/checkout/${payment.paymentId}`)}
    catch(e){setError((e as Error).message)}finally{setBusy(false)}
  }
  const showEvidence=useCallback(async(row:RightsRow)=>{
    if(!assessment)return;
    try{const policy=await api<{clauses:Record<string,string>}>(`/policies/${row.policyVersionId}`);setEvidence({row,clauses:policy.clauses});dialog.current?.showModal()}
    catch(e){setError((e as Error).message)}
  },[assessment]);
  const upgrades=assessment?.rows.filter(r=>r.status==='UPGRADE_REQUIRED').length??0,covered=assessment?.rows.filter(r=>r.status==='COVERED').length??0;
  return <div className="workspace"><div className="main-column">
    {error&&<div className="error" role="alert">{error}<button className="secondary" onClick={()=>void onRefresh()}>Reload current campaign</button></div>}
    {campaign.paymentLocked&&<div className="notice" role="status">This campaign is locked to an existing checkout. <button onClick={()=>navigate(`/checkout/${campaign.activePaymentId}`)}>Resume checkout →</button></div>}
    <section className="section"><div className="section-heading"><h2>The visuals in this campaign</h2><span className="muted">3 original assets</span></div><div className="assets">{campaign.assets.map(asset=><article className="asset" key={asset.id}><img src={asset.previewPath} alt={`${asset.name}, an original geometric illustration`}/><div className="asset-info"><h3>{asset.name}</h3><span className="tier">{asset.heldTier}</span><p>{asset.source==='SEEDED'?'Seeded prior permission':'Verified Sandbox permission'}</p></div></article>)}</div></section>
    <section className="card composer"><div className="step-number">01</div><div><p className="eyebrow">THE CLIENT REVISION</p><h2>What’s changing?</h2><InterpretationReview campaign={campaign} session={session} diagnostics={diagnostics} onRefresh={onRefresh} onDirty={setAiDirty} onRecord={setAiRecord}/><ManualUsage campaign={campaign} session={session} onRefresh={onRefresh} onDirty={setManualDirty} interpretation={aiRecord}/></div></section>
    <section className="current"><p className="eyebrow">REVISION {campaign.latestRevision.sequence} · {campaign.latestRevision.origin==='MANUAL'?'MANUALLY CONFIRMED':campaign.latestRevision.origin==='AI_CONFIRMED'?'AI PROPOSAL · USER CONFIRMED':'SEEDED BASELINE'}</p><h2>Current confirmed usage</h2><div className="usage-chips"><span>{campaign.latestRevision.usage.distributions.join(' + ')}</span><span>{campaign.latestRevision.usage.territories.join(' + ')}</span><span>{campaign.latestRevision.usage.platforms.join(' + ')}</span><span>{campaign.latestRevision.usage.startDate} → {campaign.latestRevision.usage.endDateExclusive} (end exclusive)</span></div>{(campaign.latestRevision.confirmationEvidence.excludedRequirements??[]).length>0&&<div className="notice"><strong>Excluded from this revision</strong><ul>{campaign.latestRevision.confirmationEvidence.excludedRequirements!.map(item=><li key={item.id}>{item.requirement}</li>)}</ul></div>}</section>
    <section className="card comparison"><div className="section-heading"><h2>The rights difference</h2><span className="muted">From merchant policy v1</span></div>{!assessment?<p role="status">Checking held permissions…</p>:<><p className="muted">{upgrades} {upgrades===1?'upgrade':'upgrades'} · {covered} already covered</p><Suspense fallback={<p role="status">Loading rights comparison…</p>}><RightsComparison assessment={assessment} onEvidence={showEvidence}/></Suspense></>}</section>
    <p className="fine">Coverage applies only to the confirmed usage under CutLedger Studio’s demo policy. Television, cinema, resale and AI training are not offered.</p>
  </div><aside><section className="card summary"><p className="eyebrow">PAYPAL SANDBOX · USD</p><h2>{assessment?.totalDeltaMinor===0?'Rights already held.':'Only the additional rights.'}</h2><p className="muted">Bound to confirmed revision {campaign.latestRevision.sequence}. Previous permissions remain unchanged.</p>
    {assessment&&<div className="quote-breakdown">{assessment.rows.map(row=><p key={row.assetId}><span>{row.assetName}</span><strong>{money(row.deltaMinor)}</strong></p>)}</div>}
    <div className="summary-bottom"><span>{quote?'Quote total':'Additional cost assessment'}</span><strong className="quote-total">{assessment?money(quote?.totalMinor??assessment.totalDeltaMinor):'Checking…'}</strong></div>
    {dirty&&<p className="notice">Confirm or discard your draft edits before checkout.</p>}
    {campaign.paymentLocked?<button onClick={()=>navigate(`/checkout/${campaign.activePaymentId}`)}>Resume existing checkout</button>:assessment?.totalDeltaMinor===0?<button className="secondary" onClick={()=>navigate(`/campaigns/${campaign.id}?tab=permissions`)}>View permission records</button>:quote&&quote.status==='OPEN'&&seconds>0?<><p className="fine" aria-live="off">Quote expires in {Math.floor(seconds/60)}:{String(seconds%60).padStart(2,'0')}</p><button disabled={busy||dirty||!diagnostics?.checkoutAvailable} onClick={()=>void checkout()}>{busy?'Preparing checkout…':'Accept quote & continue →'}</button></>:<><p className="fine">{quote?'Price confirmation expired. Request a fresh server quote.':'Review the exact purchased tier and prior credit before accepting.'}</p><button disabled={busy||dirty||!assessment?.canQuote} onClick={()=>void createQuote()}>{busy?'Preparing quote…':quote?'Refresh quote':'Review upgrade quote →'}</button></>}
    {quote&&<div className="quote-lines">{quote.lines.map(line=><p key={line.assetId} className="fine">{line.targetTier}: {money(line.targetCumulativeMinor)} cumulative − {money(line.priorCreditMinor)} prior credit = {money(line.deltaMinor)} additional.</p>)}</div>}
    {!diagnostics?.checkoutAvailable&&<p className="notice">Checkout is unavailable until seller configuration is present.</p>}<p className="fine">Sandbox only · No real funds.<br/>AI never decides coverage or money.</p>
  </section></aside><dialog ref={dialog} className="policy-dialog" aria-labelledby="policy-title"><button className="secondary close-dialog" onClick={()=>dialog.current?.close()} aria-label="Close policy evidence">Close ×</button><h2 id="policy-title">{evidence?.row.assetName} · Policy evidence</h2><p className="muted">CutLedger Studio · policy v1</p>{evidence?.row.clauseIds.map(id=><section key={id}><h3>{id}</h3><p>{evidence.clauses[id]??'Clause unavailable. Checkout requires review.'}</p></section>)}</dialog></div>;
}

function ManualUsage({campaign,session,onRefresh,onDirty,interpretation}:{campaign:Campaign;session:Session;onRefresh:()=>Promise<void>;onDirty:(dirty:boolean)=>void;interpretation:Interpretation|null}){
  const [usage,setUsage]=useState<Usage>(campaign.latestRevision.usage),[territory,setTerritory]=useState(campaign.latestRevision.usage.territories.join(', '));
  const subset=interpretation&&['UNSUPPORTED','NEEDS_CLARIFICATION'].includes(interpretation.status)?interpretation:null;
  const [excluded,setExcluded]=useState<string[]>([]),[resolved,setResolved]=useState<string[]>([]);
  useEffect(()=>{setExcluded([]);setResolved([]);setConfirmed(false)},[interpretation?.id]);
  const [confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
  useEffect(()=>{setUsage(campaign.latestRevision.usage);setTerritory(campaign.latestRevision.usage.territories.join(', '));setConfirmed(false);onDirty(false)},[campaign.latestRevision.id]);
  function change(next:Usage){setUsage(next);setConfirmed(false);setResolved([]);onDirty(true)}
  async function save(){
    setBusy(true);setError('');
    try{
      const finalUsage={...usage,schemaVersion:1 as const,territories:territory.split(',').map(t=>t.trim().toUpperCase()).filter(Boolean)};
      const fields=['distributions','territories','platforms','startDate','endDateExclusive'] as const;
      const equal=(a:unknown,b:unknown)=>JSON.stringify(Array.isArray(a)?[...a].sort():a)===JSON.stringify(Array.isArray(b)?[...b].sort():b);
      const fieldMap:Record<string,keyof Usage>={DISTRIBUTION:'distributions',TERRITORY:'territories',PLATFORM:'platforms',START_DATE:'startDate',END_DATE:'endDateExclusive'};
      const issueResolutions=(subset?.issues??[]).map(issue=>({issueId:issue.id,field:issue.field,acknowledged:resolved.includes(issue.id),resolvedValue:fieldMap[issue.field]?finalUsage[fieldMap[issue.field]]:['DATES','DURATION'].includes(issue.field)?{startDate:finalUsage.startDate,endDateExclusive:finalUsage.endDateExclusive}:finalUsage}));
      await mutate(session,`/campaigns/${campaign.id}/revisions`,{interpretationId:subset?.id??null,baseRevisionId:campaign.latestRevision.id,origin:'MANUAL',usage:finalUsage,confirmed,resolvedIssueIds:resolved,issueResolutions,excludedRequirementsAcknowledged:excluded,userEditedFields:subset?fields.filter(field=>!equal(finalUsage[field],subset.normalizedCandidate?.[field])):fields},campaign.version);await onRefresh();onDirty(false)}
    catch(e){setError((e as Error).message)}finally{setBusy(false)}
  }
  return <details className="manual-usage"><summary>Enter usage manually</summary><form onSubmit={event=>{event.preventDefault();void save()}}><p className="fine">Manual entry — these fields are your instructions, not an AI extraction.</p><button type="button" className="secondary preset-button" disabled={campaign.paymentLocked} onClick={()=>{change({...usage,distributions:['PAID_ADS'],territories:['US','GB'],platforms:['INSTAGRAM'],startDate:campaign.anchorDate,endDateExclusive:'2027-02-13'});setTerritory('US, GB')}}>Example: paid US + UK, 90 days</button>
    <div className="form-grid"><label>Distribution<select disabled={campaign.paymentLocked} value={usage.distributions.join(',')} onChange={e=>change({...usage,distributions:e.target.value.split(',')})}><option value="ORGANIC">Organic only</option><option value="PAID_ADS">Paid advertising</option><option value="ORGANIC,PAID_ADS">Organic and paid advertising</option></select></label><label>Territories<input disabled={campaign.paymentLocked} value={territory} onChange={e=>{setTerritory(e.target.value);setConfirmed(false);setResolved([]);onDirty(true)}} maxLength={100}/><span className="fine">Comma-separated ISO codes, or WORLDWIDE. UK normalizes to GB.</span></label><label>Start date<input disabled={campaign.paymentLocked} type="date" value={usage.startDate} onChange={e=>change({...usage,startDate:e.target.value})} required/></label><label>End date (exclusive)<input disabled={campaign.paymentLocked} type="date" value={usage.endDateExclusive} onChange={e=>change({...usage,endDateExclusive:e.target.value})} required/></label></div>
    <fieldset disabled={campaign.paymentLocked}><legend>Platforms</legend>{['INSTAGRAM','YOUTUBE','WEBSITE'].map(platform=><label className="checkbox" key={platform}><input type="checkbox" checked={usage.platforms.includes(platform)} onChange={e=>change({...usage,platforms:e.target.checked?[...usage.platforms,platform]:usage.platforms.filter(value=>value!==platform)})}/>{platform}</label>)}</fieldset>
    {subset&&<div className="notice"><strong>Separate supported-use manual revision</strong><p>Resolve every issue using the exact manual fields. Any unsupported requirements must be explicitly excluded and remain in the revision record.</p>{subset.result?.unsupportedRequests.map(item=><label className="checkbox" key={item.id}><input type="checkbox" checked={excluded.includes(item.id)} onChange={e=>{setExcluded(e.target.checked?[...excluded,item.id]:excluded.filter(id=>id!==item.id));setConfirmed(false)}}/>Exclude from this permission: {item.requirement}</label>)}{(subset.issues??[]).map(issue=><label className="checkbox" key={issue.id}><input type="checkbox" checked={resolved.includes(issue.id)} onChange={e=>{setResolved(e.target.checked?[...resolved,issue.id]:resolved.filter(id=>id!==issue.id));setConfirmed(false)}}/>Resolve {issue.field} using the exact manual fields above: {issue.message}</label>)}</div>}
    <label className="checkbox confirmation"><input disabled={campaign.paymentLocked} type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>I confirm this exact usage. Unsupported uses are excluded from this merchant’s offering.</label>
    {error&&<p className="error" role="alert">{error}</p>}<div className="manual-actions"><button disabled={busy||!confirmed||campaign.paymentLocked||!!subset&&(excluded.length!==(subset.result?.unsupportedRequests.length??0)||resolved.length!==(subset.issues?.length??0))}>{busy?'Saving revision…':'Confirm manual revision'}</button><button className="secondary" type="button" onClick={()=>{setUsage(campaign.latestRevision.usage);setTerritory(campaign.latestRevision.usage.territories.join(', '));setConfirmed(false);setResolved([]);setExcluded([]);onDirty(false);setError('')}}>Discard draft</button></div></form></details>;
}

export function Permissions({campaign}:{campaign:Campaign}){
  const [grants,setGrants]=useState<Grant[]|null>(null),[error,setError]=useState('');
  useEffect(()=>{api<Grant[]>(`/campaigns/${campaign.id}/entitlements`).then(setGrants).catch(e=>setError(e.message))},[campaign.id,campaign.entitlementVersion]);
  return <section className="card permissions"><p className="eyebrow">PRESERVED PERMISSION HISTORY</p><h2>Rights already held</h2><p className="muted">Original seeded permissions stay in the ledger when an upgrade is purchased.</p>{error&&<p role="alert">{error}</p>}{!grants?<p role="status">Loading permission records…</p>:grants.map(grant=><article key={grant.id}><div><h3>{grant.assetName} · {grant.tierCode}</h3><p className={grant.status==='ACTIVE'?'coverage covered':'coverage upgrade'}>{grant.status==='ACTIVE'?'Active':'Suspended — requires review'}</p><p className="fine">{grant.scopeSnapshot.distributions.join(' + ')} · {grant.scopeSnapshot.territories.join(' + ')} · {grant.scopeSnapshot.platforms.join(' + ')}<br/>{grant.scopeSnapshot.startDate} → {grant.scopeSnapshot.endDateExclusive} (end exclusive)</p><span className="fine">{grant.source==='SEEDED'?'Seeded prior permission · no historical payment claimed':`Verified Sandbox purchase · ${money(grant.paidDeltaMinor)} additional`}</span>{grant.excludedRequirements.length>0&&<div className="notice"><strong>Excluded from this permission</strong><ul>{grant.excludedRequirements.map(item=><li key={item.id}>{item.requirement}</li>)}</ul></div>}{grant.sourcePaymentId&&<p><a href={`/receipts/${grant.sourcePaymentId}`} onClick={e=>{e.preventDefault();navigate(`/receipts/${grant.sourcePaymentId}`)}}>View verified receipt →</a></p>}</div></article>)}</section>;
}

export function Activity({campaign}:{campaign:Campaign}){
  const [events,setEvents]=useState<Array<{id:string;eventType:string;createdAt:string}>|null>(null),[error,setError]=useState('');
  useEffect(()=>{api<Array<{id:string;eventType:string;createdAt:string}>>(`/campaigns/${campaign.id}/activity`).then(setEvents).catch(e=>setError(e.message))},[campaign.id,campaign.entitlementVersion,campaign.version]);
  return <section className="card"><p className="eyebrow">CAMPAIGN ACTIVITY</p><h2>A clear record of each change</h2>{error&&<p role="alert">{error}</p>}{events?.map(event=><article className="activity-event" key={event.id}><p>{event.eventType.replaceAll('_',' ').toLowerCase()}</p><time className="fine" dateTime={event.createdAt}>{new Date(event.createdAt).toLocaleString()}</time></article>)}{!events&&<p role="status">Loading activity…</p>}</section>;
}
