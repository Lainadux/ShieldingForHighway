package RefractoredVersion.Engine;

public class CacheFallbackEgoVehicle  extends EgoVehicle implements NonNpcVehicle, NotControlledByMOBIL, PControlledVehicle{

    public Action cachedAction = Action.SLOWER;
    @Override
    public void planAction() throws Exception {

    }

}
