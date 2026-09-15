package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.*;

public class TestHeuristicShield {
    public static void main(String[] args) throws Exception {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.QUICK_TEST);
        config.setRealWorldEngineType(RealWorldEngineType.GENTLE);
        config.setPATH_TO_SAVE("src/main/java/RefractoredVersion/logs/" + System.currentTimeMillis() + "/");
        config.setEgoType(EgoType.EgoVehicle);
        config.setShieldType(ShieldType.HEURISTIC);
        config.setTestFunction(TestFunction.GEN_LOGS);

        //config.setTestFunction(TestFunction.GEN_LOGS);
        //config.setTestFunction(TestFunction.RECOVER);


        String logsRoot = "C:\\MSCProject\\RefractoredStarkedShield\\src\\main\\java\\RefractoredVersion\\logs\\table3_1786988006539\\ExploreFutureEgo_adversarial_STARK_NATIVE_LOGS\\safe_20260817_195343_093_1.json";

        config.setRecoverInitialStateFile(logsRoot);


        config.setAiProfile(AIProfile.adversarial);
        config.setNumsOfSimulations(100);
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
