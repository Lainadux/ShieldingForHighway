package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.vehicle.Vehicle;
import it.unicam.quasylab.jspear.ControlledSystem;
import it.unicam.quasylab.jspear.DefaultRandomGenerator;
import it.unicam.quasylab.jspear.EvolutionSequence;
import it.unicam.quasylab.jspear.controller.Controller;
import it.unicam.quasylab.jspear.controller.ControllerRegistry;
import it.unicam.quasylab.jspear.distl.AlwaysDisTLFormula;
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
/**
 * Implements the TTC-based heuristic shield.
 *
 * <p>The shield constructs a STARK evolution sequence over a one-second
 * prediction horizon. The candidate AI action is applied to the ego vehicle
 * at the beginning of the prediction. During prediction, each NPC keeps its
 * target lane unchanged and receives a longitudinal acceleration sampled
 * uniformly from [-1, 1] m/s^2 at every prediction step. Vehicle states are
 * then propagated using the low-level kinematic model.</p>
 *
 * <p>For every predicted state, the shield computes the minimum time to
 * collision (TTC) between the ego vehicle and the relevant surrounding
 * vehicles. A DisTL always formula checks the resulting TTC values against
 * the configured safety threshold. The candidate action is accepted only
 * when the formula has non-negative robustness.</p>
 */
public class HeuristicShield {
    protected static final double MIN_TTC_THRESHOLD = 2.0;
    private static final double DESIRED_MIN_TTC = 100.0;
    protected static final double q = 0;
    int frequency = 4;
    List<Vehicle> observedVehicles;
    EvolutionSequence sequence;
    private static final String CONTROLLER_NAME = "heuristic-central-controller";
    private static final double NPC_ACCELERATION_MIN = -1.0;
    private static final double NPC_ACCELERATION_MAX = 1.0;
    private static final double ACTION_SPEED_DELTA = 5.0;
    private static final double MAX_TARGET_SPEED = 40.0;
    private int candidateTargetLane = -1;
    protected Action candidateAction;
    protected double lastRobustness = Double.NaN;
    protected double lastMinTtc = Double.NaN;
    protected String lastDiagnosis = "";
    private double vehicleLength = 5.0;
    private int numLanes = 3;
    private int laneWidth = 4;

    public boolean verifySafe() {
        if (candidateAction == null) {
            throw new IllegalArgumentException("candidateAction must not be null");
        }

        buildEvolutionSequence();

        int lastStep = frequency;


        DisTLFormula formula = minTtcAlwaysFormula(lastStep);

        DoubleSemanticsVisitor semantics = new DoubleSemanticsVisitor();
        double robustness = semantics.eval(formula).eval(30, 0, sequence);
        lastRobustness = robustness;
        lastMinTtc = minTtcAtStep(lastStep);
        lastDiagnosis = String.format(
                "HeuristicShield diagnosis: minTTC=%.3f threshold=%.3f robustness=%.3f",
                lastMinTtc,
                MIN_TTC_THRESHOLD,
                lastRobustness
        );

        return robustness >= 0;
    }

    public String getDiagnosis() {
        return lastDiagnosis;
    }

    public double getLastMinTtc() {
        return lastMinTtc;
    }

    public double getLastRobustness() {
        return lastRobustness;
    }

    protected double minTtcAtStep(int step) {
        return sequence.get(step).stream()
                .mapToDouble(systemState -> systemState.getDataState().get(minTtcIndex()))
                .min()
                .orElse(Double.NaN);
    }

    protected DisTLFormula minTtcAlwaysFormula(int lastStep) {
        return new AlwaysDisTLFormula(
                new TargetDisTLFormula(
                        this::stabilizeMinTtc,
                        this::minTtcPenalty,
                        q
                ),
                0,
                lastStep
        );
    }
    protected double minTtcPenalty(DataState state) {
        double minTtc = state.get(minTtcIndex());
        return minTtc <= minTtcThreshold() ? 1.0 : 0.0;
    }
    protected double minTtcThreshold() {
        return 4.0;
    }
    protected DataState stabilizeMinTtc(RandomGenerator rg, DataState state) {
        return state.apply(List.of(
                new DataStateUpdate(minTtcIndex(), DESIRED_MIN_TTC)
        ));
    }

