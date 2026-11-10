package com.cutledger.payment;

import com.cutledger.bootstrap.AppConfig;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Sandbox-only raw HTTP. No mutation retry: callers must durably claim each dispatch first. */
public final class PayPalHttp implements PaymentProvider {
    static final URI SANDBOX=URI.create("https://api-m.sandbox.paypal.com");
    private final URI origin;
    private final HttpClient http;
    private final AppConfig config;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration timeout;
    private String accessToken;
    private Instant tokenExpires=Instant.EPOCH;
    public static final class ProviderFailure extends RuntimeException {
        public final int httpStatus;
        public final String safeCode;
        public ProviderFailure(int httpStatus){this(httpStatus,"PAYPAL_UNAVAILABLE");}
        public ProviderFailure(int httpStatus,String safeCode){super("PAYPAL_UNAVAILABLE");this.httpStatus=httpStatus;this.safeCode=safeCode;}
    }
    public PayPalHttp(AppConfig config,ObjectMapper json,Clock clock) {
        this(config,json,clock,SANDBOX,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    // Package-private loopback test seam, never bound to an app property or public endpoint.
    PayPalHttp(AppConfig config,ObjectMapper json,Clock clock,URI origin,HttpClient http) {
        this(config,json,clock,origin,http,Duration.ofSeconds(20));
    }
    PayPalHttp(AppConfig config,ObjectMapper json,Clock clock,URI origin,HttpClient http,Duration timeout) {
        if(!origin.equals(SANDBOX)&&!("http".equals(origin.getScheme())&&Set.of("127.0.0.1","localhost").contains(origin.getHost())))throw new IllegalArgumentException("Sandbox origin required");
        this.config=config;this.json=json;this.clock=clock;this.origin=origin;this.http=http;this.timeout=timeout;
    }
    public JsonNode createOrder(UUID durableRequestId,String immutablePayload) {return request("POST","/v2/checkout/orders",Objects.requireNonNull(durableRequestId),Objects.requireNonNull(immutablePayload));}
    public JsonNode captureOrder(String orderId,UUID durableRequestId,String immutablePayload) {return request("POST","/v2/checkout/orders/"+segment(orderId)+"/capture",Objects.requireNonNull(durableRequestId),Objects.requireNonNull(immutablePayload));}
    public JsonNode getOrder(String orderId) {return request("GET","/v2/checkout/orders/"+segment(orderId),null,null);}
    public JsonNode getCapture(String captureId) {return request("GET","/v2/payments/captures/"+segment(captureId),null,null);}
    public JsonNode verifyWebhook(String verificationBody) {return request("POST","/v1/notifications/verify-webhook-signature",null,verificationBody);}
    private JsonNode request(String method,String path,UUID requestId,String payload) {
        var builder=HttpRequest.newBuilder(origin.resolve(path)).timeout(timeout).header("Authorization","Bearer "+token()).header("Accept","application/json");
        if(requestId!=null)builder.header("PayPal-Request-Id",requestId.toString()).header("Prefer","return=representation");
        if(payload!=null)builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(payload));else builder.GET();
        try {return send(builder.build());}
        catch(ProviderFailure failure){if(failure.httpStatus==401){synchronized(this){accessToken=null;tokenExpires=Instant.EPOCH;}}throw failure;}
    }
    private synchronized String token() {
        if(!config.paypalConfigured())throw new ProviderFailure(503);
        if(accessToken!=null&&clock.instant().isBefore(tokenExpires))return accessToken;
        String basic=Base64.getEncoder().encodeToString((config.paypalClientId()+":"+config.paypalClientSecret()).getBytes(StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(origin.resolve("/v1/oauth2/token")).timeout(timeout)
            .header("Authorization","Basic "+basic).header("Content-Type","application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
        JsonNode response=send(request);
        String token=response.path("access_token").asText();long expires=response.path("expires_in").asLong();
        if(token.isBlank()||!response.path("token_type").asText().equalsIgnoreCase("bearer")||expires<1)throw new ProviderFailure(502);
        accessToken=token;tokenExpires=clock.instant().plusSeconds(Math.max(0,expires-Math.min(30,Math.max(1,expires/2))));return token;
    }
    private JsonNode send(HttpRequest request) {
        CompletableFuture<HttpResponse<byte[]>> pending=http.sendAsync(request,info->new BoundedBody());
        try {
            var response=pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);
            if(response.statusCode()<200||response.statusCode()>=300){
                String code="PAYPAL_UNAVAILABLE";
                try{JsonNode error=json.readTree(response.body());String candidate=error.path("details").path(0).path("issue").asText(error.path("name").asText());if(candidate.matches("[A-Z0-9_]{1,80}"))code=candidate;}catch(IOException ignored){}
                throw new ProviderFailure(response.statusCode(),code);
            }
            return json.readTree(response.body());
        }catch(IOException|ExecutionException ignored){throw new ProviderFailure(0);}
        catch(TimeoutException ignored){pending.cancel(true);throw new ProviderFailure(0);}
        catch(InterruptedException ignored){pending.cancel(true);Thread.currentThread().interrupt();throw new ProviderFailure(0);}
    }
    private static class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        final CompletableFuture<byte[]> result=new CompletableFuture<>();final ByteArrayOutputStream bytes=new ByteArrayOutputStream();Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription value){subscription=value;value.request(1);}
        public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){if(bytes.size()+buffer.remaining()>262144){subscription.cancel();result.completeExceptionally(new IOException("Provider response size bound"));return;}byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);}
            subscription.request(1);
        }
        public void onError(Throwable failure){result.completeExceptionally(failure);}
        public void onComplete(){result.complete(bytes.toByteArray());}
    }
    private static String segment(String id){if(id==null||id.isBlank()||id.length()>100)throw new IllegalArgumentException("Invalid provider ID");return URLEncoder.encode(id,StandardCharsets.UTF_8).replace("+","%20");}
}
