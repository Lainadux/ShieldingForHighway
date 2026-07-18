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
    private EgoType egoType = EgoType.RandomEgoVehicle;
    private int frequency = 40;
    private int predictionTime = 3;
    private double maxTargetSpeed = 40.0;
    private boolean fixPrediction = false;
    private String recoverInitialStateFile;
    private ShieldType shieldType = ShieldType.ALL_SLOWER;
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

}
