package RefractoredVersion.TestScript.Logging;



import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Records.CrashDetail;
import RefractoredVersion.TestScript.Records.EpisodeAcceptanceSequence;
import RefractoredVersion.TestScript.Stats.CollisionStats;
import RefractoredVersion.TestScript.Stats.DistanceStats;
import RefractoredVersion.TestScript.Stats.GenLogSummary;
import RefractoredVersion.TestScript.Stats.ShieldStats;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public class GenLogReportWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final LogDirectory logDirectory;

    public GenLogReportWriter(LogDirectory logDirectory) {
        this.logDirectory = logDirectory;
    }

    public void saveInitialState(JavaMomentumConfig config,
                                 String initialStateJson,
                                 boolean crashed,
                                 int simulationIndex) {
        String prefix = crashed ? "crashed_" : "safe_";
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        Path logFile = logDirectory.forConfig(config)
                .resolve(prefix + timestamp + "_" + simulationIndex + ".json");

        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, initialStateJson, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save initial state log: " + logFile, e);
        }
    }

    public void saveSummary(JavaMomentumConfig config, GenLogSummary summary) {
        Path logFile = logDirectory.forConfig(config)
                .resolve("summary_" + LocalDateTime.now().format(LOG_TIME_FORMAT) + ".json");

        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, GSON.toJson(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save gen log summary: " + logFile, e);
        }
    }

    public void saveStatFiles(JavaMomentumConfig config, GenLogSummary summary) {
        Path logDir = logDirectory.forConfig(config);

        try {
            Files.createDirectories(logDir);

            Files.writeString(
                    logDir.resolve("collision_stats.json"),
                    GSON.toJson(new CollisionStats(summary)),
                    StandardCharsets.UTF_8
            );

            Files.writeString(
                    logDir.resolve("shield_stats.json"),
                    GSON.toJson(new ShieldStats(summary)),
                    StandardCharsets.UTF_8
            );

            Files.writeString(
                    logDir.resolve("distance_stats.json"),
                    GSON.toJson(new DistanceStats(summary)),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save gen log statistics in: " + logDir, e);
        }
    }

    public void saveCrashDetails(JavaMomentumConfig config, List<CrashDetail> crashDetails) {
        Path logFile = logDirectory.forConfig(config).resolve("crash_details.json");

        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, GSON.toJson(crashDetails), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save crash details: " + logFile, e);
        }
    }

    public void saveAcceptanceSequences(JavaMomentumConfig config,
                                        List<EpisodeAcceptanceSequence> acceptanceSequences) {
        Path logFile = logDirectory.forConfig(config).resolve("episode_acceptance_sequences.json");
        List<EpisodeAcceptanceSequence> sorted = acceptanceSequences.stream()
                .sorted(Comparator.comparingInt(sequence -> sequence.simulationIndex))
                .toList();

        try {
            Files.createDirectories(logFile.getParent());
            Files.writeString(logFile, GSON.toJson(sorted), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save acceptance sequences: " + logFile, e);
        }
    }
}
