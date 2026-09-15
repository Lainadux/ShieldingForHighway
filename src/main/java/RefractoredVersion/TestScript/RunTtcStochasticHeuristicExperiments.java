package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.RealWorldEngineType;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.Config.TestFunction;

import java.util.List;

public class RunTtcStochasticHeuristicExperiments {
    private static final int EPISODES = 100;

    public static void main(String[] args) throws Exception {
        String root = "src/main/java/RefractoredVersion/logs/ttc_stochastic_heuristic_"
                + System.currentTimeMillis() + "/";

        List<ExperimentSetting> settings = List.of(

                new ExperimentSetting(
                        "TTC stochastic heuristic shield / adversarial",
                        AIProfile.adversarial
                )
        );

        for (ExperimentSetting setting : settings) {
            System.out.println("========== Running " + setting.name + " ==========");
            new Runner().run(configFor(setting, root));
        }

        System.out.println(
                "All TTC stochastic heuristic experiments finished. Root log directory: " + root
        );
    }

    private static JavaMomentumConfig configFor(ExperimentSetting setting, String root) {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.GEN_LOGS);
        config.setPATH_TO_SAVE(root);

        config.setEgoType(EgoType.EgoVehicle);
        config.setShieldType(ShieldType.TTC_STOCHASTIC_HEURISTIC);
        config.setAiProfile(setting.aiProfile);

        config.setNumsOfSimulations(EPISODES);
        config.setRealWorldEngineType(RealWorldEngineType.GENTLE);
        config.setDuration(30);
        config.setFrequency(20);
        config.setMaxTargetSpeed(40);
        config.setPredictionTime(1);
        config.setFixPrediction(true);
        config.setSensorRange(100);
        config.setNoisySensorOuterRange(100);
        config.setMinX(0);
        config.setMaxX(400);
        return config;
    }

    private static final class ExperimentSetting {
        private final String name;
        private final AIProfile aiProfile;

        private ExperimentSetting(String name, AIProfile aiProfile) {
            this.name = name;
            this.aiProfile = aiProfile;
        }
    }
}
