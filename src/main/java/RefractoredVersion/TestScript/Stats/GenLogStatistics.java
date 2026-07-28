package RefractoredVersion.TestScript.Stats;

import RefractoredVersion.TestScript.Records.SimulationRunResult;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;

public  class GenLogStatistics {
        private final int requestedRuns;
        private final AtomicInteger completedRuns = new AtomicInteger(0);
        private final AtomicInteger crashedRuns = new AtomicInteger(0);
        private final AtomicLong aiDecisionCount = new AtomicLong(0);
        private final AtomicLong rejectedAiDecisionCount = new AtomicLong(0);
        private final DoubleAccumulator minFinalEgoX = new DoubleAccumulator(Double::min, Double.POSITIVE_INFINITY);
        private final DoubleAccumulator maxFinalEgoX = new DoubleAccumulator(Double::max, Double.NEGATIVE_INFINITY);
        private final AtomicLong finalEgoXCount = new AtomicLong(0);
        private final DoubleAccumulator finalEgoXSum = new DoubleAccumulator(Double::sum, 0.0);

        private GenLogStatistics(int requestedRuns) {
            this.requestedRuns = requestedRuns;
        }

        private void add(SimulationRunResult result) {
            completedRuns.incrementAndGet();
            if (result.crashed) {
                crashedRuns.incrementAndGet();
            }
            aiDecisionCount.addAndGet(result.aiDecisionCount);
            rejectedAiDecisionCount.addAndGet(result.rejectedAiDecisionCount);
            if (!result.crashed && !Double.isNaN(result.finalEgoX)) {
                finalEgoXCount.incrementAndGet();
                finalEgoXSum.accumulate(result.finalEgoX);
                minFinalEgoX.accumulate(result.finalEgoX);
                maxFinalEgoX.accumulate(result.finalEgoX);
            }
        }

        private GenLogSummary snapshot() {
            long totalDecisions = aiDecisionCount.get();
            long rejectedDecisions = rejectedAiDecisionCount.get();
            long egoXSamples = finalEgoXCount.get();
            return new GenLogSummary(
                    requestedRuns,
                    completedRuns.get(),
                    crashedRuns.get(),
                    totalDecisions,
                    rejectedDecisions,
                    totalDecisions == 0 ? 0.0 : (double) rejectedDecisions / totalDecisions,
                    egoXSamples,
                    egoXSamples == 0 ? Double.NaN : finalEgoXSum.get() / egoXSamples,
                    egoXSamples == 0 ? Double.NaN : minFinalEgoX.get(),
                    egoXSamples == 0 ? Double.NaN : maxFinalEgoX.get()
            );
        }

        private void print() {
            GenLogSummary summary = snapshot();
            System.out.printf(
                    "GenLogs summary: runs=%d/%d crashed=%d aiDecisions=%d rejected=%d rejectedPercent=%.2f%% completedTraceFinalEgoX(count/avg/min/max)=%d/%.2f/%.2f/%.2f%n",
                    summary.completedRuns,
                    summary.requestedRuns,
                    summary.crashedRuns,
                    summary.aiDecisionCount,
                    summary.rejectedAiDecisionCount,
                    summary.rejectedAiDecisionPercent,
                    summary.completedTraceCount,
                    summary.averageFinalEgoX,
                    summary.minFinalEgoX,
                    summary.maxFinalEgoX);
        }
    }