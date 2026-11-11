import {useEffect,useState} from 'react';
import {ApiError,api,money,mutate,navigate} from '../../api';
import type {Campaign,Payment,Quote,Receipt,Session} from '../../api';

const terminal=new Set(['SETTLED','FAILED','CANCELLED','REVIEW_REQUIRED']);
const messages:Record<string,string>={CREATING_ORDER:'Preparing your genuine PayPal Sandbox order.',AWAITING_APPROVAL:'Awaiting buyer approval and your explicit capture confirmation.',CAPTURE_COMMITTED:'Capture is committed. Your server is completing this same payment.',CAPTURE_PENDING:'PayPal reports a pending capture. No permission has been issued.',RECONCILING:'Payment is being checked. Do not start another checkout.',SETTLED:'Payment verified. Your new permissions are in the ledger.',FAILED:'The payment was conclusively declined or could not be created. No new permission was issued.',CANCELLED:'Checkout cancelled before capture commitment. No new permission was issued.',REVIEW_REQUIRED:'This payment requires review. Replacement checkout remains blocked.'};

export function Checkout({session,campaign,paymentId,returnToken,cancelReturn}:{session:Session;campaign:Campaign;paymentId:string;returnToken:string|null;cancelReturn:boolean}){
  const [payment,setPayment]=useState<Payment|null>(null),[quote,setQuote]=useState<Quote|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[offline,setOffline]=useState(false),[poll,setPoll]=useState(0);
  const [loadError,setLoadError]=useState('');
  useEffect(()=>{
    let stopped=false;let timer:ReturnType<typeof setTimeout>|undefined;
    async function load(){
      if(document.hidden)return;
      try{
        const current=await api<Payment>(`/payments/${paymentId}`);if(stopped)return;setPayment(previous=>!previous||current.version>=previous.version?current:previous);setOffline(false);setLoadError('');
        const snapshot=await api<Quote>(`/quotes/${current.quoteId}`);if(stopped)return;setQuote(snapshot);
        if(!terminal.has(current.state))timer=setTimeout(()=>void load(),current.state==='AWAITING_APPROVAL'?10000:2000);
      }catch(e){if(stopped)return;setLoadError((e as Error).message);setOffline(true);if(!(e instanceof ApiError)||e.status===0||e.status>=500)timer=setTimeout(()=>void load(),10000)}
    }
    function visibility(){if(timer)clearTimeout(timer);if(!document.hidden)void load()}
    void load();document.addEventListener('visibilitychange',visibility);
    return()=>{stopped=true;if(timer)clearTimeout(timer);document.removeEventListener('visibilitychange',visibility)};
  },[paymentId,poll]);
  async function capture(){
    if(!payment?.providerOrderId)return;setBusy(true);setError('');
    try{const result=await mutate<Payment>(session,`/payments/${paymentId}/capture`,{returnedOrderId:returnToken??payment.providerOrderId});setPayment(previous=>!previous||result.version>=previous.version?result:previous);setPoll(value=>value+1)}
    catch(e){setError((e as Error).message)}finally{setBusy(false)}
  }
  async function cancel(){setBusy(true);setError('');try{setPayment(await mutate<Payment>(session,`/payments/${paymentId}/cancel`,{}));setPoll(value=>value+1)}catch(e){setError((e as Error).message)}finally{setBusy(false)}}
  return <div className="checkout-layout"><section className="card checkout-main"><p className="eyebrow">PAYPAL SANDBOX · NO REAL FUNDS</p><h1>{cancelReturn?'Return from checkout':payment?.state==='SETTLED'?'Permissions updated':returnToken?'Confirm your capture':'Your revision checkout'}</h1>
    {(error||loadError)&&<div className="error" role="alert">{error||loadError}</div>}
    {!payment?loadError?<><button className="secondary" onClick={()=>setPoll(value=>value+1)}>Retry existing checkout</button><p><a href={`/campaigns/${campaign.id}`} onClick={e=>{e.preventDefault();navigate(`/campaigns/${campaign.id}`)}}>← Return to campaign</a></p></>:<p role="status">Loading the existing checkout…</p>:<><p className="checkout-status" role="status" aria-live="polite">{messages[payment.state]??'Loading provider status.'}</p><span className={`coverage ${payment.state==='SETTLED'?'covered':'upgrade'}`}>{payment.state.replaceAll('_',' ')}</span>
    {payment.resolution&&<p className="fine">Status detail: {payment.resolution.replaceAll('_',' ').toLowerCase()}</p>}
    <p className="muted">Northstar Launch · the accepted quote and revision are fixed for this payment.</p>
    {payment.state==='AWAITING_APPROVAL'&&<div className="checkout-actions">{payment.approvalUrl&&<a className={`button-link${busy||offline?' disabled':''}`} href={busy||offline?undefined:payment.approvalUrl}>Continue to PayPal Sandbox →</a>}
      {(returnToken||!payment.approvalUrl)&&payment.canCapture?<button disabled={busy||offline} onClick={()=>void capture()}>{busy?'Committing capture…':`Confirm Sandbox capture · ${money(payment.amountMinor)}`}</button>:payment.canCapture?<details><summary>I already approved this order</summary><p className="fine">The server checks the actual PayPal order before committing capture.</p><button disabled={busy||offline} onClick={()=>void capture()}>Confirm Sandbox capture · {money(payment.amountMinor)}</button></details>:<p className="notice">The quote expired before capture commitment. Cancel this checkout and request a fresh quote.</p>}
    </div>}
    {payment.state==='SETTLED'&&<button onClick={()=>navigate(`/receipts/${paymentId}`)}>View verified permission record →</button>}
    {['CAPTURE_COMMITTED','CAPTURE_PENDING','RECONCILING'].includes(payment.state)&&<p className="notice">You can close this page. Durable reconciliation continues on the server. Refreshing does not create another payment.</p>}
    <div className="checkout-controls"><button className="secondary" disabled={busy} onClick={()=>setPoll(value=>value+1)}>Check payment status</button>{payment.canCancel&&<button className="secondary" disabled={busy||offline} onClick={()=>void cancel()}>Cancel this checkout</button>}</div>
    <p><a href={`/campaigns/${campaign.id}`} onClick={e=>{e.preventDefault();navigate(`/campaigns/${campaign.id}`)}}>← Return to campaign</a></p></>}
  </section><aside className="card summary"><p className="eyebrow">ACCEPTED SERVER QUOTE</p><h2>Only the difference.</h2>{quote?<><p className="fine">{quote.lines.length} eligible upgrades · revision {quote.campaignVersion}</p>{quote.lines.map(line=><div className="checkout-line" key={line.assetId}><h3>{campaign.assets.find(a=>a.id===line.assetId)?.name??'Campaign asset'}</h3><p className="fine">{line.targetTier} · {money(line.targetCumulativeMinor)} cumulative − {money(line.priorCreditMinor)} prior credit</p><strong>{money(line.deltaMinor)}</strong><p className="fine">Purchased scope: {line.purchasedScope.distributions.join(' + ')} · {line.purchasedScope.territories.join(' + ')}<br/>{line.purchasedScope.startDate} → {line.purchasedScope.endDateExclusive} (end exclusive)</p></div>)}{quote.excludedRequirements.length>0&&<div className="notice"><strong>Excluded from this permission</strong><ul>{quote.excludedRequirements.map(item=><li key={item.id}>{item.requirement}</li>)}</ul></div>}<div className="summary-bottom"><span>Incremental total · USD</span><strong className="quote-total">{money(quote.totalMinor)}</strong></div></>:<p role="status">Loading accepted quote…</p>}<p className="fine">Only a server-verified completed capture can issue permission records.</p></aside></div>;
}

