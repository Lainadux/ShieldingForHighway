package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.RealWorldEngineType;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.Config.TestFunction;
import RefractoredVersion.TestScript.Runner;

import java.nio.file.Path;

final class PaperExperimentSupport {
    static final int EPISODES = 100;

    private PaperExperimentSupport() {
    }

    static Path logRoot(String experimentName) {
        return Path.of(
                "src/main/java/RefractoredVersion/logs/paper_experiments",
                experimentName + "_" + System.currentTimeMillis()
        );
    }

    static JavaMomentumConfig config(EgoType egoType,
                                     ShieldType shieldType,
                                     AIProfile aiProfile,
                                     Path outputDirectory) {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.GEN_LOGS);
        config.setPATH_TO_SAVE(outputDirectory.toString());
        config.setEgoType(egoType);
        config.setShieldType(shieldType);
        config.setAiProfile(aiProfile);
        config.setNumsOfSimulations(EPISODES);
        config.setRealWorldEngineType(RealWorldEngineType.BRUTAL);
        config.setDuration(30);
        config.setFrequency(20);
        config.setMaxTargetSpeed(40.0);
        config.setFixPrediction(true, 1.0);
        config.setSensorRange(100);
        config.setNoisySensorOuterRange(100);
        config.setMinX(0.0);
        config.setMaxX(400.0);
        config.setVehicleSpacing(1.0);
        return config;
    }

    static void run(String name, JavaMomentumConfig config) throws Exception {
        System.out.println("========== Running " + name + " ==========");
        new Runner().run(config);
    }
}
