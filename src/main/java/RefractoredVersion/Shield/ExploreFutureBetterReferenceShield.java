package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.vehicle.Vehicle;
import it.unicam.quasylab.jspear.ds.DataState;
import it.unicam.quasylab.jspear.ds.DataStateUpdate;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExploreFutureBetterReferenceShield extends ExploreFutureActionShield{
    public ExploreFutureBetterReferenceShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        super(sourceEngine, futureActions);
    }

    public ExploreFutureBetterReferenceShield(JavaHighwayEngine sourceEngine, int predictionTime) {
        super(sourceEngine, predictionTime);
    }

    @Override
    protected void populateSubclassVehicleValues(Map<Integer, Double> values,
                                                 List<Vehicle> vehiclesAtStep,
                                                 boolean[] historicalCutInIntent,
                                                 Action initialIntention) {
        markFinalStabilityReferenceVehicles(values);
    }

    protected void markFinalStabilityReferenceVehicles(Map<Integer, Double> values) {
        int egoIndex = getEgoVehicleIndexFromValues(values);
        if (egoIndex < 0) {
            return;
        }

        int egoOffset = vehicleOffset(egoIndex);
        int egoLane = values.get(egoOffset + VarTable.lane_index.ordinal()).intValue();
        double egoX = values.get(egoOffset + VarTable.x.ordinal());

        int frontIndex = getFrontVehicleIndexInLaneFromValues(values, egoIndex, egoLane);
        if (frontIndex >= 0) {
            values.put(vehicleOffset(frontIndex) + VarTable.finalStabilityReference.ordinal(), 1.0);
        }

        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            int lane = values.get(offset + VarTable.lane_index.ordinal()).intValue();
            double x = values.get(offset + VarTable.x.ordinal());
            if (lane != egoLane
                    && x > egoX
                    && hasHistoricalCutInIntentTowardEgoLane(values, i, egoLane)) {
                values.put(offset + VarTable.finalStabilityReference.ordinal(), 1.0);
            }
        }
    }

    @Override
    protected List<Integer> getFinalStabilityReferenceVehicles(DataState state, int egoIndex) {
        List<Integer> referenceVehicles = new ArrayList<>();
        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }
            if (state.get(vehicleOffset(i) + VarTable.finalStabilityReference.ordinal()) > 0.0) {
                referenceVehicles.add(i);
            }
        }

        return referenceVehicles;
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
        double gap = state.get(frontOffset + VarTable.x.ordinal())
                - state.get(egoOffset + VarTable.x.ordinal())
                - VEHICLE_LENGTH;
        double closingSpeed = state.get(egoOffset + VarTable.vx.ordinal())
                - state.get(frontOffset + VarTable.vx.ordinal());

        if (closingSpeed <= 0.0) {
            return 0.0;
        }

        double ttc = gap / closingSpeed;
        double threshold = aggressiveV3TtcThreshold();
        if (ttc > threshold) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, (threshold - ttc) / threshold));
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

        double targetVx = Double.POSITIVE_INFINITY;
        for (int vehicleIndex : referenceVehicles) {
            int offset = vehicleOffset(vehicleIndex);
            targetVx = Math.min(targetVx, state.get(offset + VarTable.vx.ordinal()));
        }

        int egoOffset = vehicleOffset(egoIndex);
        return state.apply(List.of(new DataStateUpdate(egoOffset + VarTable.vx.ordinal(), targetVx)));
    }

    protected int getEgoVehicleIndexFromValues(Map<Integer, Double> values) {
        for (int i = 0; i < vehicleCount(); i++) {
            Double role = values.get(vehicleOffset(i) + VarTable.role.ordinal());
            if (role != null && role == 0.0) {
                return i;
            }
        }
        return -1;
    }

    protected int getFrontVehicleIndexInLaneFromValues(Map<Integer, Double> values, int egoIndex, int targetLane) {
        int egoOffset = vehicleOffset(egoIndex);
        double egoX = values.get(egoOffset + VarTable.x.ordinal());
        int frontIndex = -1;
        double closestFrontX = Double.POSITIVE_INFINITY;
        for (int i = 0; i < vehicleCount(); i++) {
            if (i == egoIndex) {
                continue;
            }
            int offset = vehicleOffset(i);
            int lane = values.get(offset + VarTable.lane_index.ordinal()).intValue();
            double x = values.get(offset + VarTable.x.ordinal());
            if (lane == targetLane && x > egoX && x < closestFrontX) {
                frontIndex = i;
                closestFrontX = x;
            }
        }
        return frontIndex;
    }

    protected boolean hasHistoricalCutInIntentTowardEgoLane(Map<Integer, Double> values, int vehicleIndex, int egoLane) {
        int offset = vehicleOffset(vehicleIndex);
        double historicalCutInIntent = values.getOrDefault(
                offset + VarTable.historicalCutInIntent.ordinal(),
                Double.NaN
        );
        if (!Double.isNaN(historicalCutInIntent)) {
            return historicalCutInIntent > 0.0;
        }
        int targetLane = values.get(offset + VarTable.target_lane_index.ordinal()).intValue();
        return targetLane == egoLane;
    }
}
