package RefractoredVersion.TestScript.Logging;

import RefractoredVersion.TestScript.Config.JavaMomentumConfig;

import java.nio.file.Path;

public class LogDirectory {
    public Path forConfig(JavaMomentumConfig config) {
        return Path.of(config.getPATH_TO_SAVE())
                .resolve(nameFor(config));
    }

    private String nameFor(JavaMomentumConfig config) {
        String shieldType = config.getShieldType() == null ? "NO_SHIELD_TYPE" : config.getShieldType().name();
        return config.getEgoType().name() + "_" + config.getAiProfile().name() + "_" + shieldType + "_LOGS";
    }
}
