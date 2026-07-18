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

public class EgoVehicle extends Vehicle implements NonNpcVehicle, NotControlledByMOBIL, PControlledVehicle{
    public AIProfile aiProfile = AIProfile.base;
    public int sensorRange = 100;
    public ArrayList<Vehicle> detectedVehicles = new ArrayList<>();
    private JavaHighwayAiClient.AiDecision lastAiDecision = new JavaHighwayAiClient.AiDecision();
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
            }
        }
        return detectedVehicles;
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

    private void applyAiAction(JavaHighwayAiClient.AiDecision decision) throws Exception {
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
        recordAiDecision(!shieldDecision.safe);
        applyAction(action);
    }

    private ShieldDecision verifyActionSafe(Action action) throws Exception {
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
                if (config.getFutureActions() == null) {
                    throw new IllegalArgumentException(
                            "futureActions must be set when shieldType is EXPLORE_FUTURE_ACTION");
                }
                ExploreFutureActionShield exploreShield =
                        new ExploreFutureActionShield(this.getEngine(), config.getFutureActions());
                boolean exploreSafe = exploreShield.verifySafe(action);
                return new ShieldDecision(exploreSafe, exploreShield.getUnsafeDiagnosis());
            default:
                throw new IllegalArgumentException("Unsupported shield type: " + shieldType);
        }
    }

    private static class ShieldDecision {
        private final boolean safe;
        private final String diagnosis;

        private ShieldDecision(boolean safe, String diagnosis) {
            this.safe = safe;
            this.diagnosis = diagnosis;
        }
    }

    private void recordAiDecision(boolean rejected) {
        aiDecisionCount++;
        if (rejected) {
            rejectedAiDecisionCount++;
        }
    }

    private boolean shouldPrintDiagnostics() {
        return this.getEngine().config == null || !this.getEngine().config.isGenLogs();
    }

    private Action parseAction(JavaHighwayAiClient.AiDecision decision) {
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
}
