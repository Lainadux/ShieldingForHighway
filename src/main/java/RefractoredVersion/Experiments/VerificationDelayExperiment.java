package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.nio.file.Path;
import java.util.List;

/** Reproduces the 50 ms verification-delay comparison in Table 9. */
public class VerificationDelayExperiment {
    private static final int DELAY_STEPS = 1;

    public static void main(String[] args) throws Exception {
        Path root = PaperExperimentSupport.logRoot("table_9_verification_delay");
        List<Setting> settings = List.of(
                new Setting("No shield", EgoType.NoShieldEgo, ShieldType.NONE),
                new Setting("Reco shield", EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM),
                new Setting("Reco shield delayed", EgoType.ExploreFutureDelayedVehicle,
                        ShieldType.STARK_NATIVE_DELAYED_ACTION)
        );

        for (Setting setting : settings) {
            JavaMomentumConfig config = PaperExperimentSupport.config(
                    setting.egoType,
                    setting.shieldType,
                    AIProfile.adversarial,
                    root
            );
            config.setDelayedActionStep(DELAY_STEPS);
            PaperExperimentSupport.run(setting.name + " / adversarial", config);
        }
        System.out.println("Table 9 logs: " + root.toAbsolutePath());
    }

    private record Setting(String name, EgoType egoType, ShieldType shieldType) {
    }
}
