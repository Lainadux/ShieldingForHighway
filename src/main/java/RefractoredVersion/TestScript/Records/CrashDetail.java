package RefractoredVersion.TestScript.Records;

import RefractoredVersion.Engine.BeforeCrashActionLog;
import RefractoredVersion.Engine.CollisionLog;
import RefractoredVersion.TestScript.Records.SimulationRunResult;
import RefractoredVersion.TestScript.Runner;

import java.util.List;

public class CrashDetail {
    private final int simulationIndex;
    private final String crashMessage;
    private final CollisionLog collision;
    private final Double relativeSpeed;
    private final List<BeforeCrashActionLog> beforeCrashActions;

    private CrashDetail(int simulationIndex, SimulationRunResult result) {
        this.simulationIndex = simulationIndex;
        this.crashMessage = result.crashException == null ? null : result.crashException.getMessage();
        this.collision = result.collisionLog;
        this.relativeSpeed = collisionSeverity(result.collisionLog);
        this.beforeCrashActions = result.beforeCrashActions == null ? List.of() : result.beforeCrashActions;
    }

    private static Double collisionSeverity(CollisionLog collisionLog) {
        if (collisionLog == null) {
            return null;
        }
        double dvx = collisionLog.firstVx() - collisionLog.secondVx();
        double dvy = collisionLog.firstVy() - collisionLog.secondVy();
        return Math.sqrt(dvx * dvx + dvy * dvy);
    }
}