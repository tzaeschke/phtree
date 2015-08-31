package ch.ethz.globis.pht.util;

/**
 * Type of mapper that does not use the key of the PHEntry, only the value.
 */
public interface PhMapperV<T, R> extends PhMapper<T, R> {

    static <T> PhMapperV<T, T> VALUE() {
        return e -> (e.getValue());
    }

}
