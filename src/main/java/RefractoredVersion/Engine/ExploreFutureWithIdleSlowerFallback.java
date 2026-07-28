package RefractoredVersion.Engine;

import RefractoredVersion.Shield.AllSlowerShield;
import RefractoredVersion.Shield.ExploreFutureActionShield;
import RefractoredVersion.Shield.ExploreFutureActionSmarterShield;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;
import it.unicam.quasylab.jspear.distl.DisTLFormula;

import java.util.List;

public class ExploreFutureWithIdleSlowerFallback extends ExploreFutureEgo {
    @Override
    protected ShieldDecision verifyActionSafe(Action action) throws Exception {
        JavaMomentumConfig config = this.getEngine().config;
        ShieldType shieldType = config == null || config.getShieldType() == null
                ? ShieldType.ALL_SLOWER
                : config.getShieldType();
        switch (shieldType) {
            case EXPLORE_FUTURE_ACTION:
                return verifyAllExploreFutureActionShields(action);
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }

    private ShieldDecision verifyAllExploreFutureActionShields(Action action) throws Exception {
        boolean anySafe = false;
        boolean idleSafe = false;
        String lastDiagnosis = "";

        for (Action futureAction : this.firstFallbackOrderedActionSet) {
            List<Action> fallbackSequence = List.of(futureAction);
            ExploreFutureActionShield exploreShield =
                    new ExploreFutureActionShield(this.getEngine(), fallbackSequence);
            List<DisTLFormula> exploreFutureCriteria = List.of(exploreShield.evaluateCutIn());
            boolean exploreSafe = exploreShield.verifySafe(action, exploreFutureCriteria);
            if (shouldPrintDiagnostics()) {
                System.out.println("----------");
                System.out.printf("%s explore future shield: ai_action=%s, fallback_sequence=%s%n",
                        exploreSafe ? "Safe" : "Unsafe",
                        action,
                        fallbackSequence);
                System.out.println(exploreShield.getUnsafeDiagnosis());
            }
            if (exploreSafe) {
                anySafe = true;
                if (futureAction == Action.IDLE) {
                    idleSafe = true;
                }
            }
            lastDiagnosis = exploreShield.getUnsafeDiagnosis();
        }

        if (anySafe) {
            rebuildCachedActions(List.of(idleSafe ? Action.IDLE : Action.SLOWER));
            return new ShieldDecision(true, lastDiagnosis);
        }
        return new ShieldDecision(false, lastDiagnosis);
    }


}
