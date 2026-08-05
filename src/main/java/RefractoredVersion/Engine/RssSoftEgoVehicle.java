package RefractoredVersion.Engine;

import java.util.List;

public class RssSoftEgoVehicle extends RssStrictEgoVehicle {
    @Override
    protected List<Integer> adjacentLaneIndicesIncludingCurrent() {
        return List.of(getLaneIndex());
    }
}
