package RefractoredVersion.TestScript.Stats;

public  class CollisionStats {
        private final int requestedRuns;
        private final int completedRuns;
        private final int crashedRuns;
        private final int safeRuns;
        private final double crashRate;
        private final double crashPercent;

        public CollisionStats(GenLogSummary summary) {
            this.requestedRuns = summary.requestedRuns;
            this.completedRuns = summary.completedRuns;
            this.crashedRuns = summary.crashedRuns;
            this.safeRuns = summary.safeRuns;
            this.crashRate = summary.crashRate;
            this.crashPercent = summary.crashPercent;
        }
    }
