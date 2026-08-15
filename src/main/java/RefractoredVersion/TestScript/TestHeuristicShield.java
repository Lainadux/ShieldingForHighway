package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.Config.TestFunction;

public class TestHeuristicShield {
    public static void main(String[] args) throws Exception {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.QUICK_TEST);
        config.setPATH_TO_SAVE("src/main/java/RefractoredVersion/logs/InstantHeuristicSHield" + System.currentTimeMillis() + "/");
        config.setEgoType(EgoType.EgoVehicle);
        config.setShieldType(ShieldType.INSTANT_HEURISTIC);

        config.setTestFunction(TestFunction.GEN_LOGS);
        config.setAiProfile(AIProfile.adversarial);
        config.setNumsOfSimulations(50);
        config.setMinX(0);
        config.setMaxX(400);
        config.setDuration(30);
        config.setFrequency(20);
        config.setMaxTargetSpeed(40);
        config.setSensorRange(100);
        config.setNoisySensorOuterRange(100);

        Runner runner = new Runner();
        runner.run(config);
    }
}
