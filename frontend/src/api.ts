export type Session={workspaceId:string;campaignId:string;csrfToken:string;expiresAt:string;serverTime:string};
export type Diagnostics={environment:string;payments:string;interpretation:string;checkoutAvailable:boolean;aiAvailable:boolean;accessRequired:boolean};
export type Usage={schemaVersion:1;distributions:string[];territories:string[];platforms:string[];startDate:string;endDateExclusive:string};
export type Asset={id:string;slug:string;name:string;previewPath:string;heldTier:string;source:string;scope:string};
export type Campaign={id:string;title:string;anchorDate:string;version:number;entitlementVersion:number;latestRevision:{id:string;sequence:number;origin:string;usage:Usage;confirmationEvidence:{excludedRequirements?:Array<{id:string;requirement:string}>}};assets:Asset[];paymentLocked:boolean;activePaymentId:string|null;latestInterpretationId:string|null};
export type RightsRow={assetId:string;assetSlug:string;assetName:string;previewPath:string;status:string;priorGrantId:string|null;heldTier:string|null;targetTier:string|null;priorCreditMinor:number;targetCumulativeMinor:number;deltaMinor:number;reasonCodes:string[];clauseIds:string[];heldScope:Usage|null;requestedScope:Usage;policyVersionId:string};
export type Assessment={revisionId:string;canQuote:boolean;currency:string;totalDeltaMinor:number;rows:RightsRow[];paymentLocked:boolean;policyVersionId:string};
export type QuoteLine={assetId:string;targetTier:string;priorCreditMinor:number;targetCumulativeMinor:number;deltaMinor:number;purchasedScope:Usage;clauseIds:string[]};
export type Quote={excludedRequirements:Array<{id:string;requirement:string}>;id:string;campaignId:string;revisionId:string;campaignVersion:number;entitlementVersion:number;totalMinor:number;currency:string;snapshotHash:string;status:string;expiresAt:string;serverTime:string;lines:QuoteLine[]};
export type Payment={paymentId:string;campaignId:string;quoteId:string;state:string;version:number;resolution:string|null;amountMinor:number;currency:string;approvalUrl:string|null;providerOrderId:string|null;canCapture:boolean;canCancel:boolean;receiptUrl:string|null;lastCheckedAt:string|null};
export type Grant={id:string;assetId:string;assetName:string;tierCode:string;scopeSnapshot:Usage;source:string;sourcePaymentId:string|null;paidDeltaMinor:number;status:string;createdAt:string;acceptedRevisionId:string;excludedRequirements:Array<{id:string;requirement:string}>};
export type Receipt={excludedRequirements:Array<{id:string;requirement:string}>;payment:Payment;captureReference:string;quote:Quote;grants:Array<{id:string;assetId:string;tierCode:string;paidDeltaMinor:number;scopeSnapshot:string}>};
export class ApiError extends Error {constructor(public status:number,public code:string,message:string){super(message)}}
export async function api<T>(path:string,init?:RequestInit):Promise<T>{
  let response:Response;
  try{response=await fetch(`/api/v1${path}`,{...init,credentials:'same-origin',headers:{'Content-Type':'application/json',...init?.headers}})}
  catch{throw new ApiError(0,'OFFLINE','Cannot reach CutLedger. Check your connection, then retry.')}
  if(!response.ok){const problem=await response.json().catch(()=>({}));throw new ApiError(response.status,problem.code??'UNAVAILABLE',problem.detail??'Could not load this record. Try again.')}
  return response.json() as Promise<T>;
}
export function money(minor:number){return new Intl.NumberFormat('en-US',{style:'currency',currency:'USD'}).format(minor/100)}
export function navigate(url:string){if(!dispatchEvent(new Event('cl-before-navigate',{cancelable:true})))return;history.pushState({},'',url);dispatchEvent(new Event('cl-route-change'))}
export function mutate<T>(session:Session,path:string,body:unknown,version?:number,intent='default'){
  const cache=`cl-request:${session.workspaceId}:${path}:${intent}:${JSON.stringify(body)}`;
  let key=sessionStorage.getItem(cache);
  if(!key){key=crypto.randomUUID();sessionStorage.setItem(cache,key)}
  return api<T>(path,{method:'POST',body:JSON.stringify(body),headers:{'X-CSRF-Token':session.csrfToken,'Idempotency-Key':key,...(version===undefined?{}:{'If-Match':`"${version}"`})}});
}
