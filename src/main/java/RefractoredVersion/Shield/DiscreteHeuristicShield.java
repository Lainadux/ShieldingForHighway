package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.Vehicle;
import it.unicam.quasylab.jspear.controller.Controller;
import it.unicam.quasylab.jspear.controller.ControllerRegistry;
import it.unicam.quasylab.jspear.controller.GenerativeChoiceController;
import it.unicam.quasylab.jspear.controller.ParallelController;
import it.unicam.quasylab.jspear.ds.DataState;
import it.unicam.quasylab.jspear.ds.DataStateUpdate;

import java.util.ArrayList;
import java.util.List;

public class DiscreteHeuristicShield extends HeuristicShield {
    private static final String EGO_CONTROLLER_PREFIX = "discrete-heuristic-ego-controller-";
    private static final String NPC_CONTROLLER_PREFIX = "discrete-heuristic-npc-controller-";
    private static final double[] NPC_ACCELERATION_CHOICES = {-1.0, -0.5, 0.0, 0.5, 1.0};

    public DiscreteHeuristicShield(List<Vehicle> vehicles, Action candidateAction) {
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
            }
            else {
                vehicleController = npcChoiceController(registry, vehicleIndex);
            }
            centralController = centralController == null
                    ? vehicleController
                    : new ParallelController(centralController, vehicleController);
        }

        if (centralController == null) {
            throw new IllegalStateException("Cannot build DiscreteHeuristicShield controller without vehicles");
        }
        if (!egoControllerCreated) {
            throw new IllegalStateException("Cannot build DiscreteHeuristicShield controller without ego vehicle");
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

        int offset = vehicleOffset(egoIndex);
        updates.add(new DataStateUpdate(
                offset + VarTable.plannedAcceleration.ordinal(),
                egoPlannedAcceleration(state, egoIndex, updates)
        ));
        return updates;
    }

    private Controller npcChoiceController(ControllerRegistry registry, int vehicleIndex) {
        String name = NPC_CONTROLLER_PREFIX + vehicleIndex;
        Controller self = registry.reference(name);
        List<Controller> choices = new ArrayList<>();
        for (double acceleration : NPC_ACCELERATION_CHOICES) {
            choices.add(Controller.doAction(
                    (rg, state) -> npcAccelerationUpdates(state, vehicleIndex, acceleration),
                    self
            ));
        }
        registry.set(name, uniformChoice(choices, 0));
        return self;
    }

    private Controller uniformChoice(List<Controller> choices, int index) {
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("NPC acceleration choices must not be empty");
        }
        if (index == choices.size() - 1) {
            return choices.get(index);
        }
        double probabilityOfCurrentChoice = 1.0 / (choices.size() - index);
        return new GenerativeChoiceController(
                probabilityOfCurrentChoice,
                choices.get(index),
                uniformChoice(choices, index + 1)
        );
    }

    private List<DataStateUpdate> npcAccelerationUpdates(DataState state, int vehicleIndex, double acceleration) {
        int egoIndex = getEgoVehicleIndex(state);
        if (egoIndex < 0) {
            throw new IllegalStateException("No ego vehicle in DataState");
        }
        if (vehicleIndex == egoIndex) {
            throw new IllegalStateException("Updating acceleration for ego vehicle in npcAccelerationUpdates");
        }

        int offset = vehicleOffset(vehicleIndex);
        return List.of(new DataStateUpdate(
                offset + VarTable.plannedAcceleration.ordinal(),
                acceleration
        ));
    }
}
