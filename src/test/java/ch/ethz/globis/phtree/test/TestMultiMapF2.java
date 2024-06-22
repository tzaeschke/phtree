/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import ch.ethz.globis.phtree.PhDistanceF;
import ch.ethz.globis.phtree.PhEntryDistF;
import ch.ethz.globis.phtree.PhTreeMultiMapF2;
import ch.ethz.globis.phtree.PhTreeMultiMapF2.*;
import ch.ethz.globis.phtree.util.BitTools;
import ch.ethz.globis.phtree.util.Bits;
import ch.ethz.globis.phtree.util.PhTreeStats;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestMultiMapF2 {

    private <T> PhTreeMultiMapF2<T> newTree(int DIM) {
        return PhTreeMultiMapF2.create(DIM);
    }

    @Test
    public void testCRUD() {
        PhTreeMultiMapF2<Integer> idx = newTree(2);
        Random R = new Random(0);
        int DIM = 3;
        int DX = 3;
        int N = 1000;
        HashMap<Integer, double[]> map = new HashMap<>();

        int id = 1;
        for (int i = 0; i < N; i++) {
            double[] v = new double[DIM];
            for (int j = 0; j < DIM; j++) {
                v[j] = R.nextDouble();
            }
            for (int x = 0; x < DX; x++) {
                map.put(id, v);
                assertTrue(Bits.toBinary(v), idx.put(v, id));
                assertTrue(idx.contains(v, id));
                ArrayList<Integer> list = new ArrayList<>();
                idx.get(v).forEach(list::add);
                for (int x2 = 0; x2 <= x; x2++) {
                    assertTrue(list.contains(id - x2));
                }
                id++;
            }
        }

        assertEquals(N * DX, map.size());
        assertEquals(N * DX, idx.size());

        // replace values
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            assertTrue(idx.replace(e.getValue(), e.getKey(), -e.getKey()));
        }

        assertEquals(N * DX, idx.size());

        // update keys
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            double[] v2 = new double[DIM];
            for (int j = 0; j < DIM; j++) {
                // preserve duplicates
                v2[j] = e.getValue()[j] + 0.1;
            }
            assertTrue(idx.update(e.getValue(), -e.getKey(), v2));
            assertFalse(idx.update(e.getValue(), -e.getKey(), v2));
            map.put(e.getKey(), v2);
        }

        assertEquals(N * DX, idx.size());

        // remove
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            if (idx.size() < N * DX / 2) {
                // The key may have already been removed
                if (idx.get(e.getValue()).iterator().hasNext()) {
                    assertEquals(-e.getKey(), (int) idx.remove(e.getValue()).iterator().next());
                }
                assertFalse(idx.remove(e.getValue()).iterator().hasNext());
                assertFalse(idx.remove(e.getValue(), -e.getKey()));
            } else {
                assertTrue(idx.remove(e.getValue(), -e.getKey()));
                assertFalse(idx.remove(e.getValue(), -e.getKey()));
            }
        }

        assertEquals(0, idx.size());
    }

    /**
     * Test CRUD operations with new JDK 8 functions: putIfAbsent(), computeIfAbsent(), compute(), computeIfPresent().
     */
    @Test
    public void testCRUD_JDK8() {
        PhTreeMultiMapF2<Integer> idx = newTree(2);
        Random R = new Random(0);
        int DIM = 3;
        int DX = 3;
        int N = 1000;
        HashMap<Integer, double[]> map = new HashMap<>();

        int id = 1;
        for (int i = 0; i < N; i++) {
            double[] v = new double[DIM];
            for (int j = 0; j < DIM; j++) {
                v[j] = R.nextDouble();
            }
            for (int x = 0; x < DX; x++) {
                map.put(id, v);
                switch (R.nextInt(3)) {
                    case 0:
                        assertNull(idx.putIfAbsent(v, id));
                        assertEquals(id, (int) idx.putIfAbsent(v, id));
                        break;
                    case 1: {
                        final int id2 = id;
                        assertEquals(id2, (int) idx.computeIfAbsent(v, id2, v2 -> id2));
                        assertNull(idx.computeIfAbsent(v, id2, v2 -> id2));
                        break;
                    }
                    case 2:
                        final int id2 = id;
                        assertEquals(id2, (int) idx.compute(v, id, (v2, idNull2) -> id2));
                        // idempotent operation:
                        assertEquals(id2, (int) idx.compute(v, id, (v2, idNull2) -> id2));
                        break;
                    default:
                        throw new IllegalStateException();
                }
                assertTrue(idx.contains(v, id));
                assertTrue(idx.get(v).iterator().hasNext());
                id++;
            }
        }

        assertEquals(N * DX, map.size());
        assertEquals(N * DX, idx.size());

        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            assertTrue(idx.contains(e.getValue(), e.getKey()));
        }

        // replace values
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            if (R.nextBoolean()) {
                assertEquals(-e.getKey(), (int) idx.compute(e.getValue(), e.getKey(), (v2, id2) -> -id2));
            } else {
                assertEquals(-e.getKey(), (int) idx.computeIfPresent(e.getValue(), e.getKey(), (v2, id2) -> -id2));
            }
        }

        assertEquals(N * DX, idx.size());

        // remove
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            if (idx.size() <= N * DX / 2) {
                assertNull(idx.compute(e.getValue(), -e.getKey(), (v2, id2) -> null));
                assertNull(idx.compute(e.getValue(), -e.getKey(), (v2, id2) -> null));
            } else {
                assertNull(idx.computeIfPresent(e.getValue(), -e.getKey(), (v2, id2) -> null));
                assertNull(idx.computeIfPresent(e.getValue(), -e.getKey(), (v2, id2) -> null));
            }
            assertFalse(idx.contains(e.getValue(), e.getKey()));
        }

        assertEquals(0, idx.size());
    }


    @Test
    public void testRangeQuery() {
        PhTreeMultiMapF2<double[]> idx = newTree(2);
        idx.put(new double[]{2, 2}, new double[]{2, 2});
        idx.put(new double[]{2, 2}, new double[]{2, 2});
        idx.put(new double[]{2, 2}, new double[]{2, 2});
        idx.put(new double[]{1, 1}, new double[]{1, 1});
        idx.put(new double[]{1, 3}, new double[]{1, 3});
        idx.put(new double[]{3, 1}, new double[]{3, 1});

        List<double[]> result = toList(idx.rangeQuery(0, 3, 3));
        assertTrue(result.isEmpty());

        result = toList(idx.rangeQuery(1, 2, 2));
        assertEquals(3, result.size());
        check(result.get(0), 2, 2);
        check(result.get(1), 2, 2);
        check(result.get(2), 2, 2);

        result = toList(idx.rangeQuery(1, 1, 1));
        assertEquals(1, result.size());
        check(result.get(0), 1, 1);

        result = toList(idx.rangeQuery(1, 1, 3));
        assertEquals(1, result.size());
        check(result.get(0), 1, 3);

        result = toList(idx.rangeQuery(1, 3, 1));
        assertEquals(1, result.size());
        check(result.get(0), 3, 1);
    }

    @Test
    public void testKNN() {
        PhTreeMultiMapF2<double[]> idx = newTree(2);
        idx.put(new double[]{2, 2}, new double[]{2, 2});
        idx.put(new double[]{2, 2}, new double[]{2, 2});
        idx.put(new double[]{2, 2}, new double[]{2, 2});
        idx.put(new double[]{1, 1}, new double[]{1, 1});
        idx.put(new double[]{1, 3}, new double[]{1, 3});
        idx.put(new double[]{3, 1}, new double[]{3, 1});

        List<PhEntryDistF<double[]>> result = toList(idx.nearestNeighbour(0, 3, 3));
        assertTrue(result.isEmpty());

        result = toList(idx.nearestNeighbour(3, 2, 2));
        check(result.get(0).getKey(), 2, 2);
        check(result.get(1).getKey(), 2, 2);
        check(result.get(2).getKey(), 2, 2);
        assertTrue(3 <= result.size());

        result = toList(idx.nearestNeighbour(1, 1, 1));
        assertTrue(!result.isEmpty());
        check(result.get(0).getKey(), 1, 1);

        result = toList(idx.nearestNeighbour(1, 1, 3));
        assertTrue(!result.isEmpty());
        check(result.get(0).getKey(), 1, 3);

        result = toList(idx.nearestNeighbour(1, 3, 1));
        assertTrue(!result.isEmpty());
        check(result.get(0).getKey(), 3, 1);
    }

    @Test
    public void testKnnLarge() {
        final int DIM = 3;
        final int LOOP = 10;
        final int N = 1000;
        final int N_DUPL = 3;
        final int NQ = 100;
        final int MAXV = 1000;
        final int MIN_RESULT = 10;
        final Random R = new Random(0);
        final ArrayList<EntryDist<Integer>> list = new ArrayList<>();
        for (int d = 0; d < LOOP; d++) {
            list.clear();
            int id = 0;
            PhTreeMultiMapF2<Integer> ind = newTree(DIM);
            for (int i = 0; i < N; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextDouble() * MAXV;
                }
                for (int dupl = 0; dupl <= i % N_DUPL; dupl++) {
                    ind.put(v, id);
                    list.add(new EntryDist<>(v, id, 0));
                    id++;
                }
            }
            assertEquals(id, ind.size());

            PhKnnQueryF<Integer> q = ind.nearestNeighbour(MIN_RESULT, new double[DIM]);
            for (int i = 0; i < NQ; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextDouble() * MAXV;
                }
                list.forEach(xx -> xx.dist = dist(v, xx.key));
                list.sort((o1, o2) -> Double.compare(o1.dist, o2.dist));
                List<PhEntryDistF<Integer>> nnList = toList(q.reset(MIN_RESULT, PhDistanceF.THIS, v));
                assertFalse("i=" + i + " d=" + d, nnList.isEmpty());
                for (int x = 0; x < MIN_RESULT; ++x) {
                    assertEquals(list.get(x).dist, nnList.get(x).dist(), 0.0);
                    assertArrayEquals(list.get(x).key, nnList.get(x).getKey(), 0.0);
                }
            }
        }
    }

    @Test
    public void testQueryFullExtent() {
        final int DIM = 5;
        final int N = 10000;
        Random R = new Random(0);

        for (int d = 0; d < DIM; d++) {
            PhTreeMultiMapF2<double[]> ind = newTree(DIM);
            for (int i = 0; i < N; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextDouble();
                }
                ind.put(v, v);
                ind.put(v, v);
            }

            //check full result
            int n = 0;
            PhExtentF<double[]> it = ind.queryExtent();
            for (int i = 0; i < N * 2; i++) {
                it.next();
                n++;
            }
            assertFalse(it.hasNext());
            assertEquals(N * 2, n);

            it.reset();
            assertTrue(it.hasNext());
        }
    }

    @Test
    public void testQuery() {
        final int MAX_DIM = 10;
        final int N = 1000;
        Random R = new Random(0);

        for (int DIM = 3; DIM <= MAX_DIM; DIM++) {
            PhTreeMultiMapF2<double[]> ind = newTree(DIM);
            for (int i = 0; i < N; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextDouble();
                }
                assertTrue(Bits.toBinary(v), ind.put(v, v));
                assertTrue(Bits.toBinary(v), ind.put(v, v));
            }

            double[] min = new double[DIM];
            double[] max = new double[DIM];
            for (int i = 0; i < DIM; i++) {
                min[i] = -0.5;
                max[i] = 0.5;
            }

            // query
            PhQueryF<double[]> it = ind.query(min, max);
            int n = 0;
            while (it.hasNext()) {
                n++;
                it.next();
            }
            assertTrue(n > 0);
            assertTrue(n < N);
            assertEquals(0, n % 2);

            // reset
            it.reset(min, max);
            n = 0;
            while (it.hasNext()) {
                n++;
                it.next();
            }
            assertTrue(n > 0);
            assertTrue(n < N);
            assertEquals(0, n % 2);
        }
    }

    @Test
    public void testRangeQueryWithDistanceFunction() {
        final int DIM = 3;
        final int LOOP = 10;
        final int N = 1000;
        final int NQ = 100;
        final int MAXV = 1000;
        final int range = MAXV / 2;
        final Random R = new Random(0);
        for (int d = 0; d < LOOP; d++) {
            PhTreeMultiMapF2<Integer> ind = newTree(DIM);
            PhRangeQueryF<Integer> q = ind.rangeQuery(1, PhDistanceF.THIS, new double[DIM]);
            for (int i = 0; i < N; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextDouble() * MAXV;
                }
                ind.put(v, 2 * i);
                ind.put(v, 2 * i + 1);
            }
            for (int i = 0; i < NQ; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextDouble() * MAXV;
                }
                double[] exp = rangeQuery(ind, range, v).get(0);
                List<double[]> nnList = toList(q.reset(range, v));
                assertFalse("i=" + i + " d=" + d, nnList.isEmpty());
                double[] nn = nnList.get(0);
                check(v, exp, nn);
            }
        }
    }

    private ArrayList<double[]> rangeQuery(PhTreeMultiMapF2<?> tree, double range, double[] q) {
        ArrayList<double[]> points = new ArrayList<>();
        PhIteratorF<?> i = tree.queryExtent();
        while (i.hasNext()) {
            double[] cand = i.nextEntry().getKey();
            double dNew = dist(q, cand);
            if (dNew < range) {
                points.add(cand);
            }
        }
        return points;
    }

    private void check(double[] v, double[] c1, double[] c2) {
        for (int i = 0; i < c1.length; i++) {
            if (c1[i] != c2[i]) {
                double d1 = dist(v, c1);
                double d2 = dist(v, c2);
                double maxEps = Math.abs(d2 - d1) / d1;
                if (maxEps >= 1) {
                    System.out.println("WARNING: different values found: " + d1 + "/" + d2);
                    System.out.println("c1=" + Arrays.toString(c1));
                    System.out.println("c2=" + Arrays.toString(c2));
                    fail();
                }
                break;
            }
        }
    }

    private double dist(double[] v1, double[] v2) {
        double d = 0;
        for (int i = 0; i < v1.length; i++) {
            double dl = v1[i] - v2[i];
            d += dl * dl;
        }
        return Math.sqrt(d);
    }

    private void check(double[] t, double... ints) {
        for (int i = 0; i < ints.length; i++) {
            assertEquals("i=" + i + " | " + toBinary(ints) + " / " + toBinary(t), ints[i], t[i], 0.0);
        }
    }

    private List<double[]> toList(PhRangeQueryF<?> q) {
        ArrayList<double[]> ret = new ArrayList<>();
        while (q.hasNext()) {
            ret.add(q.nextEntry().getKey());
        }
        return ret;
    }

    private <T> List<PhEntryDistF<T>> toList(PhKnnQueryF<T> q) {
        ArrayList<PhEntryDistF<T>> ret = new ArrayList<>();
        while (q.hasNext()) {
            if (ret.size() % 2 == 0) {
                ret.add(q.nextEntry());
            } else {
                PhEntryDistF<T> e = q.nextEntryReuse();
                ret.add(new PhEntryDistF<>(e.getKey().clone(), e.getValue(), e.dist()));
            }
        }
        return ret;
    }

    private String toBinary(double[] d) {
        long[] l = new long[d.length];
        for (int i = 0; i < l.length; i++) {
            l[i] = BitTools.toSortableLong(d[i]);
        }
        return Bits.toBinary(l);
    }

    private static class EntryDist<T> {
        double[] key;
        T value;
        double dist;

        public EntryDist(double[] key, T v, double dist) {
            this.key = key;
            this.value = v;
            this.dist = dist;
        }
    }

    @Test
    public void testEmptyTreeStats() {
        testEmptyTreeStats(2);
        testEmptyTreeStats(60);
        testEmptyTreeStats(600);
    }

    private void testEmptyTreeStats(int dim) {
        PhTreeMultiMapF2<Integer> ind = newTree(dim);
        PhTreeStats stats = ind.getStats();
        assertEquals(0, stats.nNodes);
    }
}
