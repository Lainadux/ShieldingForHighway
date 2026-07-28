package RefractoredVersion.Engine;

import RefractoredVersion.Shield.AllSlowerShield;
import RefractoredVersion.Shield.ExploreFutureActionShield;
import RefractoredVersion.Shield.ExploreFutureActionSmarterShield;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.util.List;

public class SlowerAndMinimalTrajectoryVehicle extends ExploreFutureSlowerVehicle{

    private static final List<Action> SLOWER_SLOWER_SEQUENCE = List.of(Action.SLOWER, Action.SLOWER);
    private static final List<Action> EMPTY_SEQUENCE = List.of();
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
                return new ShieldDecision(allSlowerSafe, allSlowerShield.getUnsafeDiagnosis());
            case EXPLORE_FUTURE_ACTION:

                ExploreFutureActionShield exploreShield =
                        new ExploreFutureActionShield(this.getEngine(), SLOWER_SLOWER_SEQUENCE);
                boolean exploreSafe = exploreShield.verifySafe(action);
                List<Action> verifiedSequence = SLOWER_SLOWER_SEQUENCE;
                if (!exploreSafe) {
                    exploreShield = new ExploreFutureActionShield(this.getEngine(), EMPTY_SEQUENCE);
                    exploreSafe = exploreShield.verifySafe(action);
                    verifiedSequence = EMPTY_SEQUENCE;
                }
                if (shouldPrintDiagnostics()) {
                    System.out.println("----------");
                    System.out.printf("%s explore future shield: ai_action=%s, fallback_sequence=%s%n",
                            exploreSafe ? "Safe" : "Unsafe",
                            action,
                            verifiedSequence);
                    System.out.println(exploreShield.getUnsafeDiagnosis());
                }
                if(exploreSafe){
                    rebuildCachedActions(SLOWER_SLOWER_SEQUENCE);
                    return new ShieldDecision(true, exploreShield.getUnsafeDiagnosis());
                }
                return new ShieldDecision(false, exploreShield.getUnsafeDiagnosis());
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
                    return new ShieldDecision(true, smarterShield.getUnsafeDiagnosis());
                }
                return new ShieldDecision(false, smarterShield.getUnsafeDiagnosis());
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }
}
