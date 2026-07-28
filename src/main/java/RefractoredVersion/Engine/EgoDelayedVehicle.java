package RefractoredVersion.Engine;

import RefractoredVersion.TestScript.Config.JavaMomentumConfig;

public class EgoDelayedVehicle extends EgoVehicle {
    public int delayedStep = 1;
    public Action planAheadAction = null;

    @Override
    public void planAction() throws Exception {
        updateDelayedStepFromConfig();
        if (this.getEngine().isDecisionTime()) {
            applyAction(Action.IDLE);
            JavaHighwayAiClient.AiDecision decision = JavaHighwayAiClient.getInstance().decide(this);
            planAheadAction = getPlanAheadAction(decision);
            if (shouldPrintDiagnostics()) {
                System.out.printf("AI decision: action=%d, action_name=%s%n",
                        decision.action,
                        decision.action_name);
                System.out.printf("Planned delayed action: %s%n", planAheadAction);
            }
        } else if (isDelayedPerformStep()) {
            if (planAheadAction == null) {
                throw new IllegalStateException("Delayed perform step reached before an action was planned.");
            }
            applyAction(planAheadAction);
            planAheadAction = null;
        }

        this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, this.getEngine().vehicles);
        this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
    }

    protected Action getPlanAheadAction(JavaHighwayAiClient.AiDecision decision) throws Exception {
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
            action = Action.SLOWER;
        }
        recordDecisionLogs(decision, parseAction(decision), shieldDecision, action, false);
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
}
