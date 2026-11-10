package com.cutledger.payment;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

/** Provider contract primitives; only the durable payment service may invoke mutations. */
public final class PayPalContract {
    private PayPalContract(){}
    public record Expected(UUID paymentId, UUID quoteId, String orderId, String merchantId, long amountMinor) {
        public Expected {Objects.requireNonNull(paymentId);Objects.requireNonNull(quoteId);if(merchantId==null||merchantId.isBlank()||amountMinor<=0)throw new IllegalArgumentException("Invalid payment identity");}
    }
    public record VerifiedCapture(String orderId,String captureId,String merchantId,long amountMinor,String currency) {}
    public static class VerificationFailure extends RuntimeException {
        public final String code;
        VerificationFailure(String code){super(code);this.code=code;}
    }
    public static ObjectNode createPayload(ObjectMapper json,Expected expected,URI origin) {
        if(!"https".equals(origin.getScheme()) && !("http".equals(origin.getScheme())&&Set.of("localhost","127.0.0.1").contains(origin.getHost())))
            throw new IllegalArgumentException("Invalid return origin");
        if(origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||(!origin.getPath().isEmpty()&&!origin.getPath().equals("/")))
            throw new IllegalArgumentException("Return origin must not contain a path or credentials");
        ObjectNode result=json.createObjectNode();result.put("intent","CAPTURE");
        var unit=result.putArray("purchase_units").addObject();
        unit.put("reference_id",expected.quoteId.toString());unit.put("custom_id",expected.paymentId.toString());
        unit.putObject("amount").put("currency_code","USD").put("value",BigDecimal.valueOf(expected.amountMinor,2).toPlainString());
        unit.putObject("payee").put("merchant_id",expected.merchantId);
        var experience=result.putObject("payment_source").putObject("paypal").putObject("experience_context");
        experience.put("shipping_preference","NO_SHIPPING").put("user_action","PAY_NOW");
        String base=origin.toString().replaceAll("/$","");
        experience.put("return_url",base+"/checkout/return?paymentId="+expected.paymentId);
        experience.put("cancel_url",base+"/checkout/cancel?paymentId="+expected.paymentId);
        return result;
    }
    public static URI approvalUrl(JsonNode order) {
        for(var link:order.path("links"))if(Set.of("payer-action","approve").contains(link.path("rel").asText())&&link.path("method").asText().equals("GET")) {
            try {
                URI uri=URI.create(link.path("href").asText());
                if("https".equals(uri.getScheme())&&uri.getHost()!=null&&Set.of("www.sandbox.paypal.com","sandbox.paypal.com").contains(uri.getHost())
                    &&uri.getUserInfo()==null&&uri.getFragment()==null&&uri.getPort()==-1&&uri.getPath().equals("/checkoutnow")
                    &&Objects.equals(uri.getRawQuery(),"token="+order.path("id").asText())) return uri;
            }catch(IllegalArgumentException ignored){}
        }
        throw new VerificationFailure("INVALID_APPROVAL_URL");
    }
    public static VerifiedCapture verifyCompleted(Expected expected,JsonNode order,JsonNode capture) {
        verifyCaptureIdentity(expected,order,capture);
        require(order.path("status").asText().equals("COMPLETED")&&capture.path("status").asText().equals("COMPLETED"),"CAPTURE_NOT_COMPLETED");
        require(order.path("purchase_units").get(0).path("payments").path("captures").get(0).path("status").asText().equals("COMPLETED"),"CAPTURE_NOT_COMPLETED");
        require(capture.path("final_capture").asBoolean(false),"PARTIAL_CAPTURE_UNSUPPORTED");
        JsonNode unit=order.path("purchase_units").get(0);
        require(!unit.path("payments").has("refunds")||unit.path("payments").path("refunds").isEmpty(),"REFUND_REVIEW");
        return new VerifiedCapture(expected.orderId,capture.path("id").asText(),expected.merchantId,expected.amountMinor,"USD");
    }
    public static void verifyCaptureIdentity(Expected expected,JsonNode order,JsonNode capture) {
        verifyOrderIdentity(expected,order);
        JsonNode unit=order.path("purchase_units").get(0);
        if(capture.has("payee"))require(capture.path("payee").path("merchant_id").asText().equals(expected.merchantId),"MERCHANT_MISMATCH");
        var captures=unit.path("payments").path("captures");
        require(captures.isArray()&&captures.size()==1,"CAPTURE_COUNT_MISMATCH");
        JsonNode associated=captures.get(0);
        String captureId=capture.path("id").asText();
        require(!captureId.isBlank()&&associated.path("id").asText().equals(captureId),"CAPTURE_ID_MISMATCH");
        require(capture.path("supplementary_data").path("related_ids").path("order_id").asText().equals(expected.orderId),"CAPTURE_ORDER_MISMATCH");
        checkAmount(associated.path("amount"),expected.amountMinor);checkAmount(capture.path("amount"),expected.amountMinor);
    }
    public static void verifyOrderIdentity(Expected expected,JsonNode order){
        require(expected.orderId!=null&&expected.orderId.equals(order.path("id").asText()),"ORDER_MISMATCH");
        require(order.path("intent").asText().equals("CAPTURE"),"ORDER_INTENT_MISMATCH");
        JsonNode units=order.path("purchase_units");require(units.isArray()&&units.size()==1,"PURCHASE_UNIT_MISMATCH");
        JsonNode unit=units.get(0);
        require(unit.path("reference_id").asText().equals(expected.quoteId.toString())&&unit.path("custom_id").asText().equals(expected.paymentId.toString()),"QUOTE_REFERENCE_MISMATCH");
        require(unit.path("payee").path("merchant_id").asText().equals(expected.merchantId),"MERCHANT_MISMATCH");
        checkAmount(unit.path("amount"),expected.amountMinor);
    }
    private static void checkAmount(JsonNode amount,long expected) {
        require(amount.path("currency_code").asText().equals("USD"),"CURRENCY_MISMATCH");
        String value=amount.path("value").asText();
        require(value.matches("(0|[1-9][0-9]{0,15})\\.[0-9]{2}"),"INVALID_AMOUNT_FORMAT");
        try {require(new BigDecimal(value).movePointRight(2).longValueExact()==expected,"AMOUNT_MISMATCH");}
        catch(ArithmeticException ignored){throw new VerificationFailure("AMOUNT_MISMATCH");}
    }
    private static void require(boolean condition,String code){if(!condition)throw new VerificationFailure(code);}
}
