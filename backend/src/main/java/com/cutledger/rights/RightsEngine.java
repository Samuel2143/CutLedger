package com.cutledger.rights;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure merchant-policy evaluator. No AI, database, clock, or provider authority. */
public final class RightsEngine {
    public enum Status { COVERED, UPGRADE_REQUIRED, UNSUPPORTED, NEEDS_CLARIFICATION, REVIEW_REQUIRED }
    public enum Reason { DISTRIBUTION_GAP, TERRITORY_GAP, PLATFORM_GAP, DATE_GAP, UNSUPPORTED_USE, AMBIGUOUS_INTENT, POLICY_MIGRATION_REQUIRED, PAYMENT_REVIEW }
    public record Scope(Set<String> distributions, Set<String> territories, Set<String> platforms, LocalDate startDate, LocalDate endDateExclusive) {
        public Scope {
            distributions=Set.copyOf(distributions); territories=Set.copyOf(territories); platforms=Set.copyOf(platforms);
            Objects.requireNonNull(startDate);Objects.requireNonNull(endDateExclusive);
        }
        public boolean covers(Scope use) {
            return distributions.containsAll(use.distributions) && (territories.equals(Set.of("WORLDWIDE")) || territories.containsAll(use.territories))
                && platforms.containsAll(use.platforms) && !use.startDate.isBefore(startDate) && !use.endDateExclusive.isAfter(endDateExclusive);
        }
    }
    public record Context(UUID merchantId, UUID campaignId, UUID assetId, String policyFamily, int policyVersion, LocalDate anchor) {}
    public record Intent(Scope scope, List<String> unsupportedRequirements, List<String> unresolvedIssues) {
        public Intent {unsupportedRequirements=List.copyOf(unsupportedRequirements);unresolvedIssues=List.copyOf(unresolvedIssues);}
    }
    public record Tier(String code, int rank, long cumulativeMinor, Scope scope, List<String> clauseIds) {
        public Tier {clauseIds=List.copyOf(clauseIds);if(rank<0||cumulativeMinor<0)throw new IllegalArgumentException("Invalid tier");}
    }
    public record Grant(UUID id, UUID merchantId, UUID campaignId, UUID assetId, String policyFamily, int policyVersion,
                        String tierCode, Scope scope, long creditedMinor, boolean active, List<String> clauseIds) {
        public Grant {clauseIds=List.copyOf(clauseIds);if(creditedMinor<0)throw new IllegalArgumentException("Invalid credit");}
    }
    public record Decision(UUID assetId, Status status, UUID priorGrantId, String heldTier, String targetTier,
                           long priorCreditMinor, long targetCumulativeMinor, long deltaMinor, Scope purchasedScope,
                           Set<Reason> reasonCodes, List<String> clauseIds) {
        public Decision {reasonCodes=Set.copyOf(reasonCodes);clauseIds=List.copyOf(clauseIds);}
    }
    public record Assessment(List<Decision> rows, boolean canQuote, long totalDeltaMinor, String currency) {
        public Assessment {rows=List.copyOf(rows);}
    }
    private static final Set<String> ISO=Set.of(Locale.getISOCountries());
    private static final Set<String> DISTRIBUTIONS=Set.of("ORGANIC","PAID_ADS"),PLATFORMS=Set.of("INSTAGRAM","YOUTUBE","WEBSITE");

