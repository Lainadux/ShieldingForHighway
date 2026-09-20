package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Engine.JavaHighwayAiClient;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RunShieldsOnSafeAdversarialSequences {
    private static final int SAFE_SEQUENCE_COUNT = 10;
    private static final double VEHICLE_SPACING = 1.4;

    public static void main(String[] args) {
        Path root = Path.of(
                "src/main/java/RefractoredVersion/logs",
                "safe_adversarial_replay_" + System.currentTimeMillis()
        );

        try {
            JavaMomentumConfig noShieldConfig = config(
                    EgoType.NoShieldEgo,
                    ShieldType.ALL_SLOWER
            );
            List<String> safeInitialStates = collectSafeInitialStates(noShieldConfig, root);

            runShield(
                    "Heuristic shield",
                    config(EgoType.EgoVehicle, ShieldType.HEURISTIC),
                    safeInitialStates,
                    root.resolve("heuristic_shield")
            );
            runShield(
                    "Reco shield",
                    config(EgoType.ExploreFutureSlowerVehicle, ShieldType.STARK_NATIVE_RANDOM_IDM),
                    safeInitialStates,
                    root.resolve("reco_shield")
            );

            System.out.println("Experiment logs: " + root.toAbsolutePath());
        } finally {
            JavaHighwayAiClient.stopAll();
        }
    }

    private static List<String> collectSafeInitialStates(JavaMomentumConfig config, Path root) {
        List<String> safeStates = new ArrayList<>();
        List<Double> safeFinalEgoPositions = new ArrayList<>();
        int attempts = 0;

        while (safeStates.size() < SAFE_SEQUENCE_COUNT) {
            attempts++;
            String initialStateJson = SameInitialRunTable3.generateInitialStateJson(config);
            Double finalEgoX = SameInitialRunTable3.safeFinalEgoX(config, initialStateJson);
            boolean crashed = finalEgoX == null;

            if (!crashed) {
                int safeIndex = safeStates.size();
                safeStates.add(initialStateJson);
                safeFinalEgoPositions.add(finalEgoX);
                SameInitialRunTable3.saveJson(
                        root.resolve("selected_initial_states")
                                .resolve(String.format("safe_%02d_attempt_%05d.json", safeIndex, attempts)),
                        initialStateJson
                );
            }

            System.out.printf(
                    "No-shield selection: attempts=%d, safe=%d/%d, latest=%s%n",
                    attempts,
                    safeStates.size(),
                    SAFE_SEQUENCE_COUNT,
                    crashed ? "crashed" : "safe"
            );
        }

        double meanFinalEgoX = safeFinalEgoPositions.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);
        double minFinalEgoX = safeFinalEgoPositions.stream()
                .mapToDouble(Double::doubleValue)
                .min()
                .orElse(Double.NaN);
        double maxFinalEgoX = safeFinalEgoPositions.stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(Double.NaN);

        SameInitialRunTable3.saveJson(
                root.resolve("selection_summary.json"),
                String.format(Locale.ROOT,
                        "{\n"
                                + "  \"attempts\": %d,\n"
                                + "  \"selectedSafeSequences\": %d,\n"
                                + "  \"meanNoShieldDisplacement\": %.6f,\n"
                                + "  \"minNoShieldDisplacement\": %.6f,\n"
                                + "  \"maxNoShieldDisplacement\": %.6f\n"
                                + "}\n",
                        attempts,
                        safeStates.size(),
                        meanFinalEgoX,
                        minFinalEgoX,
                        maxFinalEgoX
                )
        );
        System.out.printf(Locale.ROOT,
                "Selected no-shield safe sequences: attempts=%d, mean displacement=%.2f, min=%.2f, max=%.2f%n",
                attempts,
                meanFinalEgoX,
                minFinalEgoX,
                maxFinalEgoX);
        return safeStates;
    }

    private static void runShield(String name, JavaMomentumConfig config,
                                  List<String> initialStates, Path outputDirectory) {
        System.out.println("========== Running " + name + " ==========");
        SameInitialRunTable3.runAndReport(name, config, initialStates, outputDirectory);
    }

    private static JavaMomentumConfig config(EgoType egoType, ShieldType shieldType) {
        JavaMomentumConfig config = SameInitialRunTable3.defaultExperimentConfig();
        config.setEgoType(egoType);
        config.setShieldType(shieldType);
        config.setAiProfile(AIProfile.adversarial);
        config.setNumsOfSimulations(SAFE_SEQUENCE_COUNT);
        config.setVehicleSpacing(VEHICLE_SPACING);
        return config;
    }
}
