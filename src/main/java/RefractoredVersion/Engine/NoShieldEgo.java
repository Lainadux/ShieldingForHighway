package RefractoredVersion.Engine;

public class NoShieldEgo extends EgoVehicle {
    @Override
    protected ShieldDecision verifyActionSafe(Action action) {
        return new ShieldDecision(true, "NoShieldEgo approves all AI actions.");
    }
}
