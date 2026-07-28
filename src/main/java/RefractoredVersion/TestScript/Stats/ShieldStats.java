package RefractoredVersion.TestScript.Stats;

public class ShieldStats {
        private final long aiDecisionCount;
        private final long rejectedAiDecisionCount;
        private final long acceptedAiDecisionCount;
        private final double rejectedAiDecisionRate;
        private final double rejectedAiDecisionPercent;
        private final double acceptedAiDecisionRate;
        private final double acceptedAiDecisionPercent;

        public ShieldStats(GenLogSummary summary) {
            this.aiDecisionCount = summary.aiDecisionCount;
            this.rejectedAiDecisionCount = summary.rejectedAiDecisionCount;
            this.acceptedAiDecisionCount = summary.aiDecisionCount - summary.rejectedAiDecisionCount;
            this.rejectedAiDecisionRate = summary.rejectedAiDecisionRate;
            this.rejectedAiDecisionPercent = summary.rejectedAiDecisionPercent;
            this.acceptedAiDecisionRate = summary.aiDecisionCount == 0
                    ? 0.0
                    : (double) acceptedAiDecisionCount / summary.aiDecisionCount;
            this.acceptedAiDecisionPercent = acceptedAiDecisionRate * 100.0;
        }
    }