package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.Config.TestFunction;

public class TestNativeShield {
    public static void main(String[] args) throws Exception {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.QUICK_TEST);
        config.setEgoType(EgoType.ExploreFutureSlowerVehicle);
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
