package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.Vehicle;
import it.unicam.quasylab.jspear.distl.DisTLFormula;
import it.unicam.quasylab.jspear.distl.DoubleSemanticsVisitor;

import java.util.List;

/**
 * TTC heuristic evaluated over one 0.25-second decision interval.
 */
public class Heuristic4HzShield extends HeuristicShield {
    private static final int PREDICTION_FREQUENCY_HZ = 4;
    private static final int DECISION_INTERVAL_STEPS = 1;
    private static final int EVOLUTION_SEQUENCE_SIZE = 30;

    public Heuristic4HzShield(List<Vehicle> vehicles, Action candidateAction) {
        super(vehicles, candidateAction);
        frequency = PREDICTION_FREQUENCY_HZ;
    }

    @Override
    public boolean verifySafe() {
        if (candidateAction == null) {
            throw new IllegalArgumentException("candidateAction must not be null");
        }

        buildEvolutionSequence();
        DisTLFormula formula = minTtcAlwaysFormula(DECISION_INTERVAL_STEPS);

        DoubleSemanticsVisitor semantics = new DoubleSemanticsVisitor();
        lastRobustness = semantics.eval(formula).eval(EVOLUTION_SEQUENCE_SIZE, 0, sequence);
        lastMinTtc = minTtcAtStep(DECISION_INTERVAL_STEPS);
        lastDiagnosis = String.format(
                "Heuristic4HzShield diagnosis: minTTC=%.3f threshold=%.3f robustness=%.3f",
                lastMinTtc,
                minTtcThreshold(),
                lastRobustness
        );

        return lastRobustness >= 0.0;
    }
}
