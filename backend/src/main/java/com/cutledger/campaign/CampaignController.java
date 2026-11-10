package com.cutledger.campaign;

import com.cutledger.bootstrap.DomainJson;
import com.cutledger.quote.QuoteService;
import com.cutledger.workspace.SessionService.Session;
import com.cutledger.web.Problem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CampaignController {
    private final CampaignService campaigns;private final QuoteService quotes;private final JdbcTemplate db;private final DomainJson json;
    public CampaignController(CampaignService campaigns,QuoteService quotes,JdbcTemplate db,DomainJson json){this.campaigns=campaigns;this.quotes=quotes;this.db=db;this.json=json;}
    @PostMapping("/campaigns/{id}/revisions")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Object confirm(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session,@RequestHeader("If-Match") String match,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody CampaignService.Confirmation input){return campaigns.confirm(session,id,match,key,input);}
    @GetMapping("/campaigns/{id}/revisions") Object revisions(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session){return campaigns.revisions(session,id);}
    @GetMapping("/campaigns/{id}/revisions/{revision}") Object revision(@PathVariable UUID id,@PathVariable UUID revision,@RequestAttribute("workspaceSession") Session session){return campaigns.revision(session,id,revision);}
    @GetMapping("/campaigns/{id}/revisions/{revision}/assessment") Object assessment(@PathVariable UUID id,@PathVariable UUID revision,@RequestAttribute("workspaceSession") Session session){return campaigns.assessment(session,id,revision);}
    public record QuoteInput(@NotNull UUID revisionId){}
    @PostMapping("/campaigns/{id}/quotes") Object quote(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session,@RequestHeader("If-Match") String match,@RequestHeader("Idempotency-Key") UUID key,@Valid @RequestBody QuoteInput input){
        var result=quotes.create(session,id,input.revisionId,match,key);return org.springframework.http.ResponseEntity.status(result.containsKey("kind")?200:201).body(result);
    }
    @GetMapping("/quotes/{id}") Object quote(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session){return quotes.view(session,id);}
    @GetMapping("/policies/{id}") Object policy(@PathVariable UUID id){
        var rows=db.queryForList("SELECT id,family,version_number,clauses::text FROM policy_version WHERE id=?",id);if(rows.isEmpty())throw Problem.notFound();
        var row=rows.get(0);return Map.of("id",id,"family",row.get("family"),"version",row.get("version_number"),"clauses",json.parse(row.get("clauses")));
    }
    @GetMapping("/campaigns/{id}/entitlements") Object entitlements(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session){
        campaigns.ownedCampaign(session,id,false);
        return db.queryForList("""
            SELECT e.id,e.asset_id AS "assetId",a.name AS "assetName",e.tier_code AS "tierCode",e.scope_snapshot::text AS "scopeSnapshot",
            e.credited_minor AS "creditedMinor",e.paid_delta_minor AS "paidDeltaMinor",e.source,e.source_payment_id AS "sourcePaymentId",
            e.accepted_revision_id AS "acceptedRevisionId",e.policy_version_id AS "policyVersionId",e.created_at AS "createdAt",s.status
            FROM entitlement e JOIN asset a ON a.id=e.asset_id JOIN entitlement_state s ON s.entitlement_id=e.id WHERE e.campaign_id=? ORDER BY e.created_at,a.slug
            """,id).stream().map(row->{row.put("scopeSnapshot",json.parse(row.get("scopeSnapshot")));return row;}).toList();
    }
    @GetMapping("/campaigns/{id}/activity") Object activity(@PathVariable UUID id,@RequestAttribute("workspaceSession") Session session){campaigns.ownedCampaign(session,id,false);return db.queryForList("SELECT id,event_type AS \"eventType\",safe_details::text AS details,created_at AS \"createdAt\" FROM audit_event WHERE campaign_id=? ORDER BY created_at DESC LIMIT 50",id);}
}
