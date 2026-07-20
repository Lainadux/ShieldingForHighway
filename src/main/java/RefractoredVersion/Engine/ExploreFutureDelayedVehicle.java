package RefractoredVersion.Engine;

import RefractoredVersion.Shield.ExploreFutureActionShield;
import RefractoredVersion.Shield.ExploreFutureDelayedActionShield;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;
import it.unicam.quasylab.jspear.distl.DisTLFormula;

import java.util.List;

public class ExploreFutureDelayedVehicle  extends ExploreFutureEgo{
    public  int delayedStep = 1;
    public Action planAheadAction = null;
    @Override
    public void planAction() throws Exception {
        updateDelayedStepFromConfig();
        if (this.getEngine().isDecisionTime()) {
            applyAction(Action.IDLE);
            JavaHighwayAiClient.AiDecision decision = JavaHighwayAiClient.getInstance().decide(this);
            planAheadAction = getPlanAheadAction(decision);
            if (shouldPrintDiagnostics()) {
                System.out.println("----------");
                System.out.printf("AI decision: action=%d, action_name=%s%n",
                        decision.action,
                        decision.action_name);
                System.out.printf("Cached actions: %s%n", cachedActions);
            }
        }
        else if (isDelayedPerformStep()) {
            if (planAheadAction == null) {
                throw new IllegalStateException("Delayed perform step reached before an action was planned.");
            }
            applyAction(planAheadAction);
            planAheadAction = null;

        }


        this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, this.getEngine().vehicles);
        this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
    }

    public Action getPlanAheadAction(JavaHighwayAiClient.AiDecision decision) throws Exception {
        this.lastAiDecision = decision;
        Action action = parseAction(decision);
        ShieldDecision shieldDecision = verifyActionSafe(action);
        if (shouldPrintDiagnostics()) {
            System.out.printf("%s AI decision: action=%d, action_name=%s, parsed_action=%s%n",
                    shieldDecision.safe ? "Safe" : "Unsafe",
                    decision.action,
                    decision.action_name,
                    action);
            System.out.println(shieldDecision.diagnosis);
        }
        if (!shieldDecision.safe) {
            if (cachedActions.isEmpty()) {
                action = Action.SLOWER;
            } else {
                action = cachedActions.get(0);
                cachedActions.remove(0);
            }

        }
        recordCrashLog(decision, parseAction(decision), shieldDecision, action);
        recordAiDecision(!shieldDecision.safe);
        return action;
    }

    private void updateDelayedStepFromConfig() {
        JavaMomentumConfig config = this.getEngine().config;
        if (config != null) {
            delayedStep = config.getDelayedActionStep();
        }
        if (delayedStep < 1) {
            throw new IllegalArgumentException("delayedActionStep must be at least 1.");
        }
    }

    private boolean isDelayedPerformStep() {
        int shiftedStep = this.getEngine().stepsTaken - delayedStep;
        return shiftedStep >= 0 && shiftedStep % this.getEngine().getFrequency() == 0;
    }

    @Override
    protected ShieldDecision verifyActionSafe(Action action) throws Exception {
        JavaMomentumConfig config = this.getEngine().config;
        ShieldType shieldType = config == null || config.getShieldType() == null
                ? ShieldType.ALL_SLOWER
                : config.getShieldType();
        if(shieldType != ShieldType.EXPLORE_FUTURE_DELAYED_ACTION){
            throw new IllegalArgumentException("Delayed vehicle using unsupported shield type: " + shieldType);
        }
        ExploreFutureActionShield lastShield = null;
        String lastDiagnosis = "";
        for(Action f:this.firstFallbackOrderedActionSet){
            List<Action> fallbackSequence = List.of(f);
            ExploreFutureDelayedActionShield exploreShield = new ExploreFutureDelayedActionShield(this.getEngine(), fallbackSequence, delayedStep);
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
            if(exploreSafe){
                rebuildCachedActions(fallbackSequence);
                return new ShieldDecision(true, exploreShield.getUnsafeDiagnosis());
            }
            lastShield = exploreShield;
            lastDiagnosis = exploreShield.getUnsafeDiagnosis();
        }
        return new ShieldDecision(false, lastShield == null ? lastDiagnosis : lastShield.getUnsafeDiagnosis());
    }
}