    public HeuristicShield(List<Vehicle> vehicles, Action candidateAction) {
        this.observedVehicles = vehicles;
        this.candidateAction = candidateAction;
    }

    public void buildEvolutionSequence(){
        RandomGenerator rand = new DefaultRandomGenerator();
        Controller controller = getController();
        DataState state = getInitialState();
        ControlledSystem system = new ControlledSystem(controller, (rg, ds) -> ds.apply(getEnvironmentUpdates(rg, ds)), state);
        EvolutionSequence sequence = new EvolutionSequence(rand, rg -> system, 30);
        this.sequence = sequence;
    }
    protected Controller getController() {
        ControllerRegistry registry = new ControllerRegistry();
        Controller self = registry.reference(CONTROLLER_NAME);

        registry.set(
                CONTROLLER_NAME,
                Controller.doAction(
                        (rg, state) -> getControllerUpdates(rg, state),
                        self
                )
        );
        return self;
    }


    protected List<DataStateUpdate> getControllerUpdates(RandomGenerator rg, DataState state) {
        List<DataStateUpdate> updates = new ArrayList<>();

        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            throw new IllegalStateException("No ego vehicle in DataState");
        }

        for (int i = 0; i < vehicleCount(); i++) {
            int offset = vehicleOffset(i);

            if (i == egoIndex) {
                if (state.getStep() == 0) {
                    updates.addAll(applyActionToEgo(state, egoIndex, candidateAction));
                }

                double acceleration = egoPlannedAcceleration(state, egoIndex, updates);
                updates.add(new DataStateUpdate(
                        offset + VarTable.plannedAcceleration.ordinal(),
                        acceleration
                ));
            }
            else {
                double sampledAcceleration =
                        NPC_ACCELERATION_MIN
                                + (NPC_ACCELERATION_MAX - NPC_ACCELERATION_MIN) * rg.nextDouble();

                updates.add(new DataStateUpdate(
                        offset + VarTable.plannedAcceleration.ordinal(),
                        sampledAcceleration
                ));

                // NPC target lane remains unchanged.
                updates.add(new DataStateUpdate(
                        offset + VarTable.target_lane_index.ordinal(),
                        value(state, i, VarTable.target_lane_index)
                ));
            }
        }

