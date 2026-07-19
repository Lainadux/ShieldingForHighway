/*
 * STARK: Software Tool for the Analysis of Robustness in the unKnown environment
 *
 *                Copyright (C) 2023.
 *
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package RefractoredVersion.Engine;

import RefractoredVersion.Shield.AllSlowerShield;
import RefractoredVersion.Shield.ExploreFutureActionShield;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.ShieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class EgoVehicle extends Vehicle implements NonNpcVehicle, NotControlledByMOBIL, PControlledVehicle{
    public AIProfile aiProfile = AIProfile.base;
    public int sensorRange = 100;
    public int noisySensorOuterRange = 100;
    public ArrayList<Vehicle> detectedVehicles = new ArrayList<>();
    protected JavaHighwayAiClient.AiDecision lastAiDecision = new JavaHighwayAiClient.AiDecision();
    protected final List<BeforeCrashActionLog> beforeCrashActions = new ArrayList<>();
    protected CollisionLog lastCollisionLog;
    private int aiDecisionCount = 0;
    private int rejectedAiDecisionCount = 0;

    public ArrayList<Vehicle> getDetectedVehicles() {
        detectedVehicles.clear();
        for (Vehicle vehicle : this.getEngine().vehicles) {
            if (vehicle == this) {
                detectedVehicles.add(vehicle);
                continue;
            }

            double longitudinalDistance = vehicle.x - this.x;
            if (Math.abs(longitudinalDistance) <= sensorRange) {
                detectedVehicles.add(vehicle);
            } else if (Math.abs(longitudinalDistance) <= sensorRange + noisySensorOuterRange) {
                detectedVehicles.add(noisyPerceivedVehicle(vehicle));
            }
        }
        return detectedVehicles;
    }

    private Vehicle noisyPerceivedVehicle(Vehicle vehicle) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vehicle perceived = new Vehicle();
        perceived.TAU_ACC = vehicle.TAU_ACC;
        perceived.TAU_HEADING = vehicle.TAU_HEADING;
        perceived.TAU_LATERAL = vehicle.TAU_LATERAL;
        perceived.TAU_PURSUIT = vehicle.TAU_PURSUIT;
        perceived.KP_A = vehicle.KP_A;
        perceived.KP_HEADING = vehicle.KP_HEADING;
        perceived.KP_LATERAL = vehicle.KP_LATERAL;
        perceived.MAX_STEERING_ANGLE = vehicle.MAX_STEERING_ANGLE;
        perceived.DELTA_SPEED = vehicle.DELTA_SPEED;
        perceived.possible_lanes = vehicle.possible_lanes.clone();
        perceived.karma_a_new = vehicle.karma_a_new;
        perceived.mobil = vehicle.mobil;
        perceived.targetSpeed = vehicle.targetSpeed;
        perceived.id = vehicle.id;
        perceived.politeness = vehicle.politeness;
        perceived.cooldownTimer = vehicle.cooldownTimer;
        perceived.setTargetLaneIndex(vehicle.getTargetLaneIndex());
        perceived.setLaneIndex(vehicle.getLaneIndex());
        perceived.role = vehicle.role;
        perceived.x = vehicle.x + random.nextDouble(-5.0, 5.0);
        perceived.y = vehicle.y;
        double perceivedSpeed = Math.max(0.0, vehicle.speed + random.nextDouble(-2.0, 2.0));
        perceived.speed = perceivedSpeed;
        perceived.vx = Math.max(0.0, vehicle.vx + random.nextDouble(-2.0, 2.0));
        perceived.vy = vehicle.vy;
        perceived.previousSecondSpeed = vehicle.previousSecondSpeed;
        perceived.heading = vehicle.heading;
        perceived.plannedAcceleration = vehicle.plannedAcceleration;
        perceived.plannedSteering = vehicle.plannedSteering;
        perceived.setEngine(this.getEngine());
        return perceived;
    }


    @Override
    public void planAction() throws Exception {
        if (this.getEngine().isDecisionTime()){
            JavaHighwayAiClient.AiDecision decision = JavaHighwayAiClient.getInstance().decide(this);
            if (shouldPrintDiagnostics()) {
                System.out.printf("AI decision: action=%d, action_name=%s%n",
                        decision.action,
                        decision.action_name);
            }
            applyAiAction(decision);
        }

        this.plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, this.getEngine().vehicles);
        this.plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
    }

    protected void applyAiAction(JavaHighwayAiClient.AiDecision decision) throws Exception {
        this.lastAiDecision = decision;
        Action action = parseAction(decision);
        ShieldDecision shieldDecision = verifyActionSafe(action);
        if (shouldPrintDiagnostics()) {
            System.out.printf("%s AI decision: action=%d, action_name=%s, parsed_action=%s%n",
                    shieldDecision.safe ? "Safe" : "Unsafe",
                    decision.action,
                    decision.action_name,
                    action);
            System.out.println(shieldDecision.diagnosis);
        }
        if (!shieldDecision.safe) {
            action = Action.SLOWER;
        }
        recordCrashLog(decision, parseAction(decision), shieldDecision, action);
        recordAiDecision(!shieldDecision.safe);
        applyAction(action);
    }

    protected void recordCrashLog(JavaHighwayAiClient.AiDecision decision,
                                  Action proposedAction,
                                  ShieldDecision shieldDecision,
                                  Action performedAction) {
        beforeCrashActions.add(new BeforeCrashActionLog(
                this.getEngine().stepsTaken,
                this.getEngine().timeElapsed,
                decision.action,
                decision.action_name,
                proposedAction,
                shieldDecision.safe,
                shieldDecision.diagnosis,
                performedAction
        ));
        while (beforeCrashActions.size() > 3) {
            beforeCrashActions.remove(0);
        }
    }

    protected ShieldDecision verifyActionSafe(Action action) throws Exception {
        JavaMomentumConfig config = this.getEngine().config;
        ShieldType shieldType = config == null || config.getShieldType() == null
                ? ShieldType.ALL_SLOWER
                : config.getShieldType();
        switch (shieldType) {
            case ALL_SLOWER:
                AllSlowerShield allSlowerShield = new AllSlowerShield(this.getEngine());
                boolean allSlowerSafe = allSlowerShield.verifySafe(action);
                return new ShieldDecision(allSlowerSafe, allSlowerShield.getUnsafeDiagnosis());
            case EXPLORE_FUTURE_ACTION:
                ArrayList<Action> futureActions = config == null || config.getFutureActions() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(config.getFutureActions());
                ExploreFutureActionShield exploreShield =
                        new ExploreFutureActionShield(this.getEngine(), futureActions);
                boolean exploreSafe = exploreShield.verifySafe(action);
                return new ShieldDecision(exploreSafe, exploreShield.getUnsafeDiagnosis());
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }

    protected static class ShieldDecision {
        protected final boolean safe;
        protected final String diagnosis;

        protected ShieldDecision(boolean safe, String diagnosis) {
            this.safe = safe;
            this.diagnosis = diagnosis;
        }
    }

    protected void recordAiDecision(boolean rejected) {
        aiDecisionCount++;
        if (rejected) {
            rejectedAiDecisionCount++;
        }
    }

    protected boolean shouldPrintDiagnostics() {
        return this.getEngine().config == null || !this.getEngine().config.isGenLogs();
    }

    protected Action parseAction(JavaHighwayAiClient.AiDecision decision) {
        if (decision.action_name != null && !decision.action_name.isBlank()) {
            return Action.valueOf(decision.action_name.trim().toUpperCase());
        }
        return Action.fromValue(decision.action);
    }

    public JavaHighwayAiClient.AiDecision getLastAiDecision() {
        return lastAiDecision;
    }

    public int getAiDecisionCount() {
        return aiDecisionCount;
    }

    public int getRejectedAiDecisionCount() {
        return rejectedAiDecisionCount;
    }

    @Override
    public void checkCollision() {
        super.checkCollision();
    }

    public void recordCollisionWith(Vehicle other) {
        lastCollisionLog = new CollisionLog(
                this.getEngine().stepsTaken,
                this.getEngine().timeElapsed,
                this.id,
                this.role,
                this.x,
                this.y,
                this.getLaneIndex(),
                this.speed,
                this.vx,
                this.vy,
                other.id,
                other.role,
                other.x,
                other.y,
                other.getLaneIndex(),
                other.speed,
                other.vx,
                other.vy
        );
    }

    public CollisionLog retrieveCrashCollisionLog() {
        return lastCollisionLog;
    }

    public List<BeforeCrashActionLog> retrieveBeforeCrashActions(){
        return new ArrayList<>(beforeCrashActions);
    }
}
