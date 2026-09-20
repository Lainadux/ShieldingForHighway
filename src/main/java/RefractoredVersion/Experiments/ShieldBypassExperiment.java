package RefractoredVersion.Experiments;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.nio.file.Path;

/** Reproduces the shield-bypass ablation shown in Figure 5. */
public class ShieldBypassExperiment {
    private static final int[] BYPASS_PERCENTAGES = {10, 20, 40, 60};

    public static void main(String[] args) throws Exception {
        Path root = PaperExperimentSupport.logRoot("figure_5_shield_bypass");
        for (int bypassPercent : BYPASS_PERCENTAGES) {
            JavaMomentumConfig config = PaperExperimentSupport.config(
                    EgoType.EgoRandomEnableShield,
                    ShieldType.STARK_NATIVE_RANDOM_IDM,
                    AIProfile.adversarial,
                    root.resolve("bypass_" + bypassPercent)
            );
            config.setRandomEnableShieldPercent(bypassPercent);
            PaperExperimentSupport.run("Shield bypass " + bypassPercent + "%", config);
        }
        System.out.println("Figure 5 logs: " + root.toAbsolutePath());
    }
}
