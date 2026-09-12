package RefractoredVersion.Engine;

import RefractoredVersion.Shield.StarkNativeExploreFutureShield;
import RefractoredVersion.Shield.StarkNativeShield;
import RefractoredVersion.Shield.StarkNativeWithRandomIDM;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;
import it.unicam.quasylab.jspear.distl.DisTLFormula;

import java.util.List;

public class CascadedRecoExploreVehicle extends ExploreFutureEgo {
    private static final List<Action> SLOWER_SLOWER_SEQUENCE = List.of(Action.SLOWER, Action.SLOWER);

    @Override
    protected ShieldDecision verifyActionSafe(Action action) throws Exception {
        JavaMomentumConfig config = this.getEngine().config;
        ShieldType shieldType = config == null || config.getShieldType() == null
                ? ShieldType.STARK_NATIVE
                : config.getShieldType();
        return switch (shieldType) {
            case STARK_NATIVE -> verifyWithDeterministicNative(action);
            case STARK_NATIVE_RANDOM_IDM -> verifyWithRandomIdmNative(action);
            default -> throw new IllegalArgumentException(
                    "CascadedRecoExploreVehicle only supports STARK_NATIVE and STARK_NATIVE_RANDOM_IDM, got: "
                            + shieldType);
        };
    }

    private ShieldDecision verifyWithDeterministicNative(Action action) throws Exception {
        ShieldDecision recoExploreDecision = verifyDeterministicRecoExplore(action);
        if (recoExploreDecision.safe) {
            return recoExploreDecision;
        }

        StarkNativeShield recoShield = new StarkNativeShield(this.getEngine());
        boolean recoSafe = recoShield.verifySafe(action);
        if (shouldPrintDiagnostics()) {
            System.out.println("----------");
            System.out.printf("%s cascaded reco shield: ai_action=%s, fallback_sequence=%s%n",
                    recoSafe ? "Safe" : "Unsafe",
                    action,
                    SLOWER_SLOWER_SEQUENCE);
            System.out.println(recoShield.getUnsafeDiagnosis());
        }
        if (recoSafe) {
            cachedActions.clear();
            return shieldDecisionFrom(true, recoShield);
        }
        return shieldDecisionFrom(false, recoShield);
    }

    private ShieldDecision verifyWithRandomIdmNative(Action action) throws Exception {
        ShieldDecision recoExploreDecision = verifyRandomIdmRecoExplore(action);
        if (recoExploreDecision.safe) {
            return recoExploreDecision;
        }

        StarkNativeWithRandomIDM recoShield = new StarkNativeWithRandomIDM(this.getEngine());
        boolean recoSafe = recoShield.verifySafe(action);
        if (shouldPrintDiagnostics()) {
            System.out.println("----------");
            System.out.printf("%s cascaded random IDM reco shield: ai_action=%s, fallback_sequence=%s%n",
                    recoSafe ? "Safe" : "Unsafe",
                    action,
                    SLOWER_SLOWER_SEQUENCE);
            System.out.println(recoShield.getUnsafeDiagnosis());
        }
        if (recoSafe) {
            cachedActions.clear();
            return shieldDecisionFrom(true, recoShield);
        }
        return shieldDecisionFrom(false, recoShield);
    }

    private ShieldDecision verifyDeterministicRecoExplore(Action action) throws Exception {
        StarkNativeExploreFutureShield lastShield = null;
        String lastDiagnosis = "";
        for (Action fallbackAction : this.firstFallbackOrderedActionSet) {
            List<Action> fallbackSequence = List.of(fallbackAction);
            StarkNativeExploreFutureShield exploreShield =
                    new StarkNativeExploreFutureShield(this.getEngine(), fallbackSequence);
            List<DisTLFormula> exploreFutureCriteria = List.of(exploreShield.evaluateCutIn());
            boolean safe = exploreShield.verifySafe(action, exploreFutureCriteria);
            if (shouldPrintDiagnostics()) {
                System.out.println("----------");
                System.out.printf("%s cascaded reco-explore shield: ai_action=%s, fallback_sequence=%s%n",
                        safe ? "Safe" : "Unsafe",
                        action,
                        fallbackSequence);
                System.out.println(exploreShield.getUnsafeDiagnosis());
            }
            if (safe) {
                rebuildCachedActions(fallbackSequence);
                return shieldDecisionFrom(true, exploreShield);
            }
            lastShield = exploreShield;
            lastDiagnosis = exploreShield.getUnsafeDiagnosis();
        }
        return new ShieldDecision(false, lastShield == null ? lastDiagnosis : lastShield.getUnsafeDiagnosis());
    }

    private ShieldDecision verifyRandomIdmRecoExplore(Action action) throws Exception {
        StarkNativeWithRandomIDM lastShield = null;
        String lastDiagnosis = "";
        for (Action fallbackAction : this.firstFallbackOrderedActionSet) {
            List<Action> fallbackSequence = List.of(fallbackAction);
            StarkNativeWithRandomIDM exploreShield =
                    new StarkNativeWithRandomIDM(this.getEngine(), fallbackSequence);
            List<DisTLFormula> exploreFutureCriteria = List.of(exploreShield.evaluateCutIn());
            boolean safe = exploreShield.verifySafe(action, exploreFutureCriteria);
            if (shouldPrintDiagnostics()) {
                System.out.println("----------");
                System.out.printf("%s cascaded random IDM reco-explore shield: ai_action=%s, fallback_sequence=%s%n",
                        safe ? "Safe" : "Unsafe",
                        action,
                        fallbackSequence);
                System.out.println(exploreShield.getUnsafeDiagnosis());
            }
            if (safe) {
                rebuildCachedActions(fallbackSequence);
                return shieldDecisionFrom(true, exploreShield);
            }
            lastShield = exploreShield;
            lastDiagnosis = exploreShield.getUnsafeDiagnosis();
        }
        return new ShieldDecision(false, lastShield == null ? lastDiagnosis : lastShield.getUnsafeDiagnosis());
    }
}
