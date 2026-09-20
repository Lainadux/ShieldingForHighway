package RefractoredVersion.TestScript;


import RefractoredVersion.Engine.JavaHighwayEngine;
import RefractoredVersion.Engine.vehicle.Vehicle;
import RefractoredVersion.Engine.VehicleGenerator;

import java.util.ArrayList;

public class AllNpcPolitenessExperiment {
    public static void main(String[] args) throws Exception {
        JavaHighwayEngine engine = new JavaHighwayEngine();
        engine.setFrequency(20);
        engine.setRenderEnabled(true);

        ArrayList<Vehicle> vehicles = VehicleGenerator.genAllNpc(true);
        engine.vehicles = vehicles;

        for (Vehicle vehicle : engine.vehicles) {
            vehicle.setEngine(engine);
        }

        int duration = 40;
        for (int i = 0; i < duration * engine.getFrequency(); i++) {
            engine.step();
            engine.render();
        }
    }
}
