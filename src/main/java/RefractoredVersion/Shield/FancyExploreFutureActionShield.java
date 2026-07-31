//package RefractoredVersion.Shield;
//
//import RefractoredVersion.Engine.Action;
//import RefractoredVersion.Engine.JavaHighwayEngine;
//import RefractoredVersion.Engine.Vehicle;
//import it.unicam.quasylab.jspear.distl.*;
//import it.unicam.quasylab.jspear.ds.DataState;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class FancyExploreFutureActionShield extends ExploreFutureActionShield{
//
//    private static final double SAFE_TTC_SENTINEL = 1_000_000.0;
//
//    public FancyExploreFutureActionShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
//        super(sourceEngine, futureActions);
//    }
//
//    public FancyExploreFutureActionShield(JavaHighwayEngine sourceEngine, int predictionTime) {
//        super(sourceEngine, predictionTime);
//    }
//    @Override
//    public boolean verifySafeSequence(List<Action> actionSequence) throws Exception {
//
//        List<List<List<Vehicle>>> sampleTraces = predictCandidateSampleTraces(actionSequence);
//        sequence = new FixedEvolutionSequence(toSampleSetsFromSampleTraces(sampleTraces, actionSequence.get(0)));
//
//        int lastStep = lastPredictionStep(sampleTraces);
//        int firstSecondLastStep = Math.min(sourceEngine.getFrequency() - 1, lastStep);
//        DisTLFormula noCollision = new AlwaysDisTLFormula(
//                new TargetDisTLFormula(this::resetCrashState, this::crashPenalty, CRASH_DISTANCE_THRESHOLD),
//                0,
//                lastStep
//        );
////        DisTLFormula safeFrontDistanceAtFirstSecond = new AlwaysDisTLFormula(
////                new TargetDisTLFormula(this::stabilizeEgoAtFrontSafetyDistance, this::firstSecondFrontSafetyPenalty,
////                        FIRST_SECOND_SAFETY_DISTANCE_THRESHOLD),
////                firstSecondLastStep,
////                firstSecondLastStep
////        );
//        DisTLFormula safeFrontDistanceAtFirstSecond = new AlwaysDisTLFormula(
//                new TargetDisTLFormula(this::stabilizeEgoAtFrontSafetyDistance, this::firstSecondFrontSafetyPenalty,
//                        FIRST_SECOND_SAFETY_DISTANCE_THRESHOLD),
//                firstSecondLastStep,
//                firstSecondLastStep
//        );
//        DisTLFormula stableAtLastStep = new AlwaysDisTLFormula(
//                new TargetDisTLFormula(this::stabilizeEgoAgainstFrontVehicle, this::frontVehicleStabilityPenalty,
//                        STABILITY_DISTANCE_THRESHOLD),
//                lastStep,
//                lastStep
//        );
//        DisTLFormula changeLaneRearThreatAtDecisionStep = new AlwaysDisTLFormula(
//                new TargetDisTLFormula(this::stabilizeChangeLaneRearThreat, this::changeLaneRearThreatPenalty,
//                        CHANGE_LANE_REAR_THREAT_DISTANCE_THRESHOLD),
//                0,
//                0
//        );
//        DisTLFormula changeLaneLowSpeedAtDecisionStep = new AlwaysDisTLFormula(
//                new TargetDisTLFormula(this::stabilizeChangeLaneLowSpeed, this::changeLaneLowSpeedPenalty,
//                        CHANGE_LANE_LOW_SPEED_DISTANCE_THRESHOLD),
//                0,
//                0
//        );
//        DisTLFormula shieldCondition = new ConjunctionDisTLFormula(
//                noCollision,
//                new ConjunctionDisTLFormula(safeFrontDistanceAtFirstSecond, stableAtLastStep)
//        );
//        DisTLFormula rearThreatCondition = new ConjunctionDisTLFormula(
//                changeLaneRearThreatAtDecisionStep,
//                changeLaneLowSpeedAtDecisionStep
//        );
//        shieldCondition = new ConjunctionDisTLFormula(shieldCondition, rearThreatCondition);
//
//        DoubleSemanticsVisitor semantics = new DoubleSemanticsVisitor();
//        lastCollisionRobustness = semantics.eval(noCollision).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
//        lastFirstSecondSafetyRobustness = semantics.eval(safeFrontDistanceAtFirstSecond)
//                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
//        lastStabilityRobustness = semantics.eval(stableAtLastStep).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
//        lastChangeLaneRearThreatRobustness = semantics.eval(changeLaneRearThreatAtDecisionStep)
//                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
//        lastChangeLaneLowSpeedRobustness = semantics.eval(changeLaneLowSpeedAtDecisionStep)
//                .eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
//        lastShieldRobustness = semantics.eval(shieldCondition).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
//
//        return lastShieldRobustness >= MIN_ACCEPTABLE_ROBUSTNESS;
//
//    }
//    @Override
//    protected DataState toDataState(List<Vehicle> vehiclesAtStep, boolean[] historicalCutInIntent,
//                                  Action initialIntention) {
//        Map<Integer, Double> values = new HashMap<>();
//        int vehicleCount = vehicleCount();
//
//        for (int i = 0; i < vehicleCount; i++) {
//            Vehicle vehicle = i < vehiclesAtStep.size() ? vehiclesAtStep.get(i) : sourceEngine.vehicles.get(i);
//            int offset = vehicleOffset(i);
//            values.put(offset + VarTable.id.ordinal(), parseVehicleId(vehicle.id, i));
//            values.put(offset + VarTable.politeness.ordinal(), vehicle.politeness);
//            values.put(offset + VarTable.cooldownTimer.ordinal(), vehicle.cooldownTimer);
//            values.put(offset + VarTable.target_lane_index.ordinal(), (double) vehicle.getTargetLaneIndex());
//            values.put(offset + VarTable.lane_index.ordinal(), (double) vehicle.getLaneIndex());
//            values.put(offset + VarTable.x.ordinal(), vehicle.x);
//            values.put(offset + VarTable.y.ordinal(), vehicle.y);
//            values.put(offset + VarTable.vx.ordinal(), vehicle.vx);
//            values.put(offset + VarTable.vy.ordinal(), vehicle.vy);
//            values.put(offset + VarTable.speed.ordinal(), vehicle.speed);
//            values.put(offset + VarTable.heading.ordinal(), vehicle.heading);
//            values.put(offset + VarTable.plannedAcceleration.ordinal(), vehicle.plannedAcceleration);
//            values.put(offset + VarTable.plannedSteering.ordinal(), vehicle.plannedSteering);
//            values.put(offset + VarTable.role.ordinal(), "EGO".equals(vehicle.role) ? 0.0 : 1.0);
//            values.put(offset + VarTable.targetSpeed.ordinal(), vehicle.targetSpeed);
//            values.put(offset + VarTable.idmCooldownTimer.ordinal(), 0.0);
//            values.put(offset + VarTable.idmActionStepLength.ordinal(), 0.0);
//            values.put(offset + VarTable.reactionDelay.ordinal(), 0.0);
//            values.put(offset + VarTable.historicalCutInIntent.ordinal(),
//                    historicalCutInIntent != null
//                            && i < historicalCutInIntent.length
//                            && historicalCutInIntent[i] ? 1.0 : 0.0);
//        }
//
//        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.crashed),
//                hasCollision(vehiclesAtStep) ? 1.0 : 0.0);
//        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInCurrentLane), -1.0);
//        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInLeftLane), -1.0);
//        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.finalFrontVehicleIndexInRightLane), -1.0);
//        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.isInitialChangeLane),
//                isCandidateChangeLane(vehiclesAtStep, initialIntention) ? 1.0 : 0.0);
//        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.initialIntention),
//                initialIntention == null ? -1.0 : initialIntention.getValue());
//        populateRearThreatAuxiliaryValues(values, vehiclesAtStep);
//        populateEndStateTTCValues(values, vehiclesAtStep);
//
//        return new DataState(AuxiliarySingletonVarTable.stateSize(vehicleCount()),
//                index -> values.getOrDefault(index, Double.NaN));
//    }
//
//    protected void populateEndStateTTCValues(Map<Integer, Double> values, List<Vehicle> vehiclesAtStep) {
//        int egoIndex = getEgoVehicleIndexFromValues(values);
//        if (egoIndex < 0) {
//            throw new IllegalStateException("Ego vehicle not found in values.");
//
//        }
//
//        int referenceIndex = getFinalStabilityReferenceVehicleIndexFromValues(values, egoIndex);
//        if (referenceIndex < 0) {
//            values.put(auxiliaryIndex(AuxiliarySingletonVarTable.endStateTTC), SAFE_TTC_SENTINEL);
//            return;
//        }
//
//        int egoOffset = vehicleOffset(egoIndex);
//        int referenceOffset = vehicleOffset(referenceIndex);
//        double gap = values.get(referenceOffset + VarTable.x.ordinal())
//                - values.get(egoOffset + VarTable.x.ordinal())
//                - VEHICLE_LENGTH;
//        double closingSpeed = values.get(egoOffset + VarTable.vx.ordinal())
//                - values.get(referenceOffset + VarTable.vx.ordinal());
//
//        values.put(auxiliaryIndex(AuxiliarySingletonVarTable.endStateTTC), computeTTC(gap, closingSpeed));
//    }
//
//    private int getFinalStabilityReferenceVehicleIndexFromValues(Map<Integer, Double> values, int egoIndex) {
//        int egoOffset = vehicleOffset(egoIndex);
//        int egoLane = getIntValue(values, egoOffset + VarTable.lane_index.ordinal(), -1);
//        double egoX = values.get(egoOffset + VarTable.x.ordinal());
//        if (egoLane < 0) {
//            return -1;
//        }
//
//        int closestReferenceIndex = getFrontVehicleIndexInLaneFromValues(values, egoIndex, egoLane);
//        double closestReferenceX = closestReferenceIndex < 0
//                ? Double.POSITIVE_INFINITY
//                : values.get(vehicleOffset(closestReferenceIndex) + VarTable.x.ordinal());
//
//        for (int i = 0; i < vehicleCount(); i++) {
//            if (i == egoIndex) {
//                continue;
//            }
//            int offset = vehicleOffset(i);
//            int lane = getIntValue(values, offset + VarTable.lane_index.ordinal(), -1);
//            double x = values.get(offset + VarTable.x.ordinal());
//            if (lane != egoLane
//                    && x > egoX
//                    && x < closestReferenceX
//                    && hasHistoricalCutInIntentTowardEgoLane(values, i, egoLane)) {
//                closestReferenceIndex = i;
//                closestReferenceX = x;
//            }
//        }
//
//        return closestReferenceIndex;
//    }
//
//    private int getFrontVehicleIndexInLaneFromValues(Map<Integer, Double> values, int egoIndex, int lane) {
//        int egoOffset = vehicleOffset(egoIndex);
//        double egoX = values.get(egoOffset + VarTable.x.ordinal());
//        int frontIndex = -1;
//        double closestFrontX = Double.POSITIVE_INFINITY;
//        for (int i = 0; i < vehicleCount(); i++) {
//            if (i == egoIndex) {
//                continue;
//            }
//            int offset = vehicleOffset(i);
//            int vehicleLane = getIntValue(values, offset + VarTable.lane_index.ordinal(), -1);
//            double x = values.get(offset + VarTable.x.ordinal());
//            if (vehicleLane == lane && x > egoX && x < closestFrontX) {
//                frontIndex = i;
//                closestFrontX = x;
//            }
//        }
//        return frontIndex;
//    }
//
//    private boolean hasHistoricalCutInIntentTowardEgoLane(Map<Integer, Double> values, int vehicleIndex, int egoLane) {
//        int offset = vehicleOffset(vehicleIndex);
//        double historicalCutInIntent = values.getOrDefault(
//                offset + VarTable.historicalCutInIntent.ordinal(),
//                Double.NaN
//        );
//        if (!Double.isNaN(historicalCutInIntent)) {
//            return historicalCutInIntent > 0.0;
//        }
//        int targetLane = getIntValue(values, offset + VarTable.target_lane_index.ordinal(), -1);
//        return targetLane == egoLane;
//    }
//
//    private int getEgoVehicleIndexFromValues(Map<Integer, Double> values) {
//        for (int i = 0; i < vehicleCount(); i++) {
//            Double role = values.get(vehicleOffset(i) + VarTable.role.ordinal());
//            if (role != null && role == 0.0) {
//                return i;
//            }
//        }
//        return -1;
//    }
//
//    private double computeTTC(double gap, double closingSpeed) {
//        if (gap <= 0.0) {
//            return 0.0;
//        }
//        if (closingSpeed <= 0.0) {
//            return SAFE_TTC_SENTINEL;
//        }
//        return gap / closingSpeed;
//    }
//
//    private int getIntValue(Map<Integer, Double> values, int index, int fallback) {
//        Double value = values.get(index);
//        if (value == null || Double.isNaN(value)) {
//            return fallback;
//        }
//        return (int) Math.round(value);
//    }
//}
