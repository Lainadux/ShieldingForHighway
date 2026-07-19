package RefractoredVersion.Engine;

import RefractoredVersion.Shield.AllSlowerShield;
import RefractoredVersion.Shield.ExploreFutureActionShield;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;
import it.unicam.quasylab.jspear.distl.DisTLFormula;

import java.util.ArrayList;
import java.util.List;

public class ExploreFutureEgo extends EgoVehicle {
    public List<Action> cachedActions = new ArrayList<>();
    public List<Action> firstFallbackOrderedActionSet = List.of(Action.LANE_RIGHT, Action.LANE_LEFT, Action.IDLE, Action.SLOWER, Action.FASTER);

    @Override
    public void planAction() throws Exception {
        if (this.getEngine().isDecisionTime()) {
            JavaHighwayAiClient.AiDecision decision = JavaHighwayAiClient.getInstance().decide(this);
            if (shouldPrintDiagnostics()) {
                System.out.println("----------");
                System.out.printf("AI decision: action=%d, action_name=%s%n",
                        decision.action,
                        decision.action_name);
                System.out.printf("Cached actions: %s%n", cachedActions);
            }
            applyAiAction(decision);
        }

        this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, this.getEngine().vehicles);
        this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
    }

    @Override
    protected void applyAiAction(JavaHighwayAiClient.AiDecision decision) throws Exception {
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
        applyAction(action);
    }
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

                ExploreFutureActionShield lastShield = null;
                String lastDiagnosis = "";
                for(Action f:this.firstFallbackOrderedActionSet){
                    List<Action> fallbackSequence = List.of(f);
                    ExploreFutureActionShield exploreShield = new ExploreFutureActionShield(this.getEngine(), fallbackSequence);
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
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }

    private void rebuildCachedActions(List<Action> fallbackSequence) {
        cachedActions.clear();
        cachedActions.addAll(fallbackSequence);
    }





}
