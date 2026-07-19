/*
 * STARK: Software Tool for the Analysis of Robustness in the unKnown environment
 *  
 *                Copyright (C) 2023.
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayAiClient;
import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Engine.BeforeCrashActionLog;
import RefractoredVersion.Engine.CollisionLog;
import RefractoredVersion.Engine.EgoVehicle;
import RefractoredVersion.Engine.ExploreFutureEgo;
import RefractoredVersion.Engine.RandomEgoVehicle;
import RefractoredVersion.Engine.Vehicle;
import RefractoredVersion.Engine.VehicleGenerator;
import RefractoredVersion.TestScript.Config.Config;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.FallBackMode;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.Config.TestFunction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;

public class Runner {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type VEHICLE_LIST_TYPE = new TypeToken<List<Vehicle>>() {}.getType();
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final int THREAD_COUNT = 4;

    Config config;
    public void run(Config config) throws Exception {
        this.config = config;
        if(config instanceof JavaMomentumConfig){
            JavaMomentumConfig javaMomentumConfig = (JavaMomentumConfig) config;
            if(javaMomentumConfig.getTestFunction() == TestFunction.RECOVER){
                recoverRun(javaMomentumConfig);
                return;
            }
            validateShieldConfig(javaMomentumConfig);
            if(javaMomentumConfig.getTestFunction() != TestFunction.GEN_LOGS){
                singleRun(javaMomentumConfig.getTestFunction() == TestFunction.QUICK_TEST);
                return;
            }

            int simulations = javaMomentumConfig.getNumOFSimulations() == null
                    ? javaMomentumConfig.DEFAULT_NUM_OF_SIMULATIONS
                    : javaMomentumConfig.getNumOFSimulations();
            int workerCount = Math.max(1, Math.min(THREAD_COUNT, simulations));
            ExecutorService executor = Executors.newFixedThreadPool(workerCount);
            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger completed = new AtomicInteger(0);
            GenLogStatistics statistics = new GenLogStatistics(simulations);
            List<CrashDetail> crashDetails = Collections.synchronizedList(new ArrayList<>());
            try {
                if (shouldSaveLogs(javaMomentumConfig)) {
                    saveRunMetadata(javaMomentumConfig);
                }
                for(int i = 0; i < simulations; i++){
                    final int simulationIndex = i;
                    futures.add(executor.submit(() -> {
                        SimulationRunResult result = null;
                        try {
                            result = singleRun(javaMomentumConfig, false, true);
                            if (result.crashed) {
                                System.err.println("Simulation " + simulationIndex + " crashed: "
                                        + result.crashException.getMessage());
                                crashDetails.add(new CrashDetail(simulationIndex, result));
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            if (shouldSaveLogs(javaMomentumConfig) && result != null && result.initialStateJson != null) {
                                saveInitialStateLog(javaMomentumConfig, result.initialStateJson,
                                        result.crashed, simulationIndex);
                            }
                            if (result != null) {
                                statistics.add(result);
                            }
                        }
                        int done = completed.incrementAndGet();
                        System.out.printf("Finished simulation %d/%d: %s%n",
                                done, simulations, result != null && result.crashed ? "crashed" : "safe");
                        return null;
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
                statistics.print();
                if (shouldSaveLogs(javaMomentumConfig)) {
                    saveGenLogSummary(javaMomentumConfig, statistics.snapshot());
                    saveGenLogStatFiles(javaMomentumConfig, statistics.snapshot());
                    saveCrashDetails(javaMomentumConfig, crashDetails);
                }
            } finally {
                executor.shutdownNow();
                JavaHighwayAiClient.stopAll();
            }
        }
    }

    public String singleRun(boolean rendered) throws Exception {
        if (config instanceof JavaMomentumConfig javaMomentumConfig) {
            return singleRun(javaMomentumConfig, rendered);
        }

        return null;
    }

    private String recoverRun(JavaMomentumConfig javaMomentumConfig) throws Exception {
        if (javaMomentumConfig.getRecoverInitialStateFile() == null
                || javaMomentumConfig.getRecoverInitialStateFile().isBlank()) {
            throw new IllegalArgumentException("recoverInitialStateFile must be set for TestFunction.RECOVER");
        }

        Path recoverPath = Path.of(javaMomentumConfig.getRecoverInitialStateFile());
        applyRecoveredRunMetadata(recoverPath, javaMomentumConfig);
        validateShieldConfig(javaMomentumConfig);

        JavaHighwayEngine realWorld = new JavaHighwayEngine();
        realWorld.setRenderEnabled(true);
        realWorld.setFrequency(javaMomentumConfig.getFrequency());
        realWorld.config = javaMomentumConfig;
        realWorld.vehicles = loadVehiclesFromLog(recoverPath, javaMomentumConfig);

        for(int i = 0; i < javaMomentumConfig.getDuration() * javaMomentumConfig.getFrequency(); i++){
            realWorld.step();
            realWorld.render();
        }
        return null;
    }

    private String singleRun(JavaMomentumConfig javaMomentumConfig, boolean rendered) throws Exception {
        SimulationRunResult result = singleRun(javaMomentumConfig, rendered, false);
        return result == null ? null : result.initialStateJson;
    }

    private SimulationRunResult singleRun(JavaMomentumConfig javaMomentumConfig, boolean rendered,
                                          boolean captureRuntimeCrash) throws Exception {
        if(config instanceof JavaMomentumConfig){
            JavaHighwayEngine realWorld = new JavaHighwayEngine();
            realWorld.setRenderEnabled(rendered);
            realWorld.setFrequency(javaMomentumConfig.getFrequency());
            realWorld.config = javaMomentumConfig;
            List<Vehicle> vehicles = VehicleGenerator.generateVehicles(javaMomentumConfig);
            String initialStateJson = GSON.toJson(vehicles);
            realWorld.vehicles = vehicles;

            try {
                for(int i =0; i<javaMomentumConfig.getDuration()* javaMomentumConfig.getFrequency();i++){
                    realWorld.step();
                    if(rendered){
                        realWorld.render();
                    }
                }
                return new SimulationRunResult(initialStateJson, false, null, getAiDecisionCount(realWorld),
                        getRejectedAiDecisionCount(realWorld), realWorld.getEgoFinalX(),
                        getBeforeCrashActions(realWorld), getCollisionLog(realWorld));
            } catch (RuntimeException e) {
                if (!captureRuntimeCrash) {
                    throw e;
                }
                return new SimulationRunResult(initialStateJson, true, e, getAiDecisionCount(realWorld),
                        getRejectedAiDecisionCount(realWorld), realWorld.getEgoFinalX(),
                        getBeforeCrashActions(realWorld), getCollisionLog(realWorld));
            }
        }

        return null;
    }

    private int getAiDecisionCount(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? 0 : egoVehicle.getAiDecisionCount();
    }

    private int getRejectedAiDecisionCount(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? 0 : egoVehicle.getRejectedAiDecisionCount();
    }

    private List<BeforeCrashActionLog> getBeforeCrashActions(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? List.of() : egoVehicle.retrieveBeforeCrashActions();
    }

    private CollisionLog getCollisionLog(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? null : egoVehicle.retrieveCrashCollisionLog();
    }

    private EgoVehicle getEgoVehicle(JavaHighwayEngine engine) {
        if (engine.vehicles == null) {
            return null;
        }
        for (Vehicle vehicle : engine.vehicles) {
            if (vehicle instanceof EgoVehicle egoVehicle) {
                return egoVehicle;
            }
        }
        return null;
    }

    private void validateShieldConfig(JavaMomentumConfig config) {
    }

    private List<Vehicle> loadVehiclesFromLog(Path path, JavaMomentumConfig config) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        List<Vehicle> rawVehicles = GSON.fromJson(json, VEHICLE_LIST_TYPE);
        if (rawVehicles == null || rawVehicles.isEmpty()) {
            throw new IllegalArgumentException("Recover log contains no vehicles: " + path);
        }

        List<Vehicle> restored = new ArrayList<>();
        for (Vehicle raw : rawVehicles) {
            Vehicle vehicle = "EGO".equals(raw.role) ? createRecoveredEgoVehicle(config) : new Vehicle();
            copyRecoveredVehicleState(raw, vehicle);
            restored.add(vehicle);
        }
        return restored;
    }

    private Vehicle createRecoveredEgoVehicle(JavaMomentumConfig config) {
        EgoType egoType = config.getEgoType() == null ? EgoType.RandomEgoVehicle : config.getEgoType();
        switch (egoType) {
            case EgoVehicle:
                EgoVehicle egoVehicle = new EgoVehicle();
                AIProfile aiProfile = config.getAiProfile() == null ? AIProfile.base : config.getAiProfile();
                egoVehicle.aiProfile = aiProfile;
                return egoVehicle;
            case ExploreFutureEgo:
                ExploreFutureEgo exploreFutureEgo = new ExploreFutureEgo();
                AIProfile exploreFutureAiProfile = config.getAiProfile() == null ? AIProfile.base : config.getAiProfile();
                exploreFutureEgo.aiProfile = exploreFutureAiProfile;
                return exploreFutureEgo;
            case RandomEgoVehicle:
                return new RandomEgoVehicle();
            default:
                throw new IllegalArgumentException("Unsupported recovered ego type: " + egoType);
        }
    }

    private void copyRecoveredVehicleState(Vehicle source, Vehicle target) {
        target.TAU_ACC = source.TAU_ACC;
        target.TAU_HEADING = source.TAU_HEADING;
        target.TAU_LATERAL = source.TAU_LATERAL;
        target.TAU_PURSUIT = source.TAU_PURSUIT;
        target.KP_A = source.KP_A;
        target.KP_HEADING = source.KP_HEADING;
        target.KP_LATERAL = source.KP_LATERAL;
        target.MAX_STEERING_ANGLE = source.MAX_STEERING_ANGLE;
        target.DELTA_SPEED = source.DELTA_SPEED;
        target.possible_lanes = source.possible_lanes == null ? null : source.possible_lanes.clone();
        target.karma_a_new = source.karma_a_new;
        target.mobil = source.mobil;
        target.targetSpeed = source.targetSpeed;
        target.id = source.id;
        target.politeness = source.politeness;
        target.cooldownTimer = source.cooldownTimer;
        target.setTargetLaneIndex(source.getTargetLaneIndex());
        target.setLaneIndex(source.getLaneIndex());
        target.role = source.role;
        target.x = source.x;
        target.y = source.y;
        target.vx = source.vx;
        target.vy = source.vy;
        target.speed = source.speed;
        target.previousSecondSpeed = source.previousSecondSpeed;
        target.heading = source.heading;
        target.plannedAcceleration = source.plannedAcceleration;
        target.plannedSteering = source.plannedSteering;
    }

    private boolean shouldSaveLogs(JavaMomentumConfig config) {
        return config.getPATH_TO_SAVE() != null && !config.getPATH_TO_SAVE().isBlank();
    }

    private void saveInitialStateLog(JavaMomentumConfig config, String initialStateJson, boolean crashed, int simulationIndex) {
        String prefix = crashed ? "crashed_" : "safe_";
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        Path logDir = logDirectory(config);
        Path logFile = logDir.resolve(prefix + timestamp + "_" + simulationIndex + ".json");

        try {
            Files.createDirectories(logDir);
            Files.writeString(logFile, initialStateJson, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save initial state log: " + logFile, e);
        }
    }

    private void saveRunMetadata(JavaMomentumConfig config) {
        Path metadataPath = logDirectory(config).resolve("meta.json");
        try {
            Files.createDirectories(metadataPath.getParent());
            Files.writeString(metadataPath, GSON.toJson(new RunMetadata(config)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save run metadata: " + metadataPath, e);
        }
    }

    private void saveGenLogSummary(JavaMomentumConfig config, GenLogSummary summary) {
        Path logDir = logDirectory(config);
        Path summaryFile = logDir.resolve("summary_" + LocalDateTime.now().format(LOG_TIME_FORMAT) + ".json");

        try {
            Files.createDirectories(logDir);
            Files.writeString(summaryFile, GSON.toJson(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save gen log summary: " + summaryFile, e);
        }
    }

    private void saveGenLogStatFiles(JavaMomentumConfig config, GenLogSummary summary) {
        Path logDir = logDirectory(config);
        try {
            Files.createDirectories(logDir);
            Files.writeString(logDir.resolve("collision_stats.json"),
                    GSON.toJson(new CollisionStats(summary)), StandardCharsets.UTF_8);
            Files.writeString(logDir.resolve("shield_stats.json"),
                    GSON.toJson(new ShieldStats(summary)), StandardCharsets.UTF_8);
            Files.writeString(logDir.resolve("distance_stats.json"),
                    GSON.toJson(new DistanceStats(summary)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save gen log statistics in: " + logDir, e);
        }
    }

    private void saveCrashDetails(JavaMomentumConfig config, List<CrashDetail> crashDetails) {
        Path logDir = logDirectory(config);
        try {
            Files.createDirectories(logDir);
            Files.writeString(logDir.resolve("crash_details.json"),
                    GSON.toJson(crashDetails), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save crash details in: " + logDir, e);
        }
    }

    private void applyRecoveredRunMetadata(Path initialStatePath, JavaMomentumConfig config) {
        Path metadataPath = sharedMetadataPathFor(initialStatePath);
        if (!Files.exists(metadataPath)) {
            metadataPath = metadataPathFor(initialStatePath);
        }
        if (!Files.exists(metadataPath)) {
            System.out.println("No recover metadata found for " + initialStatePath
                    + "; using current JavaMomentumConfig values.");
            return;
        }

        try {
            RunMetadata metadata = GSON.fromJson(Files.readString(metadataPath, StandardCharsets.UTF_8),
                    RunMetadata.class);
            if (metadata == null) {
                return;
            }
            metadata.applyTo(config);
            System.out.printf(
                    "Recovered config: egoType=%s aiProfile=%s frequency=%d duration=%d predictionTime=%d maxTargetSpeed=%.2f fixPrediction=%s shieldType=%s futureActions=%s%n",
                    config.getEgoType(),
                    config.getAiProfile(),
                    config.getFrequency(),
                    config.getDuration(),
                    config.getPredictionTime(),
                    config.getMaxTargetSpeed(),
                    config.isFixPrediction(),
                    config.getShieldType(),
                    config.getFutureActions());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read recover metadata: " + metadataPath, e);
        }
    }

    private Path metadataPathFor(Path logFile) {
        return Path.of(logFile.toString() + ".meta.json");
    }

    private Path sharedMetadataPathFor(Path logFile) {
        Path parent = logFile.getParent();
        return parent == null ? Path.of("meta.json") : parent.resolve("meta.json");
    }

    private Path logDirectory(JavaMomentumConfig config) {
        return Path.of(config.getPATH_TO_SAVE())
                .resolve(config.getEgoType().name() + "_" + config.getAiProfile().name() + "_LOGS");
    }

    private static class SimulationRunResult {
        private final String initialStateJson;
        private final boolean crashed;
        private final RuntimeException crashException;
        private final int aiDecisionCount;
        private final int rejectedAiDecisionCount;
        private final double finalEgoX;
        private final List<BeforeCrashActionLog> beforeCrashActions;
        private final CollisionLog collisionLog;

        private SimulationRunResult(String initialStateJson, boolean crashed, RuntimeException crashException,
                                    int aiDecisionCount, int rejectedAiDecisionCount, double finalEgoX,
                                    List<BeforeCrashActionLog> beforeCrashActions, CollisionLog collisionLog) {
            this.initialStateJson = initialStateJson;
            this.crashed = crashed;
            this.crashException = crashException;
            this.aiDecisionCount = aiDecisionCount;
            this.rejectedAiDecisionCount = rejectedAiDecisionCount;
            this.finalEgoX = finalEgoX;
            this.beforeCrashActions = beforeCrashActions;
            this.collisionLog = collisionLog;
        }
    }

    private static class CrashDetail {
        private final int simulationIndex;
        private final String crashMessage;
        private final CollisionLog collision;
        private final double relativeSpeed;
        private final List<BeforeCrashActionLog> beforeCrashActions;

        private CrashDetail(int simulationIndex, SimulationRunResult result) {
            this.simulationIndex = simulationIndex;
            this.crashMessage = result.crashException == null ? null : result.crashException.getMessage();
            this.collision = result.collisionLog;
            this.relativeSpeed = collisionSeverity(result.collisionLog);
            this.beforeCrashActions = result.beforeCrashActions == null ? List.of() : result.beforeCrashActions;
        }

        private static double collisionSeverity(CollisionLog collisionLog) {
            if (collisionLog == null) {
                return Double.NaN;
            }
            double dvx = collisionLog.firstVx() - collisionLog.secondVx();
            double dvy = collisionLog.firstVy() - collisionLog.secondVy();
            return Math.sqrt(dvx * dvx + dvy * dvy);
        }
    }

    private static class GenLogStatistics {
        private final int requestedRuns;
        private final AtomicInteger completedRuns = new AtomicInteger(0);
        private final AtomicInteger crashedRuns = new AtomicInteger(0);
        private final AtomicLong aiDecisionCount = new AtomicLong(0);
        private final AtomicLong rejectedAiDecisionCount = new AtomicLong(0);
        private final DoubleAccumulator minFinalEgoX = new DoubleAccumulator(Double::min, Double.POSITIVE_INFINITY);
        private final DoubleAccumulator maxFinalEgoX = new DoubleAccumulator(Double::max, Double.NEGATIVE_INFINITY);
        private final AtomicLong finalEgoXCount = new AtomicLong(0);
        private final DoubleAccumulator finalEgoXSum = new DoubleAccumulator(Double::sum, 0.0);

        private GenLogStatistics(int requestedRuns) {
            this.requestedRuns = requestedRuns;
        }

        private void add(SimulationRunResult result) {
            completedRuns.incrementAndGet();
            if (result.crashed) {
                crashedRuns.incrementAndGet();
            }
            aiDecisionCount.addAndGet(result.aiDecisionCount);
            rejectedAiDecisionCount.addAndGet(result.rejectedAiDecisionCount);
            if (!Double.isNaN(result.finalEgoX)) {
                finalEgoXCount.incrementAndGet();
                finalEgoXSum.accumulate(result.finalEgoX);
                minFinalEgoX.accumulate(result.finalEgoX);
                maxFinalEgoX.accumulate(result.finalEgoX);
            }
        }

        private GenLogSummary snapshot() {
            long totalDecisions = aiDecisionCount.get();
            long rejectedDecisions = rejectedAiDecisionCount.get();
            long egoXSamples = finalEgoXCount.get();
            return new GenLogSummary(
                    requestedRuns,
                    completedRuns.get(),
                    crashedRuns.get(),
                    totalDecisions,
                    rejectedDecisions,
                    totalDecisions == 0 ? 0.0 : (double) rejectedDecisions / totalDecisions,
                    egoXSamples == 0 ? Double.NaN : finalEgoXSum.get() / egoXSamples,
                    egoXSamples == 0 ? Double.NaN : minFinalEgoX.get(),
                    egoXSamples == 0 ? Double.NaN : maxFinalEgoX.get()
            );
        }

        private void print() {
            GenLogSummary summary = snapshot();
            System.out.printf(
                    "GenLogs summary: runs=%d/%d crashed=%d aiDecisions=%d rejected=%d rejectedPercent=%.2f%% egoFinalX(avg/min/max)=%.2f/%.2f/%.2f%n",
                    summary.completedRuns,
                    summary.requestedRuns,
                    summary.crashedRuns,
                    summary.aiDecisionCount,
                    summary.rejectedAiDecisionCount,
                    summary.rejectedAiDecisionPercent,
                    summary.averageFinalEgoX,
                    summary.minFinalEgoX,
                    summary.maxFinalEgoX);
        }
    }

    private static class GenLogSummary {
        private final int requestedRuns;
        private final int completedRuns;
        private final int crashedRuns;
        private final int safeRuns;
        private final double crashRate;
        private final double crashPercent;
        private final long aiDecisionCount;
        private final long rejectedAiDecisionCount;
        private final double rejectedAiDecisionRate;
        private final double rejectedAiDecisionPercent;
        private final double averageFinalEgoX;
        private final double minFinalEgoX;
        private final double maxFinalEgoX;

        private GenLogSummary(int requestedRuns, int completedRuns, int crashedRuns, long aiDecisionCount,
                              long rejectedAiDecisionCount, double rejectedAiDecisionRate,
                              double averageFinalEgoX, double minFinalEgoX, double maxFinalEgoX) {
            this.requestedRuns = requestedRuns;
            this.completedRuns = completedRuns;
            this.crashedRuns = crashedRuns;
            this.safeRuns = completedRuns - crashedRuns;
            this.crashRate = completedRuns == 0 ? 0.0 : (double) crashedRuns / completedRuns;
            this.crashPercent = crashRate * 100.0;
            this.aiDecisionCount = aiDecisionCount;
            this.rejectedAiDecisionCount = rejectedAiDecisionCount;
            this.rejectedAiDecisionRate = rejectedAiDecisionRate;
            this.rejectedAiDecisionPercent = rejectedAiDecisionRate * 100.0;
            this.averageFinalEgoX = averageFinalEgoX;
            this.minFinalEgoX = minFinalEgoX;
            this.maxFinalEgoX = maxFinalEgoX;
        }
    }

    private static class CollisionStats {
        private final int requestedRuns;
        private final int completedRuns;
        private final int crashedRuns;
        private final int safeRuns;
        private final double crashRate;
        private final double crashPercent;

        private CollisionStats(GenLogSummary summary) {
            this.requestedRuns = summary.requestedRuns;
            this.completedRuns = summary.completedRuns;
            this.crashedRuns = summary.crashedRuns;
            this.safeRuns = summary.safeRuns;
            this.crashRate = summary.crashRate;
            this.crashPercent = summary.crashPercent;
        }
    }

    private static class ShieldStats {
        private final long aiDecisionCount;
        private final long rejectedAiDecisionCount;
        private final long acceptedAiDecisionCount;
        private final double rejectedAiDecisionRate;
        private final double rejectedAiDecisionPercent;
        private final double acceptedAiDecisionRate;
        private final double acceptedAiDecisionPercent;

        private ShieldStats(GenLogSummary summary) {
            this.aiDecisionCount = summary.aiDecisionCount;
            this.rejectedAiDecisionCount = summary.rejectedAiDecisionCount;
            this.acceptedAiDecisionCount = summary.aiDecisionCount - summary.rejectedAiDecisionCount;
            this.rejectedAiDecisionRate = summary.rejectedAiDecisionRate;
            this.rejectedAiDecisionPercent = summary.rejectedAiDecisionPercent;
            this.acceptedAiDecisionRate = summary.aiDecisionCount == 0
                    ? 0.0
                    : (double) acceptedAiDecisionCount / summary.aiDecisionCount;
            this.acceptedAiDecisionPercent = acceptedAiDecisionRate * 100.0;
        }
    }

    private static class DistanceStats {
        private final double averageFinalEgoX;
        private final double minFinalEgoX;
        private final double maxFinalEgoX;

        private DistanceStats(GenLogSummary summary) {
            this.averageFinalEgoX = summary.averageFinalEgoX;
            this.minFinalEgoX = summary.minFinalEgoX;
            this.maxFinalEgoX = summary.maxFinalEgoX;
        }
    }

    private static class RunMetadata {
        private String egoType;
        private String aiProfile;
        private Integer frequency;
        private Integer duration;
        private Integer predictionTime;
        private Double maxTargetSpeed;
        private Boolean fixPrediction;
        private String shieldType;
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
            this.shieldType = config.getShieldType() == null ? null : config.getShieldType().name();
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
            if (shieldType != null && !shieldType.isBlank()) {
                config.setShieldType(ShieldType.valueOf(shieldType));
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
