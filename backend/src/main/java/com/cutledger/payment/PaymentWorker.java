package com.cutledger.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.cutledger.web.Problem;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentWorker {
    private final PaymentService payments;private final PaymentProvider provider;
    private final String owner=UUID.randomUUID().toString();private final boolean enabled;
    public PaymentWorker(PaymentService payments,PaymentProvider provider,@Value("${cutledger.jobs.enabled:true}") boolean enabled){this.payments=payments;this.provider=provider;this.enabled=enabled;}
    @Scheduled(fixedDelay=1000) public void scheduled(){if(enabled)drainOne();}
    public boolean drainOne(){
        var job=payments.transactions.execute(tx->{
            var rows=payments.db.queryForList("""
                SELECT * FROM payment_job WHERE done_at IS NULL AND run_after<=? AND (lease_until IS NULL OR lease_until<?)
                ORDER BY run_after,id FOR UPDATE SKIP LOCKED LIMIT 1
                """,payments.now(),payments.now());
            if(rows.isEmpty())return null;var row=rows.get(0);
            payments.db.update("UPDATE payment_job SET lease_owner=?,lease_until=?,attempts=attempts+1 WHERE id=?",owner,Timestamp.from(payments.clock.instant().plusSeconds(60)),row.get("id"));return row;
        });
        if(job==null)return false;
        UUID id=(UUID)job.get("payment_id");String kind=job.get("kind").toString();boolean retry=false;
        try{retry=switch(kind){case "CREATE_ORDER"->{create(id);yield false;}case "CAPTURE"->{capture(id);yield false;}case "VERIFY"->verify(id);default->throw new IllegalStateException("Unknown durable operation");};}
        catch(PayPalContract.VerificationFailure mismatch){payments.review(id,mismatch.code);}
        catch(Problem invalidBinding){payments.review(id,invalidBinding.code);}
        catch(PayPalHttp.ProviderFailure unavailable){
            payments.db.update("UPDATE payment_operation SET completed_at=?,result_class='PROVIDER_UNAVAILABLE',http_status=?,sanitized_error=? WHERE payment_id=? AND kind=? AND attempt_no=1",payments.now(),unavailable.httpStatus,unavailable.safeCode,id,kind);
            if(kind.equals("CREATE_ORDER")){
                if(Set.of(400,401,403,404,405,422).contains(unavailable.httpStatus))fail(id,unavailable.safeCode);
                else uncertain(id,"CREATE_OUTCOME_UNKNOWN");
            }else if(kind.equals("CAPTURE"))uncertain(id,"CAPTURE_OUTCOME_UNKNOWN");
            else retry=true;
        }
        catch(RuntimeException failure){
            // A local commit can fail after provider capture. Retain identity/lock and re-read.
            if(kind.equals("VERIFY"))retry=true;else uncertain(id,"LOCAL_RECOVERY_REQUIRED");
        }
        if(retry){
            int attempts=((Number)job.get("attempts")).intValue()+1;
            long seconds=attempts==1?5:attempts==2?15:attempts==3?30:attempts==4?60:300;
            payments.db.update("UPDATE payment_job SET run_after=?,lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?",Timestamp.from(payments.clock.instant().plusSeconds(seconds)),job.get("id"),owner);
        }else payments.db.update("UPDATE payment_job SET done_at=?,lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?",payments.now(),job.get("id"),owner);
        return true;
    }
    private boolean claimMutation(UUID id,String kind){return payments.locked(id,l->{
        var p=l.payment();var state=payments.state(p);
        if(kind.equals("CREATE_ORDER")&&state!=PaymentState.CREATING_ORDER)return false;
        if(kind.equals("CAPTURE")&&(p.get("capture_committed_at")==null||state!=PaymentState.CAPTURE_COMMITTED))return false;
        int inserted=payments.db.update("INSERT INTO payment_operation VALUES (?,?,?,?,1,?,NULL,'DISPATCH_CLAIMED',NULL,NULL,NULL) ON CONFLICT(payment_id,kind,attempt_no) DO NOTHING",UUID.randomUUID(),id,kind,p.get(kind.equals("CREATE_ORDER")?"create_request_id":"capture_request_id"),payments.now());
        if(inserted==0)return false;
        String field=kind.equals("CREATE_ORDER")?"first_create_sent_at":"first_capture_sent_at";
        payments.db.update("UPDATE payment SET "+field+"=? WHERE id=?",payments.now(),id);return true;
    });}
    private void create(UUID id){
        if(!claimMutation(id,"CREATE_ORDER")){var p=payments.row(id);if(!payments.state(p).terminal()&&p.get("provider_order_id")==null)uncertain(id,"CREATE_DISPATCH_ALREADY_CLAIMED");return;}
        var p=payments.row(id);String payload=p.get("create_payload").toString();
        if(!p.get("create_payload_hash").equals(payments.json.hash(payments.json.parse(payload))))throw new PayPalContract.VerificationFailure("CREATE_PAYLOAD_MISMATCH");
        JsonNode result=provider.createOrder((UUID)p.get("create_request_id"),payload);
        String order=result.path("id").asText();if(order.isBlank()||order.length()>100)throw new PayPalContract.VerificationFailure("INVALID_ORDER_RESPONSE");
        String approval=PayPalContract.approvalUrl(result).toString();
        payments.locked(id,l->{
            var current=l.payment();payments.db.update("UPDATE payment SET provider_order_id=?,approval_url=? WHERE id=?",order,approval,id);
            if(payments.state(current)==PaymentState.CREATING_ORDER||payments.state(current)==PaymentState.RECONCILING)payments.transition(current,PaymentState.AWAITING_APPROVAL,null);
            completeOperation(id,"CREATE_ORDER");return null;
        });
    }
    private void capture(UUID id){
        if(!claimMutation(id,"CAPTURE")){var p=payments.row(id);if(p.get("capture_committed_at")!=null&&!payments.state(p).terminal())uncertain(id,"CAPTURE_DISPATCH_ALREADY_CLAIMED");return;}
        var p=payments.row(id);String payload=p.get("capture_payload").toString();
        if(!p.get("capture_payload_hash").equals(payments.json.hash(payments.json.parse(payload))))throw new PayPalContract.VerificationFailure("CAPTURE_PAYLOAD_MISMATCH");
        provider.captureOrder(p.get("provider_order_id").toString(),(UUID)p.get("capture_request_id"),payload);
        payments.locked(id,l->{completeOperation(id,"CAPTURE");if(payments.state(l.payment())==PaymentState.CAPTURE_COMMITTED)payments.transition(l.payment(),PaymentState.RECONCILING,"VERIFYING_CAPTURE");payments.enqueue(id,"VERIFY");return null;});
    }
    private void completeOperation(UUID id,String kind){payments.db.update("UPDATE payment_operation SET completed_at=?,result_class='ACKNOWLEDGED' WHERE payment_id=? AND kind=? AND attempt_no=1",payments.now(),id,kind);}
    private void uncertain(UUID id,String reason){payments.locked(id,l->{
        var p=l.payment();if(!payments.state(p).terminal()){
            if(payments.state(p)==PaymentState.CREATING_ORDER||payments.state(p)==PaymentState.CAPTURE_COMMITTED||payments.state(p)==PaymentState.CAPTURE_PENDING)payments.transition(p,PaymentState.RECONCILING,reason);
            payments.enqueue(id,"VERIFY");
        }return null;
    });}
    private void fail(UUID id,String reason){payments.locked(id,l->{var p=l.payment();if(!payments.state(p).terminal()){payments.transition(p,PaymentState.FAILED,reason);payments.db.update("UPDATE quote SET status='CLOSED' WHERE id=?",p.get("quote_id"));payments.unlock(p);}return null;});}
    private boolean verify(UUID id){
        var p=payments.row(id);var state=payments.state(p);
        if(state==PaymentState.CANCELLED||state==PaymentState.FAILED||state==PaymentState.REVIEW_REQUIRED)return false;
        if(p.get("provider_order_id")==null){payments.review(id,"IDEMPOTENCY_WINDOW_UNKNOWN");return false;}
        if(Duration.between(((Timestamp)p.get("created_at")).toInstant(),payments.clock.instant()).compareTo(Duration.ofHours(24))>0&&state!=PaymentState.SETTLED){payments.review(id,"RECONCILIATION_LIMIT");return false;}
        var expected=payments.expected(p);JsonNode order=provider.getOrder(expected.orderId());PayPalContract.verifyOrderIdentity(expected,order);
        var captures=order.path("purchase_units").get(0).path("payments").path("captures");
        if(!captures.isArray()||captures.isEmpty()){
            if(p.get("capture_committed_at")==null)return false;
            return true;
        }
        if(captures.size()!=1)throw new PayPalContract.VerificationFailure("CAPTURE_COUNT_MISMATCH");
        JsonNode capture=provider.getCapture(captures.get(0).path("id").asText());PayPalContract.verifyCaptureIdentity(expected,order,capture);
        String status=capture.path("status").asText();String orderCaptureStatus=captures.get(0).path("status").asText();
        if(Set.of("REFUNDED","PARTIALLY_REFUNDED","REVERSED").contains(status)){
            payments.review(id,status);return false;
        }
        if(status.equals("COMPLETED")&&orderCaptureStatus.equals("COMPLETED")&&order.path("status").asText().equals("COMPLETED")){
            if(p.get("capture_committed_at")==null){payments.review(id,"UNCOMMITTED_PROVIDER_CAPTURE");return false;}
            payments.fulfill(id,PayPalContract.verifyCompleted(expected,order,capture));return false;
        }
        if(Set.of("DENIED","DECLINED","FAILED").contains(status)&&status.equals(orderCaptureStatus)){
            if(state==PaymentState.SETTLED)payments.review(id,"CAPTURE_REVERSED");else fail(id,"DECLINED");return false;
        }
        payments.locked(id,l->{var current=l.payment();if(payments.state(current)!=PaymentState.SETTLED&&current.get("capture_committed_at")!=null){if(status.equals("PENDING"))payments.transition(current,PaymentState.CAPTURE_PENDING,"PROVIDER_PENDING");payments.db.update("UPDATE payment SET last_verified_at=? WHERE id=?",payments.now(),id);}return null;});
        return state!=PaymentState.SETTLED;
    }
    @Scheduled(fixedDelay=60000) public void checkRecentSettlements(){if(!enabled)return;
        for(var row:payments.db.queryForList("SELECT id FROM payment WHERE state='SETTLED' AND created_at>? AND last_verified_at<? LIMIT 10",Timestamp.from(payments.clock.instant().minus(Duration.ofDays(45))),Timestamp.from(payments.clock.instant().minusSeconds(300))))payments.enqueue((UUID)row.get("id"),"VERIFY");
    }
}
