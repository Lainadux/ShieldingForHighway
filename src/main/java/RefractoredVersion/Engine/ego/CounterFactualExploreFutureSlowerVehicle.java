package RefractoredVersion.Engine.ego;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.telemetry.CounterFactualReplayLog;
import RefractoredVersion.Engine.JavaHighwayAiClient;
import RefractoredVersion.Engine.vehicle.Vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CounterFactualExploreFutureSlowerVehicle extends ExploreFutureSlowerVehicle {
    private final List<CounterFactualReplayLog> counterFactualReplayLogs = new ArrayList<>();

    @Override
    protected void applyAiAction(JavaHighwayAiClient.AiDecision decision) throws Exception {
        this.lastAiDecision = decision;
        Action proposedAction = parseAction(decision);
        ShieldDecision shieldDecision = verifyActionSafe(proposedAction);
        Action performedAction = proposedAction;
        boolean cacheHit = false;

        if (shouldPrintDiagnostics()) {
            System.out.printf("%s AI decision: action=%d, action_name=%s, parsed_action=%s%n",
                    shieldDecision.safe ? "Safe" : "Unsafe",
                    decision.action,
                    decision.action_name,
                    proposedAction);
            System.out.println(shieldDecision.diagnosis);
        }

        if (!shieldDecision.safe) {
            if (cachedActions.isEmpty()) {
                performedAction = Action.SLOWER;
            } else {
                cacheHit = true;
                performedAction = cachedActions.remove(0);
            }
            recordCounterFactualReplay(decision, proposedAction, shieldDecision, performedAction);
        }

        recordDecisionLogs(decision, proposedAction, shieldDecision, performedAction, cacheHit);
        recordAiDecision(!shieldDecision.safe);
        applyAction(performedAction);
    }

    private void recordCounterFactualReplay(JavaHighwayAiClient.AiDecision decision,
                                            Action proposedAction,
                                            ShieldDecision shieldDecision,
                                            Action performedAction) {
        counterFactualReplayLogs.add(new CounterFactualReplayLog(
                this.getEngine().stepsTaken,
                this.getEngine().timeElapsed,
                decisionIndex(),
                decision.action,
                decision.action_name,
                proposedAction,
                shieldDecision.safe,
                shieldDecision.diagnosis,
                performedAction,
                proposedAction,
                snapshotVehicles(this.getEngine().vehicles)
        ));
    }

    private int decisionIndex() {
        return this.getEngine().getFrequency() == 0
                ? 0
                : this.getEngine().stepsTaken / this.getEngine().getFrequency();
    }

    private List<Vehicle> snapshotVehicles(List<Vehicle> vehicles) {
        if (vehicles == null) {
            return List.of();
        }
        List<Vehicle> snapshot = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            snapshot.add(copyVehicle(vehicle));
        }
        return snapshot;
    }

    private Vehicle copyVehicle(Vehicle source) {
        Vehicle copy = new Vehicle();
        copy.TAU_ACC = source.TAU_ACC;
        copy.TAU_HEADING = source.TAU_HEADING;
        copy.TAU_LATERAL = source.TAU_LATERAL;
        copy.TAU_PURSUIT = source.TAU_PURSUIT;
        copy.KP_A = source.KP_A;
        copy.KP_HEADING = source.KP_HEADING;
        copy.KP_LATERAL = source.KP_LATERAL;
        copy.MAX_STEERING_ANGLE = source.MAX_STEERING_ANGLE;
        copy.DELTA_SPEED = source.DELTA_SPEED;
        copy.possible_lanes = source.possible_lanes == null ? null : source.possible_lanes.clone();
        copy.karma_a_new = source.karma_a_new;
        copy.mobil = source.mobil == null ? new HashMap<>() : new HashMap<>(source.mobil);
        copy.targetSpeed = source.targetSpeed;
        copy.id = source.id;
        copy.politeness = source.politeness;
        copy.cooldownTimer = source.cooldownTimer;
        copy.setTargetLaneIndex(source.getTargetLaneIndex());
        copy.setLaneIndex(source.getLaneIndex());
        copy.role = source.role;
        copy.x = source.x;
        copy.y = source.y;
        copy.vx = source.vx;
        copy.vy = source.vy;
        copy.speed = source.speed;
        copy.previousSecondSpeed = source.previousSecondSpeed;
        copy.heading = source.heading;
        copy.plannedAcceleration = source.plannedAcceleration;
        copy.plannedSteering = source.plannedSteering;
        return copy;
    }

    public List<CounterFactualReplayLog> retrieveCounterFactualReplayLogs() {
        return new ArrayList<>(counterFactualReplayLogs);
    }
}
