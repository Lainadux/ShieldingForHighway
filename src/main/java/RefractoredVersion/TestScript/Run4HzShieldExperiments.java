package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Shield.Reco4HzShield;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.RealWorldEngineType;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.Config.TestFunction;

import java.util.List;

public class Run4HzShieldExperiments {
    private static final int EPISODES = 20;

    public static void main(String[] args) throws Exception {
        String root = "src/main/java/RefractoredVersion/logs/four_hz_"
                + System.currentTimeMillis() + "/";

        List<ExperimentSetting> settings = List.of(

                new ExperimentSetting(
                        "Reco 4 Hz with periodicInterventionEgoVehicle / adversarial",
                        EgoType.PeriodicInterventionEgoVehicle,
                        ShieldType.RECO_4HZ
                ),
                new ExperimentSetting(
                        " HEURISTIC_4HZ with periodicInterventionEgoVehicle/ adversarial",
                        EgoType.PeriodicInterventionEgoVehicle,
                        ShieldType.HEURISTIC_4HZ
                )

        );

        for (ExperimentSetting setting : settings) {
            System.out.println("========== Running " + setting.name + " ==========");
            if (setting.shieldType == ShieldType.RECO_4HZ) {
                Reco4HzShield.resetEvaluationTimingStatistics();
            }
            new Runner().run(configFor(setting, root));
            if (setting.shieldType == ShieldType.RECO_4HZ) {
                System.out.printf(
                        "Reco4HzShield mean evaluation time: %.3f ms (%d evaluations)%n",
                        Reco4HzShield.getMeanEvaluationMillis(),
                        Reco4HzShield.getEvaluationCount()
                );
            }
        }

        System.out.println("All 4 Hz experiments finished. Root log directory: " + root);
    }

    private static JavaMomentumConfig configFor(ExperimentSetting setting, String root) {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.GEN_LOGS);
        config.setPATH_TO_SAVE(root);

        config.setEgoType(setting.egoType);
        config.setShieldType(setting.shieldType);
        config.setAiProfile(AIProfile.adversarial);

        config.setNumsOfSimulations(EPISODES);
        config.setRealWorldEngineType(RealWorldEngineType.BRUTAL);
        config.setDuration(30);
        config.setFrequency(20);
        config.setMaxTargetSpeed(40);
        config.setFixPrediction(true);
        config.setSensorRange(100);
        config.setNoisySensorOuterRange(100);
        config.setMinX(0);
        config.setMaxX(400);
        return config;
    }

    private static final class ExperimentSetting {
        private final String name;
        private final EgoType egoType;
        private final ShieldType shieldType;

        private ExperimentSetting(String name, EgoType egoType, ShieldType shieldType) {
            this.name = name;
            this.egoType = egoType;
            this.shieldType = shieldType;
        }
    }
}
