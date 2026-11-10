package com.cutledger.payment;

import com.cutledger.bootstrap.AppConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class PayPalHttpTest {
    HttpServer server;
    AtomicInteger oauth=new AtomicInteger(),mutations=new AtomicInteger();
    AtomicReference<String> key=new AtomicReference<>(),body=new AtomicReference<>();
    AtomicInteger status=new AtomicInteger(201);
    PayPalHttp client;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String response;
            int code;
            if(exchange.getRequestURI().getPath().equals("/v1/oauth2/token")) {
                oauth.incrementAndGet();response="{\"access_token\":\"test-token-only\",\"token_type\":\"Bearer\",\"expires_in\":3600}";code=200;
            }else {
                mutations.incrementAndGet();key.set(exchange.getRequestHeaders().getFirst("PayPal-Request-Id"));body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                code=status.get();response=code==201?"{\"id\":\"test-order\"}":"{\"message\":\"synthetic provider failure\"}";
            }
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        var config=new AppConfig(URI.create("http://localhost:8080"),"test-pepper-with-at-least-32-characters","","sandbox","test-client","test-secret","test-merchant","","","gemini-3.6-flash",200);
        client=new PayPalHttp(config,new ObjectMapper(),Clock.systemUTC(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()),HttpClient.newHttpClient());
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void sendsPersistedProviderRequestIdAndPayloadAndCachesOauth() {
        var id=UUID.randomUUID();String payload="{\"intent\":\"CAPTURE\"}";
        client.createOrder(id,payload);assertThat(key.get()).isEqualTo(id.toString());assertThat(body.get()).isEqualTo(payload);
        client.getOrder("test-order");assertThat(oauth.get()).isEqualTo(1);assertThat(mutations.get()).isEqualTo(2);
    }
    @Test void mutationErrorIsNeverAutomaticallyRetriedAndNeverExposesRawBody() {
        status.set(503);
        assertThatThrownBy(()->client.createOrder(UUID.randomUUID(),"{}")).isInstanceOf(PayPalHttp.ProviderFailure.class).hasMessage("PAYPAL_UNAVAILABLE");
        assertThat(mutations.get()).isEqualTo(1);
    }
    @Test void unauthorizedOperationInvalidatesOauthWithoutRetryingMutation() {
        status.set(401);
        assertThatThrownBy(()->client.createOrder(UUID.randomUUID(),"{}")).isInstanceOf(PayPalHttp.ProviderFailure.class);
        assertThat(mutations.get()).isEqualTo(1);assertThat(oauth.get()).isEqualTo(1);
        status.set(201);client.getOrder("test-order");assertThat(oauth.get()).isEqualTo(2);
    }
    @Test void stalledResponseBodyHasAnEndToEndDeadline() throws Exception {
        server.createContext("/v2/checkout/orders",exchange->{mutations.incrementAndGet();exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write('{');exchange.getResponseBody().flush();try{Thread.sleep(1200);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}exchange.close();});
        var config=new AppConfig(URI.create("http://localhost:8080"),"test-pepper-with-at-least-32-characters","","sandbox","test-client","test-secret","test-merchant","","","gemini-3.6-flash",200);
        var bounded=new PayPalHttp(config,new ObjectMapper(),Clock.systemUTC(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()),HttpClient.newHttpClient(),Duration.ofMillis(250));
        long start=System.nanoTime();assertThatThrownBy(()->bounded.createOrder(UUID.randomUUID(),"{}")).isInstanceOf(PayPalHttp.ProviderFailure.class);
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(1));assertThat(mutations.get()).isEqualTo(1);
    }
}
