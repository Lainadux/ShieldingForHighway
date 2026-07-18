package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.TestFunction;

public class RecoverRun {
    public static void main(String[] args) throws Exception {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            throw new IllegalArgumentException("Usage: RecoverRun <initial-state-json> [durationSeconds]");
        }

        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setTestFunction(TestFunction.RECOVER);
        config.setRecoverInitialStateFile(args[0]);
        config.setEgoType(EgoType.EgoVehicle);
        config.setAiProfile(AIProfile.adversarial);
        config.setDuration(args.length > 1 ? Integer.parseInt(args[1]) : 40);
        config.setFrequency(20);
        config.setMaxTargetSpeed(40);
        config.setPredictionTime(1);

        System.out.printf("Recover run: state=%s duration=%ds egoType=%s aiProfile=%s%n",
                config.getRecoverInitialStateFile(),
                config.getDuration(),
                config.getEgoType(),
                config.getAiProfile());

        new Runner().run(config);
    }
}
