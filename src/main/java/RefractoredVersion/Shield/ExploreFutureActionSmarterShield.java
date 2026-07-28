package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;
import RefractoredVersion.Engine.NonNpcVehicle;
import RefractoredVersion.Engine.Vehicle;

import java.util.ArrayList;
import java.util.List;

public class ExploreFutureActionSmarterShield extends ExploreFutureActionShield {
    public ExploreFutureActionSmarterShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        super(sourceEngine, futureActions);
    }

    public ExploreFutureActionSmarterShield(JavaHighwayEngine sourceEngine, int predictionTime) {
        super(sourceEngine, predictionTime);
    }

    @Override
    protected List<Vehicle> deepCopyVehicles(List<Vehicle> vehicles, List<Action> actionSequence) {
        List<Vehicle> copies = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            Vehicle copy = vehicle instanceof NonNpcVehicle
                    ? new ShieldNonNpcVehicle(actionSequence)
                    : new ChangeLaneUnsureNpcVehicle();
            copyVehicleState(vehicle, copy);
            copies.add(copy);
        }
        return copies;
    }

    private static class ChangeLaneUnsureNpcVehicle extends Vehicle {
        @Override
        public void planAction(List<Vehicle> allVehicles) throws Exception {
            int beforeChangeLane = this.getLaneIndex();
            int computedTargetLane = JavaHighwayEngineUtils.computeTargetLane(
                    this,
                    allVehicles,
                    List.of(0, 1),
                    this.getEngine()
            );
            this.setTargetLaneIndex(computedTargetLane);

            if (isTheSecondVehicleInCurrentLane(allVehicles)
                    && computedTargetLane != beforeChangeLane
                    && JavaHighwayEngineUtils.getFrontVehicle(this, allVehicles, computedTargetLane) == null
                    && Math.random() < 0.5) {
                this.setTargetLaneIndex(beforeChangeLane);
            }

            this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, allVehicles);
            this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
        }

        public boolean isTheSecondVehicleInCurrentLane(List<Vehicle> allVehicles) {
            int vehiclesAheadInCurrentLane = 0;
            int currentLane = this.getLaneIndex();
            for (Vehicle other : allVehicles) {
                if (other == this) {
                    continue;
                }
                if (other.getLaneIndex() == currentLane && other.x > this.x) {
                    vehiclesAheadInCurrentLane++;
                }
            }
            return vehiclesAheadInCurrentLane == 1;
        }
    }
}
