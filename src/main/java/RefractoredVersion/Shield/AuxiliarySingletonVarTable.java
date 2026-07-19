package RefractoredVersion.Shield;

public enum AuxiliarySingletonVarTable {
    crashed,
    finalFrontVehicleIndexInCurrentLane,
    finalFrontVehicleIndexInLeftLane,
    finalFrontVehicleIndexInRightLane,
    isInitialChangeLane,
    initialIntention,
    rearThreatRearVehicleIndex,
    rearThreatBeforeAcceleration;

    public static int baseIndex(int vehicleCount) {
        return vehicleCount * VarTable.values().length;
    }

    public static int index(int vehicleCount, AuxiliarySingletonVarTable variable) {
        return baseIndex(vehicleCount) + variable.ordinal();
    }

    public static int stateSize(int vehicleCount) {
        return baseIndex(vehicleCount) + values().length;
    }
}
