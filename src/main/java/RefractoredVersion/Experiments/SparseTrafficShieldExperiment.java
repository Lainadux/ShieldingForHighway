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

/** Reproduces the shield evaluation at traffic spacing 1.5 in Table 7. */
public class SparseTrafficShieldExperiment {
    private static final double VEHICLE_SPACING = 1.5;

    public static void main(String[] args) {
        Path root = PaperExperimentSupport.logRoot("table_7_sparse_traffic");
        try {
            List<String> initialStates = generateInitialStates(root.resolve("initial_states"));
            runShield(
                    "Heuristic shield / spacing 1.5",
                    config(EgoType.EgoVehicle, ShieldType.HEURISTIC, root),
                    initialStates,
                    root.resolve("heuristic_shield")
            );
            runShield(
                    "Reco shield / spacing 1.5",
                    config(EgoType.ExploreFutureSlowerVehicle,
                            ShieldType.STARK_NATIVE_RANDOM_IDM, root),
                    initialStates,
                    root.resolve("reco_shield")
            );
            System.out.println("Table 7 logs: " + root.toAbsolutePath());
        } finally {
            JavaHighwayAiClient.stopAll();
        }
    }

    private static List<String> generateInitialStates(Path outputDirectory) {
        JavaMomentumConfig generationConfig = config(
                EgoType.EgoVehicle, ShieldType.HEURISTIC, outputDirectory);
        List<String> states = new ArrayList<>(PaperExperimentSupport.EPISODES);
        for (int i = 0; i < PaperExperimentSupport.EPISODES; i++) {
            String initialState = SameInitialRunTable3.generateInitialStateJson(generationConfig);
            states.add(initialState);
            SameInitialRunTable3.saveJson(
                    outputDirectory.resolve(String.format("initial_%03d.json", i)),
                    initialState
            );
        }
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
                                             Path outputDirectory) {
        JavaMomentumConfig config = PaperExperimentSupport.config(
                egoType, shieldType, AIProfile.adversarial, outputDirectory);
        config.setVehicleSpacing(VEHICLE_SPACING);
        return config;
    }
}
