# Paper experiments

Each public class in this directory has a `main` method and can be run directly. 
Unless stated otherwise, each configuration uses 100 episodes,
30 seconds per episode, a 20 Hz real-world engine, and the parameters of the
default experiment.

- `DefaultShieldComparisonExperiment`: Table 4.
- `ConstantSpeedBaselineExperiment`: Table 5.
- `CollisionFreeSpacingReplayExperiment`: Table 6.
- `SparseTrafficShieldExperiment`: Table 7.
- `ShieldBypassExperiment`: Figure 5.
- `RecoExploreExperiment`: Table 8.
- `VerificationDelayExperiment`: Table 9.
- `PeriodicInterventionExperiment`: Table 10.
- `StateDependentHeuristicExperiment`: Table 11.

All generated logs are stored below
`src/main/java/RefractoredVersion/logs/paper_experiments`.
