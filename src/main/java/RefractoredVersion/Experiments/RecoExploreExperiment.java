package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.nio.file.Path;
import java.util.List;

/** Reproduces the alternative-recovery-action comparison in Table 8. */
public class RecoExploreExperiment {
    public static void main(String[] args) throws Exception {
        Path root = PaperExperimentSupport.logRoot("table_8_reco_explore");
        List<Setting> settings = List.of(
                new Setting("No shield", EgoType.NoShieldEgo, ShieldType.NONE),
                new Setting("Reco shield", EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM),
                new Setting("RecoExplore shield", EgoType.ExploreFutureEgo,
                        ShieldType.STARK_NATIVE_RANDOM_IDM)
        );
        for (Setting setting : settings) {
            JavaMomentumConfig config = PaperExperimentSupport.config(
                    setting.egoType,
                    setting.shieldType,
                    AIProfile.adversarial,
                    root
            );
            PaperExperimentSupport.run(setting.name + " / adversarial", config);
        }
        System.out.println("Table 8 logs: " + root.toAbsolutePath());
    }

    private record Setting(String name, EgoType egoType, ShieldType shieldType) {
    }
}
