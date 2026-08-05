package RefractoredVersion.Engine;

import RefractoredVersion.Shield.RssSafetyModel;

import java.util.ArrayList;
import java.util.List;

public class RssStrictEgoVehicle extends EgoVehicle {
    private static final double EGO_REAR_RESPONSE_TIME = 1.0;
    private static final double TARGET_LANE_REAR_RESPONSE_TIME = 0.2;

    @Override
    protected ShieldDecision verifyActionSafe(Action action) {
        RssStrictDecision decision = switch (action) {
            case LANE_LEFT, LANE_RIGHT -> verifyLaneChange(action);
            case IDLE, FASTER, SLOWER -> verifyStayInLane();
        };
        return new ShieldDecision(decision.safe, decision.diagnosis);
    }

    private RssStrictDecision verifyStayInLane() {
        StringBuilder diagnosis = new StringBuilder("RSS strict stay-in-lane check");
        boolean safe = true;
        for (int lane : adjacentLaneIndicesIncludingCurrent()) {
            Vehicle front = closestFrontVehicleInLane(lane);
            if (front == null) {
                diagnosis.append(String.format("%n  lane=%d front=none", lane));
                continue;
            }
            RssCheck check = rssCheck(this, front, EGO_REAR_RESPONSE_TIME);
            diagnosis.append(String.format(
                    "%n  lane=%d frontVehicleId=%s gap=%.2f requiredGap=%.2f rho=%.2f egoSpeed=%.2f frontSpeed=%.2f safe=%s",
                    lane,
                    front.id,
                    check.actualGap,
                    check.requiredGap,
                    EGO_REAR_RESPONSE_TIME,
                    longitudinalSpeed(this),
                    longitudinalSpeed(front),
                    check.safe
            ));
            safe = safe && check.safe;
        }
        return new RssStrictDecision(safe, diagnosis.toString());
    }

    private RssStrictDecision verifyLaneChange(Action action) {
        int targetLane = targetLaneAfter(action);
        if (targetLane == getLaneIndex()) {
            return verifyStayInLane();
        }

        StringBuilder diagnosis = new StringBuilder(String.format(
                "RSS strict lane-change check: action=%s targetLane=%d",
                action,
                targetLane
        ));
        Vehicle rear = closestRearVehicleInLane(targetLane);
        if (rear == null) {
            diagnosis.append(String.format("%n  target lane rear=none"));
            return new RssStrictDecision(true, diagnosis.toString());
        }

        RssCheck check = rssCheck(rear, this, TARGET_LANE_REAR_RESPONSE_TIME);
        diagnosis.append(String.format(
                "%n  rearVehicleId=%s gap=%.2f requiredGap=%.2f rho=%.2f rearSpeed=%.2f egoSpeed=%.2f safe=%s",
                rear.id,
                check.actualGap,
                check.requiredGap,
                TARGET_LANE_REAR_RESPONSE_TIME,
                longitudinalSpeed(rear),
                longitudinalSpeed(this),
                check.safe
        ));
        return new RssStrictDecision(check.safe, diagnosis.toString());
    }

    protected List<Integer> adjacentLaneIndicesIncludingCurrent() {
        List<Integer> lanes = new ArrayList<>();
        int currentLane = getLaneIndex();
        for (int lane = currentLane - 1; lane <= currentLane + 1; lane++) {
            if (lane >= 0 && lane < getEngine().numLanes) {
                lanes.add(lane);
            }
        }
        return lanes;
    }

    private int targetLaneAfter(Action action) {
        return switch (action) {
            case LANE_LEFT -> Math.max(0, getLaneIndex() - 1);
            case LANE_RIGHT -> Math.min(getEngine().numLanes - 1, getLaneIndex() + 1);
            default -> getLaneIndex();
        };
    }

    private Vehicle closestFrontVehicleInLane(int lane) {
        Vehicle closest = null;
        for (Vehicle vehicle : getDetectedVehicles()) {
            if (vehicle == this) {
                continue;
            }
            if (vehicle.getLaneIndex() == lane
                    && vehicle.x > this.x
                    && (closest == null || vehicle.x < closest.x)) {
                closest = vehicle;
            }
        }
        return closest;
    }

    private Vehicle closestRearVehicleInLane(int lane) {
        Vehicle closest = null;
        for (Vehicle vehicle : getDetectedVehicles()) {
            if (vehicle == this) {
                continue;
            }
            if (vehicle.getLaneIndex() == lane
                    && vehicle.x < this.x
                    && (closest == null || vehicle.x > closest.x)) {
                closest = vehicle;
            }
        }
        return closest;
    }

    private RssCheck rssCheck(Vehicle rear, Vehicle front, double rho) {
        double actualGap = front.x - rear.x - RssSafetyModel.VEHICLE_LENGTH;
        double requiredGap = RssSafetyModel.requiredLongitudinalGap(
                longitudinalSpeed(rear),
                longitudinalSpeed(front),
                rho
        );
        return new RssCheck(actualGap, requiredGap, actualGap >= requiredGap);
    }

    private double longitudinalSpeed(Vehicle vehicle) {
        return vehicle.vx != 0.0 ? vehicle.vx : vehicle.speed;
    }

    private record RssStrictDecision(boolean safe, String diagnosis) {
    }

    private record RssCheck(double actualGap, double requiredGap, boolean safe) {
    }
}
