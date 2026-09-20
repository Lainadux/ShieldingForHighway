package RefractoredVersion.Engine.ego;

import RefractoredVersion.Engine.Action;

import RefractoredVersion.Shield.ExploreFutureActionShield;
import RefractoredVersion.Shield.ExploreFutureBetterReferenceShield;
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
                ? ShieldType.STARK_NATIVE_RANDOM_IDM
                : config.getShieldType();
        switch (shieldType) {
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
            case STARK_NATIVE:
                StarkNativeShield starkNativeShield = new StarkNativeShield(this.getEngine());
                boolean starkNativeSafe = starkNativeShield.verifySafe(
                        action,
                        List.of(starkNativeShield.evaluateCutIn())
                );
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
                boolean randomIdmSafe = randomIdmShield.verifySafe(
                        action,
                        List.of(randomIdmShield.evaluateCutIn())
                );
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
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }
}
