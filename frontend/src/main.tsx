import {StrictMode,useEffect,useRef,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {api,ApiError,navigate} from './api';
import type {Campaign,Diagnostics,Session} from './api';
import {Activity,Permissions,Workspace} from './features/campaign/Workspace';
import {Checkout,ReceiptPage} from './features/checkout/Checkout';
import './style.css';

function App(){
  const [session,setSession]=useState<Session|null>(null),[campaign,setCampaign]=useState<Campaign|null>(null);
  const [diagnostics,setDiagnostics]=useState<Diagnostics|null>(null),[loading,setLoading]=useState(true),[busy,setBusy]=useState(false);
  const [error,setError]=useState(''),[code,setCode]=useState(''),[route,setRoute]=useState(location.pathname+location.search);
  const generation=useRef(0);const activeRoute=useRef(location.pathname+location.search);
  async function load(){
    const request=++generation.current;setError('');setLoading(true);
    try{
      const status=await api<Diagnostics>('/diagnostics');if(request!==generation.current)return;setDiagnostics(status);
      try{
        const current=await api<Session>('/session');if(request!==generation.current)return;setSession(current);
        const match=location.pathname.match(/^\/campaigns\/([^/]+)$/);
        const result=await api<Campaign>(`/campaigns/${match?.[1]??current.campaignId}`);if(request!==generation.current)return;setCampaign(result);
      }catch(e){if(!(e instanceof ApiError)||e.status!==401)throw e;setSession(null);setCampaign(null)}
    }catch(e){if(request===generation.current)setError((e as Error).message)}finally{if(request===generation.current)setLoading(false)}
  }
  useEffect(()=>{void load();function changed(event:Event){if(event.type==='popstate'&&!dispatchEvent(new Event('cl-before-navigate',{cancelable:true}))){history.pushState({},'',activeRoute.current);return}activeRoute.current=location.pathname+location.search;setRoute(activeRoute.current);void load()}addEventListener('popstate',changed);addEventListener('cl-route-change',changed);return()=>{removeEventListener('popstate',changed);removeEventListener('cl-route-change',changed)}},[]);

  async function open(){
    if(session){navigate(`/campaigns/${session.campaignId}`);return}
    setBusy(true);setError('');
    try{const current=await api<Session>('/demo-sessions',{method:'POST',body:JSON.stringify({accessCode:code})});setCode('');setSession(current);navigate(`/campaigns/${current.campaignId}`)}
    catch(e){setError((e as Error).message)}finally{setBusy(false)}
  }
  const tab=new URLSearchParams(route.split('?')[1]).get('tab')??'workspace';
  const pathname=route.split('?')[0];const query=new URLSearchParams(route.split('?')[1]);
  const checkout=pathname.startsWith('/checkout/');
  const returned=pathname==='/checkout/return'||pathname==='/checkout/cancel';
  const paymentId=returned?query.get('paymentId'):pathname.split('/')[2];
  const landing=pathname==='/'||!campaign||!session;
  return <><a className="skip" href="#main">Skip to content</a><header><a className="brand" href="/" onClick={event=>{event.preventDefault();navigate('/')}}><span className="mark" aria-hidden="true">C<span>↗</span></span>CutLedger</a><div className="header-right"><span className="sandbox">PayPal Sandbox</span><span className="studio">CutLedger Studio</span></div></header>
    <main id="main" className={landing?'landing':''}>
      {error&&<div className="error" role="alert"><span>{error}</span><button className="secondary" onClick={()=>void load()}>Retry</button></div>}
      {loading?<div className="skeleton" role="status">Loading your campaign…</div>:landing?<section className="welcome"><p className="eyebrow">A little more reach. Only the rights you need.</p><h1>New brief.<br/>Same assets.<br/><em>Just the difference.</em></h1><p className="lead">When your campaign changes, find and buy only the additional media rights it needs.</p>{pathname!=='/'&&!session&&<p className="notice">This record requires its original browser workspace. A payment ID does not grant access.</p>}<div className="welcome-bottom"><form onSubmit={e=>{e.preventDefault();void open()}}>{!session&&<><label htmlFor="access">{diagnostics?.accessRequired?'Private demo access code':<>Demo access code <span className="muted">(not required locally)</span></>}</label><input id="access" type="password" value={code} onChange={e=>setCode(e.target.value)} autoComplete="off"/></>}<button disabled={busy||!!error}>{busy?'Opening campaign…':session?'Resume campaign →':'Open sample campaign →'}</button></form><p className="fine">Three original assets. Seeded prior permissions.<br/>Sandbox only · No real funds.</p></div></section>:checkout?paymentId?<Checkout session={session} campaign={campaign} paymentId={paymentId} returnToken={query.get('token')} cancelReturn={pathname==='/checkout/cancel'}/>:<div className="error" role="alert">Missing payment identity. Reopen the checkout from your campaign.</div>:pathname.startsWith('/receipts/')?<ReceiptPage campaign={campaign} paymentId={pathname.split('/')[2]}/>:<>
        <div className="breadcrumb">Campaigns <span>/</span> Northstar Launch</div><div className="page-heading"><div><p className="eyebrow">CAMPAIGN WORKSPACE</p><h1>{campaign.title}</h1><p className="muted">Your visuals stay. Your permissions keep up.</p></div><div className="revision">Revision {campaign.latestRevision.sequence}<span>Anchor · Nov 15, 2026</span></div></div>
        <nav className="tabs" aria-label="Campaign sections">{[['workspace','Workspace'],['permissions','Permissions'],['activity','Activity']].map(([key,label])=><button key={key} onClick={()=>navigate(`/campaigns/${campaign.id}?tab=${key}`)} aria-current={tab===key?'page':undefined}>{label}</button>)}</nav>
        {tab==='permissions'?<Permissions campaign={campaign}/>:tab==='activity'?<Activity campaign={campaign}/>:<Workspace campaign={campaign} session={session} diagnostics={diagnostics} onRefresh={load}/>}
      </>}
      <footer><span>CutLedger · Rights for the next revision.</span><span>Merchant-defined demo policies · Sandbox only</span></footer>
    </main></>;
}
createRoot(document.getElementById('root')!).render(<StrictMode><App/></StrictMode>);
