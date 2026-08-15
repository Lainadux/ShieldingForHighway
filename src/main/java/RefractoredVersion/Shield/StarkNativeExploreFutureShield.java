package RefractoredVersion.Shield;

import RefractoredVersion.Engine.Action;
import RefractoredVersion.Engine.JavaHighwayEngine;

import java.util.List;

public class StarkNativeExploreFutureShield extends StarkNativeShield {
    public StarkNativeExploreFutureShield(JavaHighwayEngine sourceEngine, List<Action> futureActions) {
        super(sourceEngine, futureActions);
    }
}
