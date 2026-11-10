package com.cutledger.rights;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.cutledger.rights.RightsEngine.*;

class RightsEngineTest {
    final RightsEngine engine=new RightsEngine();
    final UUID merchant=UUID.randomUUID(),campaign=UUID.randomUUID();
    final List<UUID> assets=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
    final LocalDate anchor=LocalDate.of(2026,11,15);
    final long[][] prices={{1000,1800,4000},{2000,3200,6000},{1500,3000,6000}};
    Context context(int i){return new Context(merchant,campaign,assets.get(i),"CUTLEDGER_CAMPAIGN_V1",1,anchor);}
    List<Tier> tiers(int i){return List.of(
        new Tier("ORGANIC30",0,prices[i][0],tierScope(anchor,30,Set.of("ORGANIC"),Set.of("US")),List.of("V1-DIST-ORG","V1-TERR-US","V1-WINDOW-30")),
        new Tier("PAID90",1,prices[i][1],tierScope(anchor,90,Set.of("ORGANIC","PAID_ADS"),Set.of("US","GB")),List.of("V1-DIST-PAID","V1-TERR-USGB","V1-WINDOW-90","V1-UPGRADE-CREDIT")),
        new Tier("GLOBAL365",2,prices[i][2],tierScope(anchor,365,Set.of("ORGANIC","PAID_ADS"),Set.of("WORLDWIDE")),List.of("V1-DIST-PAID","V1-TERR-WORLD","V1-WINDOW-365")));}
    Grant grant(int i,int tier){var t=tiers(i).get(tier);return new Grant(UUID.randomUUID(),merchant,campaign,assets.get(i),"CUTLEDGER_CAMPAIGN_V1",1,t.code(),t.scope(),t.cumulativeMinor(),true,t.clauseIds());}
    List<Grant> seeds(){return List.of(grant(0,0),grant(1,0),grant(2,2));}
    Intent intent(Set<String> distribution,Set<String> territory,int days){return new Intent(new Scope(distribution,territory,Set.of("INSTAGRAM"),anchor,anchor.plusDays(days)),List.of(),List.of());}
    Intent paid90(){return intent(Set.of("PAID_ADS"),Set.of("US","GB"),90);}
    Assessment assess(Intent intent,List<Grant> grants){return engine.combine(java.util.stream.IntStream.range(0,3).mapToObj(i->engine.assess(context(i),intent,tiers(i),grants)).toList());}

