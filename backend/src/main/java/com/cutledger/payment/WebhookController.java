package com.cutledger.payment;

import com.cutledger.bootstrap.AppConfig;
import com.cutledger.web.Problem;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {
    private final PaymentService payments;private final PaymentProvider provider;private final AppConfig config;
    public WebhookController(PaymentService payments,PaymentProvider provider,AppConfig config){this.payments=payments;this.provider=provider;this.config=config;}
    @PostMapping("/api/v1/webhooks/paypal") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void webhook(@RequestBody JsonNode event,HttpServletRequest request){
        if(!AppConfig.present(config.paypalWebhookId()))throw new Problem(503,"WEBHOOK_NOT_CONFIGURED","Webhook authentication is not configured.");
        var verification=new LinkedHashMap<String,Object>();
        for(String name:List.of("auth_algo","cert_url","transmission_id","transmission_sig","transmission_time")){
            String header="PAYPAL-"+name.replace('_','-').toUpperCase(Locale.ROOT);var values=Collections.list(request.getHeaders(header));String value=request.getHeader(header);
            if(values.size()!=1||value==null||value.isBlank()||value.length()>8192)throw new Problem(400,"INVALID_WEBHOOK_HEADERS","Webhook signature headers are missing or invalid.");verification.put(name,value);
        }
        String eventId=event.path("id").asText(),type=event.path("event_type").asText(),resource=event.path("resource").path("id").asText();
        if(eventId.isBlank()||eventId.length()>100||type.length()>100||!type.matches("[A-Z][A-Z0-9_.]+")||resource.length()>100)throw new Problem(400,"INVALID_WEBHOOK","Malformed webhook metadata.");
        verification.put("webhook_id",config.paypalWebhookId());verification.put("webhook_event",event);
        JsonNode response;
        try{response=provider.verifyWebhook(payments.json.write(verification));}catch(PayPalHttp.ProviderFailure failure){throw new Problem(503,"WEBHOOK_VERIFICATION_UNAVAILABLE","Signature verification is unavailable; retry delivery.");}
        if(!response.path("verification_status").asText().equals("SUCCESS"))throw new Problem(400,"WEBHOOK_SIGNATURE_REJECTED","Webhook signature was not verified.");
        String order=type.startsWith("CHECKOUT.ORDER.")?resource:event.path("resource").path("supplementary_data").path("related_ids").path("order_id").asText();
        if(order.length()>100)throw new Problem(400,"INVALID_WEBHOOK","Invalid related order identity.");
        payments.transactions.execute(tx->{
            String hash=payments.json.hash(event);
            String status=event.path("resource").path("status").asText();if(!status.matches("[A-Z_]{1,40}"))status="UNKNOWN";
            int inserted=payments.db.update("INSERT INTO webhook_event VALUES (?,?,?,?,?,?,?::jsonb,'VERIFIED',NULL) ON CONFLICT(provider_event_id) DO NOTHING",eventId,type,resource,payments.now(),payments.now(),hash,payments.json.write(Map.of("id",resource,"orderId",order,"status",status)));
            if(inserted==0){if(!hash.equals(payments.db.queryForObject("SELECT payload_hash FROM webhook_event WHERE provider_event_id=?",String.class,eventId)))throw new Problem(400,"WEBHOOK_EVENT_CONFLICT","The event identity was reused with different content.");return null;}
            var matching=payments.db.queryForList("SELECT id FROM payment WHERE provider_order_id=? OR provider_capture_id=?",order,resource);
            for(var row:matching)payments.enqueue((UUID)row.get("id"),"VERIFY");return null;
        });
    }
}
