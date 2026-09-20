package RefractoredVersion.TestScript;

import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.RealWorldEngineType;
import RefractoredVersion.TestScript.Config.TestFunction;

public class RunConstantSpeedEgo {
    private static final double INITIAL_SPEED = 22.0;
    private static final int EPISODES = 100;

    public static void main(String[] args) throws Exception {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setEgoType(EgoType.ConstantSpeedEgoVehicle);
        config.setInitialEgoSpeed(INITIAL_SPEED);
        config.setMinX(0.0);
        config.setMaxX(400.0);
        config.setDuration(30);
        config.setFrequency(20);
        config.setNumsOfSimulations(EPISODES);
        config.setRealWorldEngineType(RealWorldEngineType.GENTLE);
        config.setPATH_TO_SAVE(
                "src/main/java/RefractoredVersion/logs/constant_speed_" + System.currentTimeMillis() + "/"
        );
        config.setTestFunction(TestFunction.GEN_LOGS);

        new Runner().run(config);
    }
}
