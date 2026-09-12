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

import RefractoredVersion.TestScript.Config.EgoType;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Config.MethodToGenInitialState;

import java.util.ArrayList;
import java.util.Random;


//This class is used to generator initial environments
public class VehicleGenerator {
    private static final int DEFAULT_TARGET_VEHICLES = 36;
    private static final int DEFAULT_NUM_LANES = 3;
    private static final double DEFAULT_MIN_X = 0.0;
    private static final double DEFAULT_MAX_X = 400.0;
    private static final double LANE_WIDTH = 4.0;
    private static final int SPAWN_ATTEMPT_MULTIPLIER = 200;
    private static final double MIN_SPAWN_FRONT_GAP = 12.0;
    private static final double SPAWN_REACTION_TIME = 0.5;
    private static final double SPAWN_RESPONSE_MAX_ACCELERATION = 3.0;
    private static final double SPAWN_REAR_MIN_BRAKE = 5.0;
    private static final double SPAWN_FRONT_MAX_BRAKE = 5.0;
    private static final double NPC_INITIAL_SPEED_MIN = 21.0;
    private static final double NPC_INITIAL_SPEED_MAX = 24.0;

    public static ArrayList<Vehicle> generateVehicles(JavaMomentumConfig javaMomentumConfig) {
        MethodToGenInitialState method = javaMomentumConfig.getMethodToGenInitialState();
        if(method == null || method == MethodToGenInitialState.DEFAULT) {
            return populateInitialTraffic(javaMomentumConfig);
        }

        throw new IllegalArgumentException("Unsupported initial state generation method: " + method);
    }

    public static ArrayList<Vehicle> genAllNpc(boolean randomizePoliteness) {
        ArrayList<Vehicle> vehicles = new ArrayList<>();
        Random rand = new Random();
        int spawned = 0;
        int attempts = 0;
        int maxAttempts = DEFAULT_TARGET_VEHICLES * SPAWN_ATTEMPT_MULTIPLIER;

        while (spawned < DEFAULT_TARGET_VEHICLES && attempts < maxAttempts) {
            attempts++;

            Vehicle vehicle = new Vehicle();
            int lane = rand.nextInt(DEFAULT_NUM_LANES);
            vehicle.role = "NPC";
            vehicle.id = String.valueOf(spawned);
            vehicle.y = lane * LANE_WIDTH;
            vehicle.setLaneIndex(lane);
            vehicle.setTargetLaneIndex(lane);
            vehicle.cooldownTimer = rand.nextDouble();
            vehicle.speed = sampleNpcInitialSpeed(rand);
            vehicle.x = nextPythonStyleNpcX(vehicles, rand, vehicle.speed);
            vehicle.vx = vehicle.speed;
            vehicle.vy = 0.0;
            vehicle.targetSpeed = vehicle.speed;
            if (randomizePoliteness) {
                vehicle.politeness = rand.nextDouble() * 0.3;
            }

            if (!isSpawnDynamicallySafe(vehicles, vehicle)) {
                continue;
            }

            vehicles.add(vehicle);
            spawned++;
        }

        return vehicles;
    }

