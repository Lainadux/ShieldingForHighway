package RefractoredVersion.Engine;

public record CollisionLog(
        int step,
        double time,
        String firstVehicleId,
        String firstVehicleRole,
        double firstX,
        double firstY,
        int firstLane,
        double firstSpeed,
        double firstVx,
        double firstVy,
        String secondVehicleId,
        String secondVehicleRole,
        double secondX,
        double secondY,
        int secondLane,
        double secondSpeed,
        double secondVx,
        double secondVy
) {
}
