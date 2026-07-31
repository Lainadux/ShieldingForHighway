package RefractoredVersion.Shield;

public final class RssSafetyModel {
    public static final double VEHICLE_LENGTH = 5.0;
    public static final double VEHICLE_WIDTH = 2.0;
    public static final double MAX_SPEED = 40.0;
    public static final double MIN_SPEED = 0.0;
    public static final double MAX_ACCELERATION = 5.0;
    public static final double MAX_BRAKING_DECELERATION = 3.0;
    public static final double MIN_GUARANTEED_BRAKING_DECELERATION = 3.0;
    public static final double NPC_RESPONSE_TIME = 1.0 / 20.0;

    private RssSafetyModel() {

    }

    public static double requiredLongitudinalGap(double rearSpeed, double frontSpeed) {
        double vRear = Math.max(MIN_SPEED, rearSpeed);
        double vFront = Math.max(MIN_SPEED, frontSpeed);
        double rho = NPC_RESPONSE_TIME;
        double aMaxAccel = MAX_ACCELERATION;
        double aMinBrake = MIN_GUARANTEED_BRAKING_DECELERATION;
        double aMaxBrake = MAX_BRAKING_DECELERATION;

        double term1 = vRear * rho;
        double term2 = 0.5 * aMaxAccel * rho * rho;
        double term3 = Math.pow(vRear + rho * aMaxAccel, 2.0) / (2.0 * aMinBrake);
        double term4 = vFront * vFront / (2.0 * aMaxBrake);

        return Math.max(0.0, term1 + term2 + term3 - term4);
    }

    public static double distanceViolation(double rearX, double rearSpeed, double frontX, double frontSpeed) {
        double actualGap = frontX - rearX - VEHICLE_LENGTH;
        double requiredGap = requiredLongitudinalGap(rearSpeed, frontSpeed);
        return Math.max(0.0, requiredGap - actualGap);
    }
}