    private static ArrayList<Vehicle> populateInitialTraffic(JavaMomentumConfig javaMomentumConfig) {
        ArrayList<Vehicle> vehicles = new ArrayList<>();
        Random rand = new Random();
        int spawned = 0;
        int attempts = 0;
        int maxAttempts = DEFAULT_TARGET_VEHICLES * SPAWN_ATTEMPT_MULTIPLIER;
        double minX = javaMomentumConfig.getMinX();
        double maxX = javaMomentumConfig.getMaxX();
        if (maxX <= minX) {
            minX = DEFAULT_MIN_X;
            maxX = DEFAULT_MAX_X;
        }
        boolean egoSpawnInMiddle = javaMomentumConfig.isEgoSpawnInMiddle();
        boolean egoSpawned = false;
        double egoMiddleX = (minX + maxX) / 2.0;

        while (spawned < DEFAULT_TARGET_VEHICLES && attempts < maxAttempts) {
            attempts++;

            int lane = rand.nextInt(DEFAULT_NUM_LANES);
            double candidateNpcSpeed = Double.NaN;
            double candidateNpcX = Double.NaN;
            boolean isEgo;
            if (egoSpawnInMiddle) {
                candidateNpcSpeed = sampleNpcInitialSpeed(rand);
                candidateNpcX = nextPythonStyleNpcX(vehicles, rand, candidateNpcSpeed);
                isEgo = !egoSpawned && candidateNpcX >= egoMiddleX;
            }
            else {
                isEgo = spawned == 0;
            }

            if (!isEgo && Double.isNaN(candidateNpcSpeed)) {
                candidateNpcSpeed = sampleNpcInitialSpeed(rand);
                candidateNpcX = nextPythonStyleNpcX(vehicles, rand, candidateNpcSpeed);
            }
            Vehicle vehicle = isEgo ? createEgoVehicle(javaMomentumConfig) : new Vehicle();
            vehicle.role = isEgo ? "EGO" : "NPC";
            vehicle.id = String.valueOf(spawned);
            vehicle.y = lane * LANE_WIDTH;
            vehicle.setLaneIndex(lane);
            vehicle.setTargetLaneIndex(lane);
            vehicle.cooldownTimer = rand.nextDouble();

            vehicle.speed = isEgo ? 25.0 : candidateNpcSpeed;
            if (isEgo && spawned == 0) {
                vehicle.x = minX;
            }
            else if (isEgo) {
                vehicle.x = egoMiddleX;
            }
            else {
                vehicle.x = candidateNpcX;
                if (vehicle.x > maxX) {
                    break;
                }
                vehicle.x = Math.max(minX, vehicle.x);
            }
            vehicle.vx = vehicle.speed;
            vehicle.vy = 0.0;
            vehicle.targetSpeed = vehicle.speed;
            if (!isEgo && javaMomentumConfig.isRandomizeNpcPoliteness()) {
                vehicle.politeness = rand.nextDouble() * 0.3;
            }

            if (!isSpawnDynamicallySafe(vehicles, vehicle)) {
                continue;
            }

            vehicles.add(vehicle);
            if (isEgo) {
                egoSpawned = true;
            }
            spawned++;
        }

        if (vehicles.stream().noneMatch(vehicle -> "EGO".equals(vehicle.role))) {
            throw new IllegalStateException("Failed to spawn ego vehicle within configured initial traffic range.");
        }

        return vehicles;
    }

    private static Vehicle createEgoVehicle(JavaMomentumConfig javaMomentumConfig) {
        EgoType egoType = javaMomentumConfig.getEgoType();
        EgoType resolvedEgoType = egoType == null ? EgoType.RandomEgoVehicle : egoType;
        switch (resolvedEgoType) {
            case RandomEgoVehicle:
                return new RandomEgoVehicle();
            case NoShieldEgo:
                NoShieldEgo noShieldEgo = new NoShieldEgo();
                return configureEgoVehicle(noShieldEgo, javaMomentumConfig);
            case EgoVehicle:
                EgoVehicle egoVehicle = new EgoVehicle();
                return configureEgoVehicle(egoVehicle, javaMomentumConfig);
            case EgoDelayedVehicle:
                EgoDelayedVehicle egoDelayedVehicle = new EgoDelayedVehicle();
                return configureEgoVehicle(egoDelayedVehicle, javaMomentumConfig);
            case AlwaysFasterVehicle:
                AlwaysFasterVehicle alwaysFasterVehicle = new AlwaysFasterVehicle();
                return configureEgoVehicle(alwaysFasterVehicle, javaMomentumConfig);
            case ExploreFutureEgo:
                ExploreFutureEgo exploreFutureEgo = new ExploreFutureEgo();
                return configureEgoVehicle(exploreFutureEgo, javaMomentumConfig);
            case ExploreFutureWithoutCachingFallback:
                ExploreFutureWithoutCachingFallback exploreFutureWithoutCachingFallback =
                        new ExploreFutureWithoutCachingFallback();
                return configureEgoVehicle(exploreFutureWithoutCachingFallback, javaMomentumConfig);
            case ExploreFutureWithIdleSlowerFallback:
                ExploreFutureWithIdleSlowerFallback exploreFutureWithIdleSlowerFallback =
                        new ExploreFutureWithIdleSlowerFallback();
                return configureEgoVehicle(exploreFutureWithIdleSlowerFallback, javaMomentumConfig);
            case ExploreFutureSlowerVehicle:
                ExploreFutureSlowerVehicle exploreFutureSlowerVehicle = new ExploreFutureSlowerVehicle();
                return configureEgoVehicle(exploreFutureSlowerVehicle, javaMomentumConfig);
            case CascadedRecoExploreVehicle:
                CascadedRecoExploreVehicle cascadedRecoExploreVehicle = new CascadedRecoExploreVehicle();
                return configureEgoVehicle(cascadedRecoExploreVehicle, javaMomentumConfig);
            case CounterFactualExploreFutureSlowerVehicle:
                CounterFactualExploreFutureSlowerVehicle counterFactualExploreFutureSlowerVehicle =
                        new CounterFactualExploreFutureSlowerVehicle();
                return configureEgoVehicle(counterFactualExploreFutureSlowerVehicle, javaMomentumConfig);
            case EgoRandomEnableShield:
                EgoRandomEnableShield egoRandomEnableShield =
                        new EgoRandomEnableShield(javaMomentumConfig.getRandomEnableShieldPercent());
                return configureEgoVehicle(egoRandomEnableShield, javaMomentumConfig);
            case EgoRandomFallback:
                EgoRandomFallback egoRandomFallback =
                        new EgoRandomFallback(javaMomentumConfig.getRandomEnableShieldPercent());
                return configureEgoVehicle(egoRandomFallback, javaMomentumConfig);
            case SlowerAndMinimalTrajectoryVehicle:
                SlowerAndMinimalTrajectoryVehicle slowerAndMinimalTrajectoryVehicle =
                        new SlowerAndMinimalTrajectoryVehicle();
                return configureEgoVehicle(slowerAndMinimalTrajectoryVehicle, javaMomentumConfig);
            case RssStrictEgoVehicle:
                RssStrictEgoVehicle rssStrictEgoVehicle = new RssStrictEgoVehicle();
                return configureEgoVehicle(rssStrictEgoVehicle, javaMomentumConfig);
            case RssSoftEgoVehicle:
                RssSoftEgoVehicle rssSoftEgoVehicle = new RssSoftEgoVehicle();
                return configureEgoVehicle(rssSoftEgoVehicle, javaMomentumConfig);
            case ExploreFutureDelayedVehicle:
                ExploreFutureDelayedVehicle exploreFutureDelayedVehicle = new ExploreFutureDelayedVehicle();
                return configureEgoVehicle(exploreFutureDelayedVehicle, javaMomentumConfig);
            default:
                throw new IllegalArgumentException("Unsupported ego type: " + resolvedEgoType);
        }
    }

