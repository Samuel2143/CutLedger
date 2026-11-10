package com.cutledger.interpretation;

import com.cutledger.bootstrap.*;
import com.cutledger.campaign.CampaignService;
import com.cutledger.web.Problem;
import com.cutledger.workspace.SessionService;
import com.cutledger.workspace.SessionService.Session;
import jakarta.validation.constraints.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterpretationService {
    final JdbcTemplate db;final DomainJson json;final Clock clock;final CampaignService campaigns;final SessionService sessions;final AppConfig config;
    public InterpretationService(JdbcTemplate db,DomainJson json,Clock clock,CampaignService campaigns,SessionService sessions,AppConfig config){this.db=db;this.json=json;this.clock=clock;this.campaigns=campaigns;this.sessions=sessions;this.config=config;}
    public record Input(@NotBlank @Size(max=4000) String brief,@NotNull UUID baseRevisionId){}
    @Transactional public Map<String,Object> create(Session session,UUID campaign,UUID key,Input input){
        var current=campaigns.ownedCampaign(session,campaign,true);var payload=Map.of("campaignId",campaign,"input",input);
        UUID prior=campaigns.dedup(session,"INTERPRET",key,payload);if(prior!=null)return view(session,prior);
        if(!config.aiConfigured())throw new Problem(503,"AI_UNAVAILABLE","Hosted interpretation is unavailable. Manual entry remains available.");
        if(current.get("active_payment_id")!=null)throw new Problem(409,"ACTIVE_PAYMENT","Resolve the existing checkout before interpreting another revision.");
        if(!input.baseRevisionId.equals(current.get("latest_revision_id")))throw new Problem(409,"STALE_REVISION","Interpret the latest confirmed revision.");
        var base=campaigns.revision(session,campaign,input.baseRevisionId).get("usage");var reference=LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String cache=json.hash(Map.of("briefHash",json.hashText(input.brief),"baseHash",json.hash(base),"referenceDate",reference,"model",config.aiModel(),"prompt",GeminiHttp.PROMPT_VERSION,"schema",1));
        var existing=db.queryForList("SELECT id FROM interpretation WHERE workspace_id=? AND campaign_id=? AND base_revision_id=? AND cache_key=? AND status<>'FAILED' ORDER BY created_at DESC LIMIT 1",session.workspaceId(),campaign,input.baseRevisionId,cache);
        if(!existing.isEmpty()){UUID id=(UUID)existing.get(0).get("id");campaigns.remember(session,"INTERPRET",key,payload,id);return view(session,id);}
        if(!sessions.takeQuota(session.workspaceId().toString(),"AI_SUBMIT",20,Duration.ofHours(1)))throw new Problem(429,"AI_QUOTA","Interpretation limit reached. Try later or use manual entry.");
        UUID id=UUID.randomUUID();var now=Timestamp.from(clock.instant());
        db.update("""
            INSERT INTO interpretation(id,workspace_id,campaign_id,base_revision_id,brief_text,brief_hash,reference_date,request_key,request_hash,status,model_id,prompt_version,schema_version,run_after,created_at,base_usage_hash,cache_key)
            VALUES (?,?,?,?,?,?,?,?,?,'PENDING',?,?,1,?,?,?,?)
            """,id,session.workspaceId(),campaign,input.baseRevisionId,input.brief,json.hashText(input.brief),java.sql.Date.valueOf(reference),key,json.hash(payload),config.aiModel(),GeminiHttp.PROMPT_VERSION,now,now,json.hash(base),cache);
        campaigns.remember(session,"INTERPRET",key,payload,id);return view(session,id);
    }
    Map<String,Object> row(Session session,UUID id){var rows=db.queryForList("SELECT * FROM interpretation WHERE id=? AND workspace_id=?",id,session.workspaceId());if(rows.isEmpty())throw Problem.notFound();return rows.get(0);}
    public Map<String,Object> view(Session session,UUID id){
        var row=row(session,id);var result=new LinkedHashMap<String,Object>();
        for(String name:List.of("id","status"))result.put(name,row.get(name));
        result.put("brief",row.get("brief_text"));result.put("baseRevisionId",row.get("base_revision_id"));result.put("model",row.get("model_id"));result.put("promptVersion",row.get("prompt_version"));result.put("referenceDate",row.get("reference_date").toString());
        for(var field:Map.of("proposedEdits","proposed_edits","normalizedCandidate","normalized_candidate","issues","issues","evidence","evidence","result","result_metadata").entrySet())result.put(field.getKey(),row.get(field.getValue())==null?null:json.parse(row.get(field.getValue())));
        result.put("failureCode",row.get("failure_code"));result.put("retryAt",((Timestamp)row.get("run_after")).toInstant());result.put("attempts",row.get("attempts"));return result;
    }
}
