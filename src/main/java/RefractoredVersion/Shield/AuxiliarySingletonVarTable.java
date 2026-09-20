package RefractoredVersion.Shield;

public enum AuxiliarySingletonVarTable {
    crashed,
    @Deprecated
    finalFrontVehicleIndexInCurrentLane,
    @Deprecated
    finalFrontVehicleIndexInLeftLane,
    @Deprecated
    finalFrontVehicleIndexInRightLane,
    isInitialChangeLane,
    initialIntention,
    @Deprecated
    rearThreatRearVehicleIndex,
    @Deprecated
    rearThreatBeforeAcceleration,
    @Deprecated
    endStateTTC;


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
