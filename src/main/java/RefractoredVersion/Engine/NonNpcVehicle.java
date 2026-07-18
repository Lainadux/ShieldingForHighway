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

public interface NonNpcVehicle {
    void planAction() throws Exception;

    /**
     * Updating the vehicle's target lane index and target speed based on the action.
     * @param action The action to apply.
     */
    default void applyAction(Action action) {
        if (!(this instanceof Vehicle v)) {
            throw new IllegalStateException("NonNpcVehicle must also be a Vehicle to apply actions.");
        }
        switch (action) {
            case LANE_LEFT:
                v.setTargetLaneIndex(Math.max(0, v.getLaneIndex() - 1));
                break;
            case LANE_RIGHT:
                v.setTargetLaneIndex(Math.min(v.getEngine().numLanes - 1, v.getLaneIndex() + 1));
                break;
            case FASTER:
                v.targetSpeed = clipTargetSpeed(v, v.targetSpeed + 5);
                break;
            case SLOWER:
                v.targetSpeed = clipTargetSpeed(v, v.targetSpeed - 5);
                break;
            case IDLE:
                break;
            default:
                throw new IllegalArgumentException("Unsupported AI action: " + action);
        }
    }

    private double clipTargetSpeed(Vehicle vehicle, double targetSpeed) {
        double maxTargetSpeed = vehicle.getEngine().config == null
                ? 40.0
                : vehicle.getEngine().config.getMaxTargetSpeed();
        return Math.max(0.0, Math.min(maxTargetSpeed, targetSpeed));
    }
}
