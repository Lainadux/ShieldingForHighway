package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.ActionAcceptanceLog;
import RefractoredVersion.Engine.BeforeCrashActionLog;
import RefractoredVersion.Engine.CascadedRecoExploreVehicle;
import RefractoredVersion.Engine.CollisionLog;
import RefractoredVersion.Engine.EgoVehicle;
import RefractoredVersion.Engine.ExploreFutureEgo;
import RefractoredVersion.Engine.ExploreFutureSlowerVehicle;
import RefractoredVersion.Engine.GentleNpcHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayAiClient;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.NoShieldEgo;
import RefractoredVersion.Engine.Vehicle;
import RefractoredVersion.Engine.VehicleGenerator;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.RealWorldEngineType;
import RefractoredVersion.TestScript.Config.SandboxNpcPolitenessMode;
import RefractoredVersion.TestScript.Config.ShieldType;
import RefractoredVersion.TestScript.Records.EpisodeAcceptanceSequence;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class SameInitialRunTable3 {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type VEHICLE_LIST_TYPE = new TypeToken<List<Vehicle>>() {}.getType();
    private static final int EPISODES = 100;
    private static final int THREAD_COUNT = 4;

    public static void main(String[] args) {
        Path rootLogDir = Path.of("src/main/java/RefractoredVersion/logs",
                "same_initial_table3_" + System.currentTimeMillis());

        List<ExperimentSetting> settings = List.of(
                new ExperimentSetting("Heuristic shield / adversarial",
                        EgoType.EgoVehicle,
                        ShieldType.HEURISTIC,
                        AIProfile.adversarial),
                new ExperimentSetting("Reco shield / adversarial",
                        EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM,
                        AIProfile.adversarial),
                new ExperimentSetting("ExploreFuture / adversarial",
                        EgoType.ExploreFutureEgo,
                        ShieldType.STARK_NATIVE_RANDOM_IDM,
                        AIProfile.adversarial),
                new ExperimentSetting("Cascade / adversarial",
                        EgoType.CascadedRecoExploreVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM,
                        AIProfile.adversarial),
                new ExperimentSetting("Reco shield / base",
                        EgoType.ExploreFutureSlowerVehicle,
                        ShieldType.STARK_NATIVE_RANDOM_IDM,
                        AIProfile.base),

                new ExperimentSetting("Heuristic shield / base",
                        EgoType.EgoVehicle,
                        ShieldType.HEURISTIC,
                        AIProfile.base),

                new ExperimentSetting("No shield / base",
                        EgoType.NoShieldEgo,
                        ShieldType.ALL_SLOWER,
                        AIProfile.base),
                new ExperimentSetting("No shield / adversarial",
                        EgoType.NoShieldEgo,
                        ShieldType.ALL_SLOWER,
                        AIProfile.adversarial)

        );

        try {
            List<String> initialStates = generateInitialStates(rootLogDir.resolve("initial_states"));
            for (ExperimentSetting setting : settings) {
                System.out.println("========== Running " + setting.name + " ==========");
                JavaMomentumConfig config = configFor(setting, rootLogDir);
                Path groupDir = groupDirectory(rootLogDir, setting);
                ExperimentSummary summary = runGroup(config, initialStates, groupDir);
                System.out.printf(
                        "Finished %s: runs=%d crashed=%d crash=%.2f%% rejected=%.2f%% meanX=%s%n",
                        setting.name,
                        summary.completedRuns,
                        summary.crashedRuns,
                        summary.crashPercent,
                        summary.rejectedAiDecisionPercent,
                        summary.averageFinalEgoX == null ? "NaN" : String.format("%.2f", summary.averageFinalEgoX)
                );
            }
            System.out.println("Same-initial Table 3 logs: " + rootLogDir.toAbsolutePath());
        } finally {
            JavaHighwayAiClient.stopAll();
        }
    }

    private static List<String> generateInitialStates(Path initialStateDir) {
        System.out.println("Generating shared initial states: " + initialStateDir.toAbsolutePath());
        try {
            Files.createDirectories(initialStateDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create initial state directory: " + initialStateDir, e);
        }

        JavaMomentumConfig generationConfig = baseConfig();
        generationConfig.setEgoType(EgoType.EgoVehicle);
        generationConfig.setAiProfile(AIProfile.base);
        generationConfig.setShieldType(ShieldType.HEURISTIC);

        List<String> initialStates = new ArrayList<>();
        for (int i = 0; i < EPISODES; i++) {
            String json = GSON.toJson(VehicleGenerator.generateVehicles(generationConfig));
            initialStates.add(json);
            writeJson(initialStateDir.resolve(String.format("initial_%03d.json", i)), json);
            if ((i + 1) % 20 == 0 || i + 1 == EPISODES) {
                System.out.printf("Generated initial state %d/%d%n", i + 1, EPISODES);
            }
        }
        return initialStates;
    }

    private static ExperimentSummary runGroup(JavaMomentumConfig config, List<String> initialStates, Path groupDir) {
        List<EpisodeAcceptanceSequence> acceptanceSequences = new ArrayList<>();
        List<CrashDetail> crashDetails = new ArrayList<>();
        ExperimentStats stats = new ExperimentStats(initialStates.size());

        writeJson(groupDir.resolve("meta.json"), GSON.toJson(new RunMetadata(config)));

        List<IndexedSimulationResult> results = runSimulationsInParallel(config, initialStates);
        for (IndexedSimulationResult indexedResult : results) {
            int i = indexedResult.simulationIndex;
            SimulationResult result = indexedResult.result;
            stats.add(result);
            acceptanceSequences.add(new EpisodeAcceptanceSequence(
                    i,
                    result.crashed,
                    result.actionAcceptanceSequence
            ));
            if (result.crashed) {
                crashDetails.add(new CrashDetail(i, result));
            }

            String prefix = result.crashed ? "crashed_" : "safe_";
            writeJson(groupDir.resolve(prefix + String.format("%03d.json", i)), initialStates.get(i));
        }

        ExperimentSummary summary = stats.snapshot();
        writeJson(groupDir.resolve("episode_acceptance_sequences.json"), GSON.toJson(acceptanceSequences));
        writeJson(groupDir.resolve("crash_details.json"), GSON.toJson(crashDetails));
        writeJson(groupDir.resolve("collision_stats.json"), GSON.toJson(new CollisionStats(summary)));
        writeJson(groupDir.resolve("shield_stats.json"), GSON.toJson(new ShieldStats(summary)));
        writeJson(groupDir.resolve("distance_stats.json"), GSON.toJson(new DistanceStats(summary)));
        writeJson(groupDir.resolve("summary.json"), GSON.toJson(summary));
        return summary;
    }

    private static List<IndexedSimulationResult> runSimulationsInParallel(JavaMomentumConfig config,
                                                                          List<String> initialStates) {
        int workerCount = Math.max(1, Math.min(THREAD_COUNT, initialStates.size()));
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<IndexedSimulationResult>> futures = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);
        try {
            for (int i = 0; i < initialStates.size(); i++) {
                final int simulationIndex = i;
                futures.add(executor.submit(() -> {
                    SimulationResult result = runSimulation(config, initialStates.get(simulationIndex));
                    int done = completed.incrementAndGet();
                    System.out.printf("Finished simulation %d/%d: %s%n",
                            done,
                            initialStates.size(),
                            result.crashed ? "crashed" : "safe");
                    return new IndexedSimulationResult(simulationIndex, result);
                }));
            }

            List<IndexedSimulationResult> results = new ArrayList<>();
            for (Future<IndexedSimulationResult> future : futures) {
                results.add(future.get());
            }
            results.sort((left, right) -> Integer.compare(left.simulationIndex, right.simulationIndex));
            return results;
        } catch (Exception e) {
            throw new RuntimeException("Failed to run same-initial Table 3 group in parallel.", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private static SimulationResult runSimulation(JavaMomentumConfig config, String initialStateJson) {
        JavaHighwayEngine engine = createRealWorldEngine(config);
        engine.setRenderEnabled(false);
        engine.setFrequency(config.getFrequency());
        engine.config = config;
        engine.vehicles = restoreVehicles(initialStateJson, config);
        for (Vehicle vehicle : engine.vehicles) {
            vehicle.setEngine(engine);
        }

        try {
            for (int i = 0; i < config.getDuration() * config.getFrequency(); i++) {
                engine.step();
            }
            return SimulationResult.from(engine, false, null);
        } catch (RuntimeException e) {
            return SimulationResult.from(engine, true, e);
        } catch (Exception e) {
            throw new RuntimeException("Simulation failed unexpectedly.", e);
        }
    }

    private static JavaHighwayEngine createRealWorldEngine(JavaMomentumConfig config) {
        RealWorldEngineType engineType = config.getRealWorldEngineType() == null
                ? RealWorldEngineType.BRUTAL
                : config.getRealWorldEngineType();
        return switch (engineType) {
            case BRUTAL -> new JavaHighwayEngine();
            case GENTLE -> new GentleNpcHighwayEngine();
        };
    }

    private static List<Vehicle> restoreVehicles(String initialStateJson, JavaMomentumConfig config) {
        List<Vehicle> rawVehicles = GSON.fromJson(initialStateJson, VEHICLE_LIST_TYPE);
        if (rawVehicles == null || rawVehicles.isEmpty()) {
            throw new IllegalArgumentException("Initial state contains no vehicles.");
        }

        List<Vehicle> vehicles = new ArrayList<>();
        for (Vehicle raw : rawVehicles) {
            Vehicle vehicle = "EGO".equals(raw.role) ? createEgoVehicle(config) : new Vehicle();
            copyVehicleState(raw, vehicle);
            vehicles.add(vehicle);
        }
        return vehicles;
    }

    private static EgoVehicle createEgoVehicle(JavaMomentumConfig config) {
        EgoVehicle ego = switch (config.getEgoType()) {
            case EgoVehicle -> new EgoVehicle();
            case ExploreFutureSlowerVehicle -> new ExploreFutureSlowerVehicle();
            case ExploreFutureEgo -> new ExploreFutureEgo();
            case CascadedRecoExploreVehicle -> new CascadedRecoExploreVehicle();
            case NoShieldEgo -> new NoShieldEgo();
            default -> throw new IllegalArgumentException(
                    "SameInitialRunTable3 does not support ego type: " + config.getEgoType());
        };
        ego.aiProfile = config.getAiProfile();
        ego.sensorRange = config.getSensorRange();
        ego.noisySensorOuterRange = config.getNoisySensorOuterRange();
        return ego;
    }

    private static void copyVehicleState(Vehicle source, Vehicle target) {
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
        target.mobil = source.mobil == null ? new HashMap<>() : new HashMap<>(source.mobil);
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

    private static JavaMomentumConfig configFor(ExperimentSetting setting, Path rootLogDir) {
        JavaMomentumConfig config = baseConfig();
        config.setEgoType(setting.egoType);
        config.setShieldType(setting.shieldType);
        config.setAiProfile(setting.aiProfile);
        config.setPATH_TO_SAVE(rootLogDir == null ? "" : rootLogDir.toString());
        return config;
    }

    private static JavaMomentumConfig baseConfig() {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setGenLogs(true);
        config.setFrequency(20);
        config.setDuration(30);
        config.setPredictionTime(1);
        config.setNumsOfSimulations(EPISODES);
        config.setMaxTargetSpeed(40.0);
        config.setFixPrediction(true, 1.0);
        config.setDelayedActionStep(1);
        config.setSensorRange(100);
        config.setNoisySensorOuterRange(100);
        config.setRandomEnableShieldPercent(60);
        config.setRandomizeNpcPoliteness(false);
        config.setSandboxNpcPolitenessMode(SandboxNpcPolitenessMode.COPY_REAL);
        config.setRealWorldEngineType(RealWorldEngineType.BRUTAL);
        config.setMinX(0.0);
        config.setMaxX(400.0);
        return config;
    }

    private static Path groupDirectory(Path rootLogDir, ExperimentSetting setting) {
        String safeName = setting.name
                .replace(" / ", "_")
                .replace(" ", "_")
                .replace("(", "")
                .replace(")", "");
        return rootLogDir.resolve(safeName + "_"
                + setting.egoType.name() + "_"
                + setting.aiProfile.name() + "_"
                + setting.shieldType.name() + "_LOGS");
    }

    private static EgoVehicle getEgoVehicle(JavaHighwayEngine engine) {
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

    private static void writeJson(Path path, String json) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write file: " + path, e);
        }
    }

    private static class ExperimentSetting {
        private final String name;
        private final EgoType egoType;
        private final ShieldType shieldType;
        private final AIProfile aiProfile;

        private ExperimentSetting(String name, EgoType egoType, ShieldType shieldType, AIProfile aiProfile) {
            this.name = name;
            this.egoType = egoType;
            this.shieldType = shieldType;
            this.aiProfile = aiProfile;
        }
    }

    private static class SimulationResult {
        private final boolean crashed;
        private final RuntimeException crashException;
        private final int aiDecisionCount;
        private final int rejectedAiDecisionCount;
        private final double finalEgoX;
        private final List<BeforeCrashActionLog> beforeCrashActions;
        private final CollisionLog collisionLog;
        private final List<ActionAcceptanceLog> actionAcceptanceSequence;

        private SimulationResult(boolean crashed,
                                 RuntimeException crashException,
                                 int aiDecisionCount,
                                 int rejectedAiDecisionCount,
                                 double finalEgoX,
                                 List<BeforeCrashActionLog> beforeCrashActions,
                                 CollisionLog collisionLog,
                                 List<ActionAcceptanceLog> actionAcceptanceSequence) {
            this.crashed = crashed;
            this.crashException = crashException;
            this.aiDecisionCount = aiDecisionCount;
            this.rejectedAiDecisionCount = rejectedAiDecisionCount;
            this.finalEgoX = finalEgoX;
            this.beforeCrashActions = beforeCrashActions == null ? List.of() : List.copyOf(beforeCrashActions);
            this.collisionLog = collisionLog;
            this.actionAcceptanceSequence = actionAcceptanceSequence == null
                    ? List.of()
                    : List.copyOf(actionAcceptanceSequence);
        }

        private static SimulationResult from(JavaHighwayEngine engine, boolean crashed, RuntimeException e) {
            EgoVehicle ego = getEgoVehicle(engine);
            return new SimulationResult(
                    crashed,
                    e,
                    ego == null ? 0 : ego.getAiDecisionCount(),
                    ego == null ? 0 : ego.getRejectedAiDecisionCount(),
                    engine.getEgoFinalX(),
                    ego == null ? List.of() : ego.retrieveBeforeCrashActions(),
                    ego == null ? null : ego.retrieveCrashCollisionLog(),
                    ego == null ? List.of() : ego.retrieveActionAcceptanceSequence()
            );
        }
    }

    private static class IndexedSimulationResult {
        private final int simulationIndex;
        private final SimulationResult result;

        private IndexedSimulationResult(int simulationIndex, SimulationResult result) {
            this.simulationIndex = simulationIndex;
            this.result = result;
        }
    }

    private static class ExperimentStats {
        private final int requestedRuns;
        private int completedRuns;
        private int crashedRuns;
        private long aiDecisionCount;
        private long rejectedAiDecisionCount;
        private int completedTraceCount;
        private double finalEgoXSum;
        private Double minFinalEgoX;
        private Double maxFinalEgoX;

        private ExperimentStats(int requestedRuns) {
            this.requestedRuns = requestedRuns;
        }

        private void add(SimulationResult result) {
            completedRuns++;
            if (result.crashed) {
                crashedRuns++;
            }
            aiDecisionCount += result.aiDecisionCount;
            rejectedAiDecisionCount += result.rejectedAiDecisionCount;
            if (!result.crashed && !Double.isNaN(result.finalEgoX)) {
                completedTraceCount++;
                finalEgoXSum += result.finalEgoX;
                minFinalEgoX = minFinalEgoX == null ? result.finalEgoX : Math.min(minFinalEgoX, result.finalEgoX);
                maxFinalEgoX = maxFinalEgoX == null ? result.finalEgoX : Math.max(maxFinalEgoX, result.finalEgoX);
            }
        }

        private ExperimentSummary snapshot() {
            double rejectedRate = aiDecisionCount == 0 ? 0.0 : (double) rejectedAiDecisionCount / aiDecisionCount;
            return new ExperimentSummary(
                    requestedRuns,
                    completedRuns,
                    crashedRuns,
                    aiDecisionCount,
                    rejectedAiDecisionCount,
                    rejectedRate,
                    completedTraceCount,
                    completedTraceCount == 0 ? null : finalEgoXSum / completedTraceCount,
                    minFinalEgoX,
                    maxFinalEgoX
            );
        }
    }

    private static class ExperimentSummary {
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
        private final int completedTraceCount;
        private final Double averageFinalEgoX;
        private final Double minFinalEgoX;
        private final Double maxFinalEgoX;

        private ExperimentSummary(int requestedRuns,
                                  int completedRuns,
                                  int crashedRuns,
                                  long aiDecisionCount,
                                  long rejectedAiDecisionCount,
                                  double rejectedAiDecisionRate,
                                  int completedTraceCount,
                                  Double averageFinalEgoX,
                                  Double minFinalEgoX,
                                  Double maxFinalEgoX) {
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
            this.completedTraceCount = completedTraceCount;
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

        private CollisionStats(ExperimentSummary summary) {
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

        private ShieldStats(ExperimentSummary summary) {
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
        private final int completedTraceCount;
        private final Double averageFinalEgoX;
        private final Double minFinalEgoX;
        private final Double maxFinalEgoX;

        private DistanceStats(ExperimentSummary summary) {
            this.completedTraceCount = summary.completedTraceCount;
            this.averageFinalEgoX = summary.averageFinalEgoX;
            this.minFinalEgoX = summary.minFinalEgoX;
            this.maxFinalEgoX = summary.maxFinalEgoX;
        }
    }

    private static class CrashDetail {
        private final int simulationIndex;
        private final String crashMessage;
        private final CollisionLog collision;
        private final Double relativeSpeed;
        private final List<BeforeCrashActionLog> beforeCrashActions;

        private CrashDetail(int simulationIndex, SimulationResult result) {
            this.simulationIndex = simulationIndex;
            this.crashMessage = result.crashException == null ? null : result.crashException.getMessage();
            this.collision = result.collisionLog;
            this.relativeSpeed = collisionSeverity(result.collisionLog);
            this.beforeCrashActions = result.beforeCrashActions;
        }

        private static Double collisionSeverity(CollisionLog collisionLog) {
            if (collisionLog == null) {
                return null;
            }
            double dvx = collisionLog.firstVx() - collisionLog.secondVx();
            double dvy = collisionLog.firstVy() - collisionLog.secondVy();
            return Math.sqrt(dvx * dvx + dvy * dvy);
        }
    }

    private static class RunMetadata {
        private final String egoType;
        private final String aiProfile;
        private final int frequency;
        private final int duration;
        private final int predictionTime;
        private final double maxTargetSpeed;
        private final boolean fixPrediction;
        private final double fixedPredictionTargetSpeedDelta;
        private final double aggressiveV3TtcThreshold;
        private final String shieldType;
        private final String realWorldEngineType;
        private final int delayedActionStep;
        private final int sensorRange;
        private final int noisySensorOuterRange;
        private final int randomEnableShieldPercent;
        private final boolean randomizeNpcPoliteness;
        private final String sandboxNpcPolitenessMode;
        private final List<String> futureActions;
        private final double minX;
        private final double maxX;

        private RunMetadata(JavaMomentumConfig config) {
            this.egoType = config.getEgoType().name();
            this.aiProfile = config.getAiProfile().name();
            this.frequency = config.getFrequency();
            this.duration = config.getDuration();
            this.predictionTime = config.getPredictionTime();
            this.maxTargetSpeed = config.getMaxTargetSpeed();
            this.fixPrediction = config.isFixPrediction();
            this.fixedPredictionTargetSpeedDelta = config.getFixedPredictionTargetSpeedDelta();
            this.aggressiveV3TtcThreshold = config.getAggressiveV3TtcThreshold();
            this.shieldType = config.getShieldType().name();
            this.realWorldEngineType = config.getRealWorldEngineType().name();
            this.delayedActionStep = config.getDelayedActionStep();
            this.sensorRange = config.getSensorRange();
            this.noisySensorOuterRange = config.getNoisySensorOuterRange();
            this.randomEnableShieldPercent = config.getRandomEnableShieldPercent();
            this.randomizeNpcPoliteness = config.isRandomizeNpcPoliteness();
            this.sandboxNpcPolitenessMode = config.getSandboxNpcPolitenessMode().name();
            this.futureActions = config.getFutureActions().stream().map(Action::name).toList();
            this.minX = config.getMinX();
            this.maxX = config.getMaxX();
        }
    }
}
