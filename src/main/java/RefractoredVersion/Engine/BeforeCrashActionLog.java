package RefractoredVersion.Engine;

public record BeforeCrashActionLog(
        int step,
        double time,
        int aiActionValue,
        String aiActionName,
        Action proposedAction,
        boolean shieldSafe,
        String shieldDiagnosis,
        Action performedAction
) {
}
