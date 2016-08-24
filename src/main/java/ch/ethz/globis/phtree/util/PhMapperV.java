package ch.ethz.globis.phtree.util;

/**
 * Type of mapper that does not use the key of the PHEntry, only the value.
 * 
 * @param <T> Value type
 * @param <R> Result type
 */
public interface PhMapperV<T, R> extends PhMapper<T, R> {

    static <T> PhMapperV<T, T> VALUE() {
        return e -> (e.getValue());
    }

}
