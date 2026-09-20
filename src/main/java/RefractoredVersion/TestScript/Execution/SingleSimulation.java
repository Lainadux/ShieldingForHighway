package RefractoredVersion.TestScript.Execution;


import RefractoredVersion.Engine.telemetry.ActionAcceptanceLog;
import RefractoredVersion.Engine.telemetry.BeforeCrashActionLog;
import RefractoredVersion.Engine.telemetry.CollisionLog;
import RefractoredVersion.Engine.ego.EgoVehicle;
import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.vehicle.Vehicle;
import RefractoredVersion.Engine.VehicleGenerator;
import RefractoredVersion.TestScript.Config.JavaMomentumConfig;
import RefractoredVersion.TestScript.Records.SimulationRunResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public class SingleSimulation {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public SimulationRunResult run(JavaMomentumConfig config,
                                   boolean rendered,
                                   boolean captureRuntimeCrash) throws Exception {
        JavaHighwayEngine realWorld = new JavaHighwayEngine();
        realWorld.setRenderEnabled(rendered);
        realWorld.setFrequency(config.getFrequency());
        realWorld.config = config;

        List<Vehicle> vehicles = VehicleGenerator.generateVehicles(config);
        String initialStateJson = GSON.toJson(vehicles);
        realWorld.vehicles = vehicles;

        try {
            for (int i = 0; i < config.getDuration() * config.getFrequency(); i++) {
                realWorld.step();
                if (rendered) {
                    realWorld.render();
                }
            }

            return result(initialStateJson, false, null, realWorld);
        } catch (RuntimeException e) {
            if (!captureRuntimeCrash) {
                throw e;
            }
            return result(initialStateJson, true, e, realWorld);
        }
    }

    private SimulationRunResult result(String initialStateJson,
                                       boolean crashed,
                                       RuntimeException crashException,
                                       JavaHighwayEngine engine) {
        return new SimulationRunResult(
                initialStateJson,
                crashed,
                crashException,
                getAiDecisionCount(engine),
                getRejectedAiDecisionCount(engine),
                engine.getEgoFinalX(),
                getBeforeCrashActions(engine),
                getCollisionLog(engine),
                getActionAcceptanceSequence(engine)
        );
    }

    private int getAiDecisionCount(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? 0 : egoVehicle.getAiDecisionCount();
    }

    private int getRejectedAiDecisionCount(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? 0 : egoVehicle.getRejectedAiDecisionCount();
    }

    private List<BeforeCrashActionLog> getBeforeCrashActions(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? List.of() : egoVehicle.retrieveBeforeCrashActions();
    }

    private CollisionLog getCollisionLog(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? null : egoVehicle.retrieveCrashCollisionLog();
    }

    private List<ActionAcceptanceLog> getActionAcceptanceSequence(JavaHighwayEngine engine) {
        EgoVehicle egoVehicle = getEgoVehicle(engine);
        return egoVehicle == null ? List.of() : egoVehicle.retrieveActionAcceptanceSequence();
    }

    private EgoVehicle getEgoVehicle(JavaHighwayEngine engine) {
        if (engine.vehicles == null) {
            return null;
        }
        for (Vehicle vehicle : engine.vehicles) {
            if (vehicle instanceof EgoVehicle egoVehicle) {
                return egoVehicle;
            }
        }
        return null;
    }
}
