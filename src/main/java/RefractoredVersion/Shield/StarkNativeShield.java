package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.ego.EgoVehicle;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;
import RefractoredVersion.Engine.vehicle.NonNpcVehicle;
import RefractoredVersion.Engine.vehicle.PControlledVehicle;
import RefractoredVersion.Engine.vehicle.Vehicle;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.SandboxNpcPolitenessMode;
import it.unicam.quasylab.jspear.ControlledSystem;
import it.unicam.quasylab.jspear.DefaultRandomGenerator;
import it.unicam.quasylab.jspear.EvolutionSequence;
import it.unicam.quasylab.jspear.SystemState;
import it.unicam.quasylab.jspear.controller.Controller;
import it.unicam.quasylab.jspear.controller.ControllerRegistry;
import it.unicam.quasylab.jspear.distl.AlwaysDisTLFormula;
import it.unicam.quasylab.jspear.distl.ConjunctionDisTLFormula;
import it.unicam.quasylab.jspear.distl.DisTLFormula;
import it.unicam.quasylab.jspear.distl.DoubleSemanticsVisitor;
import it.unicam.quasylab.jspear.distl.TargetDisTLFormula;
import it.unicam.quasylab.jspear.ds.DataState;
import it.unicam.quasylab.jspear.ds.DataStateUpdate;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class StarkNativeShield extends ExploreFutureBetterReferenceShield {
    private static final String CONTROLLER_NAME = "stark-native-highway-controller";
    private static final List<Action> AI_SLOWER_SLOWER = List.of(Action.SLOWER, Action.SLOWER);
    private static final double TARGET_SPEED_SAMPLE_DELTA = 1.0;
    private static final double ACTION_SPEED_DELTA = 5.0;
    protected final List<Action> futureActions;

    public StarkNativeShield(JavaHighwayEngine sourceEngine) {
        this(sourceEngine, AI_SLOWER_SLOWER);
    }

    public StarkNativeShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        super(sourceEngine, futureActions);
        if (futureActions == null) {
            throw new IllegalArgumentException("futureActions cannot be null.");
        }
        this.futureActions = List.copyOf(futureActions);
    }

    public StarkNativeShield(JavaHighwayEngine sourceEngine, EgoVehicle egoVehicle, JavaMomentumConfig config) {
        this(sourceEngine);
    }

    @Override
    protected void populateSubclassVehicleValues(Map<Integer, Double> values,
                                                 List<Vehicle> vehiclesAtStep,
                                                 boolean[] historicalCutInIntent,
                                                 Action initialIntention) {
        int egoIndex = getEgoVehicleIndexFromValues(values);
        if (egoIndex < 0) {
            throw new IllegalStateException("Cannot construct final stability reference set without ego vehicle");
        }

        int egoOffset = vehicleOffset(egoIndex);
        int egoLane = values.get(egoOffset + VarTable.lane_index.ordinal()).intValue();
        double egoX = values.get(egoOffset + VarTable.x.ordinal());

        // stableAtLastStep reads these marks only from the ending state.
        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }

            int offset = vehicleOffset(i);
            double x = values.get(offset + VarTable.x.ordinal());
            int lane = values.get(offset + VarTable.lane_index.ordinal()).intValue();
            int targetLane = values.get(offset + VarTable.target_lane_index.ordinal()).intValue();
            boolean sharesEgoLane = lane == egoLane;
            boolean intendsToEnterEgoLane = lane != egoLane && targetLane == egoLane;

            if (x > egoX && (sharesEgoLane || intendsToEnterEgoLane)) {
                values.put(offset + VarTable.finalStabilityReference.ordinal(), 1.0);
            }
        }
    }

    @Override
    public boolean verifySafe(Action candidateAction) throws Exception {
        Objects.requireNonNull(candidateAction, "candidateAction must not be null");
        return verifySafeSequence(actionSequence(candidateAction));
    }

    @Override
    public boolean verifySafe(Action candidateAction, List<DisTLFormula> moreCriteria) throws Exception {
        Objects.requireNonNull(candidateAction, "candidateAction must not be null");
        return verifySafeSequence(actionSequence(candidateAction), moreCriteria);
    }

    @Override
    public EvolutionSequence getPredictionSequence(Action candidateAction) throws Exception {
        Objects.requireNonNull(candidateAction, "candidateAction must not be null");
        List<Action> actionSequence = actionSequence(candidateAction);
        validateActionSequence(actionSequence);
        createSandboxEngine(actionSequence);
        sequence = getNativePredictionSequence(actionSequence);
        return sequence;
    }

    @Override
    public boolean verifySafeSequence(List<Action> actionSequence) throws Exception {
        return verifySafeSequence(actionSequence, null);
    }

    public boolean verifySafeSequence(List<Action> actionSequence, List<DisTLFormula> moreCriteria) throws Exception {
        validateActionSequence(actionSequence);
        createSandboxEngine(actionSequence);
        sequence = getNativePredictionSequence(actionSequence);
        int lastStep = lastNativePredictionStep(actionSequence);
        sequence.generateUpTo(lastStep);

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
        DisTLFormula rearThreatCondition = changeLaneRearThreatAtDecisionStep;
        // changeLaneLowSpeedAtDecisionStep remains evaluated for diagnostics but is not a shield condition.
        shieldCondition = new ConjunctionDisTLFormula(shieldCondition, rearThreatCondition);

        if (moreCriteria != null) {
            for (DisTLFormula criteria : moreCriteria) {
                shieldCondition = new ConjunctionDisTLFormula(shieldCondition, criteria);
            }
        }

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

    protected List<Action> actionSequence(Action candidateAction) {
        List<Action> actions = new ArrayList<>();
        actions.add(candidateAction);
        actions.addAll(futureActions);
        return actions;
    }

    protected void validateActionSequence(List<Action> actionSequence) {
        if (actionSequence == null || actionSequence.isEmpty() || actionSequence.get(0) == null) {
            throw new IllegalArgumentException("StarkNativeShield requires a non-empty action sequence.");
        }
        for (Action action : actionSequence) {
            if (action == null) {
                throw new IllegalArgumentException("StarkNativeShield action sequence cannot contain null actions.");
            }
        }
    }

    protected int lastNativePredictionStep(List<Action> actionSequence) {
        return Math.max(0, actionSequence.size() * sourceEngine.getFrequency() - 1);
    }

    protected EvolutionSequence getNativePredictionSequence(List<Action> actionSequence) {
        DefaultRandomGenerator random = new DefaultRandomGenerator();
        return new EvolutionSequence(
                random,
                rg -> {
                    Controller controller = getController(actionSequence);
                    DataState state = getInitialState(rg, actionSequence.get(0));
                    return new ControlledSystem(
                            controller,
                            (environmentRg, ds) -> ds.apply(getEnvironmentUpdates(environmentRg, ds)),
                            state
                    );
                },
                EVOLUTION_SEQUENCE_SIZE
        );
    }

    protected Controller getController(List<Action> actionSequence) {
        ControllerRegistry registry = new ControllerRegistry();
        registry.set(CONTROLLER_NAME,
                Controller.doAction((rg, ds) -> getControllerUpdates(rg, ds, actionSequence),
                        registry.reference(CONTROLLER_NAME)));
        return registry.reference(CONTROLLER_NAME);
    }

    protected DataState getInitialState(RandomGenerator rg, Action initialIntention) {
        List<Vehicle> vehicles = copyDetectedVehiclesForNativePrediction(rg);
        return toNativeDataState(vehicles, new boolean[vehicleCount()], initialIntention);
    }

    protected List<DataStateUpdate> getControllerUpdates(RandomGenerator rg, DataState state,
                                                       List<Action> actionSequence) {
        List<Vehicle> vehicles = vehiclesFromState(state);
        JavaHighwayEngine nativeEngine = nativeEngineFor(state, vehicles);
        Vehicle ego = egoVehicle(vehicles);
        if (ego != null && state.getStep() % sourceEngine.getFrequency() == 0) {
            int actionIndex = state.getStep() / sourceEngine.getFrequency();
            if (actionIndex >= actionSequence.size()) {
                throw new IllegalStateException(String.format(
                        "Missing native shield action at decision index %d, actionSequence size is %d",
                        actionIndex,
                        actionSequence.size()
                ));
            }
            applyAction(ego, actionSequence.get(actionIndex));
        }

        try {
            for (Vehicle vehicle : vehicles) {
                if (!(vehicle instanceof NonNpcVehicle)) {
                    vehicle.setTargetLaneIndex(JavaHighwayEngineUtils.sandboxComputeTargetLane(
                            vehicle,
                            vehicles,
                            List.of(0, 1),
                            nativeEngine
                    ));
                }
                vehicle.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(vehicle, vehicles);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply STARK-native highway controller", e);
        }
        return updatesForVehicles(vehicles, state);
    }

    protected List<DataStateUpdate> getEnvironmentUpdates(RandomGenerator rg, DataState state) {
        List<Vehicle> vehicles = vehiclesFromState(state);
        JavaHighwayEngine nativeEngine = nativeEngineFor(state, vehicles);
        for (Vehicle vehicle : vehicles) {
            vehicle.setEngine(nativeEngine);
            vehicle.plannedSteering = JavaHighwayEngineUtils.computeSteering(vehicle);
            vehicle.applyPhysics();
        }
        return updatesForVehicles(vehicles, state);
    }

    protected List<Vehicle> copyDetectedVehiclesForNativePrediction(RandomGenerator rg) {
        List<Vehicle> copies = new ArrayList<>();
        for (Vehicle source : detectedVehiclesForNativePrediction()) {
            Vehicle copy = source instanceof NonNpcVehicle ? new NativeNonNpcVehicle() : new NativeNpcVehicle();
            copyVehicleStateForNativePrediction(source, copy, rg);
            copies.add(copy);
        }
        return copies;
    }

    protected List<Vehicle> detectedVehiclesForNativePrediction() {
        for (Vehicle vehicle : sourceEngine.vehicles) {
            if (vehicle instanceof EgoVehicle egoVehicle) {
                return new ArrayList<>(egoVehicle.getDetectedVehicles());
            }
        }
        throw new IllegalStateException("Cannot create native shield prediction without an EgoVehicle.");
    }

    protected void copyVehicleStateForNativePrediction(Vehicle source, Vehicle copy, RandomGenerator rg) {
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
        copy.politeness = sandboxPoliteness(source, copy, rg);
        copy.cooldownTimer = copy instanceof NonNpcVehicle ? source.cooldownTimer : rg.nextDouble();
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
            copy.targetSpeed = randomFixedPredictionTargetSpeed(copy, rg);
        }
    }

    protected double sandboxPoliteness(Vehicle source, Vehicle copy, RandomGenerator rg) {
        if (copy instanceof NonNpcVehicle) {
            return source.politeness;
        }
        JavaMomentumConfig config = sourceEngine.config;
        SandboxNpcPolitenessMode mode = config == null || config.getSandboxNpcPolitenessMode() == null
                ? SandboxNpcPolitenessMode.COPY_REAL
                : config.getSandboxNpcPolitenessMode();
        return switch (mode) {
            case ZERO -> 0.0;
            case RANDOM -> rg.nextDouble() * 0.3;
            case COPY_REAL -> source.politeness;
        };
    }

    protected boolean isFixPredictionEnabled() {
        return sourceEngine.config != null && sourceEngine.config.isFixPrediction();
    }

    protected double randomFixedPredictionTargetSpeed(Vehicle vehicle, RandomGenerator rg) {
        double delta = sourceEngine.config == null
                ? TARGET_SPEED_SAMPLE_DELTA
                : sourceEngine.config.getFixedPredictionTargetSpeedDelta();
        double minTargetSpeed = Math.max(0.0, vehicle.speed - delta);
        double maxTargetSpeed = vehicle.speed + delta;
        return minTargetSpeed + rg.nextDouble() * (maxTargetSpeed - minTargetSpeed);
    }

    protected DataState toNativeDataState(List<Vehicle> vehicles, boolean[] historicalCutInIntent,
                                        Action initialIntention) {
        Map<Integer, Double> values = valuesForVehicles(vehicles, historicalCutInIntent, initialIntention);
        return new DataState(AuxiliarySingletonVarTable.stateSize(vehicleCount()),
                index -> values.getOrDefault(index, Double.NaN));
    }

    private double valueOf(Vehicle vehicle, VarTable variable, int fallbackId) {
        return switch (variable) {
            case id -> parseVehicleId(vehicle.id, fallbackId);
            case politeness -> vehicle.politeness;
            case cooldownTimer -> vehicle.cooldownTimer;
            case target_lane_index -> vehicle.getTargetLaneIndex();
            case lane_index -> vehicle.getLaneIndex();
            case x -> vehicle.x;
            case y -> vehicle.y;
            case vx -> vehicle.vx;
            case vy -> vehicle.vy;
            case speed -> vehicle.speed;
            case heading -> vehicle.heading;
            case plannedAcceleration -> vehicle.plannedAcceleration;
            case plannedSteering -> vehicle.plannedSteering;
            case role -> "EGO".equals(vehicle.role) ? 0.0 : 1.0;
            case targetSpeed -> vehicle.targetSpeed;
            case idmCooldownTimer, idmActionStepLength, reactionDelay -> 0.0;
            case historicalCutInIntent, finalStabilityReference -> 0.0;
        };
    }

    protected List<Vehicle> vehiclesFromState(DataState state) {
        List<Vehicle> vehicles = new ArrayList<>();
        for (int i = 0; i < vehicleCount(); i++) {
            Vehicle vehicle = state.get(vehicleOffset(i) + VarTable.role.ordinal()) == 0.0
                    ? new NativeNonNpcVehicle()
                    : new NativeNpcVehicle();
            copyStateToVehicle(state, i, vehicle);
            vehicles.add(vehicle);
        }
        return vehicles;
    }

    protected void copyStateToVehicle(DataState state, int vehicleIndex, Vehicle vehicle) {
        int offset = vehicleOffset(vehicleIndex);
        vehicle.id = String.valueOf((int) state.get(offset + VarTable.id.ordinal()));
        vehicle.politeness = state.get(offset + VarTable.politeness.ordinal());
        vehicle.cooldownTimer = state.get(offset + VarTable.cooldownTimer.ordinal());
        vehicle.setTargetLaneIndex((int) state.get(offset + VarTable.target_lane_index.ordinal()));
        vehicle.setLaneIndex((int) state.get(offset + VarTable.lane_index.ordinal()));
        vehicle.x = state.get(offset + VarTable.x.ordinal());
        vehicle.y = state.get(offset + VarTable.y.ordinal());
        vehicle.vx = state.get(offset + VarTable.vx.ordinal());
        vehicle.vy = state.get(offset + VarTable.vy.ordinal());
        vehicle.speed = state.get(offset + VarTable.speed.ordinal());
        vehicle.heading = state.get(offset + VarTable.heading.ordinal());
        vehicle.plannedAcceleration = state.get(offset + VarTable.plannedAcceleration.ordinal());
        vehicle.plannedSteering = state.get(offset + VarTable.plannedSteering.ordinal());
        vehicle.role = state.get(offset + VarTable.role.ordinal()) == 0.0 ? "EGO" : "NPC";
        vehicle.targetSpeed = state.get(offset + VarTable.targetSpeed.ordinal());
    }

    protected List<DataStateUpdate> updatesForVehicles(List<Vehicle> vehicles, DataState previousState) {
        boolean[] historicalCutInIntent = historicalCutInIntent(previousState, vehicles);
        Action initialIntention = initialIntention(previousState);
        Map<Integer, Double> values = valuesForVehicles(vehicles, historicalCutInIntent, initialIntention);
        List<DataStateUpdate> updates = new ArrayList<>();
        int stateSize = AuxiliarySingletonVarTable.stateSize(vehicleCount());
        for (int index = 0; index < stateSize; index++) {
            updates.add(new DataStateUpdate(index, values.getOrDefault(index, Double.NaN)));
        }
        return updates;
    }

    protected Map<Integer, Double> valuesForVehicles(List<Vehicle> vehicles, boolean[] historicalCutInIntent,
                                                   Action initialIntention) {
        Map<Integer, Double> values = new HashMap<>();
        for (int i = 0; i < vehicles.size(); i++) {
            Vehicle vehicle = vehicles.get(i);
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
            values.put(offset + VarTable.finalStabilityReference.ordinal(), 0.0);
        }
        populateSubclassVehicleValues(values, vehicles, historicalCutInIntent, initialIntention);

        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.crashed), hasCollision(vehicles) ? 1.0 : 0.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInCurrentLane), -1.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInLeftLane), -1.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInRightLane), -1.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.isInitialChangeLane),
                isCandidateChangeLane(vehicles, initialIntention) ? 1.0 : 0.0);
        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.initialIntention),
                initialIntention == null ? -1.0 : initialIntention.getValue());
        populateRearThreatAuxiliaryValues(values, vehicles);
        values.putIfAbsent(auxiliaryIndex(AuxiliarySingletonVarTable.endStateTTC), 0.0);
        return values;
    }

    protected boolean[] historicalCutInIntent(DataState previousState, List<Vehicle> vehicles) {
        boolean[] historical = new boolean[vehicleCount()];
        for (int i = 0; i < historical.length; i++) {
            int offset = vehicleOffset(i);
            if (offset + VarTable.historicalCutInIntent.ordinal() < previousState.size()) {
                historical[i] = previousState.get(offset + VarTable.historicalCutInIntent.ordinal()) > 0.0;
            }
        }
        int egoLane = -1;
        for (Vehicle vehicle : vehicles) {
            if ("EGO".equals(vehicle.role)) {
                egoLane = vehicle.getLaneIndex();
                break;
            }
        }
        if (egoLane < 0) {
            return historical;
        }
        for (int i = 0; i < vehicles.size() && i < historical.length; i++) {
            Vehicle vehicle = vehicles.get(i);
            if (!"EGO".equals(vehicle.role)
                    && vehicle.getLaneIndex() != egoLane
                    && vehicle.getTargetLaneIndex() == egoLane) {
                historical[i] = true;
            }
        }
        return historical;
    }

    protected Action initialIntention(DataState state) {
        if (auxiliaryIndex(AuxiliarySingletonVarTable.initialIntention) >= state.size()) {
            return null;
        }
        double value = state.get(auxiliaryIndex(AuxiliarySingletonVarTable.initialIntention));
        if (value < 0.0 || Double.isNaN(value)) {
            return null;
        }
        return Action.fromValue((int) Math.round(value));
    }

    protected JavaHighwayEngine nativeEngineFor(DataState state, List<Vehicle> vehicles) {
        JavaHighwayEngine nativeEngine = new JavaHighwayEngine();
        nativeEngine.setFrequency(sourceEngine.getFrequency());
        nativeEngine.config = sourceEngine.config;
        nativeEngine.numLanes = sourceEngine.numLanes;
        nativeEngine.stepsTaken = state.getStep();
        nativeEngine.timeElapsed = state.getStep() * nativeEngine.getDt();
        nativeEngine.vehicles = vehicles;
        for (Vehicle vehicle : vehicles) {
            vehicle.setEngine(nativeEngine);
        }
        return nativeEngine;
    }

    protected Vehicle egoVehicle(List<Vehicle> vehicles) {
        for (Vehicle vehicle : vehicles) {
            if ("EGO".equals(vehicle.role)) {
                return vehicle;
            }
        }
        return null;
    }

    protected void applyAction(Vehicle vehicle, Action action) {
        switch (action) {
            case LANE_LEFT:
                vehicle.setTargetLaneIndex(Math.max(0, vehicle.getLaneIndex() - 1));
                break;
            case LANE_RIGHT:
                vehicle.setTargetLaneIndex(Math.min(sourceEngine.numLanes - 1, vehicle.getLaneIndex() + 1));
                break;
            case FASTER:
                vehicle.targetSpeed = clipTargetSpeed(vehicle.targetSpeed + ACTION_SPEED_DELTA);
                break;
            case SLOWER:
                vehicle.targetSpeed = clipTargetSpeed(vehicle.targetSpeed - ACTION_SPEED_DELTA);
                break;
            case IDLE:
                break;
            default:
                throw new IllegalArgumentException("Unsupported action: " + action);
        }
    }

    protected double clipTargetSpeed(double targetSpeed) {
        double maxTargetSpeed = sourceEngine.config == null ? 40.0 : sourceEngine.config.getMaxTargetSpeed();
        return Math.max(0.0, Math.min(maxTargetSpeed, targetSpeed));
    }

    @Override
    protected double parseVehicleId(String id, int fallback) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static class NativeNonNpcVehicle extends Vehicle implements NonNpcVehicle, PControlledVehicle {
        @Override
        public void planAction() {
        }
    }

    private static class NativeNpcVehicle extends Vehicle {
    }
}
