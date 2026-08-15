package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.*;

public class TestNativeShield {
    public static void main(String[] args) throws Exception {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.GEN_LOGS);
        config.setRealWorldEngineType(RealWorldEngineType.BRUTAL);
        config.setNumsOfSimulations(100);
        config.setEgoType(EgoType.ExploreFutureEgo);
        config.setShieldType(ShieldType.STARK_NATIVE);
        config.setAiProfile(AIProfile.adversarial);

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
