package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Shield.Reco4HzShield;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.nio.file.Path;
import java.util.List;

/** Reproduces the 4 Hz periodic-intervention comparison in Table 10. */
public class PeriodicInterventionExperiment {
    public static void main(String[] args) throws Exception {
        Path root = PaperExperimentSupport.logRoot("table_10_periodic_intervention");
        List<Setting> settings = List.of(
                new Setting("Heuristic shield", EgoType.EgoVehicle, ShieldType.HEURISTIC),
                new Setting("Heuristic shield f", EgoType.PeriodicInterventionEgoVehicle,
                        ShieldType.HEURISTIC_4HZ),
                new Setting("RecoHeuristic shield", EgoType.PeriodicInterventionEgoVehicle,
                        ShieldType.RECO_4HZ)
        );

        for (Setting setting : settings) {
            if (setting.shieldType == ShieldType.RECO_4HZ) {
                Reco4HzShield.resetEvaluationTimingStatistics();
            }
            JavaMomentumConfig config = PaperExperimentSupport.config(
                    setting.egoType,
                    setting.shieldType,
                    AIProfile.adversarial,
                    root
            );
            PaperExperimentSupport.run(setting.name + " / adversarial", config);
            if (setting.shieldType == ShieldType.RECO_4HZ) {
                System.out.printf("Reco4HzShield mean evaluation time: %.3f ms (%d evaluations)%n",
                        Reco4HzShield.getMeanEvaluationMillis(),
                        Reco4HzShield.getEvaluationCount());
            }
        }
        System.out.println("Table 10 logs: " + root.toAbsolutePath());
    }

    private record Setting(String name, EgoType egoType, ShieldType shieldType) {
    }
}
