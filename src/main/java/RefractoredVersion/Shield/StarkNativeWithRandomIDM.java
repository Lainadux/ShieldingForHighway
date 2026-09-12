package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;
import RefractoredVersion.Engine.NonNpcVehicle;
import RefractoredVersion.Engine.Vehicle;
import it.unicam.quasylab.jspear.ds.DataState;
import it.unicam.quasylab.jspear.ds.DataStateUpdate;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.List;

public class StarkNativeWithRandomIDM extends StarkNativeShield {
    private static final double IDM_ACCELERATION_NOISE_RADIUS = 0.5;

    public StarkNativeWithRandomIDM(JavaHighwayEngine sourceEngine) {
        super(sourceEngine);
    }

    public StarkNativeWithRandomIDM(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        super(sourceEngine, futureActions);
    }

    @Override
    protected List<DataStateUpdate> getControllerUpdates(RandomGenerator rg, DataState state,
                                                         List<Action> actionSequence) {
        List<Vehicle> vehicles = vehiclesFromState(state);
        JavaHighwayEngine nativeEngine = nativeEngineFor(state, vehicles);
        Vehicle ego = egoVehicle(vehicles);
        if (ego != null && state.getStep() % sourceEngine.getFrequency() == 0) {
            int actionIndex = state.getStep() / sourceEngine.getFrequency();
            if (actionIndex >= actionSequence.size()) {
                throw new IllegalStateException(String.format(
                        "Missing native shield action at decision index %d, actionSequence size is %d",
                        actionIndex,
                        actionSequence.size()
                ));
            }
            applyAction(ego, actionSequence.get(actionIndex));
        }

        JavaHighwayEngineUtils.IdmNoiseContext noiseContext =
                JavaHighwayEngineUtils.IdmNoiseContext.sample(rg, vehicles, IDM_ACCELERATION_NOISE_RADIUS);

        try {
            for (Vehicle vehicle : vehicles) {
                if (!(vehicle instanceof NonNpcVehicle)) {
                    vehicle.setTargetLaneIndex(JavaHighwayEngineUtils.sandboxComputeTargetLaneWithNoise(
                            vehicle,
                            vehicles,
                            List.of(0, 1),
                            nativeEngine,
                            noiseContext
                    ));
                    vehicle.plannedAcceleration =
                            JavaHighwayEngineUtils.computeIdmAccelerationWithNoise(vehicle, vehicles, noiseContext);
                }
                else {
                    vehicle.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(vehicle, vehicles);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply noisy STARK-native highway controller", e);
        }
        return updatesForVehicles(vehicles, state);
    }
}
