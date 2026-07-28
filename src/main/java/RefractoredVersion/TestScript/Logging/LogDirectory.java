package RefractoredVersion.TestScript.Logging;

import RefractoredVersion.TestScript.Config.JavaMomentumConfig;

import java.nio.file.Path;

public class LogDirectory {
    public Path forConfig(JavaMomentumConfig config) {
        return Path.of(config.getPATH_TO_SAVE())
                .resolve(config.getEgoType().name() + "_" + config.getAiProfile().name() + "_LOGS");
    }
}