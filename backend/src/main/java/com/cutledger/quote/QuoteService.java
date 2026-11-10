package com.cutledger.quote;

import com.cutledger.bootstrap.DomainJson;
import com.cutledger.campaign.CampaignService;
import com.cutledger.rights.RightsEngine.Status;
import com.cutledger.web.Problem;
import com.cutledger.workspace.SessionService.Session;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteService {
    private final JdbcTemplate db;
    private final CampaignService campaigns;
    private final DomainJson json;
    private final Clock clock;
    public QuoteService(JdbcTemplate db,CampaignService campaigns,DomainJson json,Clock clock){this.db=db;this.campaigns=campaigns;this.json=json;this.clock=clock;}
    public Map<String,Object> owned(Session session,UUID id,boolean lock){
        var rows=db.queryForList("SELECT q.* FROM quote q JOIN campaign c ON c.id=q.campaign_id WHERE q.id=? AND c.workspace_id=?"+(lock?" FOR UPDATE OF q":""),id,session.workspaceId());
        if(rows.isEmpty())throw Problem.notFound();return rows.get(0);
    }
    public List<Map<String,Object>> lines(UUID quote){return db.queryForList("SELECT * FROM quote_line WHERE quote_id=? ORDER BY asset_id",quote);}
    public Map<String,Object> snapshot(Map<String,Object> quote,List<Map<String,Object>> lines){
        var policy=db.queryForMap("SELECT family,version_number,terms_hash FROM policy_version WHERE id=?",quote.get("policy_version_id"));
        String usageHash=db.queryForObject("SELECT usage_hash FROM campaign_revision WHERE id=?",String.class,quote.get("revision_id"));
        var result=new LinkedHashMap<String,Object>();
        result.put("schemaVersion",1);result.put("id",quote.get("id"));result.put("campaignId",quote.get("campaign_id"));result.put("revisionId",quote.get("revision_id"));
        result.put("campaignVersion",quote.get("campaign_version"));result.put("entitlementVersion",quote.get("entitlement_version"));
        result.put("policyVersionId",quote.get("policy_version_id"));result.put("policyFamily",policy.get("family"));result.put("policyVersion",policy.get("version_number"));result.put("policyHash",policy.get("terms_hash"));result.put("usageHash",usageHash);
        result.put("totalMinor",quote.get("total_minor"));result.put("currency",quote.get("currency"));result.put("createdAt",((Timestamp)quote.get("created_at")).toInstant());result.put("expiresAt",((Timestamp)quote.get("expires_at")).toInstant());
        var details=new ArrayList<Map<String,Object>>();
        lines.stream().sorted(Comparator.comparing(l->l.get("asset_id").toString())).forEach(line->{
            var item=new LinkedHashMap<String,Object>();item.put("assetId",line.get("asset_id"));item.put("priorGrantId",line.get("prior_grant_id"));item.put("targetTier",line.get("target_tier"));
            item.put("priorCreditMinor",line.get("prior_credit_minor"));item.put("targetCumulativeMinor",line.get("target_cumulative_minor"));item.put("deltaMinor",line.get("delta_minor"));
            item.put("purchasedScope",json.parse(line.get("purchased_scope")));item.put("reasonCodes",json.parse(line.get("reason_codes")));item.put("clauseIds",json.parse(line.get("clause_ids")));details.add(item);
        });result.put("lines",details);return result;
    }
    public void valid(Map<String,Object> campaign,Map<String,Object> quote){
        bindingValid(campaign,quote,true);
    }
    public void bindingValid(Map<String,Object> campaign,Map<String,Object> quote,boolean uncommitted){
        if(!quote.get("campaign_id").equals(campaign.get("id"))||!quote.get("revision_id").equals(campaign.get("latest_revision_id"))||
            !quote.get("campaign_version").equals(campaign.get("version"))||!quote.get("entitlement_version").equals(campaign.get("entitlement_version"))||
            (uncommitted&&!quote.get("policy_version_id").equals(campaigns.currentPolicy((UUID)campaign.get("merchant_id")).get("id"))))
            throw new Problem(409,"QUOTE_INVALIDATED","The campaign, policy or permissions changed. Review a new quote.");
        if(uncommitted&&!clock.instant().isBefore(((Timestamp)quote.get("expires_at")).toInstant()))throw new Problem(410,"QUOTE_EXPIRED","The ten-minute quote expired before capture commitment.");
        if(!quote.get("snapshot_hash").equals(json.hash(snapshot(quote,lines((UUID)quote.get("id"))))))throw new Problem(409,"QUOTE_INTEGRITY_FAILURE","The accepted snapshot requires review.");
    }
    public Map<String,Object> view(Session session,UUID id){
        var quote=owned(session,id,false);var result=new LinkedHashMap<>(snapshot(quote,lines(id)));
        result.put("snapshotHash",quote.get("snapshot_hash"));
        String status=quote.get("status").toString();
        if(status.equals("OPEN")&&!clock.instant().isBefore(((Timestamp)quote.get("expires_at")).toInstant()))status="EXPIRED";
        result.put("status",status);result.put("serverTime",clock.instant());return result;
    }
    @Transactional
    public Map<String,Object> create(Session session,UUID campaignId,UUID revisionId,String match,UUID key){
        var campaign=campaigns.ownedCampaign(session,campaignId,true);var payload=Map.of("campaignId",campaignId,"revisionId",revisionId);
        UUID prior=campaigns.dedup(session,"CREATE_QUOTE",key,payload);if(prior!=null)return view(session,prior);
        campaigns.version(campaign,match);
        if(campaign.get("active_payment_id")!=null)throw new Problem(409,"ACTIVE_PAYMENT","Resume the existing checkout before creating a replacement.");
        if(!revisionId.equals(campaign.get("latest_revision_id")))throw new Problem(409,"STALE_REVISION","Only the latest confirmed revision may be purchased.");
        var revision=campaigns.revision(session,campaignId,revisionId);
        var assessment=campaigns.assessScope(campaign,json.scope((com.fasterxml.jackson.databind.JsonNode)revision.get("usage")));
        if(assessment.rows().stream().allMatch(d->d.status()==Status.COVERED))return Map.of("kind","NO_PURCHASE_REQUIRED","assessment",campaigns.assessment(session,campaignId,revisionId));
        if(!assessment.canQuote())throw new Problem(422,"USAGE_NOT_PURCHASABLE","Unsupported, uncertain or review-required use cannot be quoted.");
        UUID id=UUID.randomUUID();var policy=campaigns.currentPolicy((UUID)campaign.get("merchant_id"));
        Instant now=clock.instant().truncatedTo(ChronoUnit.MILLIS);
        var quote=new LinkedHashMap<String,Object>();quote.put("id",id);quote.put("campaign_id",campaignId);quote.put("revision_id",revisionId);
        quote.put("campaign_version",campaign.get("version"));quote.put("entitlement_version",campaign.get("entitlement_version"));quote.put("policy_version_id",policy.get("id"));quote.put("total_minor",assessment.totalDeltaMinor());quote.put("currency","USD");quote.put("created_at",Timestamp.from(now));quote.put("expires_at",Timestamp.from(now.plusSeconds(600)));
        var lines=new ArrayList<Map<String,Object>>();
        for(var decision:assessment.rows())if(decision.status()==Status.UPGRADE_REQUIRED){
            var line=new LinkedHashMap<String,Object>();line.put("id",UUID.randomUUID());line.put("asset_id",decision.assetId());line.put("prior_grant_id",decision.priorGrantId());line.put("target_tier",decision.targetTier());line.put("prior_credit_minor",decision.priorCreditMinor());line.put("target_cumulative_minor",decision.targetCumulativeMinor());line.put("delta_minor",decision.deltaMinor());line.put("purchased_scope",json.write(json.scopeSnapshot(decision.purchasedScope())));line.put("reason_codes",json.write(new TreeSet<>(decision.reasonCodes())));line.put("clause_ids",json.write(decision.clauseIds()));lines.add(line);
        }
        String hash=json.hash(snapshot(quote,lines));
        db.update("INSERT INTO quote VALUES (?,?,?,?,?,?,?,?,?,'OPEN',?,?)",id,campaignId,revisionId,campaign.get("version"),campaign.get("entitlement_version"),policy.get("id"),hash,assessment.totalDeltaMinor(),"USD",Timestamp.from(now),Timestamp.from(now.plusSeconds(600)));
        for(var line:lines)db.update("INSERT INTO quote_line VALUES (?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb)",line.get("id"),id,line.get("asset_id"),line.get("prior_grant_id"),line.get("target_tier"),line.get("prior_credit_minor"),line.get("target_cumulative_minor"),line.get("delta_minor"),line.get("purchased_scope"),line.get("reason_codes"),line.get("clause_ids"));
        campaigns.remember(session,"CREATE_QUOTE",key,payload,id);campaigns.audit(session,campaignId,null,"QUOTE_CREATED",Map.of("quoteId",id,"totalMinor",assessment.totalDeltaMinor()));return view(session,id);
    }
}