    private static <T extends EgoVehicle> T configureEgoVehicle(T egoVehicle, JavaMomentumConfig javaMomentumConfig) {
        egoVehicle.aiProfile = javaMomentumConfig.getAiProfile();
        egoVehicle.sensorRange = javaMomentumConfig.getSensorRange();
        egoVehicle.noisySensorOuterRange = javaMomentumConfig.getNoisySensorOuterRange();
        return egoVehicle;
    }

    private static double nextPythonStyleNpcX(ArrayList<Vehicle> vehicles, Random rand, double speed) {
        double defaultSpacing = 12.0 + speed;
        double offset = defaultSpacing * Math.exp(-5.0 / 40.0 * DEFAULT_NUM_LANES);
        double x0 = vehicles.isEmpty() ? 3.0 * offset : maxVehicleX(vehicles);
        return x0 + offset * (0.9 + 0.2 * rand.nextDouble());
    }

    private static double sampleNpcInitialSpeed(Random rand) {
        return NPC_INITIAL_SPEED_MIN + rand.nextDouble() * (NPC_INITIAL_SPEED_MAX - NPC_INITIAL_SPEED_MIN);
    }

    private static double maxVehicleX(ArrayList<Vehicle> vehicles) {
        double maxX = Double.NEGATIVE_INFINITY;
        for (Vehicle vehicle : vehicles) {
            maxX = Math.max(maxX, vehicle.x);
        }
        return maxX;
    }

    private static boolean isSpawnDynamicallySafe(ArrayList<Vehicle> vehicles, Vehicle candidate) {
        for (Vehicle existing : vehicles) {
            if (existing.getLaneIndex() != candidate.getLaneIndex()) {
                continue;
            }

            Vehicle rear = candidate.x < existing.x ? candidate : existing;
            Vehicle front = candidate.x < existing.x ? existing : candidate;
            double bumperGap = front.x - rear.x - rear.LENGTH;
            if (bumperGap < requiredInitialBumperGap(rear, front)) {
                return false;
            }
        }
        return true;
    }

    private static double requiredInitialBumperGap(Vehicle rear, Vehicle front) {
        double rearSpeed = Math.max(0.0, rear.speed);
        double frontSpeed = Math.max(0.0, front.speed);
        double rearResponseSpeed = rearSpeed + SPAWN_RESPONSE_MAX_ACCELERATION * SPAWN_REACTION_TIME;
        double rearResponseDistance = rearSpeed * SPAWN_REACTION_TIME
                + 0.5 * SPAWN_RESPONSE_MAX_ACCELERATION * SPAWN_REACTION_TIME * SPAWN_REACTION_TIME;
        double rearBrakingDistance = rearResponseSpeed * rearResponseSpeed / (2.0 * SPAWN_REAR_MIN_BRAKE);
        double frontBrakingDistance = frontSpeed * frontSpeed / (2.0 * SPAWN_FRONT_MAX_BRAKE);
        double rssSafeDistance = rearResponseDistance + rearBrakingDistance - frontBrakingDistance;
        return MIN_SPAWN_FRONT_GAP + Math.max(0.0, rssSafeDistance);
    }
}