    public Decision assess(Context context,Intent intent,List<Tier> tiers,List<Grant> allGrants) {
        if(!intent.unsupportedRequirements.isEmpty())return blocked(context,Status.UNSUPPORTED,Reason.UNSUPPORTED_USE);
        if(!intent.unresolvedIssues.isEmpty())return blocked(context,Status.NEEDS_CLARIFICATION,Reason.AMBIGUOUS_INTENT);
        Scope use=intent.scope;
        if(use==null||use.distributions.isEmpty()||use.territories.isEmpty()||use.platforms.isEmpty())
            return blocked(context,Status.NEEDS_CLARIFICATION,Reason.AMBIGUOUS_INTENT);
        long days=ChronoUnit.DAYS.between(use.startDate,use.endDateExclusive);
        if(!DISTRIBUTIONS.containsAll(use.distributions)||!PLATFORMS.containsAll(use.platforms)||
            !(use.territories.equals(Set.of("WORLDWIDE"))||ISO.containsAll(use.territories))||
            days<1||days>365||use.startDate.isBefore(context.anchor)||use.endDateExclusive.isAfter(context.anchor.plusDays(365)))
            return blocked(context,Status.UNSUPPORTED,Reason.UNSUPPORTED_USE);
        var owned=allGrants.stream().filter(g->g.merchantId.equals(context.merchantId)&&g.campaignId.equals(context.campaignId)&&g.assetId.equals(context.assetId)).toList();
        var active=owned.stream().filter(Grant::active).sorted(Comparator.comparingLong(Grant::creditedMinor).reversed().thenComparing(g->g.id.toString())).toList();
        // All dimensions must fit ONE actual grant, including a historical policy snapshot.
        var covered=active.stream().filter(g->g.scope.covers(use)).findFirst();
        if(covered.isPresent()) {
            Grant grant=covered.get();
            return new Decision(context.assetId,Status.COVERED,grant.id,grant.tierCode,null,grant.creditedMinor,grant.creditedMinor,0,null,Set.of(),grant.clauseIds);
        }
        if(owned.stream().anyMatch(g->!g.active))return blocked(context,Status.REVIEW_REQUIRED,Reason.PAYMENT_REVIEW);
        var candidates=tiers.stream().sorted(Comparator.comparingInt(Tier::rank)).filter(t->t.scope.covers(use)).toList();
        if(candidates.isEmpty())return blocked(context,Status.UNSUPPORTED,Reason.UNSUPPORTED_USE);
        if(active.stream().anyMatch(g->!g.policyFamily.equals(context.policyFamily)||g.policyVersion!=context.policyVersion))
            return blocked(context,Status.REVIEW_REQUIRED,Reason.POLICY_MIGRATION_REQUIRED);
        Tier target=candidates.get(0);
        Grant prior=active.stream().filter(g->g.policyFamily.equals(context.policyFamily)&&g.policyVersion==context.policyVersion).findFirst().orElse(null);
        long credit=prior==null?0:prior.creditedMinor;
        if(credit>=target.cumulativeMinor)return blocked(context,Status.REVIEW_REQUIRED,Reason.POLICY_MIGRATION_REQUIRED);
        var reasons=EnumSet.noneOf(Reason.class);
        if(prior==null||!prior.scope.distributions.containsAll(use.distributions))reasons.add(Reason.DISTRIBUTION_GAP);
        if(prior==null||!(prior.scope.territories.equals(Set.of("WORLDWIDE"))||prior.scope.territories.containsAll(use.territories)))reasons.add(Reason.TERRITORY_GAP);
        if(prior==null||!prior.scope.platforms.containsAll(use.platforms))reasons.add(Reason.PLATFORM_GAP);
        if(prior==null||use.startDate.isBefore(prior.scope.startDate)||use.endDateExclusive.isAfter(prior.scope.endDateExclusive))reasons.add(Reason.DATE_GAP);
        return new Decision(context.assetId,Status.UPGRADE_REQUIRED,prior==null?null:prior.id,prior==null?null:prior.tierCode,target.code,
            credit,target.cumulativeMinor,Math.subtractExact(target.cumulativeMinor,credit),target.scope,reasons,target.clauseIds);
    }
    public Assessment combine(List<Decision> decisions) {
        boolean safe=decisions.stream().allMatch(d->d.status==Status.COVERED||d.status==Status.UPGRADE_REQUIRED);
        long total=0;
        for(var decision:decisions)total=Math.addExact(total,decision.deltaMinor);
        // Explanatory row deltas may remain visible, but unsafe campaigns have no payable total.
        return new Assessment(decisions,safe&&total>0,safe?total:0,"USD");
    }
    private Decision blocked(Context context,Status status,Reason reason) {
        return new Decision(context.assetId,status,null,null,null,0,0,0,null,Set.of(reason),List.of("V1-EXCLUSIONS"));
    }
    public static Scope tierScope(LocalDate anchor,int days,Set<String> distributions,Set<String> territories) {
        return new Scope(distributions,territories,PLATFORMS,anchor,anchor.plusDays(days));
    }
}
