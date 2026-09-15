package RefractoredVersion.Engine;

/**
 * Keeps the AI decision rate at 1 Hz while rechecking the current driving
 * intention with the shield every 0.25 seconds.
 */
public class PeriodicInterventionEgoVehicle extends EgoVehicle {
    private static final int SHIELD_INTERVENTION_FREQUENCY_HZ = 4;

    private int shieldInterventionChecks;
    private int shieldInterventions;

    @Override
    public void planAction() throws Exception {
        if (getEngine().isDecisionTime()) {
            super.planAction();
            return;
        }

        if (isShieldInterventionTime()) {
            checkWhetherCurrentIntentionCanContinue();
        }

        plannedAcceleration = JavaHighwayEngineUtils.computeIdmAcceleration(this, getEngine().vehicles);
        plannedSteering = JavaHighwayEngineUtils.computeSteering(this);
    }

    protected boolean isShieldInterventionTime() {
        int engineFrequency = getEngine().getFrequency();
        if (engineFrequency % SHIELD_INTERVENTION_FREQUENCY_HZ != 0) {
            throw new IllegalStateException(
                    "Engine frequency must be divisible by shield intervention frequency."
            );
        }

        int physicalStepsPerIntervention = engineFrequency / SHIELD_INTERVENTION_FREQUENCY_HZ;
        return getEngine().stepsTaken % physicalStepsPerIntervention == 0;
    }

    protected void checkWhetherCurrentIntentionCanContinue() throws Exception {
        ShieldDecision decision = verifyActionSafe(Action.IDLE);
        shieldInterventionChecks++;

        if (shouldPrintDiagnostics()) {
            System.out.printf(
                    "%s periodic shield check: step=%d time=%.2f action=IDLE%n",
                    decision.safe ? "Safe" : "Unsafe",
                    getEngine().stepsTaken,
                    getEngine().timeElapsed
            );
            System.out.println(decision.diagnosis);
        }

        if (!decision.safe) {
            applyAction(Action.SLOWER);
            shieldInterventions++;
        }
    }

    public int getShieldInterventionChecks() {
        return shieldInterventionChecks;
    }

    public int getShieldInterventions() {
        return shieldInterventions;
    }
}
