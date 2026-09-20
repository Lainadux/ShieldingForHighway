package RefractoredVersion.Engine.ego;

import RefractoredVersion.Engine.Action;

public class NoShieldEgo extends EgoVehicle {
    @Override
    protected ShieldDecision verifyActionSafe(Action action) {
        return new ShieldDecision(true, "NoShieldEgo approves all AI actions.");
    }
}
