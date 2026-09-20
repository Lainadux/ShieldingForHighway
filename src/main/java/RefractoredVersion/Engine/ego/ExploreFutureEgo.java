package RefractoredVersion.Engine.ego;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayAiClient;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;

import RefractoredVersion.Shield.ExploreFutureActionShield;
import RefractoredVersion.Shield.ExploreFutureBetterReferenceShield;
import RefractoredVersion.Shield.StarkNativeShield;
import RefractoredVersion.Shield.StarkNativeWithRandomIDM;
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
        boolean cacheHit = false;
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
                cacheHit = true;
                action = cachedActions.get(0);
                cachedActions.remove(0);
            }

        }
        recordDecisionLogs(decision, parseAction(decision), shieldDecision, action, cacheHit);
        recordAiDecision(!shieldDecision.safe);
        applyAction(action);
    }
    @Override
    protected ShieldDecision verifyActionSafe(Action action) throws Exception {
        JavaMomentumConfig config = this.getEngine().config;
        ShieldType shieldType = config == null || config.getShieldType() == null
                ? ShieldType.STARK_NATIVE_RANDOM_IDM
                : config.getShieldType();
        switch (shieldType) {
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
            case EXPLORE_FUTURE_BETTER_REFERENCE_ACTION:
                ExploreFutureBetterReferenceShield lastBetterShield = null;
                String lastBetterDiagnosis = "";
                for(Action f:this.firstFallbackOrderedActionSet){
                    List<Action> fallbackSequence = List.of(f);
                    ExploreFutureBetterReferenceShield betterShield =
                            new ExploreFutureBetterReferenceShield(this.getEngine(), fallbackSequence);
                    List<DisTLFormula> exploreFutureCriteria = List.of(betterShield.evaluateCutIn());
                    boolean betterSafe = betterShield.verifySafe(action, exploreFutureCriteria);
                    if (shouldPrintDiagnostics()) {
                        System.out.println("----------");
                        System.out.printf("%s better reference explore future shield: ai_action=%s, fallback_sequence=%s%n",
                                betterSafe ? "Safe" : "Unsafe",
                                action,
                                fallbackSequence);
                        System.out.println(betterShield.getUnsafeDiagnosis());
                    }
                    if(betterSafe){
                        rebuildCachedActions(fallbackSequence);
                        return new ShieldDecision(true, betterShield.getUnsafeDiagnosis());
                    }
                    lastBetterShield = betterShield;
                    lastBetterDiagnosis = betterShield.getUnsafeDiagnosis();
                }

                return new ShieldDecision(false, lastBetterShield == null
                        ? lastBetterDiagnosis
                        : lastBetterShield.getUnsafeDiagnosis());
            case STARK_NATIVE:
                StarkNativeShield lastNativeShield = null;
                String lastNativeDiagnosis = "";
                for(Action f:this.firstFallbackOrderedActionSet){
                    List<Action> fallbackSequence = List.of(f);
                    StarkNativeShield nativeShield =
                            new StarkNativeShield(this.getEngine(), fallbackSequence);
                    List<DisTLFormula> exploreFutureCriteria = List.of(nativeShield.evaluateCutIn());
                    boolean nativeSafe = nativeShield.verifySafe(action, exploreFutureCriteria);
                    if (shouldPrintDiagnostics()) {
                        System.out.println("----------");
                        System.out.printf("%s stark native explore future shield: ai_action=%s, fallback_sequence=%s%n",
                                nativeSafe ? "Safe" : "Unsafe",
                                action,
                                fallbackSequence);
                        System.out.println(nativeShield.getUnsafeDiagnosis());
                    }
                    if(nativeSafe){
                        rebuildCachedActions(fallbackSequence);
                        return shieldDecisionFrom(true, nativeShield);
                    }
                    lastNativeShield = nativeShield;
                    lastNativeDiagnosis = nativeShield.getUnsafeDiagnosis();
                }

                return new ShieldDecision(false, lastNativeShield == null
                        ? lastNativeDiagnosis
                        : lastNativeShield.getUnsafeDiagnosis());
            case STARK_NATIVE_RANDOM_IDM:
                StarkNativeWithRandomIDM lastRandomIdmShield = null;
                String lastRandomIdmDiagnosis = "";
                for(Action f:this.firstFallbackOrderedActionSet){
                    List<Action> fallbackSequence = List.of(f);
                    StarkNativeWithRandomIDM randomIdmShield =
                            new StarkNativeWithRandomIDM(this.getEngine(), fallbackSequence);
                    List<DisTLFormula> exploreFutureCriteria = List.of(randomIdmShield.evaluateCutIn());
                    boolean randomIdmSafe = randomIdmShield.verifySafe(action, exploreFutureCriteria);
                    if (shouldPrintDiagnostics()) {
                        System.out.println("----------");
                        System.out.printf("%s stark native random IDM explore future shield: ai_action=%s, fallback_sequence=%s%n",
                                randomIdmSafe ? "Safe" : "Unsafe",
                                action,
                                fallbackSequence);
                        System.out.println(randomIdmShield.getUnsafeDiagnosis());
                    }
                    if(randomIdmSafe){
                        rebuildCachedActions(fallbackSequence);
                        return shieldDecisionFrom(true, randomIdmShield);
                    }
                    lastRandomIdmShield = randomIdmShield;
                    lastRandomIdmDiagnosis = randomIdmShield.getUnsafeDiagnosis();
                }

                return new ShieldDecision(false, lastRandomIdmShield == null
                        ? lastRandomIdmDiagnosis
                        : lastRandomIdmShield.getUnsafeDiagnosis());
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }

    protected void rebuildCachedActions(List<Action> fallbackSequence) {
        cachedActions.clear();
        cachedActions.addAll(fallbackSequence);
    }





}
