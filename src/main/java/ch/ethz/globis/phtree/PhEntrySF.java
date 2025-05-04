/*
 * Copyright 2011-2025 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.util.Arrays;

/**
 * Entries in a PH-tree with ranged objects.
 * @param <T> value type of the entry
 */
public class PhEntrySF<T> {

    private final double[] lower;
    private final double[] upper;
    private T value;

    /**
     * Range object constructor.
     * @param lower lower left corner
     * @param upper upper right corner
     * @param value The value associated with the point
     */
    public PhEntrySF(double[] lower, double[] upper, T value) {
        this.lower = lower;
        this.upper = upper;
        this.value = value;
    }

    /**
     * @return the value of the entry
     */
    public T value() {
        return value;
    }

    /**
     * @return lower left corner of the entry
     */
    public double[] lower() {
        return lower;
    }

    /**
     * @return upper right corner of the entry
     */
    public double[] upper() {
        return upper;
    }

    void setValue(T value) {
        this.value = value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof PhEntrySF)) {
            return false;
        }
        PhEntrySF<T> e = (PhEntrySF<T>) obj;
        return Arrays.equals(lower, e.lower) && Arrays.equals(upper, e.upper);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(lower) ^ Arrays.hashCode(upper);
    }

    @Override
    public String toString() {
        return "{" + Arrays.toString(lower) + "," + Arrays.toString(upper) + "} => " + value;
    }
}
