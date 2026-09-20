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

public class RunShieldSpacingComparison {
    private static final int EPISODES = 100;
    private static final double[] VEHICLE_SPACINGS = { 1.5};

    public static void main(String[] args) {
        Path root = Path.of(
                "src/main/java/RefractoredVersion/logs",
                "shield_spacing_comparison_" + System.currentTimeMillis()
        );

        try {
            for (double spacing : VEHICLE_SPACINGS) {
                runSpacing(root, spacing);
            }
            System.out.println("Spacing comparison logs: " + root.toAbsolutePath());
        } finally {
            JavaHighwayAiClient.stopAll();
        }
    }

    private static void runSpacing(Path root, double spacing) {
        String spacingName = String.format(Locale.ROOT, "spacing_%.1f", spacing)
                .replace('.', '_');
        Path spacingDirectory = root.resolve(spacingName);

        System.out.printf(Locale.ROOT,
                "========== Generating %d shared states for spacing %.1f ==========%n",
                EPISODES,
                spacing);
        List<String> initialStates = generateInitialStates(spacingDirectory, spacing);

        runShield(
                String.format(Locale.ROOT, "Heuristic shield / spacing %.1f", spacing),
                config(EgoType.EgoVehicle, ShieldType.HEURISTIC, spacing),
                initialStates,
                spacingDirectory.resolve("heuristic_shield")
        );
        runShield(
                String.format(Locale.ROOT, "Reco shield / spacing %.1f", spacing),
                config(EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM, spacing),
                initialStates,
                spacingDirectory.resolve("reco_shield")
        );
    }

    private static List<String> generateInitialStates(Path spacingDirectory, double spacing) {
        JavaMomentumConfig generationConfig = config(
                EgoType.EgoVehicle,
                ShieldType.HEURISTIC,
                spacing
        );
        List<String> initialStates = new ArrayList<>(EPISODES);

        for (int i = 0; i < EPISODES; i++) {
            String json = SameInitialRunTable3.generateInitialStateJson(generationConfig);
            initialStates.add(json);
            SameInitialRunTable3.saveJson(
                    spacingDirectory.resolve("initial_states")
                            .resolve(String.format("initial_%03d.json", i)),
                    json
            );
        }
        return initialStates;
    }

    private static void runShield(String name, JavaMomentumConfig config,
                                  List<String> initialStates, Path outputDirectory) {
        System.out.println("========== Running " + name + " ==========");
        SameInitialRunTable3.runAndReport(name, config, initialStates, outputDirectory);
    }

    private static JavaMomentumConfig config(EgoType egoType, ShieldType shieldType,
                                             double spacing) {
        JavaMomentumConfig config = SameInitialRunTable3.defaultExperimentConfig();
        config.setEgoType(egoType);
        config.setShieldType(shieldType);
        config.setAiProfile(AIProfile.adversarial);
        config.setNumsOfSimulations(EPISODES);
        config.setVehicleSpacing(spacing);
        return config;
    }
}
