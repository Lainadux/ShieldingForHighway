package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.vehicle.Vehicle;
import it.unicam.quasylab.jspear.controller.Controller;
import it.unicam.quasylab.jspear.controller.ControllerRegistry;
import it.unicam.quasylab.jspear.controller.ParallelController;
import it.unicam.quasylab.jspear.ds.DataState;
import it.unicam.quasylab.jspear.ds.DataStateUpdate;
import org.apache.commons.math3.random.RandomGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Heuristic shield whose controller is the parallel composition of one ego
 * controller and one TTC-conditioned stochastic controller per NPC vehicle.
 */
public class TtcStochasticHeuristicShield extends HeuristicShield {
    private static final String EGO_CONTROLLER_PREFIX = "ttc-stochastic-ego-controller-";
    private static final String NPC_CONTROLLER_PREFIX = "ttc-stochastic-npc-controller-";

    private static final double CRITICAL_TTC = 1.0;
    private static final double SAFE_TTC = 4.0;
    private static final double MIN_BRAKE_PROBABILITY = 0.1;
    private static final double MAX_BRAKE_PROBABILITY = 0.9;
    private static final double LOW_RISK_BRAKING_LIMIT = 0.5;
    private static final double HIGH_RISK_BRAKING_LIMIT = 3.0;
    private static final double LOW_RISK_ACCELERATION_LIMIT = 5.0;
    private static final double HIGH_RISK_ACCELERATION_LIMIT = 2.0;
    private static final double VEHICLE_LENGTH = 5.0;

    public TtcStochasticHeuristicShield(List<Vehicle> vehicles, Action candidateAction) {
        super(vehicles, candidateAction);
    }

    @Override
    protected Controller getController() {
        ControllerRegistry registry = new ControllerRegistry();
        Controller centralController = null;
        boolean egoControllerCreated = false;

        for (int vehicleIndex = 0; vehicleIndex < vehicleCount(); vehicleIndex++) {
            Controller vehicleController;
            if (isInitialEgoVehicle(vehicleIndex)) {
                egoControllerCreated = true;
                vehicleController = egoController(registry, vehicleIndex);
            } else {
                vehicleController = npcController(registry, vehicleIndex);
            }

            centralController = centralController == null
                    ? vehicleController
                    : new ParallelController(centralController, vehicleController);
        }

        if (centralController == null) {
            throw new IllegalStateException(
                    "Cannot build TtcStochasticHeuristicShield controller without vehicles"
            );
        }
        if (!egoControllerCreated) {
            throw new IllegalStateException(
                    "Cannot build TtcStochasticHeuristicShield controller without ego vehicle"
            );
        }
        return centralController;
    }

    private boolean isInitialEgoVehicle(int vehicleIndex) {
        return "EGO".equals(observedVehicles.get(vehicleIndex).role);
    }

    private Controller egoController(ControllerRegistry registry, int vehicleIndex) {
        String name = EGO_CONTROLLER_PREFIX + vehicleIndex;
        Controller self = registry.reference(name);
        registry.set(
                name,
                Controller.doAction(
                        (rg, state) -> egoControllerUpdates(state, vehicleIndex),
                        self
                )
        );
        return self;
    }

