package com.cutledger.campaign;

import com.cutledger.bootstrap.DomainJson;
import com.cutledger.rights.RightsEngine;
import com.cutledger.rights.RightsEngine.*;
import com.cutledger.web.Problem;
import com.cutledger.workspace.SessionService.Session;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampaignService {
    private final JdbcTemplate db;
    public final DomainJson json;
    private final Clock clock;
    private final RightsEngine engine=new RightsEngine();
    public CampaignService(JdbcTemplate db,DomainJson json,Clock clock){this.db=db;this.json=json;this.clock=clock;}
    public record Usage(@Min(1) @Max(1) int schemaVersion,@NotEmpty @Size(max=2) Set<String> distributions,
        @NotEmpty @Size(max=30) Set<String> territories,@NotEmpty @Size(max=3) Set<String> platforms,
        @NotNull LocalDate startDate,@NotNull LocalDate endDateExclusive){
        public Scope scope(){var normalized=new TreeSet<String>();territories.forEach(t->normalized.add(t.equals("UK")?"GB":t));return new Scope(distributions,normalized,platforms,startDate,endDateExclusive);}
    }
    public record Confirmation(UUID interpretationId,@NotNull UUID baseRevisionId,@NotNull String origin,@NotNull @Valid Usage usage,
        @AssertTrue boolean confirmed,List<String> resolvedIssueIds,List<Object> issueResolutions,List<String> excludedRequirementsAcknowledged,List<String> userEditedFields){}
    public Map<String,Object> ownedCampaign(Session session,UUID id,boolean lock){
        var rows=db.queryForList("SELECT * FROM campaign WHERE id=? AND workspace_id=?"+(lock?" FOR UPDATE":""),id,session.workspaceId());
        if(rows.isEmpty())throw Problem.notFound();return rows.get(0);
    }
    public void version(Map<String,Object> campaign,String match){
        if(!Objects.equals(match,"\""+campaign.get("version")+"\""))throw new Problem(409,"STALE_CAMPAIGN","Reload the current campaign before continuing.");
    }
    public UUID dedup(Session session,String operation,UUID key,Object payload){
        if(key==null)throw new Problem(400,"IDEMPOTENCY_KEY_REQUIRED","A durable request key is required.");
        var rows=db.queryForList("SELECT payload_hash,resource_id FROM request_dedup WHERE workspace_id=? AND operation=? AND request_key=?",session.workspaceId(),operation,key);
        if(rows.isEmpty())return null;
        if(!rows.get(0).get("payload_hash").equals(json.hash(payload)))throw new Problem(409,"IDEMPOTENCY_CONFLICT","This request key belongs to different input.");
        return (UUID)rows.get(0).get("resource_id");
    }
    public void remember(Session session,String operation,UUID key,Object payload,UUID resource){
        db.update("INSERT INTO request_dedup VALUES (?,?,?,?,?,201,?)",session.workspaceId(),operation,key,json.hash(payload),resource,Timestamp.from(clock.instant()));
    }
    public void audit(Session session,UUID campaign,UUID payment,String event,Map<String,Object> details){
        audit(session,campaign,payment,event,details,"USER");
    }
    public void audit(Session session,UUID campaign,UUID payment,String event,Map<String,Object> details,String actorType){
        if(!Set.of("USER","SYSTEM").contains(actorType))throw new IllegalArgumentException("Invalid audit actor");
        db.update("INSERT INTO audit_event VALUES (?,?,?,?,?,?,?::jsonb,?)",UUID.randomUUID(),session.workspaceId(),campaign,payment,event,actorType,json.write(details),Timestamp.from(clock.instant()));
    }
    public Map<String,Object> revision(Session session,UUID campaign,UUID revision){
        ownedCampaign(session,campaign,false);
        var rows=db.queryForList("SELECT id,sequence,origin,usage::text,confirmation_evidence::text,created_at FROM campaign_revision WHERE id=? AND campaign_id=?",revision,campaign);
        if(rows.isEmpty())throw Problem.notFound();var row=rows.get(0);
        return Map.of("id",revision,"sequence",row.get("sequence"),"origin",row.get("origin"),"usage",json.parse(row.get("usage")),"confirmationEvidence",json.parse(row.get("confirmation_evidence")),"createdAt",((Timestamp)row.get("created_at")).toInstant());
    }
    @Transactional
    public Map<String,Object> confirm(Session session,UUID campaign,String match,UUID key,Confirmation input){
        var current=ownedCampaign(session,campaign,true);
        var payload=Map.of("campaignId",campaign,"confirmation",input);
        UUID prior=dedup(session,"CONFIRM_REVISION",key,payload);if(prior!=null)return revision(session,campaign,prior);
        version(current,match);
        if(current.get("active_payment_id")!=null)throw new Problem(409,"ACTIVE_PAYMENT","Resolve the existing checkout before confirming another revision.");
        if(!input.confirmed||!input.baseRevisionId.equals(current.get("latest_revision_id")))throw new Problem(409,"STALE_REVISION","Review and confirm the latest revision.");
        Map<String,Object> evidence=confirmationEvidence(session,campaign,input);
        var usage=json.scopeSnapshot(input.usage.scope());
        var assessed=assessScope(current,input.usage.scope());
        if(assessed.rows().stream().anyMatch(d->d.status()==Status.UNSUPPORTED||d.status()==Status.NEEDS_CLARIFICATION))
            throw new Problem(422,"UNSUPPORTED_USAGE","The requested use is outside this merchant's supported policy. No quote can be created.");
        UUID id=UUID.randomUUID();long sequence=((Number)current.get("version")).longValue()+1;
        db.update("INSERT INTO campaign_revision VALUES (?,?,?,?,?,?,?::jsonb,?,?::jsonb,?)",id,campaign,sequence,input.baseRevisionId,input.interpretationId,input.origin,json.write(usage),json.hash(usage),json.write(evidence),Timestamp.from(clock.instant()));
        db.update("UPDATE campaign SET latest_revision_id=?,version=? WHERE id=?",id,sequence,campaign);
        db.update("UPDATE quote SET status='INVALIDATED' WHERE campaign_id=? AND status='OPEN'",campaign);
        remember(session,"CONFIRM_REVISION",key,payload,id);audit(session,campaign,null,"USAGE_CONFIRMED",Map.of("revisionId",id,"origin",input.origin));
        return revision(session,campaign,id);
    }
    private Map<String,Object> confirmationEvidence(Session session,UUID campaign,Confirmation input){
        var fields=List.of("distributions","territories","platforms","startDate","endDateExclusive");
        var actionable=db.queryForList("SELECT id,status FROM interpretation WHERE workspace_id=? AND campaign_id=? AND base_revision_id=? AND status IN ('READY','UNSUPPORTED','NEEDS_CLARIFICATION') ORDER BY created_at DESC,id DESC LIMIT 1",session.workspaceId(),campaign,input.baseRevisionId);
        if(!actionable.isEmpty()&&Set.of("UNSUPPORTED","NEEDS_CLARIFICATION").contains(actionable.get(0).get("status"))&&!Objects.equals(input.interpretationId,actionable.get(0).get("id")))
            throw new Problem(422,"EXCLUSIONS_REQUIRED","A separate manual revision must resolve stored issues and acknowledge excluded requirements.");
        if(input.interpretationId==null){
            if(!input.origin.equals("MANUAL")||nonempty(input.resolvedIssueIds)||nonempty(input.issueResolutions)||nonempty(input.excludedRequirementsAcknowledged))throw new Problem(422,"INVALID_CONFIRMATION","Manual entry cannot claim an AI interpretation or issue resolution.");
            return Map.of("confirmed",true,"origin","MANUAL","userEditedFields",fields,"excludedRequirements",List.of());
        }
        var rows=db.queryForList("SELECT * FROM interpretation WHERE id=? AND workspace_id=? AND campaign_id=? AND base_revision_id=?",input.interpretationId,session.workspaceId(),campaign,input.baseRevisionId);
        if(rows.isEmpty())throw Problem.notFound();var row=rows.get(0);
        if(!Set.of("READY","NEEDS_CLARIFICATION","UNSUPPORTED").contains(row.get("status")))throw new Problem(422,"INTERPRETATION_NOT_READY","Wait for a valid interpretation before confirming.");
        var metadata=json.parse(row.get("result_metadata"));var unsupported=metadata.path("unsupportedRequests");
        var excluded=new TreeSet<String>();unsupported.forEach(item->excluded.add(item.path("id").asText()));
        boolean subset=!excluded.isEmpty();
        if(subset&&(!input.origin.equals("MANUAL")||!new TreeSet<>(safe(input.excludedRequirementsAcknowledged)).equals(excluded)))throw new Problem(422,"UNSUPPORTED_REQUIREMENTS","Unsupported requirements block AI confirmation. Revise the brief or explicitly acknowledge every excluded requirement in a separate manual revision.");
        if(!subset&&(!Set.of("AI_CONFIRMED","MANUAL").contains(input.origin)||nonempty(input.excludedRequirementsAcknowledged)))throw new Problem(422,"INVALID_CONFIRMATION","Confirm the stored interpretation with its complete usage.");
        var finalUsage=json.tree(json.scopeSnapshot(input.usage.scope()));var proposed=json.parse(row.get("normalized_candidate"));
        var changed=new TreeSet<String>();for(String field:fields)if(!json.hash(finalUsage.path(field)).equals(json.hash(proposed.path(field))))changed.add(field);
        if(!new TreeSet<>(safe(input.userEditedFields)).equals(changed))throw new Problem(422,"EDIT_PROVENANCE_REQUIRED","Record each field changed from the proposed usage.");
        var required=new TreeSet<String>();var resolutions=new TreeMap<String,JsonNode>();
        if(input.issueResolutions!=null)for(Object value:input.issueResolutions){var item=json.tree(value);String issue=item.path("issueId").asText();if(resolutions.put(issue,item)!=null)throw new Problem(422,"INVALID_ISSUE_RESOLUTION","Duplicate issue resolution.");}
        var fieldMap=Map.of("DISTRIBUTION","distributions","TERRITORY","territories","PLATFORM","platforms","START_DATE","startDate","END_DATE","endDateExclusive");
        for(var issue:json.parse(row.get("issues"))){
            String id=issue.path("id").asText(),field=issue.path("field").asText();required.add(id);var resolution=resolutions.get(id);
            Object expected=fieldMap.containsKey(field)?finalUsage.path(fieldMap.get(field)):field.equals("DATES")||field.equals("DURATION")?Map.of("startDate",finalUsage.path("startDate"),"endDateExclusive",finalUsage.path("endDateExclusive")):finalUsage;
            if(resolution==null||!resolution.path("acknowledged").asBoolean()||!resolution.path("field").asText().equals(field)||!json.hash(Map.of(fieldMap.getOrDefault(field,"value"),resolution.path("resolvedValue"))).equals(json.hash(Map.of(fieldMap.getOrDefault(field,"value"),expected))))throw new Problem(422,"UNRESOLVED_ISSUES","Each issue needs a field resolution matching the final usage.");
        }
        if(!required.equals(resolutions.keySet())||!required.equals(new TreeSet<>(safe(input.resolvedIssueIds))))throw new Problem(422,"UNRESOLVED_ISSUES","Resolve exactly the stored issues.");
        var provenance=new LinkedHashMap<String,Object>(json.object(metadata.path("provenance")));for(String field:changed)provenance.put(Map.of("distributions","DISTRIBUTION","territories","TERRITORY","platforms","PLATFORM","startDate","START_DATE","endDateExclusive","END_DATE").get(field),"USER_EDITED");
        return Map.of("confirmed",true,"origin",input.origin,"interpretationId",input.interpretationId,"userEditedFields",changed,"provenance",provenance,"issueResolutions",safe(input.issueResolutions),"excludedRequirements",subset?unsupported:List.of(),"model",row.get("model_id"),"promptVersion",row.get("prompt_version"));
    }
    private static boolean nonempty(List<?> value){return value!=null&&!value.isEmpty();}
    private static <T> List<T> safe(List<T> value){return value==null?List.of():value;}
    public List<Map<String,Object>> revisions(Session session,UUID id){ownedCampaign(session,id,false);return db.queryForList("SELECT id,sequence,origin,created_at AS \"createdAt\" FROM campaign_revision WHERE campaign_id=? ORDER BY sequence DESC",id);}
    public Assessment assessScope(Map<String,Object> campaign,Scope use){
        UUID campaignId=(UUID)campaign.get("id");
        var policy=currentPolicy((UUID)campaign.get("merchant_id"));
        UUID policyId=(UUID)policy.get("id");
        var grantRows=db.queryForList("""
            SELECT e.*,p.merchant_id,p.family,p.version_number,s.status FROM entitlement e
            JOIN entitlement_state s ON s.entitlement_id=e.id JOIN policy_version p ON p.id=e.policy_version_id WHERE e.campaign_id=?
            """,campaignId);
        var grants=grantRows.stream().map(g->new Grant((UUID)g.get("id"),(UUID)g.get("merchant_id"),campaignId,(UUID)g.get("asset_id"),g.get("family").toString(),((Number)g.get("version_number")).intValue(),g.get("tier_code").toString(),json.scope(json.parse(g.get("scope_snapshot"))),((Number)g.get("credited_minor")).longValue(),g.get("status").equals("ACTIVE"),clauses(g.get("tier_code").toString()))).toList();
        var decisions=new ArrayList<Decision>();
        LocalDate anchor=((java.sql.Date)campaign.get("anchor_date")).toLocalDate();
        for(var asset:db.queryForList("SELECT asset_id FROM campaign_asset WHERE campaign_id=? ORDER BY position",campaignId)){
            UUID assetId=(UUID)asset.get("asset_id");
            List<Tier> tiers=db.queryForList("SELECT * FROM asset_tier WHERE asset_id=? AND policy_version_id=? ORDER BY rank",assetId,policyId).stream().map(t->{
                var coverage=json.parse(t.get("coverage"));
                var scope=new Scope(json.strings(coverage.path("distributions")),json.strings(coverage.path("territories")),json.strings(coverage.path("platforms")),anchor,anchor.plusDays(coverage.path("durationDays").asInt()));
                return new Tier(t.get("tier_code").toString(),((Number)t.get("rank")).intValue(),((Number)t.get("cumulative_minor")).longValue(),scope,clauses(t.get("tier_code").toString()));
            }).toList();
            decisions.add(engine.assess(new Context((UUID)campaign.get("merchant_id"),campaignId,assetId,policy.get("family").toString(),((Number)policy.get("version_number")).intValue(),anchor),new Intent(use,List.of(),List.of()),tiers,grants));
        }
        return engine.combine(decisions);
    }
    public Map<String,Object> currentPolicy(UUID merchant){return db.queryForMap("SELECT * FROM policy_version WHERE merchant_id=? ORDER BY version_number DESC LIMIT 1",merchant);}
    public Map<String,Object> assessment(Session session,UUID campaignId,UUID revisionId){
        var campaign=ownedCampaign(session,campaignId,false);var revision=revision(session,campaignId,revisionId);
        var assessed=assessScope(campaign,json.scope((JsonNode)revision.get("usage")));
        var rows=new ArrayList<Map<String,Object>>();
        for(var decision:assessed.rows()){
            var asset=db.queryForMap("SELECT slug,name,local_preview_path FROM asset WHERE id=?",decision.assetId());
            var row=new LinkedHashMap<String,Object>(json.object(decision));
            UUID evidencePolicy=(UUID)currentPolicy((UUID)campaign.get("merchant_id")).get("id");
            row.put("heldScope",null);
            if(decision.priorGrantId()!=null){
                var held=db.queryForMap("SELECT scope_snapshot,policy_version_id FROM entitlement WHERE id=? AND campaign_id=?",decision.priorGrantId(),campaignId);
                row.put("heldScope",json.parse(held.get("scope_snapshot")));
                if(decision.status()==Status.COVERED)evidencePolicy=(UUID)held.get("policy_version_id");
            }
            row.put("requestedScope",revision.get("usage"));row.put("policyVersionId",evidencePolicy);
            row.put("assetSlug",asset.get("slug"));row.put("assetName",asset.get("name"));row.put("previewPath",asset.get("local_preview_path"));rows.add(row);
        }
        return Map.of("revisionId",revisionId,"canQuote",assessed.canQuote(),"currency","USD","totalDeltaMinor",assessed.totalDeltaMinor(),"rows",rows,"paymentLocked",campaign.get("active_payment_id")!=null,"policyVersionId",currentPolicy((UUID)campaign.get("merchant_id")).get("id"));
    }
    public static List<String> clauses(String tier){return switch(tier){case "ORGANIC30"->List.of("V1-DIST-ORG","V1-TERR-US","V1-PLATFORMS","V1-WINDOW-30","V1-UPGRADE-CREDIT");case "PAID90"->List.of("V1-DIST-PAID","V1-TERR-USGB","V1-PLATFORMS","V1-WINDOW-90","V1-UPGRADE-CREDIT");case "GLOBAL365"->List.of("V1-DIST-PAID","V1-TERR-WORLD","V1-PLATFORMS","V1-WINDOW-365","V1-UPGRADE-CREDIT");default->throw new IllegalStateException("Unknown stored tier");};}
}
