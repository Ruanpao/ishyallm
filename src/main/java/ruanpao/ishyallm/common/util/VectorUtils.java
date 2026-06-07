package ruanpao.ishyallm.common.util;

import java.util.List;

public final class VectorUtils {

    private VectorUtils() {}

    public static List<Double> toDoubleList(List<Float> floats) {
        return floats.stream().map(Float::doubleValue).toList();
    }
}
