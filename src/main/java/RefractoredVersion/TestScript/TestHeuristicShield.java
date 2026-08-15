package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.*;

public class TestHeuristicShield {
    public static void main(String[] args) throws Exception {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.QUICK_TEST);
        config.setRealWorldEngineType(RealWorldEngineType.BRUTAL);
        config.setPATH_TO_SAVE("src/main/java/RefractoredVersion/logs/Heursitic200Brutal" + System.currentTimeMillis() + "/");
        config.setEgoType(EgoType.EgoVehicle);
        config.setShieldType(ShieldType.HEURISTIC);

        config.setTestFunction(TestFunction.GEN_LOGS);

        config.setAiProfile(AIProfile.adversarial);
        config.setNumsOfSimulations(200);
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
