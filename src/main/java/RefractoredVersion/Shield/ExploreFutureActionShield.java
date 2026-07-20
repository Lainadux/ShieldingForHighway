package RefractoredVersion.Shield;


import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;
import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.EgoVehicle;
import RefractoredVersion.Engine.NonNpcVehicle;
import RefractoredVersion.Engine.PControlledVehicle;
import RefractoredVersion.Engine.SandboxJavaHighwayEngine;
import RefractoredVersion.Engine.Vehicle;
import it.unicam.quasylab.jspear.DefaultRandomGenerator;
import it.unicam.quasylab.jspear.EvolutionSequence;
import it.unicam.quasylab.jspear.SampleSet;
import it.unicam.quasylab.jspear.SystemState;
import it.unicam.quasylab.jspear.ds.DataState;
import it.unicam.quasylab.jspear.ds.DataStateUpdate;
import it.unicam.quasylab.jspear.distl.AlwaysDisTLFormula;
import it.unicam.quasylab.jspear.distl.ConjunctionDisTLFormula;
import it.unicam.quasylab.jspear.distl.DisTLFormula;
import it.unicam.quasylab.jspear.distl.DoubleSemanticsVisitor;
import it.unicam.quasylab.jspear.distl.TargetDisTLFormula;
import nl.tue.Monitoring.PerceivedSystemState;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// This version of shield take future actions a1a2a3...an into account
// It tells if action sequence AI, a1, a2, a3, ..., an is safe or not

public class ExploreFutureActionShield {
    private static final int DEFAULT_PREDICTION_TIME = 1;
    private static final int EVOLUTION_SEQUENCE_SIZE = 30;
    private static final double CRASH_DISTANCE_THRESHOLD = 0.0;
    private static final double FIRST_SECOND_SAFETY_DISTANCE_THRESHOLD = 0.1;
    private static final double STABILITY_DISTANCE_THRESHOLD = 0.1;
    private static final double MIN_ACCEPTABLE_ROBUSTNESS = 0.0;
    private static final double VEHICLE_LENGTH = 5.0;
    private static final double MIN_STABLE_FRONT_GAP = 15.0;
    private static final double MIN_CLOSE_FRONT_GAP = 10.0;
    private static final double MAX_STABLE_RELATIVE_SPEED = 2.0;
    private static final double SAFE_RECEDING_RELATIVE_SPEED = 2.0;
    private static final double AGGRESSIVE_V3_TTC_THRESHOLD = 4.0;
    private static final double FIRST_SECOND_LOOKAHEAD_TIME = 1.0;
    private static final double FIRST_SECOND_MIN_FRONT_GAP = 2.0;
    private static final double CHANGE_LANE_REAR_THREAT_DISTANCE_THRESHOLD = 0.1;
    private static final double CHANGE_LANE_LOW_SPEED_DISTANCE_THRESHOLD = 0.1;
    private static final double CHANGE_LANE_MIN_SPEED = 5.0;
    private static final double CHANGE_LANE_REAR_REACTION_TIME = 0.1;
    private static final double CHANGE_LANE_REAR_MAX_BRAKE = 3.0;
    private static final double MIN_FRONT_ACCELERATION_UNCERTAINTY = 0.5;
    private static final double FIXED_PREDICTION_RANDOM_TARGET_DELTA = 1.0;

    private final JavaHighwayEngine sourceEngine;
    private final int  predictionTime;
    protected SandboxJavaHighwayEngine sandboxEngine;
    private int predictionVehicleCount = -1;
    private EvolutionSequence sequence;
    private double lastCollisionRobustness = Double.NaN;
    private double lastFirstSecondSafetyRobustness = Double.NaN;
    private double lastStabilityRobustness = Double.NaN;
    private double lastChangeLaneRearThreatRobustness = Double.NaN;
    private double lastChangeLaneLowSpeedRobustness = Double.NaN;
    private double lastShieldRobustness = Double.NaN;

    public final List<Action> futureActions = new ArrayList<>();

    public ExploreFutureActionShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        this.sourceEngine = sourceEngine;
        if (futureActions == null) {
            throw new IllegalArgumentException("ExploreFutureActionShield future actions cannot be null");
        }
        this.predictionTime = futureActions.size() + 1;
        this.futureActions.addAll(futureActions);

    }