    @Test void mainRevisionChargesOnlyEightPlusTwelveWithOneCoveredAsset() {
        var result=assess(paid90(),seeds());
        assertThat(result.totalDeltaMinor()).isEqualTo(2000);assertThat(result.currency()).isEqualTo("USD");assertThat(result.canQuote()).isTrue();
        assertThat(result.rows()).extracting(Decision::deltaMinor).containsExactly(800L,1200L,0L);
        assertThat(result.rows().get(0).reasonCodes()).containsExactlyInAnyOrder(Reason.DISTRIBUTION_GAP,Reason.TERRITORY_GAP,Reason.DATE_GAP);
        assertThat(result.rows().get(2).status()).isEqualTo(Status.COVERED);
    }
    @Test void grantsAccumulateWithoutChangingOriginalScopeAndRepeatedUseCreatesNoPurchase() {
        var initial=seeds();var purchased=new ArrayList<>(initial);purchased.add(grant(0,1));purchased.add(grant(1,1));
        var repeat=assess(paid90(),purchased);
        assertThat(repeat.totalDeltaMinor()).isZero();assertThat(repeat.canQuote()).isFalse();assertThat(repeat.rows()).allMatch(d->d.status()==Status.COVERED);
        assertThat(assess(intent(Set.of("PAID_ADS"),Set.of("WORLDWIDE"),365),initial).totalDeltaMinor()).isEqualTo(7000);
        assertThat(assess(intent(Set.of("PAID_ADS"),Set.of("WORLDWIDE"),365),purchased).totalDeltaMinor()).isEqualTo(5000);
        assertThat(initial.get(0).scope().endDateExclusive()).isEqualTo(anchor.plusDays(30));
        assertThat(assess(intent(Set.of("ORGANIC"),Set.of("US"),30),purchased).totalDeltaMinor()).isZero();
    }
    @Test void unsupportedAndUncertainRequirementsPreventCampaignQuote() {
        var use=paid90().scope();
        for(var intent:List.of(new Intent(use,List.of("television broadcast"),List.of()),new Intent(use,List.of("AI training"),List.of()),new Intent(use,List.of(),List.of("Europe is ambiguous")))) {
            var result=assess(intent,seeds());assertThat(result.canQuote()).isFalse();assertThat(result.totalDeltaMinor()).isZero();
            assertThat(result.rows()).noneMatch(d->d.status()==Status.COVERED||d.status()==Status.UPGRADE_REQUIRED);
        }
        var invalid=new Intent(new Scope(Set.of("PAID_ADS"),Set.of("EUROPE"),Set.of("INSTAGRAM"),anchor,anchor.plusDays(90)),List.of(),List.of());
        assertThat(assess(invalid,seeds()).canQuote()).isFalse();
    }
    @Test void dateBoundariesAreExclusiveAndStartNeverRestartsWindow() {
        assertThat(assess(intent(Set.of("ORGANIC"),Set.of("US"),30),seeds()).canQuote()).isFalse();
        assertThat(assess(intent(Set.of("ORGANIC"),Set.of("US"),31),seeds()).totalDeltaMinor()).isEqualTo(2000);
        var moved=new Intent(new Scope(Set.of("PAID_ADS"),Set.of("US"),Set.of("INSTAGRAM"),anchor.plusDays(1),anchor.plusDays(91)),List.of(),List.of());
        assertThat(assess(moved,seeds()).totalDeltaMinor()).isEqualTo(7000);
        for(var scope:List.of(new Scope(Set.of("ORGANIC"),Set.of("US"),Set.of("INSTAGRAM"),anchor.minusDays(1),anchor.plusDays(29)),new Scope(Set.of("ORGANIC"),Set.of("US"),Set.of("INSTAGRAM"),anchor,anchor),new Scope(Set.of("ORGANIC"),Set.of("US"),Set.of("INSTAGRAM"),anchor,anchor.plusDays(366))))
            assertThat(assess(new Intent(scope,List.of(),List.of()),seeds()).canQuote()).isFalse();
    }
    @Test void complementaryGrantsCannotBeUnionedIntoWorldwidePaidCoverage() {
        var organicWorld=new Grant(UUID.randomUUID(),merchant,campaign,assets.get(0),"CUTLEDGER_CAMPAIGN_V1",1,"ORGANIC30",tierScope(anchor,90,Set.of("ORGANIC"),Set.of("WORLDWIDE")),1000,true,List.of());
        var paidUs=new Grant(UUID.randomUUID(),merchant,campaign,assets.get(0),"CUTLEDGER_CAMPAIGN_V1",1,"PAID90",tierScope(anchor,90,Set.of("PAID_ADS"),Set.of("US")),1800,true,List.of());
        var result=engine.assess(context(0),intent(Set.of("PAID_ADS"),Set.of("WORLDWIDE"),90),tiers(0),List.of(organicWorld,paidUs));
        assertThat(result.status()).isEqualTo(Status.UPGRADE_REQUIRED);assertThat(result.deltaMinor()).isEqualTo(2200);assertThat(result.targetTier()).isEqualTo("GLOBAL365");
    }
    @Test void historicalCoverageSurvivesButCrossVersionCreditRequiresReview() {
        var old=grant(0,0);var changed=new Context(merchant,campaign,assets.get(0),"CUTLEDGER_CAMPAIGN_V1",2,anchor);
        assertThat(engine.assess(changed,intent(Set.of("ORGANIC"),Set.of("US"),30),tiers(0),List.of(old)).status()).isEqualTo(Status.COVERED);
        assertThat(engine.assess(changed,paid90(),tiers(0),List.of(old)).status()).isEqualTo(Status.REVIEW_REQUIRED);
    }
    @Test void unrelatedCampaignAndMerchantGrantsCannotProvideCoverageOrCredit() {
        var foreign=new Grant(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),assets.get(0),"CUTLEDGER_CAMPAIGN_V1",1,"GLOBAL365",tiers(0).get(2).scope(),4000,true,List.of());
        var result=engine.assess(context(0),paid90(),tiers(0),List.of(foreign));assertThat(result.deltaMinor()).isEqualTo(1800);assertThat(result.priorGrantId()).isNull();
    }
    @Test void creditIsHighestEligibleValueNeverSumOfPastPayments() {
        var grants=new ArrayList<>(seeds());grants.add(grant(0,0));
        assertThat(assess(paid90(),grants).totalDeltaMinor()).isEqualTo(2000);
        var wrong=new Grant(UUID.randomUUID(),merchant,campaign,assets.get(0),"CUTLEDGER_CAMPAIGN_V1",1,"ORGANIC30",tiers(0).get(0).scope(),9999,true,List.of());
        assertThat(engine.assess(context(0),paid90(),tiers(0),List.of(wrong)).status()).isEqualTo(Status.REVIEW_REQUIRED);
    }
    @Test void everyProposedPurchasedTierCoversEachRequestedDimensionForGeneratedSubsets() {
        var random=new Random(2143);
        for(int n=0;n<500;n++) {
            int days=1+random.nextInt(365);
            var use=intent(Set.of(random.nextBoolean()?"ORGANIC":"PAID_ADS"),Set.of(random.nextBoolean()?"US":"GB"),days);
            var result=engine.assess(context(0),use,tiers(0),List.of(grant(0,0)));
            if(result.status()==Status.UPGRADE_REQUIRED){assertThat(result.purchasedScope().covers(use.scope())).isTrue();assertThat(result.deltaMinor()).isPositive();}
            else assertThat(result.status()).isEqualTo(Status.COVERED);
        }
    }
}
