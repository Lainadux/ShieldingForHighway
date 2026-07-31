package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;
import RefractoredVersion.Engine.Vehicle;
import it.unicam.quasylab.jspear.distl.*;
import it.unicam.quasylab.jspear.ds.DataState;
import it.unicam.quasylab.jspear.ds.DataStateUpdate;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.ArrayList;
import java.util.List;

public class ExploreFutureRssShield extends ExploreFutureBetterReferenceShield{
    public ExploreFutureRssShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        super(sourceEngine, futureActions);
    }

    public ExploreFutureRssShield(JavaHighwayEngine sourceEngine, int predictionTime) {
        super(sourceEngine, predictionTime);
    }


    @Override
    public boolean verifySafe(Action candidateAction, List<DisTLFormula> moreCriteria) throws Exception {
        List<Action> actionSequence = new ArrayList<>();
        actionSequence.add(candidateAction);
        actionSequence.addAll(futureActions);
        return verifySafeSequence(actionSequence, moreCriteria);
    }

    @Override
    public boolean verifySafeSequence(List<Action> actionSequence) throws Exception {
        return verifySafeSequence(actionSequence, null);
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

    @Override
    protected DataState stabilizeEgoAgainstFrontVehicle(RandomGenerator rg, DataState state) {
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
        for (int vehicleIndex : referenceVehicles) {
            int offset = vehicleOffset(vehicleIndex);
            targetX = Math.min(targetX, state.get(offset + VarTable.x.ordinal())
                    - VEHICLE_LENGTH
                    - improvedRssRequiredGap(
                            state.get(egoOffset + VarTable.vx.ordinal()),
                            state.get(offset + VarTable.vx.ordinal()),
                            state.get(egoOffset + VarTable.plannedAcceleration.ordinal())
                    ));
        }

        return state.apply(List.of(new DataStateUpdate(egoOffset + VarTable.x.ordinal(), targetX)));
    }

    @Override
    protected double frontVehicleStabilityPenalty(DataState state) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            return 0.0;
        }

        double maxPenalty = 0.0;
        for (int frontIndex : getFinalStabilityReferenceVehicles(state, egoIndex)) {
            maxPenalty = Math.max(maxPenalty, frontVehicleStabilityPenalty(state, egoIndex, frontIndex));
        }
        return maxPenalty;
    }

    @Override
    protected double frontVehicleStabilityPenalty(DataState state, int egoIndex, int frontIndex) {
        int egoOffset = vehicleOffset(egoIndex);
        int frontOffset = vehicleOffset(frontIndex);
        double actualGap = state.get(frontOffset + VarTable.x.ordinal())
                - state.get(egoOffset + VarTable.x.ordinal())
                - VEHICLE_LENGTH;
        double requiredGap = improvedRssRequiredGap(
                state.get(egoOffset + VarTable.vx.ordinal()),
                state.get(frontOffset + VarTable.vx.ordinal()),
                state.get(egoOffset + VarTable.plannedAcceleration.ordinal())
        );

        if (actualGap >= requiredGap) {
            return 0.0;
        }

        return Math.min(1.0, Math.max(0.0, (requiredGap - actualGap) / JavaHighwayEngineUtils.not_zero(requiredGap)));
    }

    @Override
    protected String diagnoseState(DataState state, int lastStep) {
        int egoIndex = getEgoVehicleIndex(state);
        StringBuilder diagnosis = new StringBuilder();
        diagnosis.append(String.format("RSS shield diagnosis at prediction step %d: crashed=%.0f",
                lastStep, state.get(auxiliaryIndex(AuxiliarySingletonVarTable.crashed))));
        diagnosis.append(String.format("%n  DisTL robustness: collision=%.3f firstSecondSafety=%.3f rssStability=%.3f rearThreat=%.3f lowSpeedLaneChange=%.3f shield=%.3f minAcceptable=%.3f",
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
            double gap = state.get(frontOffset + VarTable.x.ordinal())
                    - state.get(egoOffset + VarTable.x.ordinal())
                    - VEHICLE_LENGTH;
            double requiredGap = improvedRssRequiredGap(
                    state.get(egoOffset + VarTable.vx.ordinal()),
                    state.get(frontOffset + VarTable.vx.ordinal()),
                    state.get(egoOffset + VarTable.plannedAcceleration.ordinal())
            );
            double violation = Math.max(0.0, requiredGap - gap);
            double penalty = frontVehicleStabilityPenalty(state, egoIndex, frontIndex);
            diagnosis.append(String.format("%n  rssReference lane=%d vehicleId=%s vehicleIndex=%d gap=%.2f requiredRssGap=%.2f violation=%.2f penalty=%.3f",
                    lane,
                    vehicleId(state, frontIndex),
                    frontIndex,
                    gap,
                    requiredGap,
                    violation,
                    penalty));
        }

        diagnosis.append(String.format("%n  totalRssStabilityPenalty=%.3f threshold=%.3f",
                frontVehicleStabilityPenalty(state), STABILITY_DISTANCE_THRESHOLD));
        return diagnosis.toString();
    }

    protected String vehicleId(DataState state, int vehicleIndex) {
        return String.valueOf((int) state.get(vehicleOffset(vehicleIndex) + VarTable.id.ordinal()));
    }

    protected double improvedRssRequiredGap(double rearSpeed, double frontSpeed, double rearAcceleration) {
        double vRear = Math.max(RssSafetyModel.MIN_SPEED, rearSpeed);
        double vFront = Math.max(RssSafetyModel.MIN_SPEED, frontSpeed);
        double rho = RssSafetyModel.NPC_RESPONSE_TIME;
        double rearBrake = RssSafetyModel.MIN_GUARANTEED_BRAKING_DECELERATION;
        double frontBrake = RssSafetyModel.MAX_BRAKING_DECELERATION;
        double term1 = vRear * rho;
        double term4 = vFront * vFront / (2.0 * frontBrake);
        double rawRequiredGap;

        if (rearAcceleration > 0.0) {
            double term2 = 0.5 * rearAcceleration * rho * rho;
            double term3 = Math.pow(vRear + rho * rearAcceleration, 2.0) / (2.0 * rearBrake);
            rawRequiredGap = term1 + term2 + term3 - term4;
        } else {
            double term3 = vRear * vRear / (2.0 * rearBrake);
            rawRequiredGap = term1 + term3 - term4;
        }

        return Math.max(0.0, rawRequiredGap);
    }
}
