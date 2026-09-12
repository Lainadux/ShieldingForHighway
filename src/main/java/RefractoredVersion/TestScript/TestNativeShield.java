package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.*;

public class TestNativeShield {
    public static void main(String[] args) throws Exception {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.GEN_LOGS);
        config.setRealWorldEngineType(RealWorldEngineType.BRUTAL);
        config.setNumsOfSimulations(100);
        config.setEgoType(EgoType.CascadedRecoExploreVehicle);
        config.setShieldType(ShieldType.STARK_NATIVE_RANDOM_IDM);
        config.setAiProfile(AIProfile.adversarial);
        config.setPATH_TO_SAVE("src/main/java/RefractoredVersion/logs/" + System.currentTimeMillis() + "/");

        //config.setTestFunction(TestFunction.QUICK_TEST);


        //config.setMinX(0);
       //config.setMaxX(400);
        config.setDuration(30);
        config.setFrequency(20);
        //config.setPredictionTime(1);
        config.setMaxTargetSpeed(40);
        config.setFixPrediction(true);
        config.setSensorRange(100);
        config.setNoisySensorOuterRange(100);

        Runner runner = new Runner();
        runner.run(config);
    }
}
