package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.nio.file.Path;

/** Reproduces the constant-speed baseline reported in Table 5. */
public class ConstantSpeedBaselineExperiment {
    private static final double[] SPEEDS = {20.0, 21.0, 22.0};

    public static void main(String[] args) throws Exception {
        Path root = PaperExperimentSupport.logRoot("table_5_constant_speed");
        for (double speed : SPEEDS) {
            Path output = root.resolve("speed_" + (int) speed);
            JavaMomentumConfig config = PaperExperimentSupport.config(
                    EgoType.ConstantSpeedEgoVehicle,
                    ShieldType.NONE,
                    AIProfile.base,
                    output
            );
            config.setInitialEgoSpeed(speed);
            PaperExperimentSupport.run("Constant speed " + speed + " m/s", config);
        }
        System.out.println("Table 5 logs: " + root.toAbsolutePath());
    }
}
