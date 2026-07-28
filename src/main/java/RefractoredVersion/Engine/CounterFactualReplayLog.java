package RefractoredVersion.Engine;

import java.util.List;

public record CounterFactualReplayLog(
        int step,
        double time,
        int decisionIndex,
        int aiActionValue,
        String aiActionName,
        Action proposedAction,
        boolean shieldSafe,
        String shieldDiagnosis,
        Action performedAction,
        Action counterfactualAction,
        List<Vehicle> vehicles
) {
}
