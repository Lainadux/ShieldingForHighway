package RefractoredVersion.TestScript.Stats;

import RefractoredVersion.TestScript.Runner;

public class DistanceStats {
    public final long completedTraceCount;
    public final double averageFinalEgoX;
    public final double minFinalEgoX;
    public final double maxFinalEgoX;

    public DistanceStats(GenLogSummary summary) {
        this.completedTraceCount = summary.completedTraceCount;
        this.averageFinalEgoX = summary.averageFinalEgoX;
        this.minFinalEgoX = summary.minFinalEgoX;
        this.maxFinalEgoX = summary.maxFinalEgoX;
    }
}
