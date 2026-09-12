package RefractoredVersion.Engine;

import RefractoredVersion.Shield.AllSlowerShield;
import RefractoredVersion.Shield.ExploreFutureActionShield;
import RefractoredVersion.Shield.ExploreFutureActionSmarterShield;
import RefractoredVersion.Shield.ExploreFutureBetterReferenceShield;
import RefractoredVersion.Shield.ExploreFutureRssOShield;
import RefractoredVersion.Shield.ExploreFutureRssShield;
import RefractoredVersion.Shield.StarkNativeShield;
import RefractoredVersion.Shield.StarkNativeWithRandomIDM;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.util.List;

public class ExploreFutureSlowerVehicle extends ExploreFutureEgo{
    private static final List<Action> SLOWER_SLOWER_SEQUENCE = List.of(Action.SLOWER, Action.SLOWER);

    @Override
    protected ShieldDecision verifyActionSafe(Action action) throws Exception {
        JavaMomentumConfig config = this.getEngine().config;
        ShieldType shieldType = config == null || config.getShieldType() == null
                ? ShieldType.ALL_SLOWER
                : config.getShieldType();
        switch (shieldType) {
            case ALL_SLOWER:
                AllSlowerShield allSlowerShield = new AllSlowerShield(this.getEngine());
                boolean allSlowerSafe = allSlowerShield.verifySafe(action);
                return shieldDecisionFrom(allSlowerSafe, allSlowerShield);
            case EXPLORE_FUTURE_ACTION:

                ExploreFutureActionShield exploreShield = new ExploreFutureActionShield(this.getEngine(), SLOWER_SLOWER_SEQUENCE);
                boolean exploreSafe = exploreShield.verifySafe(action);
                if (shouldPrintDiagnostics()) {
                    System.out.println("----------");
                    System.out.printf("%s explore future shield: ai_action=%s, fallback_sequence=%s%n",
                            exploreSafe ? "Safe" : "Unsafe",
                            action,
                            SLOWER_SLOWER_SEQUENCE);
                    System.out.println(exploreShield.getUnsafeDiagnosis());
                }
                if(exploreSafe){
                    rebuildCachedActions(SLOWER_SLOWER_SEQUENCE);
                    return shieldDecisionFrom(true, exploreShield);
                }
                return shieldDecisionFrom(false, exploreShield);
            case EXPLORE_FUTURE_BETTER_REFERENCE_ACTION:
                ExploreFutureBetterReferenceShield betterShield =
                        new ExploreFutureBetterReferenceShield(this.getEngine(), SLOWER_SLOWER_SEQUENCE);
                boolean betterSafe = betterShield.verifySafe(action);
                if (shouldPrintDiagnostics()) {
                    System.out.println("----------");
                    System.out.printf("%s better reference explore future shield: ai_action=%s, fallback_sequence=%s%n",
                            betterSafe ? "Safe" : "Unsafe",
                            action,
                            SLOWER_SLOWER_SEQUENCE);
                    System.out.println(betterShield.getUnsafeDiagnosis());
                }
                if(betterSafe){
                    rebuildCachedActions(SLOWER_SLOWER_SEQUENCE);
                    return shieldDecisionFrom(true, betterShield);
                }
                return shieldDecisionFrom(false, betterShield);
            case EXPLORE_FUTURE_RSS_ACTION:
                ExploreFutureRssShield rssShield =
                        new ExploreFutureRssShield(this.getEngine(), SLOWER_SLOWER_SEQUENCE);
                boolean rssSafe = rssShield.verifySafe(action);
                if (shouldPrintDiagnostics()) {
                    System.out.println("----------");
                    System.out.printf("%s rss explore future shield: ai_action=%s, fallback_sequence=%s%n",
                            rssSafe ? "Safe" : "Unsafe",
                            action,
                            SLOWER_SLOWER_SEQUENCE);
                    System.out.println(rssShield.getUnsafeDiagnosis());
                }
                if(rssSafe){
                    rebuildCachedActions(SLOWER_SLOWER_SEQUENCE);
                    return shieldDecisionFrom(true, rssShield);
                }
                return shieldDecisionFrom(false, rssShield);
            case EXPLORE_FUTURE_RSS_O_ACTION:
                ExploreFutureRssOShield rssOShield =
                        new ExploreFutureRssOShield(this.getEngine(), SLOWER_SLOWER_SEQUENCE);
                boolean rssOSafe = rssOShield.verifySafe(action);
                if (shouldPrintDiagnostics()) {
                    System.out.println("----------");
                    System.out.printf("%s original rss explore future shield: ai_action=%s, fallback_sequence=%s%n",
                            rssOSafe ? "Safe" : "Unsafe",
                            action,
                            SLOWER_SLOWER_SEQUENCE);
                    System.out.println(rssOShield.getUnsafeDiagnosis());
                }
                if(rssOSafe){
                    rebuildCachedActions(SLOWER_SLOWER_SEQUENCE);
                    return shieldDecisionFrom(true, rssOShield);
                }
                return shieldDecisionFrom(false, rssOShield);
            case STARK_NATIVE:
                StarkNativeShield starkNativeShield = new StarkNativeShield(this.getEngine());
                boolean starkNativeSafe = starkNativeShield.verifySafe(action);
                if (shouldPrintDiagnostics()) {
                    System.out.println("----------");
                    System.out.printf("%s stark native shield: ai_action=%s, fallback_sequence=%s%n",
                            starkNativeSafe ? "Safe" : "Unsafe",
                            action,
                            SLOWER_SLOWER_SEQUENCE);
                    System.out.println(starkNativeShield.getUnsafeDiagnosis());
                }
                if(starkNativeSafe){
                    rebuildCachedActions(SLOWER_SLOWER_SEQUENCE);
                    return shieldDecisionFrom(true, starkNativeShield);
                }
                return shieldDecisionFrom(false, starkNativeShield);
            case STARK_NATIVE_RANDOM_IDM:
                StarkNativeWithRandomIDM randomIdmShield = new StarkNativeWithRandomIDM(this.getEngine());
                boolean randomIdmSafe = randomIdmShield.verifySafe(action);
                if (shouldPrintDiagnostics()) {
                    System.out.println("----------");
                    System.out.printf("%s stark native random IDM shield: ai_action=%s, fallback_sequence=%s%n",
                            randomIdmSafe ? "Safe" : "Unsafe",
                            action,
                            SLOWER_SLOWER_SEQUENCE);
                    System.out.println(randomIdmShield.getUnsafeDiagnosis());
                }
                if(randomIdmSafe){
                    rebuildCachedActions(SLOWER_SLOWER_SEQUENCE);
                    return shieldDecisionFrom(true, randomIdmShield);
                }
                return shieldDecisionFrom(false, randomIdmShield);
            case EXPLORE_FUTURE_SMARTER_ACTION:
                ExploreFutureActionSmarterShield smarterShield =
                        new ExploreFutureActionSmarterShield(this.getEngine(), SLOWER_SLOWER_SEQUENCE);
                boolean smarterSafe = smarterShield.verifySafe(action);
                if (shouldPrintDiagnostics()) {
                    System.out.println("----------");
                    System.out.printf("%s smarter explore future shield: ai_action=%s, fallback_sequence=%s%n",
                            smarterSafe ? "Safe" : "Unsafe",
                            action,
                            SLOWER_SLOWER_SEQUENCE);
                    System.out.println(smarterShield.getUnsafeDiagnosis());
                }
                if(smarterSafe){
                    rebuildCachedActions(SLOWER_SLOWER_SEQUENCE);
                    return shieldDecisionFrom(true, smarterShield);
                }
                return shieldDecisionFrom(false, smarterShield);
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }
}
