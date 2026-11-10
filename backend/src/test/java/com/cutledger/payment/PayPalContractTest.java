package com.cutledger.payment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Synthetic public provider-contract fixtures. These are never exposed by the running app. */
class PayPalContractTest {
    final ObjectMapper json=new ObjectMapper();
    final PayPalContract.Expected expected=new PayPalContract.Expected(UUID.randomUUID(),UUID.randomUUID(),"test-order","test-merchant",2000);
    ObjectNode order(){
        var order=PayPalContract.createPayload(json,expected,URI.create("http://localhost:8080"));
        order.put("id","test-order").put("status","COMPLETED");
        var capture=((ObjectNode)order.path("purchase_units").get(0)).putObject("payments").putArray("captures").addObject();
        capture.put("id","test-capture").put("status","COMPLETED");capture.putObject("amount").put("currency_code","USD").put("value","20.00");
        return order;
    }
    ObjectNode capture(){
        var capture=json.createObjectNode().put("id","test-capture").put("status","COMPLETED").put("final_capture",true);
        capture.putObject("amount").put("currency_code","USD").put("value","20.00");
        capture.putObject("supplementary_data").putObject("related_ids").put("order_id","test-order");return capture;
    }
    @Test void createPayloadUsesExactUsdReferencesMerchantAndHostedReturn() {
        var payload=PayPalContract.createPayload(json,expected,URI.create("https://cutledger.example"));
        assertThat(payload.path("intent").asText()).isEqualTo("CAPTURE");assertThat(payload.path("purchase_units")).hasSize(1);
        var unit=payload.path("purchase_units").get(0);
        assertThat(unit.path("amount").path("value").asText()).isEqualTo("20.00");
        assertThat(unit.path("custom_id").asText()).isEqualTo(expected.paymentId().toString());
        assertThat(unit.path("reference_id").asText()).isEqualTo(expected.quoteId().toString());
        assertThat(unit.path("payee").path("merchant_id").asText()).isEqualTo("test-merchant");
        assertThat(payload.path("payment_source").path("paypal").path("experience_context").path("return_url").asText()).startsWith("https://cutledger.example/checkout/return?paymentId=");
        assertThat(payload.has("application_context")).isFalse();
        assertThatThrownBy(()->PayPalContract.createPayload(json,expected,URI.create("https://evil.example/path"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void independentCompletedOrderAndCaptureProduceOnlyMinimalVerifiedMetadata() {
        var record=PayPalContract.verifyCompleted(expected,order(),capture());
        assertThat(record.captureId()).isEqualTo("test-capture");assertThat(record.amountMinor()).isEqualTo(2000);assertThat(record.currency()).isEqualTo("USD");
    }
    @Test void rejectsForgedAmountsCurrencyMerchantOrderAndQuoteReferences() {
        for(String field:List.of("amount","currency","merchant","order","custom","quote","capture-id","capture-order","pending","partial")) {
            var order=order();var capture=capture();var unit=(ObjectNode)order.path("purchase_units").get(0);
            switch(field){
                case "amount"->((ObjectNode)capture.path("amount")).put("value","19.99");
                case "currency"->((ObjectNode)capture.path("amount")).put("currency_code","EUR");
                case "merchant"->((ObjectNode)unit.path("payee")).put("merchant_id","different-merchant");
                case "order"->order.put("id","different-order");
                case "custom"->unit.put("custom_id",UUID.randomUUID().toString());
                case "quote"->unit.put("reference_id",UUID.randomUUID().toString());
                case "capture-id"->capture.put("id","different-capture");
                case "capture-order"->((ObjectNode)capture.path("supplementary_data").path("related_ids")).put("order_id","different-order");
                case "pending"->capture.put("status","PENDING");
                case "partial"->capture.put("final_capture",false);
            }
            assertThatThrownBy(()->PayPalContract.verifyCompleted(expected,order,capture)).as(field).isInstanceOf(PayPalContract.VerificationFailure.class);
        }
    }
    @Test void rejectsMultipleCapturesRefundsAndNonExactDecimalAmounts() {
        var duplicate=order();var captures=(ArrayNode)duplicate.path("purchase_units").get(0).path("payments").path("captures");captures.add(captures.get(0).deepCopy());
        assertThatThrownBy(()->PayPalContract.verifyCompleted(expected,duplicate,capture())).isInstanceOf(PayPalContract.VerificationFailure.class);
        var refunded=order();((ObjectNode)refunded.path("purchase_units").get(0).path("payments")).putArray("refunds").addObject().put("id","test-refund");
        assertThatThrownBy(()->PayPalContract.verifyCompleted(expected,refunded,capture())).isInstanceOf(PayPalContract.VerificationFailure.class);
        for(String value:List.of("2e1","20.001","NaN","-20.00","200000000000000000.00")) {
            var capture=capture();((ObjectNode)capture.path("amount")).put("value",value);
            assertThatThrownBy(()->PayPalContract.verifyCompleted(expected,order(),capture)).isInstanceOf(PayPalContract.VerificationFailure.class);
        }
    }
    @Test void approvalRedirectRequiresExactSandboxHostAndOrderToken() {
        var order=json.createObjectNode().put("id","test-order");
        var link=order.putArray("links").addObject().put("rel","payer-action").put("method","GET");
        link.put("href","https://www.sandbox.paypal.com/checkoutnow?token=test-order");
        assertThat(PayPalContract.approvalUrl(order).getHost()).isEqualTo("www.sandbox.paypal.com");
        for(String url:List.of("https://www.sandbox.paypal.com.evil.example/checkoutnow?token=test-order","https://www.paypal.com/checkoutnow?token=test-order","http://www.sandbox.paypal.com/checkoutnow?token=test-order","https://www.sandbox.paypal.com/checkoutnow?token=other","https://evil@www.sandbox.paypal.com/checkoutnow?token=test-order")) {
            link.put("href",url);assertThatThrownBy(()->PayPalContract.approvalUrl(order)).isInstanceOf(PayPalContract.VerificationFailure.class);
        }
    }
}
