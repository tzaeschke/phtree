/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.hd;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;
import java.util.Random;

import org.junit.Test;

import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.test.util.TestUtil;
import ch.ethz.globis.phtree.util.Bits;

/**
 *  This test harness contains all of the tests for trees whose number of dimensions k will
 *  be higher than 60.
 *
 */
public class TestHighDimensions {

	private static final int[] DIMS = {60, 63, 64, 65, 127, 128, 129, 191, 192, 193, 255, 256, 257};
	
	
    private PhTree<long[]> create(int dim) {
        return TestUtil.newTreeHD(dim);
    }

    @Test
    public void testHighD64Neg() {
        final int N = 1000;
        Random R = new Random(0);

        for (int DIM : DIMS) {
            //System.out.println("d="+ DIM);
            long[][] vals = new long[N][];
            PhTree<long[]> ind = create(DIM);
            for (int i = 0; i < N; i++) {
                long[] v = new long[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextLong();
                }
                vals[i] = v;
                assertNull(Bits.toBinary(v), ind.put(v, v));
            }

            //delete all
            for (long[] v: vals) {
                assertTrue("DIM=" + DIM + " v=" + Bits.toBinary(v), ind.contains(v));
                assertNotNull(ind.remove(v));
            }

            //check empty result
            long[] min = new long[DIM];
            long[] max = new long[DIM];
            for (int i = 0; i < DIM; i++) {
                min[i] = Long.MIN_VALUE;
                max[i] = Long.MAX_VALUE;
            }
            Iterator<long[]> it = ind.query(min, max);
            assertFalse(it.hasNext());

            assertEquals(0, ind.size());
        }
    }

    @Test
    public void testQueryHighD() {
        final int N = 1000;
        final long mask = ~(1L<<(63));  //only positive numbers!
        Random R = new Random(0);

        for (int DIM : DIMS) {
            //System.out.println("d="+ DIM);
            PhTree<long[]> ind = create(DIM);
            for (int i = 0; i < N; i++) {
                long[] v = new long[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextLong() & mask;
                }
                assertNull(Bits.toBinary(v), ind.put(v, v));
            }

            //check empty result
            Iterator<long[]> it;
            int n = 0;
            long[] min = new long[DIM];
            long[] max = new long[DIM];
            it = ind.query(min, max);
            while(it.hasNext()) {
                long[] v = it.next();
                assertEquals(v[0], 0);
                n++;
            }
            assertFalse(it.hasNext());
            assertEquals(0, n);

            //check full result
            for (int i = 0; i < DIM; i++) {
                min[i] = 0;
                max[i] = Long.MAX_VALUE & mask;
            }
            it = ind.query(min, max);
            for (int i = 0; i < N; i++) {
                n++;
                long[] v = it.next();
                assertNotNull(v);
            }
            assertFalse(it.hasNext());

            //check partial result
            int n2 = 0;
            //0, 0, 0, 50, 0, 50, 0, 50, 0, 50
            max[0] = 0;
            it = ind.query(min, max);
            while (it.hasNext()) {
                n2++;
                long[] v = it.next();
                assertEquals(0, v[0]);
            }
            assertTrue(n2 < n);
        }
    }

    @Test
    public void testQueryHighD64() {
        final int N = 1000;
        final long mask = Long.MAX_VALUE;
        Random R = new Random(0);

        for (int DIM : DIMS) {
            //System.out.println("d="+ DIM);
            PhTree<long[]> ind = create(DIM);
            for (int i = 0; i < N; i++) {
                long[] v = new long[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextLong() & mask;
                }
                assertNull(Bits.toBinary(v), ind.put(v, v));
            }

            //check empty result
            Iterator<long[]> it;
            int n = 0;
            long[] min = new long[DIM];
            long[] max = new long[DIM];
            it = ind.query(min, max);
            while(it.hasNext()) {
                long[] v = it.next();
                assertEquals(v[0], 0);
                n++;
            }
            assertFalse(it.hasNext());
            assertEquals(0, n);

            //check full result
            for (int i = 0; i < DIM; i++) {
                min[i] = 0;
                max[i] = Long.MAX_VALUE & mask;
            }
            it = ind.query(min, max);
            for (int i = 0; i < N; i++) {
                n++;
                long[] v = it.next();
                assertNotNull(v);
            }
            assertFalse(it.hasNext());

            //check partial result
            int n2 = 0;
            //0, 0, 0, 50, 0, 50, 0, 50, 0, 50
            max[0] = 0;
            it = ind.query(min, max);
            while (it.hasNext()) {
                n2++;
                long[] v = it.next();
                assertEquals(0, v[0]);
            }
            assertTrue(n2 < n);
        }
    }

    @Test
    public void testQueryHighD64Neg() {
        final int N = 1000;
        Random R = new Random(0);

        for (int DIM : DIMS) {
            //System.out.println("d="+ DIM);
            PhTree<long[]> ind = create(DIM);
            for (int i = 0; i < N; i++) {
                long[] v = new long[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextLong();
                }
                assertNull(Bits.toBinary(v), ind.put(v, v));
            }

            //check empty result
            Iterator<long[]> it;
            int n = 0;
            long[] min = new long[DIM];
            long[] max = new long[DIM];
            it = ind.query(min, max);
            while(it.hasNext()) {
                long[] v = it.next();
                assertEquals(v[0], 0);
                n++;
            }
            assertFalse(it.hasNext());
            assertEquals(0, n);

            //check full result
            for (int i = 0; i < DIM; i++) {
                min[i] = Long.MIN_VALUE;
                max[i] = Long.MAX_VALUE;
            }
            it = ind.query(min, max);
            for (int i = 0; i < N; i++) {
                n++;
                assertTrue("DIM=" + DIM + " i=" + i, it.hasNext());
                long[] v = it.next();
                assertNotNull(v);
            }
            assertFalse(it.hasNext());
            assertEquals(N, n);

            //check partial result
            int n2 = 0;
            //0, 0, ...
            min[0] = 0;
            max[0] = 0;
            it = ind.query(min, max);
            while (it.hasNext()) {
                n2++;
                long[] v = it.next();
                assertEquals(0, v[0]);
            }
            assertTrue(n2 < n);
        }
    }
}
