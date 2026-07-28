package RefractoredVersion.Engine;

import java.util.concurrent.ThreadLocalRandom;

public class EgoRandomFallback extends EgoVehicle {
    private final int randomRejectPercent;

    public EgoRandomFallback() {
        this(20);
    }

    public EgoRandomFallback(int randomRejectPercent) {
        if (randomRejectPercent < 0 || randomRejectPercent > 100) {
            throw new IllegalArgumentException("randomRejectPercent must be in [0, 100].");
        }
        this.randomRejectPercent = randomRejectPercent;
    }

    @Override
    protected void applyAiAction(JavaHighwayAiClient.AiDecision decision) throws Exception {
        this.lastAiDecision = decision;
        Action proposedAction = parseAction(decision);
        boolean rejected = shouldRandomlyReject();
        Action performedAction = rejected ? Action.SLOWER : proposedAction;
        ShieldDecision randomDecision = new ShieldDecision(!rejected,
                rejected
                        ? "Random fallback rejected the AI action."
                        : "Random fallback accepted the AI action.");

        if (shouldPrintDiagnostics()) {
            System.out.printf("%s random fallback decision: action=%d, action_name=%s, parsed_action=%s, performed_action=%s%n",
                    rejected ? "Rejected" : "Accepted",
                    decision.action,
                    decision.action_name,
                    proposedAction,
                    performedAction);
        }

        recordDecisionLogs(decision, proposedAction, randomDecision, performedAction, false);
        recordAiDecision(rejected);
        applyAction(performedAction);
    }

    private boolean shouldRandomlyReject() {
        return ThreadLocalRandom.current().nextInt(100) < randomRejectPercent;
    }
}