        return updates;
    }
    protected double egoPlannedAcceleration(DataState state, int egoIndex, List<DataStateUpdate> pendingUpdates) {
        double KP_A = 1/0.6;

        double targetSpeed = value(state, egoIndex, VarTable.targetSpeed);

        int targetSpeedIndex = vehicleOffset(egoIndex) + VarTable.targetSpeed.ordinal();
        for (DataStateUpdate update : pendingUpdates) {
            if (update.getIndex() == targetSpeedIndex) {
                targetSpeed = update.getValue();
            }
        }

        double speed = value(state, egoIndex, VarTable.speed);
        return KP_A * (targetSpeed - speed);
    }

    protected double value(DataState state, int vehicleIndex, VarTable variable) {
        return state.get(vehicleOffset(vehicleIndex) + variable.ordinal());
    }

    protected int intValue(DataState state, int vehicleIndex, VarTable variable) {
        return (int) value(state, vehicleIndex, variable);
    }

    protected int vehicleOffset(int vehicleIndex) {
        return vehicleIndex * VarTable.values().length;
    }

    protected int vehicleCount() {
        return observedVehicles.size();
    }

    protected int getEgoVehicleIndex(DataState state) {
        for (int i = 0; i < vehicleCount(); i++) {
            if (value(state, i, VarTable.role) == 0.0) {
                return i;
            }
        }
        return -1;
    }
    protected List<DataStateUpdate> applyActionToEgo(DataState state, int egoIndex, Action action) {
        if (action == null) {
            throw new IllegalArgumentException("candidateAction must not be null");
        }

        List<DataStateUpdate> updates = new ArrayList<>();
        int offset = vehicleOffset(egoIndex);

        int lane = intValue(state, egoIndex, VarTable.lane_index);
        double targetSpeed = value(state, egoIndex, VarTable.targetSpeed);

        switch (action) {
            case LANE_LEFT:
                updates.add(new DataStateUpdate(
                        offset + VarTable.target_lane_index.ordinal(),
                        Math.max(0, lane - 1)
                ));
                break;

            case LANE_RIGHT:
                updates.add(new DataStateUpdate(
                        offset + VarTable.target_lane_index.ordinal(),
                        Math.min(2, lane + 1)
                ));
                break;

            case FASTER:
                updates.add(new DataStateUpdate(
                        offset + VarTable.targetSpeed.ordinal(),
                        clipTargetSpeed(targetSpeed + ACTION_SPEED_DELTA)
                ));
                break;

            case SLOWER:
                updates.add(new DataStateUpdate(
                        offset + VarTable.targetSpeed.ordinal(),
                        clipTargetSpeed(targetSpeed - ACTION_SPEED_DELTA)
                ));
                break;

            case IDLE:
                break;

            default:
                throw new IllegalArgumentException("Unsupported action: " + action);
        }

        return updates;
    }
    protected double clipTargetSpeed(double targetSpeed) {
        return Math.max(0.0, Math.min(MAX_TARGET_SPEED, targetSpeed));
    }
    private DataState getInitialState() {
        Map<Integer, Double> values = new HashMap<>();

        for (int i = 0; i < observedVehicles.size(); i++) {
            Vehicle vehicle = observedVehicles.get(i);
            int offset = vehicleOffset(i);

            values.put(offset + VarTable.id.ordinal(), parseVehicleId(vehicle.id));
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
            values.put(offset + VarTable.targetSpeed.ordinal(), vehicle.targetSpeed);

            values.put(offset + VarTable.idmCooldownTimer.ordinal(), 0.0);
            values.put(offset + VarTable.idmActionStepLength.ordinal(), 0.0);
            values.put(offset + VarTable.reactionDelay.ordinal(), 0.0);
            values.put(offset + VarTable.historicalCutInIntent.ordinal(), 0.0);
            values.put(offset + VarTable.finalStabilityReference.ordinal(), 0.0);

            values.put(offset + VarTable.role.ordinal(),
                    "EGO".equals(vehicle.role) ? 0.0 : 1.0);
        }
        // the initial TTC is set to infinity
        DataState stateWithoutTtc = new DataState(
                stateSize(),
                index -> values.getOrDefault(index, Double.NaN)
        );

        int egoIndex = getEgoVehicleIndex(stateWithoutTtc);
        if (egoIndex < 0) {
            throw new IllegalStateException("No ego vehicle in DataState");
        }
        candidateTargetLane = targetLaneAfterCandidateAction(stateWithoutTtc, egoIndex, candidateAction);

        values.put(minTtcIndex(), computeMinTtc(stateWithoutTtc));

        return new DataState(
                stateSize(),
                index -> values.getOrDefault(index, Double.NaN)
        );
    }
    private double parseVehicleId(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("Vehicle id must be numeric, but was null or blank");
        }

        try {
            return Double.parseDouble(vehicleId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Vehicle id must be numeric, but was: " + vehicleId, e);
        }
    }
    private int minTtcIndex() {
        return vehicleCount() * VarTable.values().length;
    }

