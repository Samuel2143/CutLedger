package com.cutledger.workspace;

import com.cutledger.CutLedgerApplication;
import com.cutledger.campaign.CampaignService;
import com.cutledger.quote.QuoteService;
import com.cutledger.payment.*;
import com.cutledger.interpretation.*;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import jakarta.servlet.http.Cookie;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=CutLedgerApplication.class,properties={"cutledger.jobs.enabled=false","cutledger.gemini-api-key=synthetic-model-key","cutledger.paypal-client-id=synthetic-client","cutledger.paypal-client-secret=synthetic-secret","cutledger.paypal-merchant-id=test-merchant","cutledger.paypal-webhook-id=test-webhook"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@org.springframework.context.annotation.Import(WorkspaceIntegrationTest.TestClockConfiguration.class)
class WorkspaceIntegrationTest {
    @TestConfiguration(proxyBeanMethods=false)
    static class TestClockConfiguration {
        @Bean @Primary AdjustableClock adjustableClock(){return new AdjustableClock();}
    }
    static class AdjustableClock extends Clock {
        final AtomicReference<Instant> time=new AtomicReference<>(Instant.now());
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return time.get();}
        void advance(Duration amount){time.updateAndGet(now->now.plus(amount));}
    }
    static final String schema="test_"+UUID.randomUUID().toString().replace("-","");
    static PostgreSQLContainer<?> postgres;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("CUTLEDGER_TEST_DATABASE_URL");
        if(url==null) {
            postgres=new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.11-alpine@sha256:3540c0362c809df084ffac0ae69fe794515f9de92692f02178bcdce0ef0302ac").asCompatibleSubstituteFor("postgres"));postgres.start();
            url=postgres.getJdbcUrl();
            properties.add("spring.datasource.username",postgres::getUsername);
            properties.add("spring.datasource.password",postgres::getPassword);
        }
        final String jdbc=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
        properties.add("spring.datasource.url",()->jdbc);
        properties.add("spring.flyway.schemas",()->schema);
        properties.add("cutledger.session-pepper",()->"integration-test-only-pepper-with-32-characters");
        properties.add("cutledger.public-base-url",()->"http://localhost:8080");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired SessionService sessions;
    @Autowired CampaignService campaigns;
    @Autowired QuoteService quotes;
    @Autowired PaymentService payments;
    @Autowired PaymentWorker worker;
    @Autowired AdjustableClock clock;
    @MockitoBean PaymentProvider provider;
    @MockitoBean InterpretationProvider ai;
    @Autowired InterpretationService interpretations;
    @Autowired InterpretationWorker aiWorker;
    final AtomicReference<com.fasterxml.jackson.databind.node.ObjectNode> providerOrder=new AtomicReference<>(),providerCapture=new AtomicReference<>();
    final AtomicBoolean loseCaptureResponse=new AtomicBoolean();
    final AtomicInteger captureCalls=new AtomicInteger();
    @BeforeEach void providerFixture(){
        clock.time.set(Instant.now());loseCaptureResponse.set(false);captureCalls.set(0);
        db.update("UPDATE payment_job SET done_at=now() WHERE done_at IS NULL");
        db.update("UPDATE interpretation SET completed_at=now(),status='FAILED' WHERE completed_at IS NULL");
        when(provider.createOrder(any(),anyString())).thenAnswer(call->{
            var order=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(call.getArgument(1,String.class));
            UUID payment=UUID.fromString(order.path("purchase_units").get(0).path("custom_id").asText());
            assertThat(db.queryForObject("SELECT count(*) FROM payment_operation WHERE payment_id=? AND kind='CREATE_ORDER'",Integer.class,payment)).isEqualTo(1);
            String orderId="test-order-"+payment;order.put("id",orderId).put("status","CREATED");
            order.putArray("links").addObject().put("rel","payer-action").put("method","GET").put("href","https://www.sandbox.paypal.com/checkoutnow?token="+orderId);
            providerOrder.set(order);return order.deepCopy();
        });
        when(provider.getOrder(anyString())).thenAnswer(call->providerOrder.get().deepCopy());
        when(provider.captureOrder(anyString(),any(),anyString())).thenAnswer(call->{
            captureCalls.incrementAndGet();var order=providerOrder.get();
            UUID payment=UUID.fromString(order.path("purchase_units").get(0).path("custom_id").asText());
            assertThat(db.queryForObject("SELECT count(*) FROM payment_operation WHERE payment_id=? AND kind='CAPTURE'",Integer.class,payment)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT capture_committed_at IS NOT NULL FROM payment WHERE id=?",Boolean.class,payment)).isTrue();
            String captureId="test-capture-"+payment;
            var capture=json.createObjectNode().put("id",captureId).put("status","COMPLETED").put("final_capture",true);
            capture.set("amount",order.path("purchase_units").get(0).path("amount").deepCopy());capture.putObject("supplementary_data").putObject("related_ids").put("order_id",order.path("id").asText());
            order.put("status","COMPLETED");((com.fasterxml.jackson.databind.node.ObjectNode)order.path("purchase_units").get(0)).putObject("payments").putArray("captures").add(capture.deepCopy());providerCapture.set(capture);
            if(loseCaptureResponse.get())throw new PayPalHttp.ProviderFailure(0);
            return order.deepCopy();
        });
        when(provider.getCapture(anyString())).thenAnswer(call->providerCapture.get().deepCopy());
        when(provider.verifyWebhook(anyString())).thenReturn(json.createObjectNode().put("verification_status","SUCCESS"));
    }
    @AfterEach void clearTestFault(){db.execute("DROP TRIGGER IF EXISTS synthetic_grant_failure ON entitlement");db.execute("DROP FUNCTION IF EXISTS synthetic_grant_failure()");}
    @AfterAll void cleanupOnlyThisTestSchema(){db.execute("DROP SCHEMA "+schema+" CASCADE");if(postgres!=null)postgres.stop();}
    SessionService.Created create(){return sessions.create();}
    record Checkout(SessionService.Created browser,Map<String,Object> quote,UUID payment){}
    Map<String,Object> paidRevision(SessionService.Session session){
        UUID base=(UUID)db.queryForMap("SELECT latest_revision_id FROM campaign WHERE id=?",session.campaignId()).get("latest_revision_id");
        var usage=new CampaignService.Usage(1,Set.of("PAID_ADS"),Set.of("US","GB"),Set.of("INSTAGRAM"),LocalDate.of(2026,11,15),LocalDate.of(2027,2,13));
        return campaigns.confirm(session,session.campaignId(),"\"1\"",UUID.randomUUID(),new CampaignService.Confirmation(null,base,"MANUAL",usage,true,List.of(),List.of(),List.of(),List.of()));
    }
    Checkout checkout(){var browser=create();var session=browser.session();var revision=paidRevision(session);
        var quote=quotes.create(session,session.campaignId(),(UUID)revision.get("id"),"\"2\"",UUID.randomUUID());
        var payment=payments.initialize(session,(UUID)quote.get("id"),quote.get("snapshotHash").toString(),"\"2\"",UUID.randomUUID());
        UUID id=(UUID)payment.get("paymentId");assertThat(worker.drainOne()).isTrue();assertThat(payments.view(session,id).get("state")).isEqualTo("AWAITING_APPROVAL");return new Checkout(browser,quote,id);
    }
    void commit(Checkout checkout){providerOrder.get().put("status","APPROVED");payments.capture(checkout.browser.session(),checkout.payment,providerOrder.get().path("id").asText(),UUID.randomUUID());}
    void settle(Checkout checkout){commit(checkout);assertThat(worker.drainOne()).isTrue();assertThat(worker.drainOne()).isTrue();assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("SETTLED");}

    String livePublicOutput() throws Exception {return json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of("../docs/evidence/gemini-alternative-generate-content-public-fixture.json"))).path("output").toString();}
    UUID interpretation(SessionService.Session session,String brief){UUID base=db.queryForObject("SELECT latest_revision_id FROM campaign WHERE id=?",UUID.class,session.campaignId());return (UUID)interpretations.create(session,session.campaignId(),UUID.randomUUID(),new InterpretationService.Input(brief,base)).get("id");}
    @Test void realShapedInterpretationIsDurableDeduplicatedOwnedAndRequiresConfirmation() throws Exception {
        var session=create().session();String brief="Keep the visuals, but we're now running this as paid advertising in the US and UK for 90 days.";
        when(ai.extract(any())).thenReturn(new InterpretationProvider.ExtractionResult(livePublicOutput(),"gemini-3.6-flash","synthetic-reference-hash",20,json.createObjectNode()));
        UUID id=interpretation(session,brief);assertThat(interpretation(session,brief)).isEqualTo(id);assertThat(aiWorker.drainOne()).isTrue();
        var view=interpretations.view(session,id);assertThat(view.get("status")).isEqualTo("READY");verify(ai,times(1)).extract(any());assertThat(interpretation(session,brief)).isEqualTo(id);
        assertThatThrownBy(()->interpretations.view(create().session(),id)).isInstanceOf(com.cutledger.web.Problem.class);
        assertThat(db.queryForObject("SELECT version FROM campaign WHERE id=?",Long.class,session.campaignId())).isEqualTo(1);
        UUID base=(UUID)view.get("baseRevisionId");var use=new CampaignService.Usage(1,Set.of("PAID_ADS"),Set.of("US","GB"),Set.of("INSTAGRAM"),LocalDate.of(2026,11,15),LocalDate.of(2027,2,13));
        var revision=campaigns.confirm(session,session.campaignId(),"\"1\"",UUID.randomUUID(),new CampaignService.Confirmation(id,base,"AI_CONFIRMED",use,true,List.of(),List.of(),List.of(),List.of()));
        assertThat(revision.get("origin")).isEqualTo("AI_CONFIRMED");assertThat(campaigns.assessment(session,session.campaignId(),(UUID)revision.get("id")).get("totalDeltaMinor")).isEqualTo(2000L);
    }
    @Test void malformedOutputRepairAndTransientRetryHaveThreeCallTotalBoundAndSurviveLeaseRecovery() throws Exception {
        var session=create().session();when(ai.extract(any())).thenThrow(new InterpretationProvider.Failure("AI_HTTP_503",true,1)).thenReturn(new InterpretationProvider.ExtractionResult("{bad","gemini-3.6-flash","hash",1,json.createObjectNode())).thenReturn(new InterpretationProvider.ExtractionResult(livePublicOutput(),"gemini-3.6-flash","hash",1,json.createObjectNode()));
        UUID id=interpretation(session,"Keep the visuals, but we're now running this as paid advertising in the US and UK for 90 days.");
        aiWorker.drainOne();clock.advance(Duration.ofSeconds(65));aiWorker.drainOne();clock.advance(Duration.ofSeconds(65));aiWorker.drainOne();
        assertThat(interpretations.view(session,id).get("status")).isEqualTo("READY");verify(ai,times(3)).extract(any());
        assertThat(db.queryForMap("SELECT attempts,transient_retries,repair_attempts FROM interpretation WHERE id=?",id)).containsEntry("attempts",3).containsEntry("transient_retries",1).containsEntry("repair_attempts",1);
        var other=create().session();UUID stranded=interpretation(other,"No changes.");db.update("UPDATE interpretation SET status='RUNNING',attempts=3,lease_owner='crashed',lease_until=? WHERE id=?",java.sql.Timestamp.from(clock.instant().minusSeconds(1)),stranded);aiWorker.drainOne();assertThat(interpretations.view(other,stranded).get("failureCode")).isEqualTo("AI_ATTEMPT_LIMIT");verify(ai,times(3)).extract(any());
    }
    @Test void unsupportedAndUnresolvedModelRequirementsCannotBeBypassedByEmptyClientLists() throws Exception {
        var session=create().session();var output=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(livePublicOutput());output.putArray("unsupportedRequests").addObject().put("requirement","Television").putArray("evidence").add("TV ads");
        when(ai.extract(any())).thenReturn(new InterpretationProvider.ExtractionResult(output.toString(),"gemini-3.6-flash","hash",1,json.createObjectNode()));
        String brief="Keep the visuals, but we're now running this as paid advertising in the US and UK for 90 days. Also TV ads";UUID id=interpretation(session,brief);aiWorker.drainOne();var view=interpretations.view(session,id);assertThat(view.get("status")).isEqualTo("UNSUPPORTED");
        var use=new CampaignService.Usage(1,Set.of("PAID_ADS"),Set.of("US","GB"),Set.of("INSTAGRAM"),LocalDate.of(2026,11,15),LocalDate.of(2027,2,13));
        assertThatThrownBy(()->campaigns.confirm(session,session.campaignId(),"\"1\"",UUID.randomUUID(),new CampaignService.Confirmation(id,(UUID)view.get("baseRevisionId"),"AI_CONFIRMED",use,true,List.of(),List.of(),List.of(),List.of()))).hasMessageContaining("Unsupported");
        assertThat(db.queryForObject("SELECT count(*) FROM quote WHERE campaign_id=?",Integer.class,session.campaignId())).isZero();
    }
    @Test void storedIssueNeedsActualMatchingFieldResolutionAndCorrectionProvenance() throws Exception {
        var session=create().session();var output=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(livePublicOutput());
        output.putArray("issues").addObject().put("field","TERRITORY").put("kind","AMBIGUOUS").put("message","Confirm countries").putArray("evidence").add("US and UK");
        when(ai.extract(any())).thenReturn(new InterpretationProvider.ExtractionResult(output.toString(),"gemini-3.6-flash","hash",1,json.createObjectNode()));
        UUID id=interpretation(session,"Keep the visuals, but we're now running this as paid advertising in the US and UK for 90 days.");aiWorker.drainOne();var view=interpretations.view(session,id);
        var usage=new CampaignService.Usage(1,Set.of("PAID_ADS"),Set.of("US","GB"),Set.of("INSTAGRAM"),LocalDate.of(2026,11,15),LocalDate.of(2027,2,13));UUID base=(UUID)view.get("baseRevisionId");
        assertThatThrownBy(()->campaigns.confirm(session,session.campaignId(),"\"1\"",UUID.randomUUID(),new CampaignService.Confirmation(id,base,"AI_CONFIRMED",usage,true,List.of("issue-0"),List.of(),List.of(),List.of()))).hasMessageContaining("resolution");
        Object wrong=Map.of("issueId","issue-0","field","TERRITORY","resolvedValue",List.of("US"),"acknowledged",true);
        assertThatThrownBy(()->campaigns.confirm(session,session.campaignId(),"\"1\"",UUID.randomUUID(),new CampaignService.Confirmation(id,base,"AI_CONFIRMED",usage,true,List.of("issue-0"),List.of(wrong),List.of(),List.of()))).hasMessageContaining("resolution");
        Object actual=Map.of("issueId","issue-0","field","TERRITORY","resolvedValue",List.of("US","GB"),"acknowledged",true);
        assertThat(campaigns.confirm(session,session.campaignId(),"\"1\"",UUID.randomUUID(),new CampaignService.Confirmation(id,base,"AI_CONFIRMED",usage,true,List.of("issue-0"),List.of(actual),List.of(),List.of())).get("origin")).isEqualTo("AI_CONFIRMED");
    }
    @Test void interpretationDuplicateKeyConflictQuotaAndActivePaymentAreServerEnforced() {
        var session=create().session();UUID base=db.queryForObject("SELECT latest_revision_id FROM campaign WHERE id=?",UUID.class,session.campaignId());UUID key=UUID.randomUUID();
        var input=new InterpretationService.Input("No usage change.",base);var first=interpretations.create(session,session.campaignId(),key,input);
        assertThat(interpretations.create(session,session.campaignId(),key,input).get("id")).isEqualTo(first.get("id"));
        assertThatThrownBy(()->interpretations.create(session,session.campaignId(),key,new InterpretationService.Input("Different brief",base))).hasMessageContaining("different input");
        for(int i=0;i<20;i++)sessions.takeQuota(session.workspaceId().toString(),"AI_SUBMIT",20,Duration.ofHours(1));
        assertThatThrownBy(()->interpretations.create(session,session.campaignId(),UUID.randomUUID(),new InterpretationService.Input("New unique brief",base))).hasMessageContaining("limit reached");
        var checkout=checkout();UUID lockedBase=db.queryForObject("SELECT latest_revision_id FROM campaign WHERE id=?",UUID.class,checkout.browser.session().campaignId());
        assertThatThrownBy(()->interpretations.create(checkout.browser.session(),checkout.browser.session().campaignId(),UUID.randomUUID(),new InterpretationService.Input("No changes",lockedBase))).hasMessageContaining("existing checkout");
    }
    @Test void actualPostgresQuoteSnapshotAndAtomicFulfillmentPreserveHistoryAndCredit() {
        var checkout=checkout();assertThat(((Number)checkout.quote.get("totalMinor")).longValue()).isEqualTo(2000);
        assertThat((List<?>)checkout.quote.get("lines")).hasSize(2);
        assertThat(db.queryForObject("SELECT capture_committed_at IS NULL FROM payment WHERE id=?",Boolean.class,checkout.payment)).isTrue();
        settle(checkout);
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE campaign_id=?",Integer.class,checkout.browser.session().campaignId())).isEqualTo(5);
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,checkout.payment)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT entitlement_version FROM campaign WHERE id=?",Long.class,checkout.browser.session().campaignId())).isEqualTo(2);
        var assessment=campaigns.assessment(checkout.browser.session(),checkout.browser.session().campaignId(),(UUID)checkout.quote.get("revisionId"));
        assertThat(assessment.get("totalDeltaMinor")).isEqualTo(0L);assertThat(assessment.get("canQuote")).isEqualTo(false);
        assertThat(quotes.create(checkout.browser.session(),checkout.browser.session().campaignId(),(UUID)checkout.quote.get("revisionId"),"\"2\"",UUID.randomUUID()).get("kind")).isEqualTo("NO_PURCHASE_REQUIRED");
        assertThat(db.queryForObject("SELECT count(*) FROM payment WHERE campaign_id=?",Integer.class,checkout.browser.session().campaignId())).isEqualTo(1);
        assertThat(payments.receipt(checkout.browser.session(),checkout.payment).get("captureReference")).isNotNull();
    }
    @Test void lostCaptureResponseIsRecoveredByReadsWithoutAnotherCapture() {
        var checkout=checkout();loseCaptureResponse.set(true);settle(checkout);
        payments.capture(checkout.browser.session(),checkout.payment,providerOrder.get().path("id").asText(),UUID.randomUUID());
        assertThat(captureCalls.get()).isEqualTo(1);assertThat(db.queryForObject("SELECT count(*) FROM payment_operation WHERE payment_id=? AND kind='CAPTURE'",Integer.class,checkout.payment)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,checkout.payment)).isEqualTo(2);
    }
    @Test void expiredQuoteCannotCommitButTimelyCommitCanFulfillAfterExpiry() {
        var first=checkout();clock.advance(Duration.ofMinutes(11));
        providerOrder.get().put("status","APPROVED");
        assertThatThrownBy(()->payments.capture(first.browser.session(),first.payment,providerOrder.get().path("id").asText(),UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class).hasMessageContaining("expired");
        assertThat(payments.view(first.browser.session(),first.payment).get("state")).isEqualTo("CANCELLED");assertThat(captureCalls.get()).isZero();
        clock.time.set(Instant.now());var second=checkout();commit(second);clock.advance(Duration.ofMinutes(11));worker.drainOne();worker.drainOne();
        assertThat(payments.view(second.browser.session(),second.payment).get("state")).isEqualTo("SETTLED");
    }
    @Test void forgedCaptureAmountRequiresReviewWithoutGrantOrReplacementCheckout() {
        var checkout=checkout();commit(checkout);worker.drainOne();((com.fasterxml.jackson.databind.node.ObjectNode)providerCapture.get().path("amount")).put("value","19.99");worker.drainOne();
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("REVIEW_REQUIRED");
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,checkout.payment)).isZero();
        assertThatThrownBy(()->quotes.create(checkout.browser.session(),checkout.browser.session().campaignId(),(UUID)checkout.quote.get("revisionId"),"\"2\"",UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class);
        assertThatThrownBy(()->payments.receipt(checkout.browser.session(),checkout.payment)).isInstanceOf(com.cutledger.web.Problem.class);
    }
    @Test void grantInsertFailureRollsBackAllFulfillmentThenReadRecoveryCompletesOnce() {
        var checkout=checkout();commit(checkout);worker.drainOne();
        UUID failAsset=db.queryForList("SELECT asset_id FROM quote_line WHERE quote_id=? ORDER BY asset_id",checkout.quote.get("id")).stream().skip(1).map(r->(UUID)r.get("asset_id")).findFirst().orElseThrow();
        db.execute("CREATE FUNCTION synthetic_grant_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.source='PAYPAL_CAPTURE' AND NEW.asset_id='"+failAsset+"'::uuid THEN RAISE EXCEPTION 'synthetic test-only rollback'; END IF; RETURN NEW; END; $$");
        db.execute("CREATE TRIGGER synthetic_grant_failure BEFORE INSERT ON entitlement FOR EACH ROW EXECUTE FUNCTION synthetic_grant_failure()");
        worker.drainOne();assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,checkout.payment)).isZero();
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isNotEqualTo("SETTLED");
        db.execute("DROP TRIGGER synthetic_grant_failure ON entitlement");db.execute("DROP FUNCTION synthetic_grant_failure()");clock.advance(Duration.ofSeconds(6));worker.drainOne();
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("SETTLED");assertThat(captureCalls.get()).isEqualTo(1);
    }
    @Test void concurrentSameQuoteAcceptanceCreatesOnePaymentAndDurableRequestIdentity() throws Exception {
        var browser=create();var revision=paidRevision(browser.session());var quote=quotes.create(browser.session(),browser.session().campaignId(),(UUID)revision.get("id"),"\"2\"",UUID.randomUUID());
        var pool=Executors.newFixedThreadPool(2);try{
            Callable<Object> initialize=()->payments.initialize(browser.session(),(UUID)quote.get("id"),quote.get("snapshotHash").toString(),"\"2\"",UUID.randomUUID()).get("paymentId");
            var first=pool.submit(initialize);var second=pool.submit(initialize);assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(second.get(10,TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertThat(db.queryForObject("SELECT count(*) FROM payment WHERE campaign_id=?",Integer.class,browser.session().campaignId())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT create_request_id<>capture_request_id FROM payment WHERE campaign_id=?",Boolean.class,browser.session().campaignId())).isTrue();
        assertThatThrownBy(()->db.update("UPDATE payment SET expected_amount_minor=1 WHERE campaign_id=?",browser.session().campaignId())).hasMessageContaining("Immutable payment identity");
        verify(provider,never()).createOrder(any(),anyString());
    }
    @Test void cancellationIsTerminalAndCaptureTokenForgeryCannotDispatch() {
        var checkout=checkout();
        assertThatThrownBy(()->payments.capture(checkout.browser.session(),checkout.payment,"forged-order",UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class);
        payments.cancel(checkout.browser.session(),checkout.payment,UUID.randomUUID());
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("CANCELLED");
        assertThat(db.queryForObject("SELECT active_payment_id IS NULL FROM campaign WHERE id=?",Boolean.class,checkout.browser.session().campaignId())).isTrue();
        payments.capture(checkout.browser.session(),checkout.payment,providerOrder.get().path("id").asText(),UUID.randomUUID());assertThat(captureCalls.get()).isZero();
    }
    @Test void signedWebhookFixtureIsDeduplicatedAndCannotTriggerUncommittedCapture() throws Exception {
        var checkout=checkout();String eventId="test-event-"+UUID.randomUUID();var event=json.createObjectNode().put("id",eventId).put("event_type","CHECKOUT.ORDER.APPROVED");event.putObject("resource").put("id",providerOrder.get().path("id").asText()).put("status","APPROVED");
        for(int i=0;i<2;i++)mvc.perform(post("/api/v1/webhooks/paypal").header("PAYPAL-AUTH-ALGO","test-algo").header("PAYPAL-CERT-URL","https://example.test/cert").header("PAYPAL-TRANSMISSION-ID","test-transmission").header("PAYPAL-TRANSMISSION-SIG","test-signature").header("PAYPAL-TRANSMISSION-TIME",Instant.now().toString()).contentType("application/json").content(event.toString())).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT count(*) FROM webhook_event WHERE provider_event_id=?",Integer.class,eventId)).isEqualTo(1);worker.drainOne();
        assertThat(captureCalls.get()).isZero();assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,checkout.payment)).isZero();
        when(provider.verifyWebhook(anyString())).thenReturn(json.createObjectNode().put("verification_status","FAILURE"));
        event.put("id","test-invalid-event");mvc.perform(post("/api/v1/webhooks/paypal").header("PAYPAL-AUTH-ALGO","test-algo").header("PAYPAL-CERT-URL","https://example.test/cert").header("PAYPAL-TRANSMISSION-ID","test-transmission").header("PAYPAL-TRANSMISSION-SIG","test-signature").header("PAYPAL-TRANSMISSION-TIME",Instant.now().toString()).contentType("application/json").content(event.toString())).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT count(*) FROM webhook_event WHERE provider_event_id='test-invalid-event'",Integer.class)).isZero();
    }
    @Test void duplicateWebhookSignatureHeaderIsRejectedBeforeProviderVerification() throws Exception {
        var checkout=checkout();
        var event=json.createObjectNode().put("id","test-duplicate-header").put("event_type","CHECKOUT.ORDER.APPROVED");event.putObject("resource").put("id",providerOrder.get().path("id").asText());
        mvc.perform(post("/api/v1/webhooks/paypal").header("PAYPAL-AUTH-ALGO","test-algo","duplicate-algo").header("PAYPAL-CERT-URL","https://example.test/cert").header("PAYPAL-TRANSMISSION-ID","test-transmission").header("PAYPAL-TRANSMISSION-SIG","test-signature").header("PAYPAL-TRANSMISSION-TIME",Instant.now().toString()).contentType("application/json").content(event.toString())).andExpect(status().isBadRequest());
        verify(provider,never()).verifyWebhook(anyString());
        assertThat(db.queryForObject("SELECT count(*) FROM webhook_event WHERE provider_event_id='test-duplicate-header'",Integer.class)).isZero();
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("AWAITING_APPROVAL");
    }

    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder webhook(com.fasterxml.jackson.databind.JsonNode event){return post("/api/v1/webhooks/paypal").header("PAYPAL-AUTH-ALGO","test-algo").header("PAYPAL-CERT-URL","https://example.test/cert").header("PAYPAL-TRANSMISSION-ID","test-transmission").header("PAYPAL-TRANSMISSION-SIG","test-signature").header("PAYPAL-TRANSMISSION-TIME",Instant.now().toString()).contentType("application/json").content(event.toString());}
    @Test void webhookVerificationOutageCannotPersistTrustedEventOrChangeMoney() throws Exception {
        var checkout=checkout();var event=json.createObjectNode().put("id","test-outage-"+UUID.randomUUID()).put("event_type","PAYMENT.CAPTURE.COMPLETED");event.putObject("resource").put("id","synthetic-capture");
        when(provider.verifyWebhook(anyString())).thenThrow(new PayPalHttp.ProviderFailure(503));mvc.perform(webhook(event)).andExpect(status().isServiceUnavailable());
        assertThat(db.queryForObject("SELECT count(*) FROM webhook_event WHERE provider_event_id=?",Integer.class,event.path("id").asText())).isZero();assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("AWAITING_APPROVAL");assertThat(captureCalls.get()).isZero();
    }
    @Test void verifiedUnknownOrderWebhookCannotInventPaymentOrGrant() throws Exception {
        int before=db.queryForObject("SELECT count(*) FROM payment",Integer.class),grants=db.queryForObject("SELECT count(*) FROM entitlement",Integer.class);
        var event=json.createObjectNode().put("id","test-unknown-"+UUID.randomUUID()).put("event_type","CHECKOUT.ORDER.APPROVED");event.putObject("resource").put("id","unknown-synthetic-order").put("status","APPROVED");mvc.perform(webhook(event)).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT count(*) FROM payment",Integer.class)).isEqualTo(before);assertThat(db.queryForObject("SELECT count(*) FROM entitlement",Integer.class)).isEqualTo(grants);assertThat(worker.drainOne()).isFalse();
    }
    @Test void delayedPendingWebhookAndConflictingReplayCannotRegressSettledGrant() throws Exception {
        var checkout=checkout();settle(checkout);var event=json.createObjectNode().put("id","test-delayed-"+UUID.randomUUID()).put("event_type","PAYMENT.CAPTURE.PENDING");var resource=event.putObject("resource").put("id",providerCapture.get().path("id").asText()).put("status","PENDING");resource.putObject("supplementary_data").putObject("related_ids").put("order_id",providerOrder.get().path("id").asText());
        mvc.perform(webhook(event)).andExpect(status().isNoContent());worker.drainOne();assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("SETTLED");
        resource.put("status","COMPLETED");mvc.perform(webhook(event)).andExpect(status().isBadRequest());assertThat(captureCalls.get()).isEqualTo(1);assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,checkout.payment)).isEqualTo(2);
    }
    @Test void restartedWorkerRecoversExpiredLeaseByProviderReadsWithoutSecondCapture() {
        var checkout=checkout();loseCaptureResponse.set(true);commit(checkout);worker.drainOne();
        db.update("UPDATE payment_job SET lease_owner='crashed-worker',lease_until=? WHERE payment_id=? AND kind='VERIFY' AND done_at IS NULL",java.sql.Timestamp.from(clock.instant().plusSeconds(60)),checkout.payment);
        var restarted=new PaymentWorker(payments,provider,false);assertThat(restarted.drainOne()).isFalse();clock.advance(Duration.ofSeconds(61));assertThat(restarted.drainOne()).isTrue();
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("SETTLED");assertThat(captureCalls.get()).isEqualTo(1);assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,checkout.payment)).isEqualTo(2);
    }
    @Test void uncertainCreateNeverDispatchesAgainOrReleasesCampaignForReplacement() {
        var browser=create();var revision=paidRevision(browser.session());
        var quote=quotes.create(browser.session(),browser.session().campaignId(),(UUID)revision.get("id"),"\"2\"",UUID.randomUUID());
        UUID id=(UUID)payments.initialize(browser.session(),(UUID)quote.get("id"),quote.get("snapshotHash").toString(),"\"2\"",UUID.randomUUID()).get("paymentId");
        doThrow(new PayPalHttp.ProviderFailure(408)).when(provider).createOrder(any(),anyString());
        worker.drainOne();assertThat(payments.view(browser.session(),id).get("state")).isEqualTo("RECONCILING");
        worker.drainOne();assertThat(payments.view(browser.session(),id).get("state")).isEqualTo("REVIEW_REQUIRED");
        payments.reconcile(browser.session(),id,UUID.randomUUID());worker.drainOne();
        verify(provider,times(1)).createOrder(any(),anyString());
        assertThat(db.queryForObject("SELECT active_payment_id FROM campaign WHERE id=?",UUID.class,browser.session().campaignId())).isEqualTo(id);
        assertThatThrownBy(()->quotes.create(browser.session(),browser.session().campaignId(),(UUID)revision.get("id"),"\"2\"",UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class);
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,id)).isZero();
    }

    @Test void pendingCapturePastRecoveryLimitStaysLockedWithNoNewDispatch() {
        var checkout=checkout();commit(checkout);worker.drainOne();
        providerCapture.get().put("status","PENDING");
        ((com.fasterxml.jackson.databind.node.ObjectNode)providerOrder.get().path("purchase_units").get(0).path("payments").path("captures").get(0)).put("status","PENDING");
        worker.drainOne();assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("CAPTURE_PENDING");
        assertThatThrownBy(()->payments.cancel(checkout.browser.session(),checkout.payment,UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class);
        clock.advance(Duration.ofHours(25));worker.drainOne();
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("REVIEW_REQUIRED");
        assertThat(db.queryForObject("SELECT active_payment_id FROM campaign WHERE id=?",UUID.class,checkout.browser.session().campaignId())).isEqualTo(checkout.payment);
        assertThat(captureCalls.get()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE source_payment_id=?",Integer.class,checkout.payment)).isZero();
    }

    @Test void verifiedReversalSuspendsPaidGrantsAndKeepsSeedHistoryImmutable() {
        var checkout=checkout();settle(checkout);
        providerCapture.get().put("status","REFUNDED");
        payments.reconcile(checkout.browser.session(),checkout.payment,UUID.randomUUID());worker.drainOne();
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("REVIEW_REQUIRED");
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement e JOIN entitlement_state s ON s.entitlement_id=e.id WHERE e.source_payment_id=? AND s.status='SUSPENDED_REVIEW'",Integer.class,checkout.payment)).isEqualTo(2);
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement e JOIN entitlement_state s ON s.entitlement_id=e.id WHERE e.campaign_id=? AND e.source='SEEDED' AND s.status='ACTIVE'",Integer.class,checkout.browser.session().campaignId())).isEqualTo(3);
        assertThat(db.queryForObject("SELECT entitlement_version FROM campaign WHERE id=?",Long.class,checkout.browser.session().campaignId())).isEqualTo(3);
        assertThatThrownBy(()->payments.receipt(checkout.browser.session(),checkout.payment)).isInstanceOf(com.cutledger.web.Problem.class);
        assertThat(db.queryForObject("SELECT count(*) FROM audit_event WHERE payment_id=? AND event_type='PERMISSION_ISSUED' AND actor_type='SYSTEM'",Integer.class,checkout.payment)).isEqualTo(2);
    }

    @Test void paymentResourcesAreOwnedAndChecksAreBoundedWithoutRepeatingDeduplicatedRequests() throws Exception {
        var checkout=checkout();var stranger=create();var cookie=new Cookie("cl_session",stranger.token());
        mvc.perform(get("/api/v1/payments/"+checkout.payment).cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/quotes/"+checkout.quote.get("id")).cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payments/"+checkout.payment+"/receipt").cookie(cookie)).andExpect(status().isNotFound());
        assertThatThrownBy(()->payments.cancel(stranger.session(),checkout.payment,UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class);
        UUID key=UUID.randomUUID();
        for(int i=0;i<10;i++)payments.reconcile(checkout.browser.session(),checkout.payment,key);
        for(int i=0;i<5;i++)payments.reconcile(checkout.browser.session(),checkout.payment,UUID.randomUUID());
        assertThatThrownBy(()->payments.reconcile(checkout.browser.session(),checkout.payment,UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class).hasMessageContaining("minute");
        assertThat(db.queryForObject("SELECT count(*) FROM payment_job WHERE payment_id=? AND kind='VERIFY' AND done_at IS NULL",Integer.class,checkout.payment)).isEqualTo(1);
        assertThat(captureCalls.get()).isZero();
    }

    @Test void cancellationWinsWhileApprovalReadIsInFlightWithoutCommittingCapture() throws Exception {
        var checkout=checkout();providerOrder.get().put("status","APPROVED");
        var reading=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call->{reading.countDown();if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Test read did not finish");return providerOrder.get().deepCopy();}).when(provider).getOrder(anyString());
        var pool=Executors.newSingleThreadExecutor();try{
            var capture=pool.submit(()->payments.capture(checkout.browser.session(),checkout.payment,providerOrder.get().path("id").asText(),UUID.randomUUID()));
            assertThat(reading.await(5,TimeUnit.SECONDS)).isTrue();
            payments.cancel(checkout.browser.session(),checkout.payment,UUID.randomUUID());release.countDown();
            assertThatThrownBy(()->capture.get(5,TimeUnit.SECONDS)).hasCauseInstanceOf(com.cutledger.web.Problem.class);
        }finally{release.countDown();pool.shutdownNow();}
        assertThat(payments.view(checkout.browser.session(),checkout.payment).get("state")).isEqualTo("CANCELLED");
        assertThat(db.queryForObject("SELECT capture_committed_at IS NULL FROM payment WHERE id=?",Boolean.class,checkout.payment)).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM payment_job WHERE payment_id=? AND kind='CAPTURE'",Integer.class,checkout.payment)).isZero();
    }

    @Test void persistedGlobalUpgradeCreditsPriorPaidTierAndRetainsOriginalCoverage() {
        var checkout=checkout();settle(checkout);var session=checkout.browser.session();
        var global=new CampaignService.Usage(1,Set.of("PAID_ADS"),Set.of("WORLDWIDE"),Set.of("INSTAGRAM"),LocalDate.of(2026,11,15),LocalDate.of(2027,11,15));
        var revision=campaigns.confirm(session,session.campaignId(),"\"2\"",UUID.randomUUID(),new CampaignService.Confirmation(null,(UUID)checkout.quote.get("revisionId"),"MANUAL",global,true,List.of(),List.of(),List.of(),List.of()));
        var quote=quotes.create(session,session.campaignId(),(UUID)revision.get("id"),"\"3\"",UUID.randomUUID());
        assertThat(quote.get("totalMinor")).isEqualTo(5000L);
        assertThat(db.queryForList("SELECT delta_minor FROM quote_line WHERE quote_id=? ORDER BY delta_minor",Long.class,quote.get("id"))).containsExactly(2200L,2800L);
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE campaign_id=?",Integer.class,session.campaignId())).isEqualTo(5);
        UUID baseline=db.queryForObject("SELECT id FROM campaign_revision WHERE campaign_id=? AND sequence=1",UUID.class,session.campaignId());
        var oldAssessment=campaigns.assessment(session,session.campaignId(),baseline);
        assertThat(oldAssessment.get("totalDeltaMinor")).isEqualTo(0L);
        assertThat(db.queryForObject("SELECT count(*) FROM payment WHERE campaign_id=?",Integer.class,session.campaignId())).isEqualTo(1);
    }

    @Test void changedRevisionInvalidatesOldQuoteAndTamperedAcceptanceCannotDispatch() {
        var browser=create();var session=browser.session();var revision=paidRevision(session);
        var quote=quotes.create(session,session.campaignId(),(UUID)revision.get("id"),"\"2\"",UUID.randomUUID());
        assertThatThrownBy(()->payments.initialize(session,(UUID)quote.get("id"),"0".repeat(64),"\"2\"",UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class);
        var global=new CampaignService.Usage(1,Set.of("PAID_ADS"),Set.of("WORLDWIDE"),Set.of("INSTAGRAM"),LocalDate.of(2026,11,15),LocalDate.of(2027,11,15));
        var next=campaigns.confirm(session,session.campaignId(),"\"2\"",UUID.randomUUID(),new CampaignService.Confirmation(null,(UUID)revision.get("id"),"MANUAL",global,true,List.of(),List.of(),List.of(),List.of()));
        assertThat(quotes.view(session,(UUID)quote.get("id")).get("status")).isEqualTo("INVALIDATED");
        assertThatThrownBy(()->payments.initialize(session,(UUID)quote.get("id"),quote.get("snapshotHash").toString(),"\"3\"",UUID.randomUUID())).isInstanceOf(com.cutledger.web.Problem.class);
        assertThat(quotes.create(session,session.campaignId(),(UUID)next.get("id"),"\"3\"",UUID.randomUUID()).get("totalMinor")).isEqualTo(7000L);
        assertThat(db.queryForObject("SELECT count(*) FROM payment WHERE campaign_id=?",Integer.class,session.campaignId())).isZero();
        verify(provider,never()).createOrder(any(),anyString());
    }

    @Test void migrationsEnforceImmutableSeedAndNoInventedPayments() {
        assertThat(db.queryForObject("SELECT count(*) FROM asset",Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT count(*) FROM asset_tier",Integer.class)).isEqualTo(9);
        var a=create();
        assertThat(db.queryForObject("SELECT count(*) FROM payment WHERE campaign_id=?",Integer.class,a.session().campaignId())).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM entitlement WHERE campaign_id=? AND source='SEEDED' AND source_payment_id IS NULL",Integer.class,a.session().campaignId())).isEqualTo(3);
        assertThatThrownBy(()->db.update("UPDATE entitlement SET credited_minor=0 WHERE campaign_id=?",a.session().campaignId())).hasMessageContaining("Immutable historical record");
        assertThatThrownBy(()->db.update("UPDATE campaign_revision SET usage='{}'::jsonb WHERE campaign_id=?",a.session().campaignId())).hasMessageContaining("Immutable historical record");
    }
    @Test void cookieBootstrapPersistsWithoutReseedingAndHashesSecrets() throws Exception {
        var first=mvc.perform(post("/api/v1/demo-sessions").header("Origin","http://localhost:8080").contentType("application/json").content("{}"))
            .andExpect(status().isCreated()).andExpect(cookie().httpOnly("cl_session",true)).andReturn().getResponse();
        var cookie=first.getCookie("cl_session");assertThat(cookie).isNotNull();
        var body=json.readTree(first.getContentAsString());
        mvc.perform(get("/api/v1/session").cookie(cookie)).andExpect(status().isOk()).andExpect(jsonPath("$.workspaceId").value(body.path("workspaceId").asText()));
        mvc.perform(post("/api/v1/demo-sessions").header("Origin","http://localhost:8080").cookie(cookie).contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.campaignId").value(body.path("campaignId").asText()));
        assertThat(db.queryForObject("SELECT count(*) FROM campaign WHERE workspace_id=?",Integer.class,UUID.fromString(body.path("workspaceId").asText()))).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM browser_session WHERE token_hash=? OR csrf_token_hash=?",Integer.class,cookie.getValue(),body.path("csrfToken").asText())).isZero();
    }
    @Test void enforcesOwnershipOriginCsrfAndBodyLimits() throws Exception {
        var a=create();var b=create();var cookie=new Cookie("cl_session",a.token());
        mvc.perform(get("/api/v1/campaigns/"+a.session().campaignId()).cookie(cookie)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/campaigns/"+b.session().campaignId()).cookie(cookie)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/demo-sessions").header("Origin","https://evil.example").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ORIGIN_REJECTED"));
        mvc.perform(post("/api/v1/campaigns/"+a.session().campaignId()+"/quotes").header("Origin","http://localhost:8080").cookie(cookie).contentType("application/json").content("{}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        mvc.perform(post("/api/v1/demo-sessions").header("Origin","http://localhost:8080").contentType("application/json").content("x".repeat(65537)))
            .andExpect(status().isPayloadTooLarge());
    }
    @Test void quotaIsDurableAndSessionExpiryIsEnforced() {
        String scope=UUID.randomUUID().toString();
        assertThat(sessions.takeQuota(scope,"TEST",2,Duration.ofHours(1))).isTrue();
        assertThat(sessions.takeQuota(scope,"TEST",2,Duration.ofHours(1))).isTrue();
        assertThat(sessions.takeQuota(scope,"TEST",2,Duration.ofHours(1))).isFalse();
        assertThat(db.queryForObject("SELECT count FROM usage_counter WHERE scope_key=?",Integer.class,scope)).isEqualTo(3);
        var a=create();
        db.update("UPDATE browser_session SET expires_at=now()-interval '1 second' WHERE workspace_id=?",a.session().workspaceId());
        assertThat(sessions.authenticate(a.token())).isEmpty();
    }
    @Test void diagnosticsNeverClaimProviderVerification() throws Exception {
        mvc.perform(get("/api/v1/diagnostics")).andExpect(status().isOk()).andExpect(jsonPath("$.checkoutAvailable").value(true))
            .andExpect(jsonPath("$.aiAvailable").value(true)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
