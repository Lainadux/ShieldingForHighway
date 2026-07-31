package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayEngine;

import java.util.List;

public class ExploreFutureRssOShield extends ExploreFutureRssShield {
    public ExploreFutureRssOShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        super(sourceEngine, futureActions);
    }

    public ExploreFutureRssOShield(JavaHighwayEngine sourceEngine, int predictionTime) {
        super(sourceEngine, predictionTime);
    }

    @Override
    protected double improvedRssRequiredGap(double rearSpeed, double frontSpeed, double rearAcceleration) {
        double vRear = Math.max(RssSafetyModel.MIN_SPEED, rearSpeed);
        double vFront = Math.max(RssSafetyModel.MIN_SPEED, frontSpeed);
        double rho = RssSafetyModel.NPC_RESPONSE_TIME;
        double rearAccelerationDuringResponse = RssSafetyModel.MAX_ACCELERATION;
        double rearBrake = RssSafetyModel.MIN_GUARANTEED_BRAKING_DECELERATION;
        double frontBrake = RssSafetyModel.MAX_BRAKING_DECELERATION;

        double term1 = vRear * rho;
        double term2 = 0.5 * rearAccelerationDuringResponse * rho * rho;
        double term3 = Math.pow(vRear + rho * rearAccelerationDuringResponse, 2.0) / (2.0 * rearBrake);
        double term4 = vFront * vFront / (2.0 * frontBrake);

        return Math.max(0.0, term1 + term2 + term3 - term4);
    }
}
