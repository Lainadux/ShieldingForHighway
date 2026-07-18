package RefractoredVersion.Shield;

import it.unicam.quasylab.jspear.DefaultRandomGenerator;
import it.unicam.quasylab.jspear.EvolutionSequence;
import it.unicam.quasylab.jspear.SampleSet;
import it.unicam.quasylab.jspear.SystemState;

import java.util.List;

public class FixedEvolutionSequence extends EvolutionSequence {
    FixedEvolutionSequence(List<SampleSet<SystemState>> sequence) {
        super(null, new DefaultRandomGenerator(), sequence);
    }
}