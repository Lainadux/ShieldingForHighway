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

package RefractoredVersion.TestScript;

import RefractoredVersion.Engine.AIProfile;
import RefractoredVersion.Engine.Action;
import RefractoredVersion.TestScript.Config.*;

import java.util.List;

public class TestScripts {

    public static void main(String[] args) throws Exception {
       // config should be  used to set the parameters of the simulation
       JavaMomentumConfig config = new JavaMomentumConfig();

       //config.setShieldType(ShieldType.EXPLORE_FUTURE_DELAYED_ACTION);
       config.setShieldType(ShieldType.EXPLORE_FUTURE_ACTION);
       config.setShieldType(ShieldType.EXPLORE_FUTURE_RSS_ACTION);
       config.setShieldType(ShieldType.EXPLORE_FUTURE_RSS_O_ACTION);
      //config.setShieldType(ShieldType.EXPLORE_FUTURE_BETTER_REFERENCE_ACTION);
       //config.setFutureActions(List.of());


//       config.setEgoType(EgoType.EgoRandomEnableShield);
//       config.setRandomEnableShieldPercent(60);

       config.setEgoType(EgoType.ExploreFutureSlowerVehicle);


//       //Ai- slower- slower
//       config.setEgoType(EgoType.ExploreFutureSlowerVehicle);
//       //AI -
//       config.setEgoType(EgoType.EgoVehicle);
         // AI-idle
       //config.setEgoType(EgoType.ExploreFutureDelayedVehicle);
       config.setAiProfile(AIProfile.adversarial);


       config.setMinX(0);
       config.setMaxX(400);
       config.setDuration(30);
       config.setFrequency(20);
       config.setNumsOfSimulations(100);
       // generate logs in the "EGOTYPE_AIPROFILE_LOGS/timestamp/..."
       config.setPATH_TO_SAVE("src/main/java/RefractoredVersion/logs/" + System.currentTimeMillis() + "/");
       config.setMaxTargetSpeed(40);
       config.setPredictionTime(1);
       // function name is misleading, when set to true targetSpeed is random.
       //config.setFixPrediction(true, 5);
       config.setFixPrediction(true);
       config.setSensorRange(100);
       config.setNoisySensorOuterRange(100);

       config.setTestFunction(TestFunction.RECOVER);
       //config.setRecoverShieldTypeOverride(ShieldType.Ego);
       //config.setTestFunction(TestFunction.GEN_LOGS);
       String logsRoot = "C:\\MSCProject\\codes\\RefractoredVersionRepo\\src\\main\\java\\RefractoredVersion\\logs\\1785468762255\\ExploreFutureSlowerVehicle_adversarial_EXPLORE_FUTURE_RSS_O_ACTION_LOGS\\safe_20260731_053320_281_4.json";

       config.setRecoverInitialStateFile(logsRoot);




       Runner runner = new Runner();
       runner.run(config);
    }
}
