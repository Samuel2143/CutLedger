package com.cutledger.payment;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
public interface PaymentProvider {
    JsonNode createOrder(UUID durableRequestId,String immutablePayload);
    JsonNode captureOrder(String orderId,UUID durableRequestId,String immutablePayload);
    JsonNode getOrder(String orderId);
    JsonNode getCapture(String captureId);
    JsonNode verifyWebhook(String verificationBody);
}
