package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayEngineUtils;
import RefractoredVersion.Engine.vehicle.NonNpcVehicle;
import RefractoredVersion.Engine.vehicle.PControlledVehicle;
import RefractoredVersion.Engine.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.List;

public class ExploreFutureDelayedActionShield extends ExploreFutureActionShield {
    private  int delayStep = 1;

    public ExploreFutureDelayedActionShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        this(sourceEngine, futureActions, 1);
    }

    public ExploreFutureDelayedActionShield(JavaHighwayEngine sourceEngine, List<Action> futureActions, int delayStep) {
        super(sourceEngine, futureActions);
        if (delayStep < 1) {
            throw new IllegalArgumentException("delayStep must be at least 1.");
        }
        this.delayStep = delayStep;
    }

    @Override
    public List<List<Vehicle>> predictCandidateTrace(List<Action> actionSequence) throws Exception {
        sandboxEngine = createSandboxEngine(actionSequence);
        int predictionSteps = Math.max(1, actionSequence.size() * sandboxEngine.getFrequency() + delayStep);
        List<List<Vehicle>> trace = new ArrayList<>();
        trace.add(deepCopyVehicles(sandboxEngine.vehicles, actionSequence));
        for (int i = 1; i < predictionSteps; i++) {
            planSandboxActions();
            sandboxEngine.applyPhysics();
            sandboxEngine.checkCollision();
            trace.add(deepCopyVehicles(sandboxEngine.vehicles, actionSequence));
            sandboxEngine.timeElapsed += sandboxEngine.getDt();
            sandboxEngine.stepsTaken += 1;
        }
        return trace;
    }

    @Override
    protected List<Vehicle> deepCopyVehicles(List<Vehicle> vehicles, List<Action> actionSequence) {
        List<Vehicle> copies = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            Vehicle copy = vehicle instanceof NonNpcVehicle
                    ? new ShieldNonNpcDelayedVehicle(actionSequence, delayStep)
                    : new ShieldNpcVehicle();
            copyVehicleState(vehicle, copy);
            copies.add(copy);
        }
        return copies;
    }

    public static class ShieldNonNpcDelayedVehicle extends Vehicle implements NonNpcVehicle, PControlledVehicle {
        private final List<Action> futureActions;
        private final int delayedStep;

        ShieldNonNpcDelayedVehicle(List<Action> futureActions, int delayedStep) {
            this.futureActions = List.copyOf(futureActions);
            this.delayedStep = delayedStep;
        }

        @Override
        public void planAction() throws Exception {
            if (this.getEngine().isDecisionTime()) {
                applyAction(Action.IDLE);
            }

            int shiftedStep = this.getEngine().stepsTaken - delayedStep;
            if (shiftedStep >= 0 && shiftedStep % this.getEngine().getFrequency() == 0) {
                int actionIndex = shiftedStep / this.getEngine().getFrequency();
                if (actionIndex >= futureActions.size()) {
                    throw new IllegalStateException(String.format(
                            "Missing delayed future action at index %d, futureActions size is %d",
                            actionIndex,
                            futureActions.size()
                    ));
                }
                applyAction(futureActions.get(actionIndex));
            }

            this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, this.getEngine().vehicles);
            this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
        }
    }
}
