package RefractoredVersion.Engine;

/**
 * An ego vehicle that keeps its initial scalar speed and current lane.
 * It does not query the AI or invoke a shield.
 */
public class ConstantSpeedEgoVehicle extends EgoVehicle {
    @Override
    public void planAction() {
        this.plannedAcceleration = 0.0;
        this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
    }
}
