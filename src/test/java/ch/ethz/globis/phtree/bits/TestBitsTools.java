/*
 * Copyright 2011-2013 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.bits;

import ch.ethz.globis.phtree.util.BitTools;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class TestBitsTools {

    void checkInverse(double value) {
        long l = BitTools.toSortableLong(value);
        double d = BitTools.toDouble(l);
        assertEquals(value, d, 0.0);
        if (!Double.isNaN(d)) {
            assertTrue(value == d);
        }
    }

    void checkInverse(float value) {
        long l = BitTools.toSortableLong(value);
        float d = BitTools.toFloat(l);
        assertEquals(value, d, 0.0);
        if (!Float.isNaN(d)) {
            assertTrue(value == d);
        }
    }

    @Test
    public void testConversionDouble() {
        checkInverse(0.0);
        checkInverse(-0.0);
        checkInverse(Double.MIN_VALUE);
        checkInverse(Double.MAX_VALUE);
        checkInverse(Double.POSITIVE_INFINITY);
        checkInverse(Double.NEGATIVE_INFINITY);
        checkInverse(Double.NaN);
        checkInverse(Double.MIN_NORMAL);
    }

    @Test
    public void testNegativeZeroDouble() {
        long l = BitTools.toSortableLong(-0.0);
        double d = BitTools.toDouble(l);
        assertEquals("-0.0", Double.toString(-0.0));
        assertEquals("-0.0", Double.toString(d));
    }

    @Test
    public void testOrderingDouble() {
        long neg_inf = BitTools.toSortableLong(Double.NEGATIVE_INFINITY);
        long neg_max = BitTools.toSortableLong(-Double.MAX_VALUE);
        long neg_min = BitTools.toSortableLong(-Double.MIN_VALUE);
        long neg_zero = BitTools.toSortableLong(-0.0);
        long pos_zero = BitTools.toSortableLong(0.0);
        long pos_min = BitTools.toSortableLong(Double.MIN_VALUE);
        long pos_max = BitTools.toSortableLong(Double.MAX_VALUE);
        long pos_inf = BitTools.toSortableLong(Double.POSITIVE_INFINITY);

        assertTrue(neg_inf < neg_max);
        assertTrue(neg_max < neg_min);
        assertTrue(neg_min < neg_zero);
        assertTrue(neg_zero < pos_zero);
        assertTrue(pos_zero < pos_min);
        assertTrue(pos_min < pos_max);
        assertTrue(pos_max < pos_inf);
    }

    @Test
    public void testConversionFloat() {
        checkInverse(0.0f);
        checkInverse(-0.0f);
        checkInverse(Float.MIN_VALUE);
        checkInverse(Float.MAX_VALUE);
        checkInverse(Float.POSITIVE_INFINITY);
        checkInverse(Float.NEGATIVE_INFINITY);
        checkInverse(Float.NaN);
        checkInverse(Float.MIN_NORMAL);
    }

    @Test
    public void testNegativeZeroFloat() {
        long l = BitTools.toSortableLong(-0.0f);
        float d = BitTools.toFloat(l);
        assertEquals("-0.0", Double.toString(-0.0f));
        assertEquals("-0.0", Double.toString(d));
    }

    @Test
    public void testOrderingFloat() {
        long neg_inf = BitTools.toSortableLong(Float.NEGATIVE_INFINITY);
        long neg_max = BitTools.toSortableLong(-Float.MAX_VALUE);
        long neg_min = BitTools.toSortableLong(-Float.MIN_VALUE);
        long neg_zero = BitTools.toSortableLong(-0.0f);
        long pos_zero = BitTools.toSortableLong(0.0f);
        long pos_min = BitTools.toSortableLong(Float.MIN_VALUE);
        long pos_max = BitTools.toSortableLong(Float.MAX_VALUE);
        long pos_inf = BitTools.toSortableLong(Float.POSITIVE_INFINITY);

        assertTrue(neg_inf < neg_max);
        assertTrue(neg_max < neg_min);
        assertTrue(neg_min < neg_zero);
        assertTrue(neg_zero < pos_zero);
        assertTrue(pos_zero < pos_min);
        assertTrue(pos_min < pos_max);
        assertTrue(pos_max < pos_inf);
    }
}
