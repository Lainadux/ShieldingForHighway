package RefractoredVersion.TestScript.Logging;



import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Engine.Action;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.RealWorldEngineType;
import RefractoredVersion.TestScript.Config.SandboxNpcPolitenessMode;
import RefractoredVersion.TestScript.Config.ShieldType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class RunMetadataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final LogDirectory logDirectory;

    public RunMetadataStore(LogDirectory logDirectory) {
        this.logDirectory = logDirectory;
    }

    public void save(JavaMomentumConfig config) {
        Path metadataPath = logDirectory.forConfig(config).resolve("meta.json");
        try {
            Files.createDirectories(metadataPath.getParent());
            Files.writeString(metadataPath, GSON.toJson(new RunMetadata(config)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save run metadata: " + metadataPath, e);
        }
    }

    public void applyRecovered(Path initialStatePath, JavaMomentumConfig config) {
        Path metadataPath = sharedMetadataPathFor(initialStatePath);
        if (!Files.exists(metadataPath)) {
            metadataPath = legacyMetadataPathFor(initialStatePath);
        }
        if (!Files.exists(metadataPath)) {
            System.out.println("No recover metadata found for " + initialStatePath
                    + "; using current JavaMomentumConfig values.");
            return;
        }

        try {
            RunMetadata metadata = GSON.fromJson(
                    Files.readString(metadataPath, StandardCharsets.UTF_8),
                    RunMetadata.class
            );
            if (metadata != null) {
                metadata.applyTo(config);
                printRecoveredConfig(config);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read recover metadata: " + metadataPath, e);
        }
    }

    private Path sharedMetadataPathFor(Path logFile) {
        Path parent = logFile.getParent();
        return parent == null ? Path.of("meta.json") : parent.resolve("meta.json");
    }

    private Path legacyMetadataPathFor(Path logFile) {
        return Path.of(logFile.toString() + ".meta.json");
    }

    private void printRecoveredConfig(JavaMomentumConfig config) {
        System.out.printf(
                "Recovered config: egoType=%s aiProfile=%s frequency=%d duration=%d predictionTime=%d maxTargetSpeed=%.2f fixPrediction=%s fixedPredictionTargetSpeedDelta=%.2f aggressiveV3TtcThreshold=%.2f shieldType=%s realWorldEngineType=%s delayedActionStep=%d sensorRange=%d noisySensorOuterRange=%d randomEnableShieldPercent=%d randomizeNpcPoliteness=%s sandboxNpcPolitenessMode=%s futureActions=%s%n",
                config.getEgoType(),
                config.getAiProfile(),
                config.getFrequency(),
                config.getDuration(),
                config.getPredictionTime(),
                config.getMaxTargetSpeed(),
                config.isFixPrediction(),
                config.getFixedPredictionTargetSpeedDelta(),
                config.getAggressiveV3TtcThreshold(),
                config.getShieldType(),
                config.getRealWorldEngineType(),
                config.getDelayedActionStep(),
                config.getSensorRange(),
                config.getNoisySensorOuterRange(),
                config.getRandomEnableShieldPercent(),
                config.isRandomizeNpcPoliteness(),
                config.getSandboxNpcPolitenessMode(),
                config.getFutureActions()
        );
    }

    private static class RunMetadata {
        private String egoType;
        private String aiProfile;
        private Integer frequency;
        private Integer duration;
        private Integer predictionTime;
        private Double maxTargetSpeed;
        private Boolean fixPrediction;
        private Double fixedPredictionTargetSpeedDelta;
        private Double aggressiveV3TtcThreshold;
        private String shieldType;
        private String realWorldEngineType;
        private Integer delayedActionStep;
        private Integer sensorRange;
        private Integer noisySensorOuterRange;
        private Integer randomEnableShieldPercent;
        private Boolean randomizeNpcPoliteness;
        private String sandboxNpcPolitenessMode;
        private List<String> futureActions;
        private Double minX;
        private Double maxX;

        private RunMetadata(JavaMomentumConfig config) {
            this.egoType = config.getEgoType() == null ? null : config.getEgoType().name();
            this.aiProfile = config.getAiProfile() == null ? null : config.getAiProfile().name();
            this.frequency = config.getFrequency();
            this.duration = config.getDuration();
            this.predictionTime = config.getPredictionTime();
            this.maxTargetSpeed = config.getMaxTargetSpeed();
            this.fixPrediction = config.isFixPrediction();
            this.fixedPredictionTargetSpeedDelta = config.getFixedPredictionTargetSpeedDelta();
            this.aggressiveV3TtcThreshold = config.getAggressiveV3TtcThreshold();
            this.shieldType = config.getShieldType() == null ? null : config.getShieldType().name();
            this.realWorldEngineType = config.getRealWorldEngineType() == null
                    ? null
                    : config.getRealWorldEngineType().name();
            this.delayedActionStep = config.getDelayedActionStep();
            this.sensorRange = config.getSensorRange();
            this.noisySensorOuterRange = config.getNoisySensorOuterRange();
            this.randomEnableShieldPercent = config.getRandomEnableShieldPercent();
            this.randomizeNpcPoliteness = config.isRandomizeNpcPoliteness();
            this.sandboxNpcPolitenessMode = config.getSandboxNpcPolitenessMode() == null
                    ? null
                    : config.getSandboxNpcPolitenessMode().name();
            this.futureActions = config.getFutureActions() == null
                    ? null
                    : config.getFutureActions().stream().map(Action::name).toList();
            this.minX = config.getMinX();
            this.maxX = config.getMaxX();
        }

        private void applyTo(JavaMomentumConfig config) {
            if (egoType != null && !egoType.isBlank()) {
                config.setEgoType(EgoType.valueOf(egoType));
            }
            if (aiProfile != null && !aiProfile.isBlank()) {
                config.setAiProfile(AIProfile.valueOf(aiProfile));
            }
            if (frequency != null) {
                config.setFrequency(frequency);
            }
            if (duration != null) {
                config.setDuration(duration);
            }
            if (predictionTime != null) {
                config.setPredictionTime(predictionTime);
            }
            if (maxTargetSpeed != null) {
                config.setMaxTargetSpeed(maxTargetSpeed);
            }
            if (fixPrediction != null) {
                config.setFixPrediction(fixPrediction);
            }
            if (fixedPredictionTargetSpeedDelta != null) {
                config.setFixedPredictionTargetSpeedDelta(fixedPredictionTargetSpeedDelta);
            }
            if (aggressiveV3TtcThreshold != null) {
                config.setAggressiveV3TtcThreshold(aggressiveV3TtcThreshold);
            }
            if (shieldType != null && !shieldType.isBlank()) {
                config.setShieldType(ShieldType.valueOf(shieldType));
            }
            if (realWorldEngineType != null && !realWorldEngineType.isBlank()) {
                config.setRealWorldEngineType(RealWorldEngineType.valueOf(realWorldEngineType));
            }
            if (delayedActionStep != null) {
                config.setDelayedActionStep(delayedActionStep);
            }
            if (sensorRange != null) {
                config.setSensorRange(sensorRange);
            }
            if (noisySensorOuterRange != null) {
                config.setNoisySensorOuterRange(noisySensorOuterRange);
            }
            if (randomEnableShieldPercent != null) {
                config.setRandomEnableShieldPercent(randomEnableShieldPercent);
            }
            if (randomizeNpcPoliteness != null) {
                config.setRandomizeNpcPoliteness(randomizeNpcPoliteness);
            }
            if (sandboxNpcPolitenessMode != null && !sandboxNpcPolitenessMode.isBlank()) {
                config.setSandboxNpcPolitenessMode(SandboxNpcPolitenessMode.valueOf(sandboxNpcPolitenessMode));
            }
            if (futureActions != null) {
                config.setFutureActions(futureActions.stream().map(Action::valueOf).toList());
            }
            if (minX != null) {
                config.setMinX(minX);
            }
            if (maxX != null) {
                config.setMaxX(maxX);
            }
        }
    }
}
