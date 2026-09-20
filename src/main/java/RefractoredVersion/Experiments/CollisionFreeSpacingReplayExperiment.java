package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Engine.JavaHighwayAiClient;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.SameInitialRunTable3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reproduces the collision-free initial-state replay reported in Table 6. */
public class CollisionFreeSpacingReplayExperiment {
    private static final int SAFE_SEQUENCE_COUNT = 10;
    private static final double[] SPACINGS = {1.0, 1.5};

    public static void main(String[] args) {
        Path root = PaperExperimentSupport.logRoot("table_6_collision_free_spacing_replay");
        try {
            for (double spacing : SPACINGS) {
                runSpacing(root, spacing);
            }
            System.out.println("Table 6 logs: " + root.toAbsolutePath());
        } finally {
            JavaHighwayAiClient.stopAll();
        }
    }

    private static void runSpacing(Path root, double spacing) {
        Path spacingDirectory = root.resolve(String.format(Locale.ROOT, "spacing_%.1f", spacing)
                .replace('.', '_'));
        JavaMomentumConfig noShieldConfig = config(
                EgoType.NoShieldEgo, ShieldType.NONE, spacing, spacingDirectory);
        List<String> safeInitialStates = collectSafeInitialStates(
                noShieldConfig, spacingDirectory.resolve("selected_initial_states"));

        runShield(
                "Heuristic shield / spacing " + spacing,
                config(EgoType.EgoVehicle, ShieldType.HEURISTIC, spacing, spacingDirectory),
                safeInitialStates,
                spacingDirectory.resolve("heuristic_shield")
        );
        runShield(
                "Reco shield / spacing " + spacing,
                config(EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM, spacing, spacingDirectory),
                safeInitialStates,
                spacingDirectory.resolve("reco_shield")
        );
    }

    private static List<String> collectSafeInitialStates(JavaMomentumConfig config, Path outputDirectory) {
        List<String> states = new ArrayList<>();
        List<Double> finalPositions = new ArrayList<>();
        int attempts = 0;

        while (states.size() < SAFE_SEQUENCE_COUNT) {
            attempts++;
            String initialState = SameInitialRunTable3.generateInitialStateJson(config);
            Double finalEgoX = SameInitialRunTable3.safeFinalEgoX(config, initialState);
            if (finalEgoX != null) {
                int index = states.size();
                states.add(initialState);
                finalPositions.add(finalEgoX);
                SameInitialRunTable3.saveJson(
                        outputDirectory.resolve(String.format(
                                "safe_%02d_attempt_%05d.json", index, attempts)),
                        initialState
                );
            }
            System.out.printf("No-shield selection: attempts=%d, safe=%d/%d%n",
                    attempts, states.size(), SAFE_SEQUENCE_COUNT);
        }

        double mean = finalPositions.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double min = finalPositions.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        double max = finalPositions.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        SameInitialRunTable3.saveJson(
                outputDirectory.getParent().resolve("no_shield_selection_summary.json"),
                String.format(Locale.ROOT,
                        "{\n  \"attempts\": %d,\n  \"selectedSafeSequences\": %d,\n"
                                + "  \"meanNoShieldDisplacement\": %.6f,\n"
                                + "  \"minNoShieldDisplacement\": %.6f,\n"
                                + "  \"maxNoShieldDisplacement\": %.6f\n}\n",
                        attempts, states.size(), mean, min, max)
        );
        return states;
    }

    private static void runShield(String name,
                                  JavaMomentumConfig config,
                                  List<String> initialStates,
                                  Path outputDirectory) {
        SameInitialRunTable3.runAndReport(name, config, initialStates, outputDirectory);
    }

    private static JavaMomentumConfig config(EgoType egoType,
                                             ShieldType shieldType,
                                             double spacing,
                                             Path outputDirectory) {
        JavaMomentumConfig config = PaperExperimentSupport.config(
                egoType, shieldType, AIProfile.adversarial, outputDirectory);
        config.setNumsOfSimulations(SAFE_SEQUENCE_COUNT);
        config.setVehicleSpacing(spacing);
        return config;
    }
}
