package com.cutledger.payment;

import com.cutledger.bootstrap.*;
import com.cutledger.campaign.CampaignService;
import com.cutledger.quote.QuoteService;
import com.cutledger.web.Problem;
import com.cutledger.workspace.SessionService.Session;
import com.cutledger.workspace.SessionService;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentService {
    final JdbcTemplate db;final CampaignService campaigns;final QuoteService quotes;final DomainJson json;final Clock clock;
    private final AppConfig config;private final PaymentProvider provider;final TransactionTemplate transactions;private final SessionService sessions;
    public PaymentService(JdbcTemplate db,CampaignService campaigns,QuoteService quotes,DomainJson json,Clock clock,AppConfig config,PaymentProvider provider,TransactionTemplate transactions,SessionService sessions){
        this.db=db;this.campaigns=campaigns;this.quotes=quotes;this.json=json;this.clock=clock;this.config=config;this.provider=provider;this.transactions=transactions;this.sessions=sessions;
    }
    Timestamp now(){return Timestamp.from(clock.instant());}
    Map<String,Object> row(UUID id){var rows=db.queryForList("SELECT * FROM payment WHERE id=?",id);if(rows.isEmpty())throw Problem.notFound();return rows.get(0);}
    Map<String,Object> owned(Session session,UUID id){var rows=db.queryForList("SELECT * FROM payment WHERE id=? AND workspace_id=?",id,session.workspaceId());if(rows.isEmpty())throw Problem.notFound();return rows.get(0);}
    record Locked(Map<String,Object> campaign,Map<String,Object> quote,Map<String,Object> payment){}
    <T> T locked(UUID id,Function<Locked,T> action){
        var identity=row(id);
        return transactions.execute(tx->{
            var campaign=db.queryForMap("SELECT * FROM campaign WHERE id=? FOR UPDATE",identity.get("campaign_id"));
            var quote=db.queryForMap("SELECT * FROM quote WHERE id=? FOR UPDATE",identity.get("quote_id"));
            var payment=db.queryForMap("SELECT * FROM payment WHERE id=? FOR UPDATE",id);
            if(!payment.get("workspace_id").equals(campaign.get("workspace_id"))||!payment.get("campaign_id").equals(quote.get("campaign_id")))throw new IllegalStateException("Payment ownership invariant");
            return action.apply(new Locked(campaign,quote,payment));
        });
    }
    Session actor(Map<String,Object> payment){return new Session((UUID)payment.get("workspace_id"),(UUID)payment.get("campaign_id"),"",Instant.MAX,clock.instant());}
    PaymentState state(Map<String,Object> payment){return PaymentState.valueOf(payment.get("state").toString());}
    void transition(Map<String,Object> payment,PaymentState next,String resolution){
        transition(payment,next,resolution,"SYSTEM");
    }
    void transition(Map<String,Object> payment,PaymentState next,String resolution,String actorType){
        var previous=state(payment);if(previous==next)return;
        if(!previous.allows(next))throw new IllegalStateException("Forbidden payment transition");
        db.update("UPDATE payment SET state=?,resolution=?,version=version+1,updated_at=? WHERE id=?",next.name(),resolution,now(),payment.get("id"));
        campaigns.audit(actor(payment),(UUID)payment.get("campaign_id"),(UUID)payment.get("id"),"PAYMENT_"+next.name(),Map.of("from",previous.name(),"resolution",resolution==null?"NONE":resolution),actorType);
        payment.put("state",next.name());payment.put("resolution",resolution);
    }
    void unlock(Map<String,Object> payment){db.update("UPDATE campaign SET active_payment_id=(SELECT id FROM payment WHERE campaign_id=? AND id<>? AND state='REVIEW_REQUIRED' ORDER BY created_at LIMIT 1) WHERE id=? AND active_payment_id=?",payment.get("campaign_id"),payment.get("id"),payment.get("campaign_id"),payment.get("id"));}
    void enqueue(UUID payment,String kind){db.update("INSERT INTO payment_job VALUES (?,?,?, ?,0,NULL,NULL,NULL) ON CONFLICT(payment_id,kind) WHERE done_at IS NULL DO NOTHING",UUID.randomUUID(),payment,kind,now());}
    PayPalContract.Expected expected(Map<String,Object> p){return new PayPalContract.Expected((UUID)p.get("id"),(UUID)p.get("quote_id"),(String)p.get("provider_order_id"),p.get("expected_merchant_id").toString(),((Number)p.get("expected_amount_minor")).longValue());}

    public Map<String,Object> initialize(Session session,UUID quoteId,String hash,String match,UUID key){
        UUID id=transactions.execute(tx->{
            var qIdentity=quotes.owned(session,quoteId,false);var campaign=campaigns.ownedCampaign(session,(UUID)qIdentity.get("campaign_id"),true);var quote=quotes.owned(session,quoteId,true);
            var payload=Map.of("quoteId",quoteId,"acceptedSnapshotHash",hash);
            UUID prior=campaigns.dedup(session,"INITIALIZE_PAYMENT",key,payload);if(prior!=null)return prior;
            if(!hash.equals(quote.get("snapshot_hash")))throw new Problem(409,"QUOTE_HASH_MISMATCH","Review and accept this exact server quote.");
            var existing=db.queryForList("SELECT id FROM payment WHERE quote_id=?",quoteId);
            if(!existing.isEmpty()){UUID same=(UUID)existing.get(0).get("id");campaigns.remember(session,"INITIALIZE_PAYMENT",key,payload,same);return same;}
            campaigns.version(campaign,match);quotes.valid(campaign,quote);
            if(!quote.get("status").equals("OPEN")||campaign.get("active_payment_id")!=null)throw new Problem(409,"ACTIVE_PAYMENT","Resume the existing checkout; a replacement cannot be started.");
            if(!config.paypalConfigured())throw new Problem(503,"PAYPAL_NOT_CONFIGURED","Sandbox seller access is not configured.");
            UUID payment=UUID.randomUUID(),createId=UUID.randomUUID(),captureId=UUID.randomUUID();
            var expected=new PayPalContract.Expected(payment,quoteId,null,config.paypalMerchantId(),((Number)quote.get("total_minor")).longValue());
            var create=PayPalContract.createPayload(new com.fasterxml.jackson.databind.ObjectMapper(),expected,config.publicBaseUrl());
            db.update("""
                INSERT INTO payment(id,workspace_id,campaign_id,quote_id,state,create_request_id,capture_request_id,create_payload,create_payload_hash,capture_payload,capture_payload_hash,expected_merchant_id,expected_amount_minor,currency,created_at,updated_at)
                VALUES (?,?,?,?,'CREATING_ORDER',?,?,?::jsonb,?,'{}'::jsonb,?,?,?,'USD',?,?)
                """,payment,session.workspaceId(),campaign.get("id"),quoteId,createId,captureId,create.toString(),json.hash(create),json.hash(json.parse("{}")),config.paypalMerchantId(),quote.get("total_minor"),now(),now());
            db.update("UPDATE quote SET status='ACCEPTED' WHERE id=?",quoteId);db.update("UPDATE campaign SET active_payment_id=? WHERE id=?",payment,campaign.get("id"));
            enqueue(payment,"CREATE_ORDER");campaigns.remember(session,"INITIALIZE_PAYMENT",key,payload,payment);campaigns.audit(session,(UUID)campaign.get("id"),payment,"QUOTE_ACCEPTED",Map.of("quoteId",quoteId,"snapshotHash",hash));return payment;
        });return view(session,id);
    }
    public Map<String,Object> view(Session session,UUID id){
        var p=owned(session,id);var state=state(p);boolean fresh=clock.instant().isBefore(db.queryForObject("SELECT expires_at FROM quote WHERE id=?",Timestamp.class,p.get("quote_id")).toInstant());
        var result=new LinkedHashMap<String,Object>();result.put("paymentId",id);result.put("state",state.name());result.put("resolution",p.get("resolution"));result.put("quoteId",p.get("quote_id"));result.put("campaignId",p.get("campaign_id"));
        result.put("version",p.get("version"));
        result.put("amountMinor",p.get("expected_amount_minor"));result.put("currency","USD");result.put("environment","Sandbox");result.put("approvalUrl",state==PaymentState.AWAITING_APPROVAL&&fresh?p.get("approval_url"):null);
        result.put("providerOrderId",p.get("provider_order_id"));result.put("canCapture",state==PaymentState.AWAITING_APPROVAL&&fresh);result.put("canCancel",p.get("capture_committed_at")==null&&!state.terminal());
        result.put("receiptUrl",state==PaymentState.SETTLED?"/receipts/"+id:null);result.put("lastCheckedAt",p.get("last_verified_at"));result.put("statusUrl","/api/v1/payments/"+id);return result;
    }
    public Map<String,Object> capture(Session session,UUID id,String returnedOrderId,UUID key){
        var initial=owned(session,id);var input=Map.of("paymentId",id,"returnedOrderId",returnedOrderId);
        if(!Objects.equals(returnedOrderId,initial.get("provider_order_id")))throw new Problem(409,"ORDER_TOKEN_MISMATCH","The returned order does not match this checkout.");
        if(initial.get("capture_committed_at")!=null||state(initial).terminal())return view(session,id);
        quota(session,id,"VERIFY_APPROVAL");
        JsonNode order;
        try{order=provider.getOrder(returnedOrderId);PayPalContract.verifyOrderIdentity(expected(initial),order);}
        catch(PayPalContract.VerificationFailure failure){review(id,failure.code);throw new Problem(409,"PAYMENT_REVIEW","Provider identity requires review; no replacement checkout is allowed.");}
        catch(PayPalHttp.ProviderFailure failure){throw new Problem(503,"PAYPAL_UNAVAILABLE","Cannot verify approval yet. Resume this same checkout.");}
        if(!order.path("status").asText().equals("APPROVED"))throw new Problem(409,"BUYER_APPROVAL_REQUIRED","Complete buyer approval in PayPal Sandbox first.");
        Problem error=locked(id,locked->{
            var p=locked.payment;var prior=campaigns.dedup(session,"CAPTURE_PAYMENT",key,input);if(prior!=null||p.get("capture_committed_at")!=null)return null;
            if(state(p)!=PaymentState.AWAITING_APPROVAL)throw new Problem(409,"PAYMENT_NOT_CAPTURABLE","This checkout cannot start capture in its current state.");
            if(!Objects.equals(locked.campaign.get("active_payment_id"),id))throw new Problem(409,"PAYMENT_LOCK_MISMATCH","The campaign requires review.");
            try{quotes.valid(locked.campaign,locked.quote);}catch(Problem invalid){
                transition(p,PaymentState.CANCELLED,invalid.status==410?"EXPIRED_BEFORE_CAPTURE":"QUOTE_INVALIDATED","USER");
                db.update("UPDATE quote SET status=? WHERE id=?",invalid.status==410?"EXPIRED":"INVALIDATED",p.get("quote_id"));unlock(p);return invalid;
            }
            db.update("UPDATE payment SET capture_committed_at=? WHERE id=?",now(),id);transition(p,PaymentState.CAPTURE_COMMITTED,null,"USER");enqueue(id,"CAPTURE");campaigns.remember(session,"CAPTURE_PAYMENT",key,input,id);return null;
        });
        if(error!=null)throw error;return view(session,id);
    }
    public Map<String,Object> cancel(Session session,UUID id,UUID key){
        owned(session,id);locked(id,l->{var p=l.payment;var payload=Map.of("paymentId",id);
            UUID prior=campaigns.dedup(session,"CANCEL_PAYMENT",key,payload);if(prior!=null)return null;
            if(p.get("capture_committed_at")!=null)throw new Problem(409,"CAPTURE_IN_PROGRESS","Capture is committed. Keep checking this same payment.");
            if(state(p)==PaymentState.CANCELLED)return null;
            if(state(p).terminal())throw new Problem(409,"PAYMENT_CLOSED","This payment is already closed or requires review.");
            transition(p,PaymentState.CANCELLED,"BUYER_CANCELLED","USER");db.update("UPDATE quote SET status='CLOSED' WHERE id=?",p.get("quote_id"));unlock(p);campaigns.remember(session,"CANCEL_PAYMENT",key,payload,id);return null;
        });return view(session,id);
    }
    private void quota(Session session,UUID id,String operation){
        if(!sessions.takeQuota(session.workspaceId()+":"+id,operation,6,Duration.ofMinutes(1)))throw new Problem(429,"PAYMENT_CHECK_LIMIT","Please wait a minute before checking this same payment again.");
    }
    public Map<String,Object> reconcile(Session session,UUID id,UUID key){owned(session,id);var payload=Map.of("paymentId",id);if(campaigns.dedup(session,"RECONCILE_PAYMENT",key,payload)!=null)return view(session,id);quota(session,id,"RECONCILE_PAYMENT");locked(id,l->{if(campaigns.dedup(session,"RECONCILE_PAYMENT",key,payload)!=null)return null;enqueue(id,"VERIFY");campaigns.remember(session,"RECONCILE_PAYMENT",key,payload,id);return null;});return view(session,id);}
    void review(UUID id,String reason){locked(id,l->{var p=l.payment;
        if(state(p)==PaymentState.SETTLED){db.update("UPDATE entitlement_state SET status='SUSPENDED_REVIEW',reason=?,changed_at=?,version=version+1 WHERE entitlement_id IN (SELECT id FROM entitlement WHERE source_payment_id=?)",reason,now(),id);db.update("UPDATE campaign SET entitlement_version=entitlement_version+1 WHERE id=?",p.get("campaign_id"));}
        if(state(p)==PaymentState.CREATING_ORDER)transition(p,PaymentState.RECONCILING,reason);
        if(!state(p).terminal()||state(p)==PaymentState.SETTLED)transition(p,PaymentState.REVIEW_REQUIRED,reason);
        if(state(p)==PaymentState.REVIEW_REQUIRED)db.update("UPDATE campaign SET active_payment_id=? WHERE id=? AND active_payment_id IS NULL",id,p.get("campaign_id"));return null;
    });}
    void fulfill(UUID id,PayPalContract.VerifiedCapture verified){locked(id,l->{
        var p=l.payment;if(state(p)==PaymentState.SETTLED){if(!Objects.equals(p.get("provider_capture_id"),verified.captureId()))throw new PayPalContract.VerificationFailure("CAPTURE_ID_MISMATCH");db.update("UPDATE payment SET last_verified_at=? WHERE id=?",now(),id);return null;}
        if(p.get("capture_committed_at")==null||!Objects.equals(l.campaign.get("active_payment_id"),id))throw new IllegalStateException("Uncommitted capture cannot fulfill");
        if(state(p).terminal())throw new IllegalStateException("Closed payment cannot fulfill");
        quotes.bindingValid(l.campaign,l.quote,false);
        var identity=expected(p);if(!verified.orderId().equals(identity.orderId())||!verified.merchantId().equals(identity.merchantId())||verified.amountMinor()!=identity.amountMinor()||!verified.currency().equals("USD"))throw new IllegalStateException("Verified identity mismatch");
        var lines=quotes.lines((UUID)p.get("quote_id"));long sum=0;for(var line:lines)sum=Math.addExact(sum,((Number)line.get("delta_minor")).longValue());
        if(sum!=identity.amountMinor()||lines.isEmpty())throw new IllegalStateException("Quote line total mismatch");
        String terms=db.queryForObject("SELECT terms_hash FROM policy_version WHERE id=?",String.class,l.quote.get("policy_version_id"));
        for(var line:lines){
            UUID grant=UUID.randomUUID();
            db.update("""
                INSERT INTO entitlement(id,campaign_id,asset_id,policy_version_id,tier_code,scope_snapshot,credited_minor,paid_delta_minor,currency,source,source_payment_id,accepted_revision_id,predecessor_id,terms_hash,created_at)
                VALUES (?,?,?,?,?,?::jsonb,?,?,'USD','PAYPAL_CAPTURE',?,?,?,?,?)
                """,grant,p.get("campaign_id"),line.get("asset_id"),l.quote.get("policy_version_id"),line.get("target_tier"),line.get("purchased_scope").toString(),line.get("target_cumulative_minor"),line.get("delta_minor"),id,l.quote.get("revision_id"),line.get("prior_grant_id"),terms,now());
            db.update("INSERT INTO entitlement_state VALUES (?,'ACTIVE',NULL,?,1)",grant,now());campaigns.audit(actor(p),(UUID)p.get("campaign_id"),id,"PERMISSION_ISSUED",Map.of("grantId",grant,"assetId",line.get("asset_id"),"revisionId",l.quote.get("revision_id")),"SYSTEM");
        }
        db.update("UPDATE campaign SET entitlement_version=entitlement_version+1 WHERE id=?",p.get("campaign_id"));db.update("UPDATE quote SET status='FULFILLED' WHERE id=?",p.get("quote_id"));
        db.update("UPDATE payment SET provider_capture_id=?,last_verified_at=?,verification_snapshot=?::jsonb WHERE id=?",verified.captureId(),now(),json.write(verified),id);
        transition(p,PaymentState.SETTLED,null);unlock(p);return null;
    });}
    public Map<String,Object> receipt(Session session,UUID id){var p=owned(session,id);if(state(p)!=PaymentState.SETTLED)throw new Problem(409,"PAYMENT_NOT_SETTLED","Permissions are issued only after server verification.");
        String capture=p.get("provider_capture_id").toString();return Map.of("payment",view(session,id),"captureReference","…"+capture.substring(Math.max(0,capture.length()-4)),"quote",quotes.view(session,(UUID)p.get("quote_id")),"grants",db.queryForList("SELECT id,asset_id AS \"assetId\",tier_code AS \"tierCode\",scope_snapshot::text AS \"scopeSnapshot\",paid_delta_minor AS \"paidDeltaMinor\",accepted_revision_id AS \"acceptedRevisionId\" FROM entitlement WHERE source_payment_id=? ORDER BY asset_id",id));
    }
}
