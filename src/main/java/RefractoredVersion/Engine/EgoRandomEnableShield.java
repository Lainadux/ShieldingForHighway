package RefractoredVersion.Engine;

import java.util.concurrent.ThreadLocalRandom;

public class EgoRandomEnableShield extends ExploreFutureSlowerVehicle {
    private final int forceUnsafeActionPercent;

    public EgoRandomEnableShield() {
        this(20);
    }

    public EgoRandomEnableShield(int forceUnsafeActionPercent) {
        if (forceUnsafeActionPercent < 0 || forceUnsafeActionPercent > 100) {
            throw new IllegalArgumentException("forceUnsafeActionPercent must be in [0, 100].");
        }
        this.forceUnsafeActionPercent = forceUnsafeActionPercent;
    }

    @Override
    protected void applyAiAction(JavaHighwayAiClient.AiDecision decision) throws Exception {
        this.lastAiDecision = decision;
        Action proposedAction = parseAction(decision);
        Action action = proposedAction;
        ShieldDecision shieldDecision = verifyActionSafe(proposedAction);
        boolean cacheHit = false;
        boolean forcedUnsafeAction = false;

        if (!shieldDecision.safe) {
            forcedUnsafeAction = shouldForceUnsafeAction();
            if (!forcedUnsafeAction) {
                if (cachedActions.isEmpty()) {
                    action = Action.SLOWER;
                } else {
                    cacheHit = true;
                    action = cachedActions.get(0);
                    cachedActions.remove(0);
                }
            }
        }

        if (shouldPrintDiagnostics()) {
            System.out.printf("%s AI decision: action=%d, action_name=%s, parsed_action=%s",
                    shieldDecision.safe ? "Safe" : "Unsafe",
                    decision.action,
                    decision.action_name,
                    proposedAction);
            if (forcedUnsafeAction) {
                System.out.print(", forced_unsafe_action=true");
            }
            System.out.println();
            System.out.println(shieldDecision.diagnosis);
        }

        recordDecisionLogs(decision, proposedAction, shieldDecision, action, cacheHit);
        recordAiDecision(!shieldDecision.safe);
        applyAction(action);
    }

    private boolean shouldForceUnsafeAction() {
        return ThreadLocalRandom.current().nextInt(100) < forceUnsafeActionPercent;
    }
}
