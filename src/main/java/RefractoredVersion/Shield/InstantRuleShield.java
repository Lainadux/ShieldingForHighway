package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.EgoVehicle;
import RefractoredVersion.Engine.Vehicle;

public class InstantRuleShield {
    private final EgoVehicle ego;
    private String unsafeDiagnosis = "";

    public InstantRuleShield(EgoVehicle ego) {
        this.ego = ego;
    }

    public boolean verifySafe(Action action) {
        unsafeDiagnosis = "";
        switch (action) {
            case LANE_LEFT:
            case LANE_RIGHT:
                return true;

            case  IDLE:
            case FASTER:
                return isStayInLaneSafe();
            case SLOWER:
                return true;
        }
        return true;
    }

    public String getUnsafeDiagnosis() {
        return unsafeDiagnosis;
    }

    boolean isStayInLaneSafe(){
        int egoLane = ego.getLaneIndex();
        Vehicle currentLaneFront = closestFrontVehicleInLane(egoLane);
        Vehicle cutInFront = closestFrontCutInVehicleTowardLane(egoLane);

        boolean currentLaneSafe = isRssSafeAgainstFront(currentLaneFront, "current lane front");
        boolean cutInSafe = isRssSafeAgainstFront(cutInFront, "cut-in front");
        return currentLaneSafe && cutInSafe;
    }

    private Vehicle closestFrontVehicleInLane(int lane) {
        Vehicle closest = null;
        for (Vehicle vehicle : ego.getDetectedVehicles()) {
            if (vehicle == ego) {
                continue;
            }
            if (vehicle.getLaneIndex() == lane && vehicle.x > ego.x
                    && (closest == null || vehicle.x < closest.x)) {
                closest = vehicle;
            }
        }
        return closest;
    }

    private Vehicle closestFrontCutInVehicleTowardLane(int lane) {
        Vehicle closest = null;
        for (Vehicle vehicle : ego.getDetectedVehicles()) {
            if (vehicle == ego) {
                continue;
            }
            if (vehicle.getLaneIndex() != lane
                    && vehicle.getTargetLaneIndex() == lane
                    && vehicle.x > ego.x
                    && (closest == null || vehicle.x < closest.x)) {
                closest = vehicle;
            }
        }
        return closest;
    }

    private boolean isRssSafeAgainstFront(Vehicle front, String label) {
        if (front == null) {
            return true;
        }

        double actualGap = front.x - ego.x - RssSafetyModel.VEHICLE_LENGTH;
        double requiredGap = RssSafetyModel.requiredLongitudinalGap(
                longitudinalSpeed(ego),
                longitudinalSpeed(front)
        );
        boolean safe = actualGap >= requiredGap;
        if (!safe) {
            unsafeDiagnosis += String.format(
                    "%s RSS unsafe: frontVehicleId=%s gap=%.2f requiredGap=%.2f egoSpeed=%.2f frontSpeed=%.2f%n",
                    label,
                    front.id,
                    actualGap,
                    requiredGap,
                    longitudinalSpeed(ego),
                    longitudinalSpeed(front)
            );
        }
        return safe;
    }

    private double longitudinalSpeed(Vehicle vehicle) {
        return vehicle.vx != 0.0 ? vehicle.vx : vehicle.speed;
    }
}
