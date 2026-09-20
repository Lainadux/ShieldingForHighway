package RefractoredVersion.TestScript.Records;

import RefractoredVersion.Engine.telemetry.ActionAcceptanceLog;
import RefractoredVersion.Engine.telemetry.BeforeCrashActionLog;
import RefractoredVersion.Engine.telemetry.CollisionLog;

import java.util.List;

public class SimulationRunResult {
    public final String initialStateJson;
    public final boolean crashed;
    public final RuntimeException crashException;
    public final int aiDecisionCount;
    public final int rejectedAiDecisionCount;
    public final double finalEgoX;
    public final List<BeforeCrashActionLog> beforeCrashActions;
    public final CollisionLog collisionLog;
    public final List<ActionAcceptanceLog> actionAcceptanceSequence;

    public SimulationRunResult(
            String initialStateJson,
            boolean crashed,
            RuntimeException crashException,
            int aiDecisionCount,
            int rejectedAiDecisionCount,
            double finalEgoX,
            List<BeforeCrashActionLog> beforeCrashActions,
            CollisionLog collisionLog,
            List<ActionAcceptanceLog> actionAcceptanceSequence
    ) {
        this.initialStateJson = initialStateJson;
        this.crashed = crashed;
        this.crashException = crashException;
        this.aiDecisionCount = aiDecisionCount;
        this.rejectedAiDecisionCount = rejectedAiDecisionCount;
        this.finalEgoX = finalEgoX;
        this.beforeCrashActions = beforeCrashActions == null ? List.of() : List.copyOf(beforeCrashActions);
        this.collisionLog = collisionLog;
        this.actionAcceptanceSequence = actionAcceptanceSequence == null ? List.of() : List.copyOf(actionAcceptanceSequence);
    }
}
