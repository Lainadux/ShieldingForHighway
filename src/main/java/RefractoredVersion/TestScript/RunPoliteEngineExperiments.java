package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.RealWorldEngineType;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.Config.TestFunction;

import java.util.List;

public class RunPoliteEngineExperiments {
    private static final int EPISODES = 100;

    public static void main(String[] args) throws Exception {
        String root = "src/main/java/RefractoredVersion/logs/polite_table_"
                + System.currentTimeMillis() + "/";

        List<ExperimentSetting> settings = List.of(
                new ExperimentSetting(
                        "Reco shield / base / polite real-world engine",
                        EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM,
                        AIProfile.base
                ),
                new ExperimentSetting(
                        "Reco shield / adversarial / polite real-world engine",
                        EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM,
                        AIProfile.adversarial
                ),
                new ExperimentSetting(
                        "Heuristic shield / base / polite real-world engine",
                        EgoType.EgoVehicle,
                        ShieldType.HEURISTIC,
                        AIProfile.base
                ),
                new ExperimentSetting(
                        "Heuristic shield / adversarial / polite real-world engine",
                        EgoType.EgoVehicle,
                        ShieldType.HEURISTIC,
                        AIProfile.adversarial
                ),
                new ExperimentSetting(
                        "No shield / base / polite real-world engine",
                        EgoType.NoShieldEgo,
                        ShieldType.NONE,
                        AIProfile.base
                ),
                new ExperimentSetting(
                        "No shield / adversarial / polite real-world engine",
                        EgoType.NoShieldEgo,
                        ShieldType.NONE,
                        AIProfile.adversarial
                ),
                new ExperimentSetting(
                        "RecoExplore shield / adversarial / polite real-world engine",
                        EgoType.ExploreFutureEgo,
                        ShieldType.STARK_NATIVE_RANDOM_IDM,
                        AIProfile.adversarial
                ),
                new ExperimentSetting(
                        "Reco shield / adversarial delayed / polite real-world engine",
                        EgoType.ExploreFutureDelayedVehicle,
                        ShieldType.EXPLORE_FUTURE_DELAYED_ACTION,
                        AIProfile.adversarial
                ),
                new ExperimentSetting(
                        "Heuristic 4 Hz / adversarial / polite real-world engine",
                        EgoType.PeriodicInterventionEgoVehicle,
                        ShieldType.HEURISTIC_4HZ,
                        AIProfile.adversarial
                ),
                new ExperimentSetting(
                        "Reco 4 Hz / adversarial / polite real-world engine",
                        EgoType.PeriodicInterventionEgoVehicle,
                        ShieldType.RECO_4HZ,
                        AIProfile.adversarial
                )
        );

        for (ExperimentSetting setting : settings) {
            System.out.println("========== Running " + setting.name + " ==========");
            new Runner().run(configFor(setting, root));
        }

        System.out.println("All polite-engine experiments finished. Root log directory: " + root);
    }

    private static JavaMomentumConfig configFor(ExperimentSetting setting, String root) {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.GEN_LOGS);
        config.setPATH_TO_SAVE(root);

        config.setEgoType(setting.egoType);
        config.setShieldType(setting.shieldType);
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
        config.setDelayedActionStep(1);
        return config;
    }

    private static final class ExperimentSetting {
        private final String name;
        private final EgoType egoType;
        private final ShieldType shieldType;
        private final AIProfile aiProfile;

        private ExperimentSetting(String name,
                                  EgoType egoType,
                                  ShieldType shieldType,
                                  AIProfile aiProfile) {
            this.name = name;
            this.egoType = egoType;
            this.shieldType = shieldType;
            this.aiProfile = aiProfile;
        }
    }
}
