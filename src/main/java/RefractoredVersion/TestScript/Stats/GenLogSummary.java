package RefractoredVersion.TestScript.Stats;

public class GenLogSummary {
    public final int requestedRuns;
    public final int completedRuns;
    public final int crashedRuns;
    public final int safeRuns;
    public final double crashRate;
    public final double crashPercent;
    public final long aiDecisionCount;
    public final long rejectedAiDecisionCount;
    public final double rejectedAiDecisionRate;
    public final double rejectedAiDecisionPercent;
    public final long completedTraceCount;
    public final double averageFinalEgoX;
    public final double minFinalEgoX;
    public final double maxFinalEgoX;

    public GenLogSummary(int requestedRuns, int completedRuns, int crashedRuns, long aiDecisionCount,
                          long rejectedAiDecisionCount, double rejectedAiDecisionRate,
                          long completedTraceCount,
                          double averageFinalEgoX, double minFinalEgoX, double maxFinalEgoX) {
        this.requestedRuns = requestedRuns;
        this.completedRuns = completedRuns;
        this.crashedRuns = crashedRuns;
        this.safeRuns = completedRuns - crashedRuns;
        this.crashRate = completedRuns == 0 ? 0.0 : (double) crashedRuns / completedRuns;
        this.crashPercent = crashRate * 100.0;
        this.aiDecisionCount = aiDecisionCount;
        this.rejectedAiDecisionCount = rejectedAiDecisionCount;
        this.rejectedAiDecisionRate = rejectedAiDecisionRate;
        this.rejectedAiDecisionPercent = rejectedAiDecisionRate * 100.0;
        this.completedTraceCount = completedTraceCount;
        this.averageFinalEgoX = averageFinalEgoX;
        this.minFinalEgoX = minFinalEgoX;
        this.maxFinalEgoX = maxFinalEgoX;
    }
}
