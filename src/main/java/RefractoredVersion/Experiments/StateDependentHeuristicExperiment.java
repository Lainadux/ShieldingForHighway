package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.nio.file.Path;
import java.util.List;

/** Reproduces the state-dependent stochastic heuristic comparison in Table 11. */
public class StateDependentHeuristicExperiment {
    public static void main(String[] args) throws Exception {
        Path root = PaperExperimentSupport.logRoot("table_11_state_dependent_heuristic");
        List<Setting> settings = List.of(
                new Setting("Heuristic shield", EgoType.EgoVehicle, ShieldType.HEURISTIC),
                new Setting("Heuristic shield s", EgoType.EgoVehicle,
                        ShieldType.TTC_STOCHASTIC_HEURISTIC),
                new Setting("No shield", EgoType.NoShieldEgo, ShieldType.NONE)
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
        System.out.println("Table 11 logs: " + root.toAbsolutePath());
    }

    private record Setting(String name, EgoType egoType, ShieldType shieldType) {
    }
}
