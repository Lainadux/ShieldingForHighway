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
import RefractoredVersion.Shield.ExploreFutureActionSmarterShield;
import RefractoredVersion.Shield.ExploreFutureBetterReferenceShield;
import RefractoredVersion.Shield.ExploreFutureRssOShield;
import RefractoredVersion.Shield.ExploreFutureRssShield;
import RefractoredVersion.Shield.HeuristicShield;
import RefractoredVersion.Shield.InstantHeuristicShield;
import RefractoredVersion.Shield.StarkNativeShield;
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
    protected final List<ActionAcceptanceLog> actionAcceptanceSequence = new ArrayList<>();
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
        recordDecisionLogs(decision, parseAction(decision), shieldDecision, action, false);
        recordAiDecision(!shieldDecision.safe);
        applyAction(action);
    }

    protected void recordDecisionLogs(JavaHighwayAiClient.AiDecision decision,
                                      Action proposedAction,
                                      ShieldDecision shieldDecision,
                                      Action performedAction,
                                      boolean cacheHit) {
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

        actionAcceptanceSequence.add(new ActionAcceptanceLog(
                this.getEngine().stepsTaken,
                this.getEngine().timeElapsed,
                decisionIndex(),
                decision.action,
                decision.action_name,
                proposedAction,
                shieldDecision.safe,
                cacheHit,
                performedAction,
                acceptanceValue(shieldDecision.safe, cacheHit),
                shieldDecision.failedSafetyCriteria,
                shieldDecision.collisionRobustness,
                shieldDecision.firstSecondSafetyRobustness,
                shieldDecision.stabilityRobustness,
                shieldDecision.rearThreatRobustness,
                shieldDecision.lowSpeedLaneChangeRobustness,
                shieldDecision.shieldRobustness
        ));
    }

    private int decisionIndex() {
        return this.getEngine().getFrequency() == 0
                ? 0
                : this.getEngine().stepsTaken / this.getEngine().getFrequency();
    }

    private double acceptanceValue(boolean shieldSafe, boolean cacheHit) {
        if (shieldSafe) {
            return 1.0;
        }
        return cacheHit ? 0.5 : 0.0;
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
            case EXPLORE_FUTURE_BETTER_REFERENCE_ACTION:
                ArrayList<Action> betterFutureActions = config == null || config.getFutureActions() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(config.getFutureActions());
                ExploreFutureBetterReferenceShield betterShield =
                        new ExploreFutureBetterReferenceShield(this.getEngine(), betterFutureActions);
                boolean betterSafe = betterShield.verifySafe(action);
                return new ShieldDecision(betterSafe, betterShield.getUnsafeDiagnosis());
            case EXPLORE_FUTURE_RSS_ACTION:
                ArrayList<Action> rssFutureActions = config == null || config.getFutureActions() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(config.getFutureActions());
                ExploreFutureRssShield rssShield =
                        new ExploreFutureRssShield(this.getEngine(), rssFutureActions);
                boolean rssSafe = rssShield.verifySafe(action);
                return new ShieldDecision(rssSafe, rssShield.getUnsafeDiagnosis());
            case EXPLORE_FUTURE_RSS_O_ACTION:
                ArrayList<Action> rssOFutureActions = config == null || config.getFutureActions() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(config.getFutureActions());
                ExploreFutureRssOShield rssOShield =
                        new ExploreFutureRssOShield(this.getEngine(), rssOFutureActions);
                boolean rssOSafe = rssOShield.verifySafe(action);
                return new ShieldDecision(rssOSafe, rssOShield.getUnsafeDiagnosis());
            case STARK_NATIVE:
                StarkNativeShield starkNativeShield = new StarkNativeShield(this.getEngine());
                boolean starkNativeSafe = starkNativeShield.verifySafe(action);
                return shieldDecisionFrom(starkNativeSafe, starkNativeShield);
            case HEURISTIC:
                HeuristicShield heuristicShield = new HeuristicShield(this.getDetectedVehicles(), action);
                boolean heuristicSafe = heuristicShield.verifySafe();
                return new ShieldDecision(heuristicSafe, heuristicShield.getDiagnosis());
            case INSTANT_HEURISTIC:
                InstantHeuristicShield instantHeuristicShield = new InstantHeuristicShield(this.getDetectedVehicles(), action);
                boolean instantHeuristicSafe = instantHeuristicShield.verifySafe();
                return new ShieldDecision(instantHeuristicSafe, instantHeuristicShield.getDiagnosis());
            case EXPLORE_FUTURE_SMARTER_ACTION:
                ArrayList<Action> smarterFutureActions = config == null || config.getFutureActions() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(config.getFutureActions());
                ExploreFutureActionSmarterShield smarterShield =
                        new ExploreFutureActionSmarterShield(this.getEngine(), smarterFutureActions);
                boolean smarterSafe = smarterShield.verifySafe(action);
                return new ShieldDecision(smarterSafe, smarterShield.getUnsafeDiagnosis());
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }

    protected static class ShieldDecision {
        protected final boolean safe;
        protected final String diagnosis;
        protected final String failedSafetyCriteria;
        protected final Double collisionRobustness;
        protected final Double firstSecondSafetyRobustness;
        protected final Double stabilityRobustness;
        protected final Double rearThreatRobustness;
        protected final Double lowSpeedLaneChangeRobustness;
        protected final Double shieldRobustness;

        protected ShieldDecision(boolean safe, String diagnosis) {
            this(safe, diagnosis, "", null, null, null, null, null, null);
        }

        protected ShieldDecision(boolean safe,
                                 String diagnosis,
                                 String failedSafetyCriteria,
                                 Double collisionRobustness,
                                 Double firstSecondSafetyRobustness,
                                 Double stabilityRobustness,
                                 Double rearThreatRobustness,
                                 Double lowSpeedLaneChangeRobustness,
                                 Double shieldRobustness) {
            this.safe = safe;
            this.diagnosis = diagnosis;
            this.failedSafetyCriteria = failedSafetyCriteria == null ? "" : failedSafetyCriteria;
            this.collisionRobustness = collisionRobustness;
            this.firstSecondSafetyRobustness = firstSecondSafetyRobustness;
            this.stabilityRobustness = stabilityRobustness;
            this.rearThreatRobustness = rearThreatRobustness;
            this.lowSpeedLaneChangeRobustness = lowSpeedLaneChangeRobustness;
            this.shieldRobustness = shieldRobustness;
        }
    }

    protected ShieldDecision shieldDecisionFrom(boolean safe, AllSlowerShield shield) {
        return new ShieldDecision(
                safe,
                shield.getUnsafeDiagnosis(),
                shield.getFailedSafetyCriteriaCsv(),
                shield.getLastCollisionRobustness(),
                shield.getLastFirstSecondSafetyRobustness(),
                shield.getLastStabilityRobustness(),
                shield.getLastChangeLaneRearThreatRobustness(),
                shield.getLastChangeLaneLowSpeedRobustness(),
                shield.getLastShieldRobustness()
        );
    }

    protected ShieldDecision shieldDecisionFrom(boolean safe, ExploreFutureActionShield shield) {
        return new ShieldDecision(
                safe,
                shield.getUnsafeDiagnosis(),
                shield.getFailedSafetyCriteriaCsv(),
                shield.getLastCollisionRobustness(),
                shield.getLastFirstSecondSafetyRobustness(),
                shield.getLastStabilityRobustness(),
                shield.getLastChangeLaneRearThreatRobustness(),
                shield.getLastChangeLaneLowSpeedRobustness(),
                shield.getLastShieldRobustness()
        );
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

    public List<ActionAcceptanceLog> retrieveActionAcceptanceSequence() {
        return new ArrayList<>(actionAcceptanceSequence);
    }
}
