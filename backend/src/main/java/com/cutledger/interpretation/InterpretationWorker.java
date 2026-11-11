package com.cutledger.interpretation;

import com.cutledger.bootstrap.*;
import com.cutledger.workspace.SessionService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class InterpretationWorker {
    private final JdbcTemplate db;private final DomainJson json;private final Clock clock;private final AppConfig config;private final SessionService sessions;private final InterpretationProvider provider;private final InterpretationValidator validator;private final TransactionTemplate tx;private final boolean enabled;
    private final String owner=UUID.randomUUID().toString();
    public InterpretationWorker(JdbcTemplate db,DomainJson json,Clock clock,AppConfig config,SessionService sessions,InterpretationProvider provider,InterpretationValidator validator,org.springframework.transaction.PlatformTransactionManager manager,@Value("${cutledger.jobs.enabled:true}") boolean enabled){this.db=db;this.json=json;this.clock=clock;this.config=config;this.sessions=sessions;this.provider=provider;this.validator=validator;this.tx=new TransactionTemplate(manager);this.enabled=enabled;}
    @Scheduled(fixedDelay=1000) public void scheduled(){if(enabled)drainOne();}
    public boolean drainOne(){
        var job=tx.execute(status->{
            var rows=db.queryForList("""
                SELECT i.*,r.usage::text AS base_usage,c.anchor_date,c.usage_timezone,c.latest_revision_id,c.active_payment_id FROM interpretation i
                JOIN campaign_revision r ON r.id=i.base_revision_id JOIN campaign c ON c.id=i.campaign_id JOIN workspace w ON w.id=i.workspace_id
                WHERE i.completed_at IS NULL AND i.run_after<=? AND (i.lease_until IS NULL OR i.lease_until<?) AND w.expires_at>?
                ORDER BY i.run_after,i.id FOR UPDATE OF i SKIP LOCKED LIMIT 1
                """,now(),now(),now());
            if(rows.isEmpty())return null;var row=rows.get(0);UUID id=(UUID)row.get("id");
            if(!row.get("model_id").equals(config.aiModel())||!row.get("prompt_version").equals(GeminiHttp.PROMPT_VERSION)){failUnleased(id,"AI_MODEL_VERSION_CHANGED");return null;}
            if(!row.get("base_revision_id").equals(row.get("latest_revision_id"))||row.get("active_payment_id")!=null){failUnleased(id,"AI_CONTEXT_CHANGED");return null;}
            if(((Number)row.get("attempts")).intValue()>=3){failUnleased(id,"AI_ATTEMPT_LIMIT");return null;}
            // Reserve a durable global slot: <=12/minute, below the observed15RPM free quota.
            int slot=db.update("UPDATE model_dispatch_gate SET next_dispatch_at=? WHERE singleton=true AND next_dispatch_at<=?",Timestamp.from(clock.instant().plusSeconds(5)),now());
            if(slot==0)return null;
            // Count every dispatch, including repair/retry/recovery, before making an external call.
            if(!sessions.takeQuota("GLOBAL","AI_CALL",config.aiDailyRequestLimit(),Duration.ofDays(1))||!sessions.takeQuota(row.get("workspace_id").toString(),"AI_CALL",60,Duration.ofDays(1))){failUnleased(id,"AI_QUOTA");return null;}
            db.update("UPDATE interpretation SET status='RUNNING',attempts=attempts+1,lease_owner=?,lease_until=? WHERE id=?",owner,Timestamp.from(clock.instant().plusSeconds(45)),id);return row;
        });
        if(job==null)return false;UUID id=(UUID)job.get("id");
        try{
            var base=json.parse(job.get("base_usage"));
            if(!json.hash(base).equals(job.get("base_usage_hash"))||!json.hashText(job.get("brief_text").toString()).equals(job.get("brief_hash")))throw new InterpretationValidator.InvalidOutput("CONTEXT_HASH_MISMATCH");
            var context=Map.<String,Object>of("schemaVersion",1,"promptVersion",job.get("prompt_version"),"brief",job.get("brief_text"),"briefHash",job.get("brief_hash"),"baseUsage",base,"campaignAnchorDate",job.get("anchor_date").toString(),"referenceDate",job.get("reference_date").toString(),"usageTimezone",job.get("usage_timezone"),"supportedDimensions",Map.of("distributions",List.of("ORGANIC","PAID_ADS"),"platforms",List.of("INSTAGRAM","YOUTUBE","WEBSITE"),"territories","ISO countries or WORLDWIDE"));
            String repair=((Number)job.get("repair_attempts")).intValue()>0?Objects.toString(job.get("failure_code"),"SCHEMA_INVALID"):null;
            var response=provider.extract(new InterpretationProvider.ExtractionInput(context,repair));if(!response.model().equals(job.get("model_id")))throw new InterpretationProvider.Failure("AI_MODEL_MISMATCH",false,1);var edits=validator.parse(response.text());
            var result=validator.validate(job.get("brief_text").toString(),json.scope(base),((java.sql.Date)job.get("anchor_date")).toLocalDate(),edits);
            var metadata=new LinkedHashMap<String,Object>(json.object(result));metadata.put("usage",response.usage());metadata.put("model",response.model());metadata.put("latencyMs",response.latencyMs());
            db.update("""
                UPDATE interpretation SET status=?,proposed_edits=?::jsonb,normalized_candidate=?::jsonb,issues=?::jsonb,evidence=?::jsonb,result_metadata=?::jsonb,provider_reference=?,latency_ms=?,completed_at=?,lease_owner=NULL,lease_until=NULL,failure_code=NULL
                WHERE id=? AND lease_owner=?
                """,result.status(),json.write(edits),json.write(json.scopeSnapshot(result.normalizedCandidate())),json.write(result.issues()),json.write(result.evidence()),json.write(metadata),response.referenceHash(),response.latencyMs(),now(),id,owner);
        }catch(InterpretationValidator.InvalidOutput invalid){retryOrFail(job,invalid.code,false,1);}
        catch(InterpretationProvider.Failure failure){db.update("UPDATE interpretation SET result_metadata=?::jsonb WHERE id=? AND lease_owner=?",json.write(Map.of("providerFailure",failure.details)),id,owner);retryOrFail(job,failure.code,failure.transientFailure,failure.retryAfterSeconds);}
        catch(RuntimeException failure){fail(id,"AI_INTERNAL_FAILURE");}
        return true;
    }
    private void retryOrFail(Map<String,Object> job,String code,boolean transientFailure,int retryAfter){
        String column=transientFailure?"transient_retries":"repair_attempts";
        boolean repairable=!transientFailure&&!code.startsWith("AI_")&&!code.equals("CONTEXT_HASH_MISMATCH");
        if((((Number)job.get("attempts")).intValue()+1)<3&&((transientFailure&&((Number)job.get(column)).intValue()==0)||(repairable&&((Number)job.get(column)).intValue()==0))){
            long seconds=Math.max(retryAfter,1)+java.util.concurrent.ThreadLocalRandom.current().nextInt(1,4);
            db.update("UPDATE interpretation SET status='PENDING',"+column+"="+column+"+1,failure_code=?,run_after=?,lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?",code,Timestamp.from(clock.instant().plusSeconds(seconds)),job.get("id"),owner);
        }else fail((UUID)job.get("id"),code);
    }
    private Timestamp now(){return Timestamp.from(clock.instant());}
    private void fail(UUID id,String code){db.update("UPDATE interpretation SET status='FAILED',failure_code=?,completed_at=?,lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?",code,now(),id,owner);}
    private void failUnleased(UUID id,String code){db.update("UPDATE interpretation SET status='FAILED',failure_code=?,completed_at=?,lease_owner=NULL,lease_until=NULL WHERE id=?",code,now(),id);}
}
