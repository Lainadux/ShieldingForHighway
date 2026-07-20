package RefractoredVersion.Engine;

import RefractoredVersion.Shield.AllSlowerShield;
import RefractoredVersion.Shield.ExploreFutureActionShield;
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
                return new ShieldDecision(allSlowerSafe, allSlowerShield.getUnsafeDiagnosis());
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
                    return new ShieldDecision(true, exploreShield.getUnsafeDiagnosis());
                }
                return new ShieldDecision(false, exploreShield.getUnsafeDiagnosis());
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }
}
