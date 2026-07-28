package RefractoredVersion.Engine;

public record ActionAcceptanceLog(
        int step,
        double time,
        int decisionIndex,
        int aiActionValue,
        String aiActionName,
        Action proposedAction,
        boolean shieldSafe,
        boolean cacheHit,
        Action performedAction,
        double acceptanceValue
) {
}
