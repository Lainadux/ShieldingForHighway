package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.telemetry.CollisionLog;
import RefractoredVersion.Engine.ego.CounterFactualExploreFutureSlowerVehicle;
import RefractoredVersion.Engine.telemetry.CounterFactualReplayLog;
import RefractoredVersion.Engine.ego.EgoVehicle;
import RefractoredVersion.Engine.ego.ExploreFutureSlowerVehicle;
import RefractoredVersion.Engine.JavaHighwayAiClient;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;
import RefractoredVersion.Engine.vehicle.Vehicle;
import RefractoredVersion.Engine.VehicleGenerator;
import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.SandboxNpcPolitenessMode;
import RefractoredVersion.TestScript.Config.ShieldType;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class CounterFactualReplayExperiment {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type EPISODE_LOG_LIST_TYPE = new TypeToken<List<CounterFactualEpisodeLog>>() {}.getType();
    private static final DateTimeFormatter DIR_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final Mode MODE = Mode.REPLAY;
    private static final ReplayPolicy REPLAY_POLICY = ReplayPolicy.EXECUTE_REJECTED_AI_ACTION;
    private static final int REPLAY_SIMULATION_INDEX = 0;
    //3
    private static final int REPLAY_REJECTION_INDEX =6;
    private static final int REPLAY_SECONDS = 10;
    private static final boolean RENDER_REPLAY = true;

    private static final Path REPLAY_LOG_FILE = Path.of("");

    public static void main(String[] args) throws Exception {
        try {
            if (MODE == Mode.GENERATE) {
                generateCounterFactualLogs();
            } else {
                replayCounterFactualLog();
            }
        } finally {
            JavaHighwayAiClient.stopAll();
        }
    }

    private static void generateCounterFactualLogs() throws Exception {
        JavaMomentumConfig config = baseConfig();
        Path logDir = Path.of(config.getPATH_TO_SAVE())
                .resolve(logDirectoryName(config));

        List<CounterFactualEpisodeLog> episodeLogs = new ArrayList<>();
        int crashedRuns = 0;
        for (int i = 0; i < config.getNumsOfSimulations(); i++) {
            SimulationResult result = runCounterFactualSimulation(config);
            if (result.crashed) {
                crashedRuns++;
            }
            if (!result.counterFactualReplayLogs.isEmpty()) {
                episodeLogs.add(new CounterFactualEpisodeLog(
                        i,
                        result.crashed,
                        result.crashMessage,
                        result.collisionLog,
                        result.counterFactualReplayLogs
                ));
            }
            System.out.printf("Finished counterfactual source simulation %d/%d: %s, rejected states=%d%n",
                    i + 1,
                    config.getNumsOfSimulations(),
                    result.crashed ? "crashed" : "safe",
                    result.counterFactualReplayLogs.size());
        }

        writeJson(logDir.resolve("meta.json"), GSON.toJson(new RunMetadata(config)));
        writeJson(logDir.resolve("counterfactual_replay_logs.json"), GSON.toJson(episodeLogs));
        writeJson(logDir.resolve("counterfactual_summary.json"),
                GSON.toJson(new CounterFactualSummary(
                        config.getNumsOfSimulations(),
                        crashedRuns,
                        episodeLogs.stream().mapToInt(log -> log.counterFactualReplayLogs.size()).sum()
                )));
        System.out.println("Counterfactual logs: " + logDir.toAbsolutePath());
    }

    private static String logDirectoryName(JavaMomentumConfig config) {
        String shieldType = config.getShieldType() == null ? "NO_SHIELD_TYPE" : config.getShieldType().name();
        return config.getEgoType().name() + "_" + config.getAiProfile().name() + "_" + shieldType + "_LOGS";
    }

    private static SimulationResult runCounterFactualSimulation(JavaMomentumConfig config) throws Exception {
        JavaHighwayEngine engine = new JavaHighwayEngine();
        engine.setRenderEnabled(false);
        engine.setFrequency(config.getFrequency());
        engine.config = config;
        engine.vehicles = VehicleGenerator.generateVehicles(config);
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
        }
    }

    private static void replayCounterFactualLog() throws Exception {
        Path logFile = REPLAY_LOG_FILE.toString().isBlank() ? latestCounterFactualLog() : REPLAY_LOG_FILE;
        replay(logFile);
    }

    public static void replay(Path logFile) throws Exception {
        if (logFile == null) {
            throw new IllegalArgumentException("Counterfactual replay log path must not be null.");
        }
        List<CounterFactualEpisodeLog> episodeLogs = GSON.fromJson(
                Files.readString(logFile, StandardCharsets.UTF_8),
                EPISODE_LOG_LIST_TYPE
        );
        CounterFactualEpisodeLog episodeLog = episodeLogs.stream()
                .filter(log -> log.simulationIndex == REPLAY_SIMULATION_INDEX)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No counterfactual episode log for simulationIndex=" + REPLAY_SIMULATION_INDEX));
        if (REPLAY_REJECTION_INDEX < 0 || REPLAY_REJECTION_INDEX >= episodeLog.counterFactualReplayLogs.size()) {
            throw new IllegalArgumentException("REPLAY_REJECTION_INDEX out of range: " + REPLAY_REJECTION_INDEX
                    + ", available=" + episodeLog.counterFactualReplayLogs.size());
        }

        CounterFactualReplayLog selected = episodeLog.counterFactualReplayLogs.get(REPLAY_REJECTION_INDEX);
        JavaMomentumConfig config = baseConfig();
        config.setGenLogs(false);
        config.setEgoType(EgoType.ExploreFutureSlowerVehicle);
        Action actionToExecute = REPLAY_POLICY == ReplayPolicy.EXECUTE_REJECTED_AI_ACTION
                ? selected.counterfactualAction()
                : selected.performedAction();

        JavaHighwayEngine engine = createReplayEngine(config, selected, actionToExecute);
        RuntimeException crash = null;
        try {
            for (int i = 0; i < REPLAY_SECONDS * config.getFrequency(); i++) {
                engine.step();
                if (RENDER_REPLAY) {
                    engine.render();
                }
            }
        } catch (RuntimeException e) {
            crash = e;
        }

        ReplayResult result = ReplayResult.from(logFile, episodeLog.simulationIndex, REPLAY_REJECTION_INDEX,
                REPLAY_POLICY, selected, actionToExecute, engine, crash);
        Path output = logFile.getParent().resolve("counterfactual_replay_result_"
                + REPLAY_POLICY.name().toLowerCase()
                + "_sim" + REPLAY_SIMULATION_INDEX
                + "_idx" + REPLAY_REJECTION_INDEX
                + ".json");
        writeJson(output, GSON.toJson(result));
        System.out.println("Replay result: " + output.toAbsolutePath());
        System.out.printf("Replay %s: action=%s crashed=%s finalEgoX=%.2f%n",
                REPLAY_POLICY,
                actionToExecute,
                result.crashed,
                result.finalEgoX);
    }

    private static JavaHighwayEngine createReplayEngine(JavaMomentumConfig config,
                                                        CounterFactualReplayLog selected,
                                                        Action firstAction) {
        JavaHighwayEngine engine = new JavaHighwayEngine();
        engine.setRenderEnabled(RENDER_REPLAY);
        engine.setFrequency(config.getFrequency());
        engine.config = config;
        engine.stepsTaken = selected.step();
        engine.timeElapsed = selected.time();
        engine.vehicles = restoreReplayVehicles(selected.vehicles(), firstAction, config);
        for (Vehicle vehicle : engine.vehicles) {
            vehicle.setEngine(engine);
        }
        return engine;
    }

    private static List<Vehicle> restoreReplayVehicles(List<Vehicle> snapshot,
                                                       Action firstAction,
                                                       JavaMomentumConfig config) {
        List<Vehicle> vehicles = new ArrayList<>();
        for (Vehicle source : snapshot) {
            Vehicle copy = "EGO".equals(source.role)
                    ? new ScriptedReplayEgoVehicle(firstAction, config.getAiProfile())
                    : new Vehicle();
            copyVehicleState(source, copy);
            if (copy instanceof EgoVehicle egoVehicle) {
                egoVehicle.aiProfile = config.getAiProfile();
                egoVehicle.sensorRange = config.getSensorRange();
                egoVehicle.noisySensorOuterRange = config.getNoisySensorOuterRange();
            }
            vehicles.add(copy);
        }
        return vehicles;
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

    private static JavaMomentumConfig baseConfig() {
        JavaMomentumConfig config = new JavaMomentumConfig();
        config.setGenLogs(true);
        config.setShieldType(ShieldType.STARK_NATIVE_RANDOM_IDM);
        config.setEgoType(EgoType.CounterFactualExploreFutureSlowerVehicle);
        config.setAiProfile(AIProfile.adversarial);
        config.setMinX(0);
        config.setMaxX(400);
        config.setDuration(30);
        config.setFrequency(20);
        config.setNumsOfSimulations(1);
        config.setPATH_TO_SAVE("src/main/java/RefractoredVersion/logs/counterfactual_"
                + LocalDateTime.now().format(DIR_TIME_FORMAT));
        config.setMaxTargetSpeed(40);
        config.setPredictionTime(1);
        config.setFixPrediction(true);
        config.setSensorRange(100);
        config.setNoisySensorOuterRange(100);
        config.setSandboxNpcPolitenessMode(SandboxNpcPolitenessMode.COPY_REAL);
        return config;
    }

    private static Path latestCounterFactualLog() throws IOException {
        Path logsRoot = Path.of("src/main/java/RefractoredVersion/logs");
        Optional<Path> latest = Files.walk(logsRoot)
                .filter(path -> path.getFileName().toString().equals("counterfactual_replay_logs.json"))
                .max(Comparator.comparing(path -> path.toFile().lastModified()));
        return latest.orElseThrow(() -> new IllegalStateException(
                "No counterfactual_replay_logs.json found under " + logsRoot.toAbsolutePath()));
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

    private enum Mode {
        GENERATE,
        REPLAY
    }

    private enum ReplayPolicy {
        EXECUTE_REJECTED_AI_ACTION,
        EXECUTE_OVERRIDE_ACTION
    }

    private static class ScriptedReplayEgoVehicle extends ExploreFutureSlowerVehicle {
        private final Action firstAction;
        private boolean firstActionApplied = false;

        private ScriptedReplayEgoVehicle(Action firstAction, AIProfile aiProfile) {
            this.firstAction = firstAction;
            this.aiProfile = aiProfile;
        }

        @Override
        public void planAction() throws Exception {
            if (!firstActionApplied && this.getEngine().isDecisionTime()) {
                applyAction(firstAction);
                firstActionApplied = true;
                this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(
                        this,
                        this.getEngine().vehicles
                );
                this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
                return;
            }
            super.planAction();
        }
    }

    private static class SimulationResult {
        private final boolean crashed;
        private final String crashMessage;
        private final CollisionLog collisionLog;
        private final List<CounterFactualReplayLog> counterFactualReplayLogs;

        private SimulationResult(boolean crashed,
                                 String crashMessage,
                                 CollisionLog collisionLog,
                                 List<CounterFactualReplayLog> counterFactualReplayLogs) {
            this.crashed = crashed;
            this.crashMessage = crashMessage;
            this.collisionLog = collisionLog;
            this.counterFactualReplayLogs = counterFactualReplayLogs == null
                    ? List.of()
                    : List.copyOf(counterFactualReplayLogs);
        }

        private static SimulationResult from(JavaHighwayEngine engine, boolean crashed, RuntimeException e) {
            EgoVehicle ego = getEgoVehicle(engine);
            List<CounterFactualReplayLog> logs =
                    ego instanceof CounterFactualExploreFutureSlowerVehicle counterFactualVehicle
                            ? counterFactualVehicle.retrieveCounterFactualReplayLogs()
                            : List.of();
            return new SimulationResult(
                    crashed,
                    e == null ? null : e.getMessage(),
                    ego == null ? null : ego.retrieveCrashCollisionLog(),
                    logs
            );
        }
    }

    private static class CounterFactualEpisodeLog {
        private int simulationIndex;
        private boolean crashed;
        private String crashMessage;
        private CollisionLog collisionLog;
        private List<CounterFactualReplayLog> counterFactualReplayLogs;

        private CounterFactualEpisodeLog(int simulationIndex,
                                         boolean crashed,
                                         String crashMessage,
                                         CollisionLog collisionLog,
                                         List<CounterFactualReplayLog> counterFactualReplayLogs) {
            this.simulationIndex = simulationIndex;
            this.crashed = crashed;
            this.crashMessage = crashMessage;
            this.collisionLog = collisionLog;
            this.counterFactualReplayLogs = counterFactualReplayLogs == null
                    ? List.of()
                    : List.copyOf(counterFactualReplayLogs);
        }
    }

    private static class CounterFactualSummary {
        private final int simulations;
        private final int crashedRuns;
        private final int rejectedStates;

        private CounterFactualSummary(int simulations, int crashedRuns, int rejectedStates) {
            this.simulations = simulations;
            this.crashedRuns = crashedRuns;
            this.rejectedStates = rejectedStates;
        }
    }

    private static class ReplayResult {
        private final String sourceLogFile;
        private final int simulationIndex;
        private final int rejectionIndex;
        private final String replayPolicy;
        private final Action actionExecuted;
        private final boolean crashed;
        private final String crashMessage;
        private final CollisionLog collisionLog;
        private final double finalEgoX;
        private final CounterFactualReplayLog sourceRejectedState;

        private ReplayResult(String sourceLogFile,
                             int simulationIndex,
                             int rejectionIndex,
                             ReplayPolicy replayPolicy,
                             Action actionExecuted,
                             boolean crashed,
                             String crashMessage,
                             CollisionLog collisionLog,
                             double finalEgoX,
                             CounterFactualReplayLog sourceRejectedState) {
            this.sourceLogFile = sourceLogFile;
            this.simulationIndex = simulationIndex;
            this.rejectionIndex = rejectionIndex;
            this.replayPolicy = replayPolicy.name();
            this.actionExecuted = actionExecuted;
            this.crashed = crashed;
            this.crashMessage = crashMessage;
            this.collisionLog = collisionLog;
            this.finalEgoX = finalEgoX;
            this.sourceRejectedState = sourceRejectedState;
        }

        private static ReplayResult from(Path sourceLogFile,
                                         int simulationIndex,
                                         int rejectionIndex,
                                         ReplayPolicy replayPolicy,
                                         CounterFactualReplayLog sourceRejectedState,
                                         Action actionExecuted,
                                         JavaHighwayEngine engine,
                                         RuntimeException crash) {
            EgoVehicle ego = getEgoVehicle(engine);
            return new ReplayResult(
                    sourceLogFile.toString(),
                    simulationIndex,
                    rejectionIndex,
                    replayPolicy,
                    actionExecuted,
                    crash != null,
                    crash == null ? null : crash.getMessage(),
                    ego == null ? null : ego.retrieveCrashCollisionLog(),
                    engine.getEgoFinalX(),
                    sourceRejectedState
            );
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
        private final int sensorRange;
        private final int noisySensorOuterRange;
        private final String sandboxNpcPolitenessMode;
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
            this.sensorRange = config.getSensorRange();
            this.noisySensorOuterRange = config.getNoisySensorOuterRange();
            this.sandboxNpcPolitenessMode = config.getSandboxNpcPolitenessMode().name();
            this.minX = config.getMinX();
            this.maxX = config.getMaxX();
        }
    }
}
