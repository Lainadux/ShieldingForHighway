package RefractoredVersion.Shield;

import it.unicam.quasylab.jspear.EvolutionSequence;
import it.unicam.quasylab.jspear.distl.ConjunctionDisTLFormula;
import it.unicam.quasylab.jspear.distl.DisTLFormula;
import it.unicam.quasylab.jspear.distl.DoubleSemanticsVisitor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Deprecated

public class VerifySafeFunction {



    public Map<String, DisTLFormula> disTLFormulas = new HashMap<>();

    // The evaluation of the aggregatedRisk, which means the conjunction of all the disTLFormulas, is done at time tauF.

    public int risk;
    public int sampleSize = 0;



    //Maybe we can also evaluate disTl in a discrete way, in that case the risk will be evaluated at each time step tau
    public Map<String, Integer> taus = new HashMap<>();
    public Map <String, Double> risks = new HashMap<>();

    public VerifySafeFunction(Map<String, DisTLFormula> disTLFormulas) {
        this.disTLFormulas = disTLFormulas;
        this.sampleSize = 30;
    }



    /**
     * Evaluate the risk of the given evolution sequence using the disTLFormulas.
     * Risk is the value of the conjunction of the disTLFormulas evaluated on the evolution sequence.
     * The formula is evaluated at step 0
     * @param evolutionSequence The evolution sequence to evaluate.
     */
    public double evaluateConjunctRisk(EvolutionSequence evolutionSequence) {
        if (disTLFormulas.isEmpty()) {
            throw new IllegalStateException("No disTLFormulas to evaluate.");
        }
        DoubleSemanticsVisitor semantics = new DoubleSemanticsVisitor();
        DisTLFormula conjunctFormula = disTLFormulas.values().stream()
                .reduce((result, formula) -> new ConjunctionDisTLFormula(result, formula))
                .orElse(null);

        return semantics.eval(conjunctFormula).eval(sampleSize, 0, evolutionSequence);
    }


}