export function ReceiptPage({campaign,paymentId}:{campaign:Campaign;paymentId:string}){
  const [receipt,setReceipt]=useState<Receipt|null>(null),[error,setError]=useState('');
  useEffect(()=>{api<Receipt>(`/payments/${paymentId}/receipt`).then(setReceipt).catch(e=>setError(e.message))},[paymentId]);
  if(error)return <section className="card"><h1>Permission record unavailable</h1><p role="alert">{error}</p><button onClick={()=>navigate(`/checkout/${paymentId}`)}>View existing payment status</button></section>;
  if(!receipt)return <section className="card" role="status">Loading verified permission record…</section>;
  return <section className="card receipt"><p className="eyebrow">VERIFIED PAYPAL SANDBOX CAPTURE</p><h1>Permissions updated.</h1><p className="lead">{receipt.grants.length} new grants. Your prior permissions stay intact.</p><div className="receipt-total"><span>Incremental amount · USD</span><strong>{money(receipt.payment.amountMinor)}</strong></div><p className="fine">Northstar Launch · revision {receipt.quote.campaignVersion}<br/>Capture reference {receipt.captureReference} · Sandbox only, no real funds.</p>{receipt.grants.map(grant=>{const scope=JSON.parse(grant.scopeSnapshot) as Quote['lines'][number]['purchasedScope'];return <article className="receipt-grant" key={grant.id}><h2>{campaign.assets.find(a=>a.id===grant.assetId)?.name} · {grant.tierCode}</h2><p>{money(grant.paidDeltaMinor)} additional · immutable permission linked to this revision</p><p className="fine">{scope.distributions.join(' + ')} · {scope.territories.join(' + ')} · {scope.platforms.join(' + ')}<br/>{scope.startDate} → {scope.endDateExclusive} (end exclusive)</p></article>})}{receipt.excludedRequirements.length>0&&<div className="notice"><strong>Excluded from this permission</strong><ul>{receipt.excludedRequirements.map(item=><li key={item.id}>{item.requirement}</li>)}</ul><p>The original full brief is not cleared by this purchase.</p></div>}<p className="notice">Covered under CutLedger Studio demo policy v1 for the purchased scope shown. This is a merchant-defined Sandbox demonstration, not general legal clearance.</p><div className="checkout-controls"><button onClick={()=>navigate(`/campaigns/${campaign.id}`)}>Return to campaign →</button><button className="secondary" onClick={()=>navigate(`/campaigns/${campaign.id}?tab=permissions`)}>View full permission history</button></div></section>;
}
