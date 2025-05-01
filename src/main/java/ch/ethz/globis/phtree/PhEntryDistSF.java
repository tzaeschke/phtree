/*
 * Copyright 2011-2025 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

public class PhEntryDistSF<T> extends PhEntrySF<T> {
    private double dist;

    /**
     * @param lower lower corner of rectangle key
     * @param upper upper corner of rectangle key
     * @param value value
     * @param dist distance value
     */
    public PhEntryDistSF(double[] lower, double[] upper, T value, double dist) {
        super(lower, upper, value);
        this.dist = dist;
    }

    void setValueDist(T value, double dist) {
        setValue(value);
        this.dist = dist;
    }

    /**
     * @return the distance to the center point of the kNN query
     */
    public double dist() {
        return dist;
    }

    @Override
    public String toString() {
        return super.toString() + " dist=" + dist;
    }
}
