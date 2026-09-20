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

package RefractoredVersion.TestScript.Config;

import RefractoredVersion.Engine.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class JavaMomentumConfig extends Config {
    private Integer duration = DEFAULT_DURATION;
    private Integer stepsToPredict = DEFAULT_STEPS_TO_PREDICT;
    private Integer numOFSimulations = DEFAULT_NUM_OF_SIMULATIONS;
    private MethodToGenInitialState methodToGenInitialState = MethodToGenInitialState.DEFAULT;
    private EgoType egoType = EgoType.EgoVehicle;
    private int frequency = 40;
    @Deprecated
    private int predictionTime = 3;
    private double maxTargetSpeed = 40.0;
    private boolean fixPrediction = false;
    private double fixedPredictionTargetSpeedDelta = 1.0;
    private double aggressiveV3TtcThreshold = 4.0;
    private String recoverInitialStateFile;
    private ShieldType shieldType = ShieldType.HEURISTIC;
    private ShieldType recoverShieldTypeOverride;
    private int delayedActionStep = 1;
    private int sensorRange = 100;
    private int noisySensorOuterRange = 100;
    private int randomEnableShieldPercent = 20;
    private boolean randomizeNpcPoliteness = false;
    private SandboxNpcPolitenessMode sandboxNpcPolitenessMode = SandboxNpcPolitenessMode.COPY_REAL;
    private RealWorldEngineType realWorldEngineType = RealWorldEngineType.BRUTAL;
    private boolean egoSpawnInMiddle = false;
    private double initialEgoSpeed = 25.0;
    private double vehicleSpacing = 1.0;
    @Deprecated
    private List<Action> futureActions = new ArrayList<>();
    @Deprecated
    private double minX;
    @Deprecated
    private double maxX;
    private TestFunction testFunction = TestFunction.SINGLE_RUN;

    public Integer getNumsOfSimulations() {
        return numOFSimulations;
    }

    public void setNumsOfSimulations(Integer numsOfSimulations) {
        this.numOFSimulations = numsOfSimulations;
    }

    public boolean isQuickTest() {
        return testFunction == TestFunction.QUICK_TEST;
    }

    public void setQuickTest(boolean quickTest) {
        if (quickTest) {
            this.testFunction = TestFunction.QUICK_TEST;
        } else if (this.testFunction == TestFunction.QUICK_TEST) {
            this.testFunction = TestFunction.SINGLE_RUN;
        }
    }

    public boolean isGenLogs() {
        return testFunction == TestFunction.GEN_LOGS;
    }

    public void setGenLogs(boolean genLogs) {
        if (genLogs) {
            this.testFunction = TestFunction.GEN_LOGS;
        } else if (this.testFunction == TestFunction.GEN_LOGS) {
            this.testFunction = TestFunction.SINGLE_RUN;
        }
    }

    public void setDelayedActionStep(int delayedActionStep) {
        if (delayedActionStep < 1) {
            throw new IllegalArgumentException("delayedActionStep must be at least 1.");
        }
        this.delayedActionStep = delayedActionStep;
    }

    public void setSensorRange(int sensorRange) {
        if (sensorRange < 0) {
            throw new IllegalArgumentException("sensorRange cannot be negative.");
        }
        this.sensorRange = sensorRange;
    }

    public void setNoisySensorOuterRange(int noisySensorOuterRange) {
        if (noisySensorOuterRange < 0) {
            throw new IllegalArgumentException("noisySensorOuterRange cannot be negative.");
        }
        this.noisySensorOuterRange = noisySensorOuterRange;
    }

    public void setRandomEnableShieldPercent(int randomEnableShieldPercent) {
        if (randomEnableShieldPercent < 0 || randomEnableShieldPercent > 100) {
            throw new IllegalArgumentException("randomEnableShieldPercent must be in [0, 100].");
        }
        this.randomEnableShieldPercent = randomEnableShieldPercent;
    }

    public void setFixPrediction(boolean fixPrediction, double fixedPredictionTargetSpeedDelta) {
        setFixPrediction(fixPrediction);
        setFixedPredictionTargetSpeedDelta(fixedPredictionTargetSpeedDelta);
    }

    public void setFixedPredictionTargetSpeedDelta(double fixedPredictionTargetSpeedDelta) {
        if (fixedPredictionTargetSpeedDelta < 0.0) {
            throw new IllegalArgumentException("fixedPredictionTargetSpeedDelta cannot be negative.");
        }
        this.fixedPredictionTargetSpeedDelta = fixedPredictionTargetSpeedDelta;
    }

    public void setAggressiveV3TtcThreshold(double aggressiveV3TtcThreshold) {
        if (aggressiveV3TtcThreshold <= 0.0) {
            throw new IllegalArgumentException("aggressiveV3TtcThreshold must be positive.");
        }
        this.aggressiveV3TtcThreshold = aggressiveV3TtcThreshold;
    }

    public void setSandboxNpcPolitenessMode(SandboxNpcPolitenessMode sandboxNpcPolitenessMode) {
        if (sandboxNpcPolitenessMode == null) {
            throw new IllegalArgumentException("sandboxNpcPolitenessMode cannot be null.");
        }
        this.sandboxNpcPolitenessMode = sandboxNpcPolitenessMode;
    }

    public void setRealWorldEngineType(RealWorldEngineType realWorldEngineType) {
        if (realWorldEngineType == null) {
            throw new IllegalArgumentException("realWorldEngineType cannot be null.");
        }
        this.realWorldEngineType = realWorldEngineType;
    }

    public void setInitialEgoSpeed(double initialEgoSpeed) {
        if (!Double.isFinite(initialEgoSpeed) || initialEgoSpeed < 0.0) {
            throw new IllegalArgumentException("initialEgoSpeed must be a finite non-negative value.");
        }
        this.initialEgoSpeed = initialEgoSpeed;
    }

    public void setVehicleSpacing(double vehicleSpacing) {
        if (!Double.isFinite(vehicleSpacing) || vehicleSpacing <= 0.0) {
            throw new IllegalArgumentException("vehicleSpacing must be a finite positive value.");
        }
        this.vehicleSpacing = vehicleSpacing;
    }

}
