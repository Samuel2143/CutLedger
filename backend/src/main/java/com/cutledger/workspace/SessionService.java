package com.cutledger.workspace;

import com.cutledger.bootstrap.AppConfig;
import com.cutledger.web.Problem;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
    private final JdbcTemplate db;
    private final AppConfig config;
    private final ObjectMapper json;
    private final Clock clock;
    public SessionService(JdbcTemplate db, AppConfig config, ObjectMapper json, Clock clock) {
        this.db=db; this.config=config; this.json=json; this.clock=clock;
    }
    public record Session(UUID workspaceId, UUID campaignId, String csrfToken, Instant expiresAt, Instant serverTime) {}
    public record Created(Session session, String token) {}
    public String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(); }
    }
    private String tokenHash(String token) { return hash(config.sessionPepper()+":session:"+token); }
    private String csrf(String token) { return hash(config.sessionPepper()+":csrf:"+token); }
    public Optional<Session> authenticate(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return db.query("""
            SELECT s.workspace_id,c.id,s.expires_at,s.csrf_token_hash FROM browser_session s
            JOIN campaign c ON c.workspace_id=s.workspace_id
            WHERE s.token_hash=? AND s.expires_at>?
            """, (rs,n) -> new Session(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),csrf(token),rs.getTimestamp(3).toInstant(),clock.instant()),
            tokenHash(token),java.sql.Timestamp.from(clock.instant())).stream().findFirst();
    }
    @Transactional
    public Created create() {
        byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID workspace=UUID.randomUUID(), campaign=UUID.randomUUID(), revision=UUID.randomUUID();
        Instant now=clock.instant(), expiry=now.plus(Duration.ofDays(7));
        var time=java.sql.Timestamp.from(now); var end=java.sql.Timestamp.from(expiry);
        db.update("INSERT INTO workspace VALUES (?,?,?)",workspace,time,end);
        db.update("INSERT INTO browser_session VALUES (?,?,?,?,?,?)",UUID.randomUUID(),workspace,tokenHash(token),hash(csrf(token)),end,time);
        UUID merchant=db.queryForObject("SELECT id FROM merchant",UUID.class);
        UUID policy=db.queryForObject("SELECT id FROM policy_version WHERE version_number=1",UUID.class);
        db.update("INSERT INTO campaign(id,workspace_id,merchant_id,title,anchor_date,usage_timezone) VALUES (?,?,?,'Northstar Launch','2026-11-15','UTC')",campaign,workspace,merchant);
        String usage="{\"schemaVersion\":1,\"distributions\":[\"ORGANIC\"],\"territories\":[\"US\"],\"platforms\":[\"INSTAGRAM\"],\"startDate\":\"2026-11-15\",\"endDateExclusive\":\"2026-12-15\"}";
        db.update("INSERT INTO campaign_revision VALUES (?,?,1,NULL,NULL,'SEEDED',?::jsonb,?,'{}'::jsonb,?)",revision,campaign,usage,hash(usage),time);
        db.update("UPDATE campaign SET latest_revision_id=? WHERE id=?",revision,campaign);
        var assets=db.queryForList("SELECT id,slug FROM asset ORDER BY slug");
        int position=0;
        for (var asset:assets) {
            UUID assetId=(UUID)asset.get("id");
            db.update("INSERT INTO campaign_asset VALUES (?,?,?)",campaign,assetId,position++);
            String tier=asset.get("slug").equals("paper-orbit")?"GLOBAL365":"ORGANIC30";
            var row=db.queryForMap("SELECT t.coverage::text,t.cumulative_minor,p.terms_hash FROM asset_tier t JOIN policy_version p ON p.id=t.policy_version_id WHERE t.asset_id=? AND t.tier_code=?",assetId,tier);
            ObjectNode scope;
            try { scope=(ObjectNode)json.readTree((String)row.get("coverage")); }
            catch(Exception e) { throw new IllegalStateException("Invalid catalog scope"); }
            int days=scope.remove("durationDays").asInt();
            scope.put("startDate","2026-11-15"); scope.put("endDateExclusive",LocalDate.of(2026,11,15).plusDays(days).toString());
            UUID grant=UUID.randomUUID();
            db.update("""
                INSERT INTO entitlement(id,campaign_id,asset_id,policy_version_id,tier_code,scope_snapshot,credited_minor,paid_delta_minor,currency,source,accepted_revision_id,terms_hash,created_at)
                VALUES (?,?,?,?,?,?::jsonb,?,0,'USD','SEEDED',?,?,?)
                """,grant,campaign,assetId,policy,tier,scope.toString(),row.get("cumulative_minor"),revision,row.get("terms_hash"),time);
            db.update("INSERT INTO entitlement_state VALUES (?,'ACTIVE',NULL,?,1)",grant,time);
        }
        db.update("INSERT INTO audit_event VALUES (?,?,?,NULL,'SEEDED_PRIOR_PERMISSIONS','SYSTEM','{}'::jsonb,?)",UUID.randomUUID(),workspace,campaign,time);
        return new Created(new Session(workspace,campaign,csrf(token),expiry,now),token);
    }
    public boolean takeQuota(String scope, String operation, int limit, Duration window) {
        long width=window.toSeconds();
        Instant start=Instant.ofEpochSecond(Math.floorDiv(clock.instant().getEpochSecond(),width)*width);
        Integer count=db.queryForObject("""
            INSERT INTO usage_counter VALUES (?,?,?,1)
            ON CONFLICT(scope_key,window_start,operation) DO UPDATE SET count=usage_counter.count+1 RETURNING count
            """,Integer.class,scope,java.sql.Timestamp.from(start),operation);
        return count!=null && count<=limit;
    }
    public Map<String,Object> campaign(Session session, UUID id) {
        if(!id.equals(session.campaignId())) throw Problem.notFound();
        var record=db.queryForMap("SELECT id,title,anchor_date,version,entitlement_version,latest_revision_id,active_payment_id FROM campaign WHERE id=? AND workspace_id=?",id,session.workspaceId());
        var revision=db.queryForMap("SELECT id,sequence,origin,usage::text FROM campaign_revision WHERE id=?",record.get("latest_revision_id"));
        List<Map<String,Object>> assets=db.queryForList("""
            SELECT a.id,a.slug,a.name,a.local_preview_path AS "previewPath",e.tier_code AS "heldTier",
            e.source,e.scope_snapshot::text AS scope FROM campaign_asset ca JOIN asset a ON a.id=ca.asset_id
            JOIN LATERAL (SELECT e.* FROM entitlement e JOIN entitlement_state es ON es.entitlement_id=e.id AND es.status='ACTIVE'
                WHERE e.campaign_id=ca.campaign_id AND e.asset_id=a.id ORDER BY e.credited_minor DESC,e.created_at DESC LIMIT 1) e ON true
            WHERE ca.campaign_id=? ORDER BY ca.position
            """,id);
        try { revision.put("usage",json.readTree((String)revision.get("usage"))); }
        catch(Exception e) { throw new IllegalStateException("Invalid revision"); }
        var result=new LinkedHashMap<String,Object>(Map.of("id",id,"title",record.get("title"),"anchorDate",record.get("anchor_date").toString(),
            "version",record.get("version"),"entitlementVersion",record.get("entitlement_version"),"latestRevision",revision,
            "assets",assets,"paymentLocked",record.get("active_payment_id")!=null));result.put("activePaymentId",record.get("active_payment_id"));return result;
    }
}
