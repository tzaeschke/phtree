package ch.ethz.globis.phtree.util;

/**
 * Type of mapper that does not use the value of the PHEntry, only the key.
 */
public interface PhMapperK<T, R> extends PhMapper<T, R> {

    static <T> PhMapperK<T, long[]> LONG_ARRAY() {
        return e -> (e.getKey());
    }

    static <T> PhMapperK<T, double[]> DOUBLE_ARRAY() {
        return e -> (toDouble(e.getKey()));
    }

    static double[] toDouble(long[] point) {
        double[] d = new double[point.length];
        for (int i = 0; i < d.length; i++) {
            d[i] = BitTools.toDouble(point[i]);
        }
        return d;
    }

}