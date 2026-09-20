package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;
import RefractoredVersion.Engine.vehicle.NonNpcVehicle;
import RefractoredVersion.Engine.vehicle.Vehicle;
import it.unicam.quasylab.jspear.distl.AlwaysDisTLFormula;
import it.unicam.quasylab.jspear.distl.ConjunctionDisTLFormula;
import it.unicam.quasylab.jspear.distl.DisTLFormula;
import it.unicam.quasylab.jspear.distl.DoubleSemanticsVisitor;
import it.unicam.quasylab.jspear.distl.TargetDisTLFormula;
import it.unicam.quasylab.jspear.ds.DataState;
import it.unicam.quasylab.jspear.ds.DataStateUpdate;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * Implements the recoverability-based shield used for periodic 4 Hz
 * intervention.
 *
 * <p>The AI continues to produce a new high-level action once per second,
 * while the shield may evaluate the current driving intention every
 * 0.25 seconds. Internally, each action in the candidate-and-recovery
 * sequence produces one prediction transition of 0.25 seconds.</p>
 *
 * <p>During prediction, NPC vehicles retain their initial target lanes.
 * Their longitudinal accelerations are sampled uniformly from
 * [-1, 1] m/s^2 at every prediction step, while the low-level kinematic
 * model propagates all vehicle states.</p>
 *
 * <p>The predicted evolution is evaluated using the recoverability-based
 * DisTL requirements inherited from the Reco shield. These requirements
 * check collision avoidance throughout the prediction horizon, front
 * safety after the candidate action, recoverability at the final state,
 * and the additional lane-change safety conditions. The evaluated action
 * is accepted only when the conjunction has sufficient robustness.</p>
 */
public class Reco4HzShield extends StarkNativeShield {
    private static final int PREDICTION_FREQUENCY_HZ = 4;
    private static final double NPC_ACCELERATION_MIN = -1.0;
    private static final double NPC_ACCELERATION_MAX = 1.0;
    private static final int FIRST_ACTION_END_STEP = 1;
    private static final LongAdder TOTAL_EVALUATION_NANOS = new LongAdder();
    private static final LongAdder EVALUATION_COUNT = new LongAdder();

    private long lastEvaluationNanos;

    public Reco4HzShield(JavaHighwayEngine sourceEngine) {
        super(sourceEngine);
    }

    @Override
    protected JavaHighwayEngine nativeEngineFor(DataState state, List<Vehicle> vehicles) {
        JavaHighwayEngine predictionEngine = new JavaHighwayEngine();
        predictionEngine.setFrequency(PREDICTION_FREQUENCY_HZ);
        predictionEngine.config = sourceEngine.config;
        predictionEngine.numLanes = sourceEngine.numLanes;
        predictionEngine.stepsTaken = state.getStep();
        predictionEngine.timeElapsed = state.getStep() * predictionEngine.getDt();
        predictionEngine.vehicles = vehicles;
        for (Vehicle vehicle : vehicles) {
            vehicle.setEngine(predictionEngine);
        }
        return predictionEngine;
    }

    @Override
    public boolean verifySafeSequence(List<Action> actionSequence, List<DisTLFormula> moreCriteria) throws Exception {
        long startNanos = System.nanoTime();
        try {
            return verifySafeSequenceUntimed(actionSequence, moreCriteria);
        } finally {
            lastEvaluationNanos = System.nanoTime() - startNanos;
            TOTAL_EVALUATION_NANOS.add(lastEvaluationNanos);
            EVALUATION_COUNT.increment();
        }
    }

