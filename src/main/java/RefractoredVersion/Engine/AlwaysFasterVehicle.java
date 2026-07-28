package RefractoredVersion.Engine;

public class AlwaysFasterVehicle extends ExploreFutureEgo{
    @Override
    public void planAction() throws Exception {
        if (this.getEngine().isDecisionTime()) {
            JavaHighwayAiClient.AiDecision decision = new JavaHighwayAiClient.AiDecision();
            decision.action = Action.FASTER.getValue();
            decision.action_name = Action.FASTER.name();
            decision.error = "Always faster action";
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
}
