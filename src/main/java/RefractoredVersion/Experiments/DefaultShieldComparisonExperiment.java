package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.nio.file.Path;
import java.util.List;

/** Reproduces the default shield comparison reported in Table 4. */
public class DefaultShieldComparisonExperiment {
    public static void main(String[] args) throws Exception {
        Path root = PaperExperimentSupport.logRoot("table_4_default_shield_comparison");
        List<Setting> settings = List.of(
                new Setting("Reco shield / base", EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM, AIProfile.base),
                new Setting("Reco shield / adversarial", EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM, AIProfile.adversarial),
                new Setting("Heuristic shield / base", EgoType.EgoVehicle,
                        ShieldType.HEURISTIC, AIProfile.base),
                new Setting("Heuristic shield / adversarial", EgoType.EgoVehicle,
                        ShieldType.HEURISTIC, AIProfile.adversarial),
                new Setting("No shield / base", EgoType.NoShieldEgo,
                        ShieldType.NONE, AIProfile.base),
                new Setting("No shield / adversarial", EgoType.NoShieldEgo,
                        ShieldType.NONE, AIProfile.adversarial)
        );

        for (Setting setting : settings) {
            JavaMomentumConfig config = PaperExperimentSupport.config(
                    setting.egoType, setting.shieldType, setting.aiProfile, root);
            PaperExperimentSupport.run(setting.name, config);
        }
        System.out.println("Table 4 logs: " + root.toAbsolutePath());
    }

    private record Setting(String name, EgoType egoType, ShieldType shieldType, AIProfile aiProfile) {
    }
}