/**
 * Calculates the size of the state based on the minimum time to collision (TTC) index.
 * The state size is determined by adding 1 to the minimum TTC index value.
 *
 * @return The size of the state, which is minTtcIndex() + 1
 */
    private int stateSize() {
        return minTtcIndex() + 1;
    }
    private double computeMinTtc(DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            throw new IllegalStateException("No ego vehicle in DataState");
        }

        int egoLane = intValue(state, egoIndex, VarTable.lane_index);

        int targetLane = candidateTargetLane;
        if (targetLane < 0) {
            targetLane = targetLaneAfterCandidateAction(state, egoIndex, candidateAction);
        }

        if (candidateAction == Action.LANE_LEFT || candidateAction == Action.LANE_RIGHT) {
            return computeLaneChangeMinTtc(state, egoIndex, targetLane);
        }

        return computeStayLaneMinTtc(state, egoIndex, egoLane);
    }
    private double computeStayLaneMinTtc(DataState state, int egoIndex, int egoLane) {
        double minTtc = Double.POSITIVE_INFINITY;

        double egoX = value(state, egoIndex, VarTable.x);
        double egoV = longitudinalSpeed(state, egoIndex);

        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }

            int lane = intValue(state, i, VarTable.lane_index);
            int targetLane = intValue(state, i, VarTable.target_lane_index);

            if (lane != egoLane && targetLane != egoLane) {
                continue;
            }

            double otherX = value(state, i, VarTable.x);
            if (otherX <= egoX) {
                continue;
            }

            double otherV = longitudinalSpeed(state, i);
            minTtc = Math.min(minTtc, frontTtc(egoX, egoV, otherX, otherV));
        }

        return minTtc;
    }
    private double computeLaneChangeMinTtc(DataState state, int egoIndex, int targetLane) {
        double minTtc = Double.POSITIVE_INFINITY;

        double egoX = value(state, egoIndex, VarTable.x);
        double egoV = longitudinalSpeed(state, egoIndex);

        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }

            int lane = intValue(state, i, VarTable.lane_index);
            int otherTargetLane = intValue(state, i, VarTable.target_lane_index);

            boolean inTargetLane = lane == targetLane;
            boolean cuttingIntoTargetLane = lane != targetLane && otherTargetLane == targetLane;

            if (!inTargetLane && !cuttingIntoTargetLane) {
                continue;
            }

            double otherX = value(state, i, VarTable.x);
            double otherV = longitudinalSpeed(state, i);

            if (otherX > egoX) {
                minTtc = Math.min(minTtc, frontTtc(egoX, egoV, otherX, otherV));
            } else if (inTargetLane) {
                minTtc = Math.min(minTtc, rearTtc(egoX, egoV, otherX, otherV));
            }
        }

        return minTtc;
    }
    private double frontTtc(double egoX, double egoV, double frontX, double frontV) {
        double gap = frontX - egoX - 5.0;
        double closingSpeed = egoV - frontV;

        if (gap <= 0.0) {
            return 0.0;
        }
        if (closingSpeed <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return gap / closingSpeed;
    }

    private double rearTtc(double egoX, double egoV, double rearX, double rearV) {
        double gap = egoX - rearX - 5.0;
        double closingSpeed = rearV - egoV;

        if (gap <= 0.0) {
            return 0.0;
        }
        if (closingSpeed <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return gap / closingSpeed;
    }
    private double longitudinalSpeed(DataState state, int vehicleIndex) {
        double vx = value(state, vehicleIndex, VarTable.vx);
        if (Math.abs(vx) > 1e-9) {
            return vx;
        }
        return value(state, vehicleIndex, VarTable.speed);
    }
    private int targetLaneAfterCandidateAction(DataState state, int egoIndex, Action action) {
        int lane = intValue(state, egoIndex, VarTable.lane_index);

        if (action == Action.LANE_LEFT) {
            return Math.max(0, lane - 1);
        }

        if (action == Action.LANE_RIGHT) {
            return Math.min(3 - 1, lane + 1);
        }

        return lane;
    }

    private double clip(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double notZero(double value) {
        double eps = 1e-2;
        if (Math.abs(value) > eps) {
            return value;
        }
        return value >= 0.0 ? eps : -eps;
    }

    private double wrapToPi(double value) {
        return ((value + Math.PI) % (2.0 * Math.PI)) - Math.PI;
    }

    private double computeSteeringFromState(DataState state, int vehicleIndex) {
        double x = value(state, vehicleIndex, VarTable.x);
        double y = value(state, vehicleIndex, VarTable.y);
        double speed = value(state, vehicleIndex, VarTable.speed);
        double heading = value(state, vehicleIndex, VarTable.heading);
        int targetLaneIndex = intValue(state, vehicleIndex, VarTable.target_lane_index);

        double tauHeading = 0.2;
        double tauLateral = 0.6;
        double tauPursuit = 0.5 * tauHeading;
        double kpHeading = 1.0 / tauHeading;
        double kpLateral = 1.0 / tauLateral;
        double maxSteeringAngle = Math.PI / 3.0;
        double vehicleLength = 5.0;

        double laneCoordsX = x;
        double laneCoordsY = y - targetLaneIndex * 4.0;
        double laneNextCoords = laneCoordsX + speed * tauPursuit;

        // Straight highway lane.
        double laneFutureHeading = 0.0;

        double lateralSpeedCommand = -kpLateral * laneCoordsY;
        double headingCommand = Math.asin(clip(
                lateralSpeedCommand / notZero(speed),
                -1.0,
                1.0
        ));

        double headingRef = laneFutureHeading + clip(
                headingCommand,
                -Math.PI / 4.0,
                Math.PI / 4.0
        );

        double headingRateCommand = kpHeading * wrapToPi(headingRef - heading);

        double slipAngle = Math.asin(clip(
                vehicleLength / 2.0 / notZero(speed) * headingRateCommand,
                -1.0,
                1.0
        ));

        double steeringAngle = Math.atan(2.0 * Math.tan(slipAngle));

        return clip(steeringAngle, -maxSteeringAngle, maxSteeringAngle);
    }
    private List<DataStateUpdate> getEnvironmentUpdates(RandomGenerator rg, DataState state) {
        List<DataStateUpdate> updates = new ArrayList<>();
        double dt = 1.0 / frequency;

        for (int i = 0; i < vehicleCount(); i++) {
            int offset = vehicleOffset(i);
            double x = value(state, i, VarTable.x);
            double y = value(state, i, VarTable.y);
            double speed = Math.max(0.0, value(state, i, VarTable.speed));
            double heading = value(state, i, VarTable.heading);
            double plannedAcceleration = value(state, i, VarTable.plannedAcceleration);
            double cooldownTimer = value(state, i, VarTable.cooldownTimer);
            double steering = computeSteeringFromState(state, i);
            double beta = Math.atan(0.5 * Math.tan(steering));
            double vxBeforeSpeedUpdate = speed * Math.cos(heading + beta);
            double vyBeforeSpeedUpdate = speed * Math.sin(heading + beta);
            double newX = x + vxBeforeSpeedUpdate * dt;
            double newY = y + vyBeforeSpeedUpdate * dt;
            double newHeading = heading + speed * Math.sin(beta) / (vehicleLength / 2.0) * dt;

            int newLaneIndex = Math.max(
                    0,
                    Math.min(numLanes - 1, (int) Math.round(newY / laneWidth))
            );
            double newCooldownTimer = cooldownTimer + dt;
            double newSpeed = Math.max(0.0, speed + plannedAcceleration * dt);
            double newVx = newSpeed * Math.cos(newHeading);
            double newVy = newSpeed * Math.sin(newHeading);

            updates.add(new DataStateUpdate(offset + VarTable.plannedSteering.ordinal(), steering));
            updates.add(new DataStateUpdate(offset + VarTable.x.ordinal(), newX));
            updates.add(new DataStateUpdate(offset + VarTable.y.ordinal(), newY));
            updates.add(new DataStateUpdate(offset + VarTable.heading.ordinal(), newHeading));
            updates.add(new DataStateUpdate(offset + VarTable.lane_index.ordinal(), newLaneIndex));
            updates.add(new DataStateUpdate(offset + VarTable.cooldownTimer.ordinal(), newCooldownTimer));
            updates.add(new DataStateUpdate(offset + VarTable.speed.ordinal(), newSpeed));
            updates.add(new DataStateUpdate(offset + VarTable.vx.ordinal(), newVx));
            updates.add(new DataStateUpdate(offset + VarTable.vy.ordinal(), newVy));
        }
        DataState updatedStateWithoutTtc = state.apply(updates);
        updates.add(new DataStateUpdate(
                minTtcIndex(),
                computeMinTtc(updatedStateWithoutTtc)
        ));

        return updates;
    }
}
