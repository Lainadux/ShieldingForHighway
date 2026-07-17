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

import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.JavaHighwayAiClient;
import RefractoredVersion.Engine.Vehicle;
import RefractoredVersion.Engine.VehicleGenerator;
import RefractoredVersion.TestScript.Config.Config;
import RefractoredVersion.TestScript.Config.FallBackMode;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class Runner {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final int THREAD_COUNT = 4;

    Config config;
    public void run(Config config) throws Exception {
        this.config = config;
        if(config instanceof JavaMomentumConfig){
            JavaMomentumConfig javaMomentumConfig = (JavaMomentumConfig) config;
            if(!javaMomentumConfig.isGenLogs()){
                singleRun(javaMomentumConfig.isQuickTest());
                return;
            }

            int simulations = javaMomentumConfig.getNumOFSimulations() == null
                    ? javaMomentumConfig.DEFAULT_NUM_OF_SIMULATIONS
                    : javaMomentumConfig.getNumOFSimulations();
            int workerCount = Math.max(1, Math.min(THREAD_COUNT, simulations));
            ExecutorService executor = Executors.newFixedThreadPool(workerCount);
            List<Future<?>> futures = new ArrayList<>();
            AtomicInteger completed = new AtomicInteger(0);
            try {
                for(int i = 0; i < simulations; i++){
                    final int simulationIndex = i;
                    futures.add(executor.submit(() -> {
                        boolean crashed = false;
                        String initialStateJson = null;
                        try {
                            initialStateJson = singleRun(javaMomentumConfig, false);
                        } catch (RuntimeException e) {
                            crashed = true;
                            System.err.println("Simulation " + simulationIndex + " crashed: " + e.getMessage());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            if (shouldSaveLogs(javaMomentumConfig) && initialStateJson != null) {
                                saveInitialStateLog(javaMomentumConfig, initialStateJson, crashed, simulationIndex);
                            }
                        }
                        int done = completed.incrementAndGet();
                        System.out.printf("Finished simulation %d/%d: %s%n",
                                done, simulations, crashed ? "crashed" : "safe");
                        return null;
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            } finally {
                executor.shutdownNow();
                JavaHighwayAiClient.stopAll();
            }
        }
    }

    public String singleRun(boolean rendered) throws Exception {
        if (config instanceof JavaMomentumConfig javaMomentumConfig) {
            return singleRun(javaMomentumConfig, rendered);
        }

        return null;
    }

    private String singleRun(JavaMomentumConfig javaMomentumConfig, boolean rendered) throws Exception {
        if(config instanceof JavaMomentumConfig){
            JavaHighwayEngine realWorld = new JavaHighwayEngine();
            realWorld.setRenderEnabled(rendered);
            realWorld.setFrequency(javaMomentumConfig.getFrequency());
            realWorld.config = javaMomentumConfig;
            List<Vehicle> vehicles = VehicleGenerator.generateVehicles(javaMomentumConfig);
            String initialStateJson = GSON.toJson(vehicles);
            realWorld.fallBackMode= FallBackMode.DEFAULT;
            realWorld.vehicles = vehicles;

            for(int i =0; i<javaMomentumConfig.getDuration()* javaMomentumConfig.getFrequency();i++){

                realWorld.step();
                if(rendered){
                    realWorld.render();
                }
            }

            return initialStateJson;

        }

        return null;
    }

    private boolean shouldSaveLogs(JavaMomentumConfig config) {
        return config.getPATH_TO_SAVE() != null && !config.getPATH_TO_SAVE().isBlank();
    }

    private void saveInitialStateLog(JavaMomentumConfig config, String initialStateJson, boolean crashed, int simulationIndex) {
        String prefix = crashed ? "crashed_" : "safe_";
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        Path logDir = Path.of(config.getPATH_TO_SAVE())
                .resolve(config.getEgoType().name() + "_" + config.getAiProfile().name() + "_LOGS");
        Path logFile = logDir.resolve(prefix + timestamp + "_" + simulationIndex + ".json");

        try {
            Files.createDirectories(logDir);
            Files.writeString(logFile, initialStateJson, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save initial state log: " + logFile, e);
        }
    }
}