    private boolean verifySafeSequenceUntimed(List<Action> actionSequence,
                                              List<DisTLFormula> moreCriteria) throws Exception {
        validateActionSequence(actionSequence);
        createSandboxEngine(actionSequence);
        sequence = getNativePredictionSequence(actionSequence);
        int lastStep = lastNativePredictionStep(actionSequence);
        sequence.generateUpTo(lastStep);

        DisTLFormula noCollision = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::resetCrashState, this::crashPenalty, CRASH_DISTANCE_THRESHOLD),
                0,
                lastStep
        );
        DisTLFormula safeAfterCandidateAction = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeEgoAtFrontSafetyDistance,
                        this::firstSecondFrontSafetyPenalty,
                        FIRST_SECOND_SAFETY_DISTANCE_THRESHOLD),
                FIRST_ACTION_END_STEP,
                FIRST_ACTION_END_STEP
        );
        DisTLFormula stableAtLastStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeEgoAgainstFrontVehicle,
                        this::frontVehicleStabilityPenalty,
                        STABILITY_DISTANCE_THRESHOLD),
                lastStep,
                lastStep
        );
        DisTLFormula changeLaneRearThreatAtDecisionStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeChangeLaneRearThreat,
                        this::changeLaneRearThreatPenalty,
                        CHANGE_LANE_REAR_THREAT_DISTANCE_THRESHOLD),
                0,
                0
        );
        DisTLFormula changeLaneLowSpeedAtDecisionStep = new AlwaysDisTLFormula(
                new TargetDisTLFormula(this::stabilizeChangeLaneLowSpeed,
                        this::changeLaneLowSpeedPenalty,
                        CHANGE_LANE_LOW_SPEED_DISTANCE_THRESHOLD),
                0,
                0
        );

        DisTLFormula shieldCondition = new ConjunctionDisTLFormula(
                noCollision,
                new ConjunctionDisTLFormula(safeAfterCandidateAction, stableAtLastStep)
        );
        shieldCondition = new ConjunctionDisTLFormula(
                shieldCondition,
                new ConjunctionDisTLFormula(
                        changeLaneRearThreatAtDecisionStep,
                        changeLaneLowSpeedAtDecisionStep
                )
        );

        if (moreCriteria != null) {
            for (DisTLFormula criterion : moreCriteria) {
                shieldCondition = new ConjunctionDisTLFormula(shieldCondition, criterion);
            }
        }

        DoubleSemanticsVisitor semantics = new DoubleSemanticsVisitor();
        lastCollisionRobustness = semantics.eval(noCollision).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastFirstSecondSafetyRobustness = semantics.eval(safeAfterCandidateAction)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastStabilityRobustness = semantics.eval(stableAtLastStep)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastChangeLaneRearThreatRobustness = semantics.eval(changeLaneRearThreatAtDecisionStep)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastChangeLaneLowSpeedRobustness = semantics.eval(changeLaneLowSpeedAtDecisionStep)
                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastShieldRobustness = semantics.eval(shieldCondition).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);

        return lastShieldRobustness >= MIN_ACCEPTABLE_ROBUSTNESS;
    }

    public double getLastEvaluationMillis() {
        return lastEvaluationNanos / 1_000_000.0;
    }

    public static long getEvaluationCount() {
        return EVALUATION_COUNT.sum();
    }

    public static double getMeanEvaluationMillis() {
        long count = EVALUATION_COUNT.sum();
        if (count == 0L) {
            return 0.0;
        }
        return TOTAL_EVALUATION_NANOS.sum() / 1_000_000.0 / count;
    }

    public static void resetEvaluationTimingStatistics() {
        TOTAL_EVALUATION_NANOS.reset();
        EVALUATION_COUNT.reset();
    }

    @Override
    public String getUnsafeDiagnosis() {
        return super.getUnsafeDiagnosis()
                + String.format("%n  evaluationTimeMs=%.3f", getLastEvaluationMillis());
    }

    @Override
    protected int lastNativePredictionStep(List<Action> actionSequence) {
        // S_0 is the initial state; each action produces one 0.25-second transition.
        return actionSequence.size();
    }

    @Override
    protected List<DataStateUpdate> getControllerUpdates(RandomGenerator rg, DataState state,
                                                         List<Action> actionSequence) {
        List<Vehicle> vehicles = vehiclesFromState(state);
        nativeEngineFor(state, vehicles);
        Vehicle ego = egoVehicle(vehicles);
        if (ego == null) {
            throw new IllegalStateException("No ego vehicle in native prediction state.");
        }

        int actionIndex = state.getStep();
        if (actionIndex >= actionSequence.size()) {
            throw new IllegalStateException(String.format(
                    "Missing 4 Hz shield action at decision index %d, actionSequence size is %d",
                    actionIndex,
                    actionSequence.size()
            ));
        }
        applyAction(ego, actionSequence.get(actionIndex));

        try {
            for (Vehicle vehicle : vehicles) {
                if (vehicle instanceof NonNpcVehicle) {
                    vehicle.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(vehicle, vehicles);
                } else {
                    vehicle.plannedAcceleration = NPC_ACCELERATION_MIN
                            + (NPC_ACCELERATION_MAX - NPC_ACCELERATION_MIN) * rg.nextDouble();
                    // Deliberately retain the target lane throughout the 0.75-second horizon.
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply the 4 Hz prediction controller", e);
        }

        return updatesForVehicles(vehicles, state);
    }
}
