package RefractoredVersion.TestScript.Records;

import RefractoredVersion.Engine.ActionAcceptanceLog;

import java.util.List;

public class EpisodeAcceptanceSequence {
    public final int simulationIndex;
    public final boolean crashed;
    public final List<ActionAcceptanceLog> decisions;

    public EpisodeAcceptanceSequence(int simulationIndex, SimulationRunResult result) {
        this(
                simulationIndex,
                result.crashed,
                result.actionAcceptanceSequence
        );
    }

    public EpisodeAcceptanceSequence(int simulationIndex,
                                     boolean crashed,
                                     List<ActionAcceptanceLog> decisions) {
        this.simulationIndex = simulationIndex;
        this.crashed = crashed;
        this.decisions = decisions == null ? List.of() : List.copyOf(decisions);
    }
}
