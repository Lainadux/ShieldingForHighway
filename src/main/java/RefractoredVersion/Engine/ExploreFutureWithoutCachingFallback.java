package RefractoredVersion.Engine;

import java.util.List;

public class ExploreFutureWithoutCachingFallback extends ExploreFutureEgo{
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
            action = Action.SLOWER;

        }
        recordDecisionLogs(decision, parseAction(decision), shieldDecision, action, cacheHit);
        recordAiDecision(!shieldDecision.safe);
        applyAction(action);
    }
    @Override
    protected void rebuildCachedActions(List<Action> fallbackSequence) {
        cachedActions.clear();
    }
}
