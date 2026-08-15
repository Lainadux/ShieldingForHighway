package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.Vehicle;
import it.unicam.quasylab.jspear.distl.DisTLFormula;
import it.unicam.quasylab.jspear.distl.DoubleSemanticsVisitor;
import it.unicam.quasylab.jspear.distl.TargetDisTLFormula;

import java.util.List;

public class InstantHeuristicShield extends HeuristicShield {
    //protected static final double MIN_TTC_THRESHOLD = 8.0;
    public InstantHeuristicShield(List<Vehicle> vehicles, Action candidateAction) {
        super(vehicles, candidateAction);
    }

    @Override
    public boolean verifySafe() {
        if (candidateAction == null) {
            throw new IllegalArgumentException("candidateAction must not be null");
        }

        buildEvolutionSequence();

        DisTLFormula formula = minTtcAlwaysFormula(0);
        DoubleSemanticsVisitor semantics = new DoubleSemanticsVisitor();
        lastRobustness = semantics.eval(formula).eval(30, 0, sequence);
        lastMinTtc = minTtcAtStep(0);
        lastDiagnosis = String.format(
                "InstantHeuristicShield diagnosis: minTTC=%.3f threshold=%.3f robustness=%.3f",
                lastMinTtc,
                MIN_TTC_THRESHOLD,
                lastRobustness
        );

        return lastRobustness >= 0;
    }

    @Override
    protected DisTLFormula minTtcAlwaysFormula(int lastStep) {
        return
                new TargetDisTLFormula(
                        this::stabilizeMinTtc,
                        this::minTtcPenalty,
                        q
                );


    }
    @Override
    protected double minTtcThreshold() {
        return 8.0;
    }

}