/**
 * Constructor for ExploreFutureActionShield class
 * If you construct the shield with this constructor, it will initialize the future actions list with SLOWER actions for the specified prediction time.
 * If you set predictionTime = 1, it is equivalent to the deprecated ALLSLOWER shield, where no future actions are considered
 * (as we show that allslower can be reduced to a single action, the "allsower" is not an appropriate name for it though)
 * @param sourceEngine The JavaHighwayEngine instance that this shield will use
 * @param predictionTime The time period for which predictions will be made
 */
    public ExploreFutureActionShield(JavaHighwayEngine sourceEngine, int predictionTime) {

        // Store the reference to the source engine
        this.sourceEngine = sourceEngine;
        if (predictionTime < 1) {
            throw new IllegalArgumentException("predictionTime must be at least 1");
        }
        // Set the prediction time period
        this.predictionTime = predictionTime;

        // Initialize future actions list with SLOWER actions
        // The loop runs predictionTime - 1 times to fill the list
        for(int i = 0; i < predictionTime - 1; i++){
            futureActions.add(Action.SLOWER);
        }
    }


    private int lastPredictionStep(List<List<List<Vehicle>>> sampleTraces) {
        return Math.max(0, sampleTraces.get(0).size() - 1);
    }
    public boolean verifySafe(Action candidateAction) throws Exception{
        return verifySafeSequence(toActionSequence(candidateAction));
    }

    public boolean verifySafe(Action candidateAction, List<DisTLFormula> moreCriteria) throws Exception{
        return verifySafeSequence(toActionSequence(candidateAction), moreCriteria);
    }

    public EvolutionSequence getPredictionSequence(Action candidateAction) throws Exception {
        sequence = buildPredictionSequence(toActionSequence(candidateAction));
        return sequence;
    }

    public String getUnsafeDiagnosis() {
        if (sequence == null) {
            return "Shield diagnosis unavailable: verifySafe() has not been called.";
        }
        int lastStep = Math.max(0, sequence.length() - 1);
        return sequence.get(lastStep).stream()
                .findFirst()
                .map(systemState -> diagnoseState(systemState.getDataState(), lastStep))
                .orElse("Shield diagnosis unavailable: no prediction state available.");
    }

    private List<Action> toActionSequence(Action candidateAction) {
        List<Action> actionSequence = new ArrayList<>();
        actionSequence.add(candidateAction);
        actionSequence.addAll(futureActions);
        return actionSequence;
    }

    private boolean verifySafeSequence(List<Action> actionSequence) throws Exception {

        List<List<List<Vehicle>>> sampleTraces = predictCandidateSampleTraces(actionSequence);
        sequence = new FixedEvolutionSequence(toSampleSetsFromSampleTraces(sampleTraces, actionSequence.get(0)));

        int lastStep = lastPredictionStep(sampleTraces);
        int firstSecondLastStep = Math.min(sourceEngine.getFrequency() - 1, lastStep);
        DisTLFormula noCollision = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::resetCrashState, this::crashPenalty, CRASH_DISTANCE_THRESHOLD),
                0,
                lastStep
        );
        DisTLFormula safeFrontDistanceAtFirstSecond = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeEgoAtFrontSafetyDistance, this::firstSecondFrontSafetyPenalty,
                        FIRST_SECOND_SAFETY_DISTANCE_THRESHOLD),
                firstSecondLastStep,
                firstSecondLastStep
        );
        DisTLFormula stableAtLastStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeEgoAgainstFrontVehicle, this::frontVehicleStabilityPenalty,
                        STABILITY_DISTANCE_THRESHOLD),
                lastStep,
                lastStep
        );
        DisTLFormula changeLaneRearThreatAtDecisionStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeChangeLaneRearThreat, this::changeLaneRearThreatPenalty,
                        CHANGE_LANE_REAR_THREAT_DISTANCE_THRESHOLD),
                0,
                0
        );
        DisTLFormula changeLaneLowSpeedAtDecisionStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeChangeLaneLowSpeed, this::changeLaneLowSpeedPenalty,
                        CHANGE_LANE_LOW_SPEED_DISTANCE_THRESHOLD),
                0,
                0
        );
        DisTLFormula shieldCondition = new ConjunctionDisTLFormula(
                noCollision,
                new ConjunctionDisTLFormula(safeFrontDistanceAtFirstSecond, stableAtLastStep)
        );
        DisTLFormula rearThreatCondition = new ConjunctionDisTLFormula(
                changeLaneRearThreatAtDecisionStep,
                changeLaneLowSpeedAtDecisionStep
        );
        shieldCondition = new ConjunctionDisTLFormula(shieldCondition, rearThreatCondition);

        DoubleSemanticsVisitor semantics = new DoubleSemanticsVisitor();
        lastCollisionRobustness = semantics.eval(noCollision).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastFirstSecondSafetyRobustness = semantics.eval(safeFrontDistanceAtFirstSecond)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastStabilityRobustness = semantics.eval(stableAtLastStep).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastChangeLaneRearThreatRobustness = semantics.eval(changeLaneRearThreatAtDecisionStep)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastChangeLaneLowSpeedRobustness = semantics.eval(changeLaneLowSpeedAtDecisionStep)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastShieldRobustness = semantics.eval(shieldCondition).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);

        return lastShieldRobustness >= MIN_ACCEPTABLE_ROBUSTNESS;

    }
    private boolean verifySafeSequence(List<Action> actionSequence, List<DisTLFormula> moreCriteria) throws Exception {

        List<List<List<Vehicle>>> sampleTraces = predictCandidateSampleTraces(actionSequence);
        sequence = new FixedEvolutionSequence(toSampleSetsFromSampleTraces(sampleTraces, actionSequence.get(0)));

        int lastStep = lastPredictionStep(sampleTraces);
        int firstSecondLastStep = Math.min(sourceEngine.getFrequency() - 1, lastStep);
        DisTLFormula noCollision = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::resetCrashState, this::crashPenalty, CRASH_DISTANCE_THRESHOLD),
                0,
                lastStep
        );
        DisTLFormula safeFrontDistanceAtFirstSecond = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeEgoAtFrontSafetyDistance, this::firstSecondFrontSafetyPenalty,
                        FIRST_SECOND_SAFETY_DISTANCE_THRESHOLD),
                firstSecondLastStep,
                firstSecondLastStep
        );
        DisTLFormula stableAtLastStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeEgoAgainstFrontVehicle, this::frontVehicleStabilityPenalty,
                        STABILITY_DISTANCE_THRESHOLD),
                lastStep,
                lastStep
        );
        DisTLFormula changeLaneRearThreatAtDecisionStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeChangeLaneRearThreat, this::changeLaneRearThreatPenalty,
                        CHANGE_LANE_REAR_THREAT_DISTANCE_THRESHOLD),
                0,
                0
        );
        DisTLFormula changeLaneLowSpeedAtDecisionStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeChangeLaneLowSpeed, this::changeLaneLowSpeedPenalty,
                        CHANGE_LANE_LOW_SPEED_DISTANCE_THRESHOLD),
                0,
                0
        );
        DisTLFormula shieldCondition = new ConjunctionDisTLFormula(
                noCollision,
                new ConjunctionDisTLFormula(safeFrontDistanceAtFirstSecond, stableAtLastStep)
        );
        DisTLFormula rearThreatCondition = new ConjunctionDisTLFormula(
                changeLaneRearThreatAtDecisionStep,
                changeLaneLowSpeedAtDecisionStep
        );
        shieldCondition = new ConjunctionDisTLFormula(shieldCondition, rearThreatCondition);

        DoubleSemanticsVisitor semantics = new DoubleSemanticsVisitor();
        lastCollisionRobustness = semantics.eval(noCollision).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastFirstSecondSafetyRobustness = semantics.eval(safeFrontDistanceAtFirstSecond)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastStabilityRobustness = semantics.eval(stableAtLastStep).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastChangeLaneRearThreatRobustness = semantics.eval(changeLaneRearThreatAtDecisionStep)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastChangeLaneLowSpeedRobustness = semantics.eval(changeLaneLowSpeedAtDecisionStep)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);


        if (moreCriteria != null) {
            for (DisTLFormula criteria : moreCriteria) {
                shieldCondition = new ConjunctionDisTLFormula(shieldCondition, criteria);
            }
        }
        lastShieldRobustness = semantics.eval(shieldCondition).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        return lastShieldRobustness >= MIN_ACCEPTABLE_ROBUSTNESS;

    }
    public DisTLFormula evaluateCutIn(){
        int lastStep = Math.max(0, predictionTime * sourceEngine.getFrequency() - 1);
        return new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeCutInRearThreat, this::cutInRearThreatPenalty,
                        CHANGE_LANE_REAR_THREAT_DISTANCE_THRESHOLD),
                0,
                lastStep
        );
    }

    private EvolutionSequence buildPredictionSequence(List<Action> actionSequence) throws Exception {
        return new FixedEvolutionSequence(
                toSampleSetsFromSampleTraces(predictCandidateSampleTraces(actionSequence), actionSequence.get(0)));
    }

    private List<List<List<Vehicle>>> predictCandidateSampleTraces(List<Action> actionSequence) throws Exception {
        List<List<List<Vehicle>>> sampleTraces = new ArrayList<>();
        for (int sample = 0; sample < EVOLUTION_SEQUENCE_SIZE; sample++) {
            sampleTraces.add(predictCandidateTrace(actionSequence));
        }
        return sampleTraces;
    }
    public List<List<Vehicle>> predictCandidateTrace(List<Action> actionSequence) throws Exception {
        sandboxEngine = createSandboxEngine(actionSequence);
        int predictionSteps = Math.max(1, actionSequence.size() * sandboxEngine.getFrequency());
        List<List<Vehicle>> trace = new ArrayList<>();
        trace.add(deepCopyVehicles(sandboxEngine.vehicles, actionSequence));
        for (int i = 1; i < predictionSteps; i++) {
            planSandboxActions();
            sandboxEngine.applyPhysics();
            sandboxEngine.checkCollision();
            trace.add(deepCopyVehicles(sandboxEngine.vehicles, actionSequence));
            sandboxEngine.timeElapsed += sandboxEngine.getDt();
            sandboxEngine.stepsTaken += 1;
        }
        return trace;
    }
    protected void planSandboxActions() throws Exception {
        for (Vehicle vehicle : sandboxEngine.vehicles) {
            if (vehicle instanceof NonNpcVehicle) {
                ((NonNpcVehicle) vehicle).planAction();
            } else {
                vehicle.planAction(sandboxEngine.vehicles);
            }
        }
    }
    protected SandboxJavaHighwayEngine createSandboxEngine(List<Action> actionSequence) {
        SandboxJavaHighwayEngine sandbox = new SandboxJavaHighwayEngine();
        sandbox.setFrequency(sourceEngine.getFrequency());
        sandbox.config = sourceEngine.config;
        sandbox.numLanes = sourceEngine.numLanes;
        sandbox.timeElapsed = 0.0;
        sandbox.stepsTaken = 0;
        sandbox.vehicles = deepCopyVehicles(detectedVehiclesForSandbox(), actionSequence);
        predictionVehicleCount = sandbox.vehicles.size();
        for (Vehicle vehicle : sandbox.vehicles) {
            vehicle.setEngine(sandbox);
        }
        return sandbox;
    }

    private List<Vehicle> detectedVehiclesForSandbox() {
        for (Vehicle vehicle : sourceEngine.vehicles) {
            if (vehicle instanceof EgoVehicle egoVehicle) {
                return new ArrayList<>(egoVehicle.getDetectedVehicles());
            }
        }
        throw new IllegalStateException("Cannot create sandbox engine without an EgoVehicle");
    }

    private List<Vehicle> deepCopyVehicles(List<Vehicle> vehicles) {
        return deepCopyVehicles(vehicles, futureActions);
    }

    protected List<Vehicle> deepCopyVehicles(List<Vehicle> vehicles, List<Action> actionSequence) {
        List<Vehicle> copies = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            Vehicle copy = vehicle instanceof NonNpcVehicle
                    ? new ShieldNonNpcVehicle(actionSequence)
                    : new ShieldNpcVehicle();
            copyVehicleState(vehicle, copy);
            copies.add(copy);
        }
        return copies;
    }
    public static class ShieldNonNpcVehicle extends Vehicle implements NonNpcVehicle, PControlledVehicle {
        private final List<Action> futureActions;

        ShieldNonNpcVehicle(List<Action> futureActions) {
            this.futureActions = List.copyOf(futureActions);
        }
        @Override
        public void planAction() throws Exception {
            if(this.getEngine().isDecisionTime()){
                int actionIndex = this.getEngine().stepsTaken / this.getEngine().getFrequency();
                if (actionIndex >= futureActions.size()) {
                    throw new IllegalStateException(String.format(
                            "Missing future action at decision index %d, futureActions size is %d",
                            actionIndex,
                            futureActions.size()
                    ));
                }
                applyAction(futureActions.get(actionIndex));
            }
            this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, this.getEngine().vehicles);
            this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);

        }
    }

    protected static class ShieldNpcVehicle extends Vehicle {
        @Override
        public void planAction(List<Vehicle> allVehicles) throws Exception {
            this.setTargetLaneIndex(JavaHighwayEngineUtils.sandboxComputeTargetLane(
                    this,
                    allVehicles,
                    List.of(0, 1),
                    this.getEngine()
            ));
            this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, allVehicles);
            this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
        }
    }

    protected void copyVehicleState(Vehicle source, Vehicle copy) {
        copy.TAU_ACC = source.TAU_ACC;
        copy.TAU_HEADING = source.TAU_HEADING;
        copy.TAU_LATERAL = source.TAU_LATERAL;
        copy.TAU_PURSUIT = source.TAU_PURSUIT;
        copy.KP_A = source.KP_A;
        copy.KP_HEADING = source.KP_HEADING;
        copy.KP_LATERAL = source.KP_LATERAL;
        copy.MAX_STEERING_ANGLE = source.MAX_STEERING_ANGLE;
        copy.DELTA_SPEED = source.DELTA_SPEED;
        copy.possible_lanes = source.possible_lanes.clone();
        copy.karma_a_new = source.karma_a_new;
        copy.mobil = source.mobil;
        copy.targetSpeed = source.targetSpeed;
        copy.id = source.id;
        copy.politeness = source.politeness;
        copy.cooldownTimer = Math.random();
        copy.setTargetLaneIndex(source.getTargetLaneIndex());
        copy.setLaneIndex(source.getLaneIndex());
        copy.role = source.role;
        copy.x = source.x;
        copy.y = source.y;
        copy.vx = source.vx;
        copy.vy = source.vy;
        copy.speed = source.speed;
        copy.previousSecondSpeed = source.previousSecondSpeed;
        copy.heading = source.heading;
        copy.plannedAcceleration = source.plannedAcceleration;
        copy.plannedSteering = source.plannedSteering;
        if (isFixPredictionEnabled() && !(copy instanceof NonNpcVehicle)) {
            copy.targetSpeed = getRandomFixedPredictionTargetSpeed(copy);
        }

    }
    private boolean isFixPredictionEnabled() {
        return sourceEngine.config != null && sourceEngine.config.isFixPrediction();
    }
    private double getRandomFixedPredictionTargetSpeed(Vehicle vehicle) {
        double minTargetSpeed = Math.max(0.0, vehicle.speed - FIXED_PREDICTION_RANDOM_TARGET_DELTA);
        double maxTargetSpeed = vehicle.speed + FIXED_PREDICTION_RANDOM_TARGET_DELTA;
        return minTargetSpeed + Math.random() * (maxTargetSpeed - minTargetSpeed);
    }
    private List<SampleSet<SystemState>> toSampleSetsFromSampleTraces(List<List<List<Vehicle>>> sampleTraces,
                                                                      Action initialIntention) {
        if (sampleTraces == null || sampleTraces.isEmpty() || sampleTraces.get(0).isEmpty()) {
            throw new IllegalArgumentException("Sample traces must contain at least one sample and one state");
        }

        int stepCount = sampleTraces.get(0).size();
        List<List<boolean[]>> historicalFlagsBySample = new ArrayList<>();
        for (List<List<Vehicle>> sampleTrace : sampleTraces) {
            historicalFlagsBySample.add(historicalCutInIntentByStep(sampleTrace));
        }
        List<SampleSet<SystemState>> sampleSets = new ArrayList<>();
        for (int step = 0; step < stepCount; step++) {
            List<SystemState> samplesAtStep = new ArrayList<>();
            for (int sampleIndex = 0; sampleIndex < sampleTraces.size(); sampleIndex++) {
                List<List<Vehicle>> sampleTrace = sampleTraces.get(sampleIndex);
                if (sampleTrace.size() != stepCount) {
                    throw new IllegalArgumentException("All sample traces must have the same length");
                }
                samplesAtStep.add(new PerceivedSystemState(toDataState(
                        sampleTrace.get(step),
                        historicalFlagsBySample.get(sampleIndex).get(step),
                        initialIntention)));
            }
            sampleSets.add(new SampleSet<>(samplesAtStep));
        }
        return sampleSets;
    }
    private DataState toDataState(List<Vehicle> vehiclesAtStep) {
        return toDataState(vehiclesAtStep, new boolean[vehicleCount()], null);
    }

    private DataState toDataState(List<Vehicle> vehiclesAtStep, boolean[] historicalCutInIntent,
                                  Action initialIntention) {
        Map<Integer, Double> values = new HashMap<>();
        int vehicleCount = vehicleCount();

        for (int i = 0; i < vehicleCount; i++) {
            Vehicle vehicle = i < vehiclesAtStep.size() ? vehiclesAtStep.get(i) : sourceEngine.vehicles.get(i);
            int offset = vehicleOffset(i);
            values.put(offset + VarTable.id.ordinal(), parseVehicleId(vehicle.id, i));
            values.put(offset + VarTable.politeness.ordinal(), vehicle.politeness);
            values.put(offset + VarTable.cooldownTimer.ordinal(), vehicle.cooldownTimer);
            values.put(offset + VarTable.target_lane_index.ordinal(), (double) vehicle.getTargetLaneIndex());
            values.put(offset + VarTable.lane_index.ordinal(), (double) vehicle.getLaneIndex());
            values.put(offset + VarTable.x.ordinal(), vehicle.x);
            values.put(offset + VarTable.y.ordinal(), vehicle.y);
            values.put(offset + VarTable.vx.ordinal(), vehicle.vx);
            values.put(offset + VarTable.vy.ordinal(), vehicle.vy);
            values.put(offset + VarTable.speed.ordinal(), vehicle.speed);
            values.put(offset + VarTable.heading.ordinal(), vehicle.heading);
            values.put(offset + VarTable.plannedAcceleration.ordinal(), vehicle.plannedAcceleration);
            values.put(offset + VarTable.plannedSteering.ordinal(), vehicle.plannedSteering);
            values.put(offset + VarTable.role.ordinal(), "EGO".equals(vehicle.role) ? 0.0 : 1.0);
            values.put(offset + VarTable.targetSpeed.ordinal(), vehicle.targetSpeed);
            values.put(offset + VarTable.idmCooldownTimer.ordinal(), 0.0);
            values.put(offset + VarTable.idmActionStepLength.ordinal(), 0.0);
            values.put(offset + VarTable.reactionDelay.ordinal(), 0.0);
            values.put(offset + VarTable.historicalCutInIntent.ordinal(),
                    historicalCutInIntent != null
                            && i < historicalCutInIntent.length
                            && historicalCutInIntent[i] ? 1.0 : 0.0);
        }

        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.crashed),
                hasCollision(vehiclesAtStep) ? 1.0 : 0.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInCurrentLane), -1.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInLeftLane), -1.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInRightLane), -1.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.isInitialChangeLane),
                isCandidateChangeLane(vehiclesAtStep, initialIntention) ? 1.0 : 0.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.initialIntention),
                initialIntention == null ? -1.0 : initialIntention.getValue());
        populateRearThreatAuxiliaryValues(values, vehiclesAtStep);

        return new DataState(AuxiliarySingletonVarTable.stateSize(vehicleCount()),
                index -> values.getOrDefault(index, Double.NaN));
    }

    private int auxiliaryIndex(AuxiliarySingletonVarTable variable) {
        return AuxiliarySingletonVarTable.index(vehicleCount(), variable);
    }

    private int vehicleCount() {
        if (predictionVehicleCount <= 0) {
            throw new IllegalStateException("Prediction vehicle count is not initialized");
        }
        return predictionVehicleCount;
    }
    private boolean hasCollision(List<Vehicle> vehiclesAtStep) {
        for (int i = 0; i < vehiclesAtStep.size(); i++) {
            for (int j = i + 1; j < vehiclesAtStep.size(); j++) {
                Vehicle first = vehiclesAtStep.get(i);
                Vehicle second = vehiclesAtStep.get(j);
                if (!(first instanceof NonNpcVehicle || second instanceof NonNpcVehicle)) {
                    continue;
                }
                boolean overlapX = Math.abs(first.x - second.x) < (first.LENGTH / 2.0 + second.LENGTH / 2.0);
                boolean overlapY = Math.abs(first.y - second.y) < (first.WIDTH / 2.0 + second.WIDTH / 2.0);
                if (overlapX && overlapY) {
                    return true;
                }
            }
        }
        return false;
    }
    private int vehicleOffset(int vehicleIndex) {
        return vehicleIndex * VarTable.values().length;
    }
    private double parseVehicleId(String id, int fallback) {
        try {
            return Double.parseDouble(id);
        } catch (Exception ignored) {
            return fallback;
        }
    }
    private DataState resetCrashState(RandomGenerator rg, DataState state) {
        return state.apply(List.of(new DataStateUpdate(auxiliaryIndex(AuxiliarySingletonVarTable.crashed), 0.0)));
    }

    private DataState stabilizeEgoAtFrontSafetyDistance(RandomGenerator rg, DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            return state;
        }

        int egoOffset = vehicleOffset(egoIndex);
        int egoLane = (int) state.get(egoOffset + VarTable.lane_index.ordinal());
        int frontIndex = getFrontVehicleIndexInLane(state, egoIndex, egoLane);
        if (frontIndex < 0) {
            return state;
        }

        int frontOffset = vehicleOffset(frontIndex);
        double safetyGap = calculateOneSecondWorstCaseSafetyGap(
                state.get(egoOffset + VarTable.vx.ordinal()),
                state.get(frontOffset + VarTable.vx.ordinal()),
                state.get(egoOffset + VarTable.plannedAcceleration.ordinal()),
                state.get(frontOffset + VarTable.plannedAcceleration.ordinal())
        );
        return state.apply(List.of(new DataStateUpdate(
                egoOffset + VarTable.x.ordinal(),
                state.get(frontOffset + VarTable.x.ordinal()) - VEHICLE_LENGTH - safetyGap
        )));
    }

    private DataState stabilizeEgoAgainstFrontVehicle(RandomGenerator rg, DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            return state;
        }

        List<Integer> referenceVehicles = getFinalStabilityReferenceVehicles(state, egoIndex);
        if (referenceVehicles.isEmpty()) {
            return state;
        }

        int egoOffset = vehicleOffset(egoIndex);
        double targetX = Double.POSITIVE_INFINITY;
        double targetVx = Double.POSITIVE_INFINITY;
        double targetSpeed = Double.POSITIVE_INFINITY;
        for (int vehicleIndex : referenceVehicles) {
            int offset = vehicleOffset(vehicleIndex);
            targetX = Math.min(targetX, state.get(offset + VarTable.x.ordinal()) - VEHICLE_LENGTH - MIN_STABLE_FRONT_GAP);
            targetVx = Math.min(targetVx, state.get(offset + VarTable.vx.ordinal()));
            targetSpeed = Math.min(targetSpeed, state.get(offset + VarTable.speed.ordinal()));
        }

        return state.apply(List.of(
                new DataStateUpdate(egoOffset + VarTable.x.ordinal(), targetX),
                new DataStateUpdate(egoOffset + VarTable.vx.ordinal(), targetVx),
                new DataStateUpdate(egoOffset + VarTable.speed.ordinal(), targetSpeed),
                new DataStateUpdate(egoOffset + VarTable.targetSpeed.ordinal(), targetSpeed)
        ));
    }

    private DataState stabilizeChangeLaneLowSpeed(RandomGenerator rg, DataState state) {
        if (state.get(auxiliaryIndex(AuxiliarySingletonVarTable.isInitialChangeLane)) <= 0.0) {
            return state;
        }
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            return state;
        }
        int egoOffset = vehicleOffset(egoIndex);
        return state.apply(List.of(
                new DataStateUpdate(egoOffset + VarTable.vx.ordinal(),
                        Math.max(state.get(egoOffset + VarTable.vx.ordinal()), CHANGE_LANE_MIN_SPEED)),
                new DataStateUpdate(egoOffset + VarTable.speed.ordinal(),
                        Math.max(state.get(egoOffset + VarTable.speed.ordinal()), CHANGE_LANE_MIN_SPEED)),
                new DataStateUpdate(egoOffset + VarTable.targetSpeed.ordinal(),
                        Math.max(state.get(egoOffset + VarTable.targetSpeed.ordinal()), CHANGE_LANE_MIN_SPEED))
        ));
    }

    private DataState stabilizeChangeLaneRearThreat(RandomGenerator rg, DataState state) {
        if (state.get(auxiliaryIndex(AuxiliarySingletonVarTable.isInitialChangeLane)) <= 0.0) {
            return state;
        }
        int egoIndex = getEgoVehicleIndex(state);
        int rearIndex = getConfiguredRearThreatRearVehicleIndex(state);
        if (egoIndex < 0 || rearIndex < 0) {
            return state;
        }

        int egoOffset = vehicleOffset(egoIndex);
        int rearOffset = vehicleOffset(rearIndex);
        int rearLane = (int) state.get(rearOffset + VarTable.lane_index.ordinal());
        double rearX = state.get(rearOffset + VarTable.x.ordinal());
        double rearVx = state.get(rearOffset + VarTable.vx.ordinal());
        double egoVx = state.get(egoOffset + VarTable.vx.ordinal());
        double desiredEgoX = rearX + VEHICLE_LENGTH + requiredGapForRearDelayedBraking(rearVx, egoVx);

        List<DataStateUpdate> updates = new ArrayList<>();
        updates.add(new DataStateUpdate(egoOffset + VarTable.x.ordinal(), desiredEgoX));
        updates.add(new DataStateUpdate(egoOffset + VarTable.lane_index.ordinal(), rearLane));
        updates.add(new DataStateUpdate(egoOffset + VarTable.target_lane_index.ordinal(), rearLane));

        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex || i == rearIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            updates.add(new DataStateUpdate(offset + VarTable.lane_index.ordinal(), -999.0));
            updates.add(new DataStateUpdate(offset + VarTable.target_lane_index.ordinal(), -999.0));
        }
        return state.apply(updates);
    }

    private DataState stabilizeCutInRearThreat(RandomGenerator rg, DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0 || !isEgoChangingLane(state, egoIndex)) {
            return state;
        }
        int egoOffset = vehicleOffset(egoIndex);
        int targetLane = (int) state.get(egoOffset + VarTable.target_lane_index.ordinal());
        int rearIndex = getRearVehicleIndexInLane(state, egoIndex, targetLane);
        if (rearIndex < 0) {
            return state;
        }

        int rearOffset = vehicleOffset(rearIndex);
        int rearLane = (int) state.get(rearOffset + VarTable.lane_index.ordinal());
        double rearX = state.get(rearOffset + VarTable.x.ordinal());
        double rearVx = state.get(rearOffset + VarTable.vx.ordinal());
        double egoVx = state.get(egoOffset + VarTable.vx.ordinal());
        double desiredEgoX = rearX + VEHICLE_LENGTH + requiredGapForRearDelayedBraking(rearVx, egoVx);

        List<DataStateUpdate> updates = new ArrayList<>();
        updates.add(new DataStateUpdate(egoOffset + VarTable.x.ordinal(), desiredEgoX));
        updates.add(new DataStateUpdate(egoOffset + VarTable.lane_index.ordinal(), rearLane));
        updates.add(new DataStateUpdate(egoOffset + VarTable.target_lane_index.ordinal(), rearLane));

        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex || i == rearIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            updates.add(new DataStateUpdate(offset + VarTable.lane_index.ordinal(), -999.0));
            updates.add(new DataStateUpdate(offset + VarTable.target_lane_index.ordinal(), -999.0));
        }
        return state.apply(updates);
    }

    private double crashPenalty(DataState state) {
        return state.get(auxiliaryIndex(AuxiliarySingletonVarTable.crashed)) > 0.0 ? 1.0 : 0.0;
    }

    private double firstSecondFrontSafetyPenalty(DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            return 0.0;
        }

        int egoOffset = vehicleOffset(egoIndex);
        int egoLane = (int) state.get(egoOffset + VarTable.lane_index.ordinal());
        int frontIndex = getFrontVehicleIndexInLane(state, egoIndex, egoLane);
        if (frontIndex < 0) {
            return 0.0;
        }

        int frontOffset = vehicleOffset(frontIndex);
        double frontGap = state.get(frontOffset + VarTable.x.ordinal())
                - state.get(egoOffset + VarTable.x.ordinal())
                - VEHICLE_LENGTH;
        double safetyGap = calculateOneSecondWorstCaseSafetyGap(
                state.get(egoOffset + VarTable.vx.ordinal()),
                state.get(frontOffset + VarTable.vx.ordinal()),
                state.get(egoOffset + VarTable.plannedAcceleration.ordinal()),
                state.get(frontOffset + VarTable.plannedAcceleration.ordinal())
        );
        if (safetyGap <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, safetyGap - frontGap) / safetyGap);
    }

    private double changeLaneLowSpeedPenalty(DataState state) {
        if (state.get(auxiliaryIndex(AuxiliarySingletonVarTable.isInitialChangeLane)) <= 0.0) {
            return 0.0;
        }
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            return 0.0;
        }
        double egoSpeed = state.get(vehicleOffset(egoIndex) + VarTable.speed.ordinal());
        if (egoSpeed >= CHANGE_LANE_MIN_SPEED) {
            return 0.0;
        }
        return Math.min(1.0, (CHANGE_LANE_MIN_SPEED - egoSpeed) / CHANGE_LANE_MIN_SPEED);
    }

    private double changeLaneRearThreatPenalty(DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        int rearIndex = getConfiguredRearThreatRearVehicleIndex(state);
        if (egoIndex < 0 || rearIndex < 0) {
            return 0.0;
        }

        int rearOffset = vehicleOffset(rearIndex);
        int egoOffset = vehicleOffset(egoIndex);
        double rearGap = state.get(egoOffset + VarTable.x.ordinal())
                - state.get(rearOffset + VarTable.x.ordinal())
                - VEHICLE_LENGTH;
        double requiredGap = requiredGapForRearDelayedBraking(
                state.get(rearOffset + VarTable.vx.ordinal()),
                state.get(egoOffset + VarTable.vx.ordinal())
        );
        if (rearGap >= requiredGap) {
            return 0.0;
        }
        if (requiredGap <= 0.0) {
            return rearGap < 0.0 ? 1.0 : 0.0;
        }
        return Math.min(1.0, (requiredGap - rearGap) / requiredGap);
    }

    private double cutInRearThreatPenalty(DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0 || !isEgoChangingLane(state, egoIndex)) {
            return 0.0;
        }
        int egoOffset = vehicleOffset(egoIndex);
        int targetLane = (int) state.get(egoOffset + VarTable.target_lane_index.ordinal());
        int rearIndex = getRearVehicleIndexInLane(state, egoIndex, targetLane);
        if (rearIndex < 0) {
            return 0.0;
        }

        int rearOffset = vehicleOffset(rearIndex);
        double rearGap = state.get(egoOffset + VarTable.x.ordinal())
                - state.get(rearOffset + VarTable.x.ordinal())
                - VEHICLE_LENGTH;
        double requiredGap = requiredGapForRearDelayedBraking(
                state.get(rearOffset + VarTable.vx.ordinal()),
                state.get(egoOffset + VarTable.vx.ordinal())
        );
        if (rearGap >= requiredGap) {
            return 0.0;
        }
        if (requiredGap <= 0.0) {
            return rearGap < 0.0 ? 1.0 : 0.0;
        }
        return Math.min(1.0, (requiredGap - rearGap) / requiredGap);
    }

    private double frontVehicleStabilityPenalty(DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            return 0.0;
        }

        double totalPenalty = 0.0;
        for (int frontIndex : getFinalStabilityReferenceVehicles(state, egoIndex)) {
            totalPenalty += frontVehicleStabilityPenalty(state, egoIndex, frontIndex);
        }
        return totalPenalty;
    }

    private double frontVehicleStabilityPenalty(DataState state, int egoIndex, int frontIndex) {
        int egoOffset = vehicleOffset(egoIndex);
        int frontOffset = vehicleOffset(frontIndex);
        double frontGap = state.get(frontOffset + VarTable.x.ordinal())
                - state.get(egoOffset + VarTable.x.ordinal())
                - VEHICLE_LENGTH;
        double relativeSpeed = state.get(egoOffset + VarTable.vx.ordinal()) - state.get(frontOffset + VarTable.vx.ordinal());
        double closingSpeed = Math.max(0.0, relativeSpeed);
        int egoLane = (int) state.get(egoOffset + VarTable.lane_index.ordinal());
        int frontLane = (int) state.get(frontOffset + VarTable.lane_index.ordinal());
        if (frontGap <= 0.0 && frontLane == egoLane) {
            return 1.0;
        }
        if (frontGap >= MIN_CLOSE_FRONT_GAP && closingSpeed == 0.0) {
            return 0.0;
        }
        if (frontGap < MIN_CLOSE_FRONT_GAP && relativeSpeed <= -SAFE_RECEDING_RELATIVE_SPEED) {
            return 0.0;
        }

        return aggressiveV3FrontStabilityPenalty(frontGap, closingSpeed);
    }
    private double aggressiveV3FrontStabilityPenalty(double frontGap, double closingSpeed) {
        if (closingSpeed <= 0.0) {
            return 0.0;
        }
        if (frontGap < MIN_STABLE_FRONT_GAP) {
            return Math.min(1.0, Math.max(0.0, closingSpeed - MAX_STABLE_RELATIVE_SPEED) / MAX_STABLE_RELATIVE_SPEED);
        }
        double ttc = frontGap / closingSpeed;
        if (ttc > AGGRESSIVE_V3_TTC_THRESHOLD) {
            return 0.0;
        }
        return Math.min(1.0, (AGGRESSIVE_V3_TTC_THRESHOLD - ttc) / AGGRESSIVE_V3_TTC_THRESHOLD);
    }
    private String diagnoseState(DataState state, int lastStep) {
        int egoIndex = getEgoVehicleIndex(state);
        StringBuilder diagnosis = new StringBuilder();
        diagnosis.append(String.format("Shield diagnosis at prediction step %d: crashed=%.0f",
                lastStep, state.get(auxiliaryIndex(AuxiliarySingletonVarTable.crashed))));
        diagnosis.append(String.format("%n  DisTL robustness: collision=%.3f firstSecondSafety=%.3f stability=%.3f rearThreat=%.3f lowSpeedLaneChange=%.3f shield=%.3f minAcceptable=%.3f",
                lastCollisionRobustness,
                lastFirstSecondSafetyRobustness,
                lastStabilityRobustness,
                lastChangeLaneRearThreatRobustness,
                lastChangeLaneLowSpeedRobustness,
                lastShieldRobustness,
                MIN_ACCEPTABLE_ROBUSTNESS));

        if (egoIndex < 0) {
            diagnosis.append(", ego not found");
            return diagnosis.toString();
        }

        int egoOffset = vehicleOffset(egoIndex);
        int egoLane = (int) state.get(egoOffset + VarTable.lane_index.ordinal());
        diagnosis.append(String.format(", egoLane=%d, egoX=%.2f, egoSpeed=%.2f, egoTargetSpeed=%.2f",
                egoLane,
                state.get(egoOffset + VarTable.x.ordinal()),
                state.get(egoOffset + VarTable.speed.ordinal()),
                state.get(egoOffset + VarTable.targetSpeed.ordinal())));

        for (int frontIndex : getFinalStabilityReferenceVehicles(state, egoIndex)) {
            int frontOffset = vehicleOffset(frontIndex);
            int lane = (int) state.get(frontOffset + VarTable.lane_index.ordinal());
            double frontGap = state.get(frontOffset + VarTable.x.ordinal())
                    - state.get(egoOffset + VarTable.x.ordinal())
                    - VEHICLE_LENGTH;
            double closingSpeed = Math.max(0.0,
                    state.get(egoOffset + VarTable.vx.ordinal()) - state.get(frontOffset + VarTable.vx.ordinal()));
            double penalty = frontVehicleStabilityPenalty(state, egoIndex, frontIndex);
            diagnosis.append(String.format("%n  front lane=%d vehicleId=%s vehicleIndex=%d gap=%.2f closingSpeed=%.2f penalty=%.3f",
                    lane,
                    vehicleId(state, frontIndex),
                    frontIndex,
                    frontGap,
                    closingSpeed,
                    penalty));
        }

        diagnosis.append(String.format("%n  totalFrontStabilityPenalty=%.3f threshold=%.3f",
                frontVehicleStabilityPenalty(state), STABILITY_DISTANCE_THRESHOLD));
        int rearThreatIndex = getConfiguredRearThreatRearVehicleIndex(state);
        if (rearThreatIndex >= 0) {
            int rearOffset = vehicleOffset(rearThreatIndex);
            double rearGap = state.get(egoOffset + VarTable.x.ordinal())
                    - state.get(rearOffset + VarTable.x.ordinal())
                    - VEHICLE_LENGTH;
            double requiredGap = requiredGapForRearDelayedBraking(
                    state.get(rearOffset + VarTable.vx.ordinal()),
                    state.get(egoOffset + VarTable.vx.ordinal())
            );
            diagnosis.append(String.format("%n  rearThreat vehicleId=%s vehicleIndex=%d gap=%.2f requiredGap=%.2f penalty=%.3f threshold=%.3f",
                    vehicleId(state, rearThreatIndex),
                    rearThreatIndex,
                    rearGap,
                    requiredGap,
                    changeLaneRearThreatPenalty(state),
                    CHANGE_LANE_REAR_THREAT_DISTANCE_THRESHOLD));
        } else {
            diagnosis.append(String.format("%n  rearThreat none threshold=%.3f",
                    CHANGE_LANE_REAR_THREAT_DISTANCE_THRESHOLD));
        }
        return diagnosis.toString();
    }
    private int getEgoVehicleIndex(DataState state) {
        for (int i = 0; i < vehicleCount(); i++) {
            if (state.get(vehicleOffset(i) + VarTable.role.ordinal()) == 0.0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEgoChangingLane(DataState state, int egoIndex) {
        int egoOffset = vehicleOffset(egoIndex);
        int egoLane = (int) state.get(egoOffset + VarTable.lane_index.ordinal());
        int egoTargetLane = (int) state.get(egoOffset + VarTable.target_lane_index.ordinal());
        return egoLane != egoTargetLane;
    }

    private int getRearVehicleIndexInLane(DataState state, int egoIndex, int lane) {
        int egoOffset = vehicleOffset(egoIndex);
        double egoX = state.get(egoOffset + VarTable.x.ordinal());
        int rearIndex = -1;
        double closestRearX = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            int vehicleLane = (int) state.get(offset + VarTable.lane_index.ordinal());
            double x = state.get(offset + VarTable.x.ordinal());
            if (vehicleLane == lane && x < egoX && x > closestRearX) {
                rearIndex = i;
                closestRearX = x;
            }
        }
        return rearIndex;
    }

    private List<Integer> getFinalStabilityReferenceVehicles(DataState state, int egoIndex) {
        int egoLane = (int) state.get(vehicleOffset(egoIndex) + VarTable.lane_index.ordinal());
        double egoX = state.get(vehicleOffset(egoIndex) + VarTable.x.ordinal());
        int closestReferenceIndex = -1;
        double closestReferenceX = Double.POSITIVE_INFINITY;

        int frontIndex = getFrontVehicleIndexInLane(state, egoIndex, egoLane);
        if (frontIndex >= 0) {
            closestReferenceIndex = frontIndex;
            closestReferenceX = state.get(vehicleOffset(frontIndex) + VarTable.x.ordinal());
        }

        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            int lane = (int) state.get(offset + VarTable.lane_index.ordinal());
            double x = state.get(offset + VarTable.x.ordinal());
            if (lane != egoLane
                    && x > egoX
                    && x < closestReferenceX
                    && hasHistoricalCutInIntentTowardEgoLane(state, i, egoLane)) {
                closestReferenceIndex = i;
                closestReferenceX = x;
            }
        }

        List<Integer> vehicles = new ArrayList<>();
        if (closestReferenceIndex >= 0) {
            vehicles.add(closestReferenceIndex);
        }
        return vehicles;
    }
    private boolean hasHistoricalCutInIntentTowardEgoLane(DataState state, int vehicleIndex, int egoLane) {
        int offset = vehicleOffset(vehicleIndex);
        double historicalCutInIntent = state.get(offset + VarTable.historicalCutInIntent.ordinal());
        if (!Double.isNaN(historicalCutInIntent)) {
            return historicalCutInIntent > 0.0;
        }
        int targetLane = (int) state.get(offset + VarTable.target_lane_index.ordinal());
        return targetLane == egoLane;
    }

    private boolean isCandidateChangeLane(List<Vehicle> vehiclesAtStep, Action initialIntention) {
        return candidateTargetLane(vehiclesAtStep, initialIntention) >= 0;
    }

    private int candidateTargetLane(List<Vehicle> vehiclesAtStep, Action initialIntention) {
        int egoIndex = egoVehicleIndex(vehiclesAtStep);
        if (egoIndex < 0 || initialIntention == null) {
            return -1;
        }
        Vehicle ego = vehiclesAtStep.get(egoIndex);
        switch (initialIntention) {
            case LANE_LEFT:
                return Math.max(0, ego.getLaneIndex() - 1);
            case LANE_RIGHT:
                return Math.min(sourceEngine.numLanes - 1, ego.getLaneIndex() + 1);
            default:
                return -1;
        }
    }

    private int egoVehicleIndex(List<Vehicle> vehiclesAtStep) {
        for (int i = 0; i < vehiclesAtStep.size(); i++) {
            if ("EGO".equals(vehiclesAtStep.get(i).role)) {
                return i;
            }
        }
        return -1;
    }

    private void populateRearThreatAuxiliaryValues(Map<Integer, Double> values, List<Vehicle> vehiclesAtStep) {
        int egoIndex = egoVehicleIndex(vehiclesAtStep);
        int targetLane = candidateTargetLane(vehiclesAtStep, initialIntention(values));
        if (egoIndex < 0 || targetLane < 0) {
            values.put(auxiliaryIndex(AuxiliarySingletonVarTable.rearThreatRearVehicleIndex), -1.0);
            values.put(auxiliaryIndex(AuxiliarySingletonVarTable.rearThreatBeforeAcceleration), 0.0);
            return;
        }

        int rearIndex = initialRearVehicleIndexInLane(values, vehicleCount(), egoIndex, targetLane);
        if (rearIndex < 0) {
            values.put(auxiliaryIndex(AuxiliarySingletonVarTable.rearThreatRearVehicleIndex), -1.0);
            values.put(auxiliaryIndex(AuxiliarySingletonVarTable.rearThreatBeforeAcceleration), 0.0);
            return;
        }

        int rearFrontIndex = initialFrontVehicleIndexInLaneExcluding(
                values, vehicleCount(), rearIndex, targetLane, egoIndex);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.rearThreatRearVehicleIndex), (double) rearIndex);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.rearThreatBeforeAcceleration),
                initialRawIdmAcceleration(values, rearIndex, rearFrontIndex));
    }

    private Action initialIntention(Map<Integer, Double> values) {
        double value = values.getOrDefault(auxiliaryIndex(AuxiliarySingletonVarTable.initialIntention), -1.0);
        if (value < 0.0 || Double.isNaN(value)) {
            return null;
        }
        return Action.fromValue((int) Math.round(value));
    }

    private int initialRearVehicleIndexInLane(Map<Integer, Double> values, int vehicleCount, int egoIndex,
                                              int targetLane) {
        int egoOffset = vehicleOffset(egoIndex);
        double egoX = values.get(egoOffset + VarTable.x.ordinal());
        int rearIndex = -1;
        double closestRearX = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < vehicleCount; i++) {
            if (i == egoIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            int lane = values.get(offset + VarTable.lane_index.ordinal()).intValue();
            double x = values.get(offset + VarTable.x.ordinal());
            if (lane == targetLane && x < egoX && x > closestRearX) {
                rearIndex = i;
                closestRearX = x;
            }
        }
        return rearIndex;
    }

    private int initialFrontVehicleIndexInLaneExcluding(Map<Integer, Double> values, int vehicleCount,
                                                        int vehicleIndex, int targetLane, int excludedIndex) {
        int vehicleOffset = vehicleOffset(vehicleIndex);
        double vehicleX = values.get(vehicleOffset + VarTable.x.ordinal());
        int frontIndex = -1;
        double closestFrontX = Double.POSITIVE_INFINITY;
        for (int i = 0; i < vehicleCount; i++) {
            if (i == vehicleIndex || i == excludedIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            int lane = values.get(offset + VarTable.lane_index.ordinal()).intValue();
            double x = values.get(offset + VarTable.x.ordinal());
            if (lane == targetLane && x > vehicleX && x < closestFrontX) {
                frontIndex = i;
                closestFrontX = x;
            }
        }
        return frontIndex;
    }

    private double initialRawIdmAcceleration(Map<Integer, Double> values, int vehicleIndex, int frontVehicleIndex) {
        int vehicleOffset = vehicleOffset(vehicleIndex);
        double v = values.get(vehicleOffset + VarTable.vx.ordinal());
        double v0 = Math.max(0.1, values.get(vehicleOffset + VarTable.targetSpeed.ordinal()));
        double freeFlowTerm = 1.0 - Math.pow(v / v0, 4.0);
        if (frontVehicleIndex < 0) {
            return 3.0 * freeFlowTerm;
        }

        int frontOffset = vehicleOffset(frontVehicleIndex);
        double frontVx = values.get(frontOffset + VarTable.vx.ordinal());
        double gap = values.get(frontOffset + VarTable.x.ordinal())
                - values.get(vehicleOffset + VarTable.x.ordinal())
                - VEHICLE_LENGTH;
        gap = Math.max(gap, 0.01);
        double dv = v - frontVx;
        double desiredGap = 10.0 + v * 1.5 + (v * dv) / (2.0 * Math.sqrt(3.0 * 5.0));
        desiredGap = Math.max(desiredGap, 10.0);
        return 3.0 * (freeFlowTerm - Math.pow(desiredGap / gap, 2.0));
    }

    private int getConfiguredRearThreatRearVehicleIndex(DataState state) {
        int rearIndex = (int) Math.round(
                state.get(auxiliaryIndex(AuxiliarySingletonVarTable.rearThreatRearVehicleIndex)));
        if (rearIndex < 0
                || rearIndex >= vehicleCount()
                || state.get(auxiliaryIndex(AuxiliarySingletonVarTable.isInitialChangeLane)) <= 0.0) {
            return -1;
        }
        return rearIndex;
    }

    private double requiredGapForRearDelayedBraking(double rearVx, double egoVx) {
        double closingSpeed = Math.max(0.0, rearVx - egoVx);
        return closingSpeed * CHANGE_LANE_REAR_REACTION_TIME
                + closingSpeed * closingSpeed / (2.0 * CHANGE_LANE_REAR_MAX_BRAKE);
    }

    private List<boolean[]> historicalCutInIntentByStep(List<List<Vehicle>> trace) {
        int vehicleCount = vehicleCount();
        boolean[] historical = new boolean[vehicleCount];
        List<boolean[]> byStep = new ArrayList<>();
        for (List<Vehicle> vehiclesAtStep : trace) {
            updateHistoricalCutInIntent(historical, vehiclesAtStep);
            byStep.add(historical.clone());
        }
        return byStep;
    }

    private void updateHistoricalCutInIntent(boolean[] historical, List<Vehicle> vehiclesAtStep) {
        int egoLane = -1;
        for (Vehicle vehicle : vehiclesAtStep) {
            if ("EGO".equals(vehicle.role)) {
                egoLane = vehicle.getLaneIndex();
                break;
            }
        }
        if (egoLane < 0) {
            return;
        }
        for (int i = 0; i < vehiclesAtStep.size() && i < historical.length; i++) {
            Vehicle vehicle = vehiclesAtStep.get(i);
            if (!"EGO".equals(vehicle.role)
                    && vehicle.getLaneIndex() != egoLane
                    && vehicle.getTargetLaneIndex() == egoLane) {
                historical[i] = true;
            }
        }
    }
    private String vehicleId(DataState state, int vehicleIndex) {
        return String.valueOf((int) state.get(vehicleOffset(vehicleIndex) + VarTable.id.ordinal()));
    }
    private int getFrontVehicleIndexInLane(DataState state, int egoIndex, int targetLane) {
        if (egoIndex < 0) {
            return -1;
        }

        int egoOffset = vehicleOffset(egoIndex);
        double egoX = state.get(egoOffset + VarTable.x.ordinal());
        int frontIndex = -1;
        double closestFrontX = Double.POSITIVE_INFINITY;
        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            int lane = (int) state.get(offset + VarTable.lane_index.ordinal());
            double x = state.get(offset + VarTable.x.ordinal());
            if (lane == targetLane && x > egoX && x < closestFrontX) {
                frontIndex = i;
                closestFrontX = x;
            }
        }
        return frontIndex;
    }
    private double calculateOneSecondWorstCaseSafetyGap(double egoVx, double frontVx, double egoAcceleration,
                                                        double frontAcceleration) {
        double egoSpeed = Math.max(0.0, egoVx);
        double frontSpeed = Math.max(0.0, frontVx);
        double frontWorstAcceleration = frontAcceleration - MIN_FRONT_ACCELERATION_UNCERTAINTY;
        double relativeClosingDistance = (egoSpeed - frontSpeed) * FIRST_SECOND_LOOKAHEAD_TIME
                + 0.5 * (egoAcceleration - frontWorstAcceleration)
                * FIRST_SECOND_LOOKAHEAD_TIME * FIRST_SECOND_LOOKAHEAD_TIME;
        return FIRST_SECOND_MIN_FRONT_GAP + Math.max(0.0, relativeClosingDistance);
    }
}