    private List<DataStateUpdate> egoControllerUpdates(DataState state, int vehicleIndex) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            throw new IllegalStateException("No ego vehicle in DataState");
        }
        if (vehicleIndex != egoIndex) {
            throw new IllegalStateException(
                    "Ego controller was created for vehicle index " + vehicleIndex
                            + " but current ego index is " + egoIndex
            );
        }

        List<DataStateUpdate> updates = new ArrayList<>();
        if (state.getStep() == 0) {
            updates.addAll(applyActionToEgo(state, egoIndex, candidateAction));
        }

        updates.add(new DataStateUpdate(
                vehicleOffset(egoIndex) + VarTable.plannedAcceleration.ordinal(),
                egoPlannedAcceleration(state, egoIndex, updates)
        ));
        return updates;
    }

    private Controller npcController(ControllerRegistry registry, int vehicleIndex) {
        String name = NPC_CONTROLLER_PREFIX + vehicleIndex;
        Controller self = registry.reference(name);
        registry.set(
                name,
                Controller.doAction(
                        (rg, state) -> npcControllerUpdates(rg, state, vehicleIndex),
                        self
                )
        );
        return self;
    }

    private List<DataStateUpdate> npcControllerUpdates(
            RandomGenerator rg,
            DataState state,
            int vehicleIndex
    ) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            throw new IllegalStateException("No ego vehicle in DataState");
        }
        if (vehicleIndex == egoIndex) {
            throw new IllegalStateException("NPC controller cannot update the ego vehicle");
        }

        return List.of(new DataStateUpdate(
                vehicleOffset(vehicleIndex) + VarTable.plannedAcceleration.ordinal(),
                sampleAcceleration(rg, state, vehicleIndex)
        ));
    }

    private double sampleAcceleration(RandomGenerator rg, DataState state, int vehicleIndex) {
        double risk = ttcRisk(minimumRelevantFrontTtc(state, vehicleIndex));
        double brakeProbability = MIN_BRAKE_PROBABILITY
                + (MAX_BRAKE_PROBABILITY - MIN_BRAKE_PROBABILITY) * risk;
        double z = (rg.nextDouble() + rg.nextDouble()) / 2.0;

        if (rg.nextDouble() < brakeProbability) {
            double brakingLimit = LOW_RISK_BRAKING_LIMIT
                    + (HIGH_RISK_BRAKING_LIMIT - LOW_RISK_BRAKING_LIMIT) * risk;
            return -brakingLimit * z;
        }

        double accelerationLimit = LOW_RISK_ACCELERATION_LIMIT
                + (HIGH_RISK_ACCELERATION_LIMIT - LOW_RISK_ACCELERATION_LIMIT) * risk;
        return accelerationLimit * z;
    }

    private double minimumRelevantFrontTtc(DataState state, int vehicleIndex) {
        int currentLane = intValue(state, vehicleIndex, VarTable.lane_index);
        int targetLane = intValue(state, vehicleIndex, VarTable.target_lane_index);

        double minimumTtc = frontTtc(
                state,
                vehicleIndex,
                frontVehicleIndexInLane(state, vehicleIndex, currentLane)
        );

        if (targetLane != currentLane) {
            minimumTtc = Math.min(
                    minimumTtc,
                    frontTtc(
                            state,
                            vehicleIndex,
                            frontVehicleIndexInLane(state, vehicleIndex, targetLane)
                    )
            );
        }
        return minimumTtc;
    }

    private int frontVehicleIndexInLane(DataState state, int vehicleIndex, int laneIndex) {
        double vehicleX = value(state, vehicleIndex, VarTable.x);
        int frontIndex = -1;
        double closestFrontX = Double.POSITIVE_INFINITY;

        for (int i = 0; i < vehicleCount(); i++) {
            if (i == vehicleIndex || intValue(state, i, VarTable.lane_index) != laneIndex) {
                continue;
            }

            double otherX = value(state, i, VarTable.x);
            if (otherX > vehicleX && otherX < closestFrontX) {
                frontIndex = i;
                closestFrontX = otherX;
            }
        }
        return frontIndex;
    }

    private double frontTtc(DataState state, int vehicleIndex, int frontVehicleIndex) {
        if (frontVehicleIndex < 0) {
            return Double.POSITIVE_INFINITY;
        }

        double gap = value(state, frontVehicleIndex, VarTable.x)
                - value(state, vehicleIndex, VarTable.x)
                - VEHICLE_LENGTH;
        if (gap <= 0.0) {
            return 0.0;
        }

        double closingSpeed = longitudinalSpeed(state, vehicleIndex)
                - longitudinalSpeed(state, frontVehicleIndex);
        return closingSpeed > 0.0
                ? gap / closingSpeed
                : Double.POSITIVE_INFINITY;
    }

    private double longitudinalSpeed(DataState state, int vehicleIndex) {
        double vx = value(state, vehicleIndex, VarTable.vx);
        return Math.abs(vx) > 1e-9
                ? vx
                : value(state, vehicleIndex, VarTable.speed);
    }

    private double ttcRisk(double ttc) {
        if (ttc <= CRITICAL_TTC) {
            return 1.0;
        }
        if (ttc >= SAFE_TTC) {
            return 0.0;
        }
        return (SAFE_TTC - ttc) / (SAFE_TTC - CRITICAL_TTC);
    }
}
