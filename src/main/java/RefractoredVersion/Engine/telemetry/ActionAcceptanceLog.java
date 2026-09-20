package RefractoredVersion.Engine.telemetry;

import RefractoredVersion.Engine.Action;

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
        double acceptanceValue,
        String failedSafetyCriteria,
        Double collisionRobustness,
        Double firstSecondSafetyRobustness,
        Double stabilityRobustness,
        Double rearThreatRobustness,
        Double lowSpeedLaneChangeRobustness,
        Double shieldRobustness
) {
}
