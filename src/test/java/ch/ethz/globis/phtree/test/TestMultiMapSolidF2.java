/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import ch.ethz.globis.phtree.*;
import ch.ethz.globis.phtree.PhTreeMultiMapSF2.PhExtentF;
import ch.ethz.globis.phtree.PhTreeMultiMapSF2.PhIteratorSF;
import ch.ethz.globis.phtree.PhTreeMultiMapSF2.PhKnnQuerySF;
import ch.ethz.globis.phtree.PhTreeMultiMapSF2.PhQuerySF;
import ch.ethz.globis.phtree.pre.PreProcessorRangeF;
import ch.ethz.globis.phtree.util.BitTools;
import ch.ethz.globis.phtree.util.Bits;
import ch.ethz.globis.phtree.util.PhTreeStats;
import org.junit.Test;

import java.util.*;
import java.util.function.BiFunction;

import static org.junit.Assert.*;

public class TestMultiMapSolidF2 {

    private <T> PhTreeMultiMapSF2<T> newTree(int dim) {
        return PhTreeMultiMapSF2.create(dim);
    }

    @Test
    public void testCRUD() {
        PhTreeMultiMapSF2<Integer> idx = newTree(2);
        final Random rnd = new Random(0);
        final int DIM = 3;
        final int DX = 3;
        final int N = 1000;
        HashMap<Integer, PhEntrySF<Integer>> map = new HashMap<>();

        int id = 1;
        for (int i = 0; i < N; i++) {
            double[] vLo = new double[DIM];
            double[] vUp = new double[DIM];
            for (int j = 0; j < DIM; j++) {
                vLo[j] = rnd.nextDouble();
                vUp[j] = vLo[j] + 0.1 * rnd.nextDouble();
            }
            for (int x = 0; x < DX; x++) {
                map.put(id, new PhEntrySF<>(vLo, vUp, id));
                assertTrue(idx.put(vLo, vUp, id));
                assertTrue(idx.contains(vLo, vUp, id));
                ArrayList<Integer> list = new ArrayList<>();
                idx.get(vLo, vUp).forEach(list::add);
                for (int x2 = 0; x2 <= x; x2++) {
                    assertTrue(list.contains(id - x2));
                }
                id++;
            }
        }

        assertEquals(N * DX, map.size());
        assertEquals(N * DX, idx.size());

        // replace values
        for (Map.Entry<Integer, PhEntrySF<Integer>> e2 : map.entrySet()) {
            PhEntrySF<Integer> e = e2.getValue();
            assertTrue(idx.replace(e.lower(), e.upper(), e2.getKey(), -e2.getKey()));
        }

        assertEquals(N * DX, idx.size());

        // update keys
        for (Map.Entry<Integer, PhEntrySF<Integer>> e2 : map.entrySet()) {
            PhEntrySF<Integer> e = e2.getValue();
            double[] vLo2 = new double[DIM];
            double[] vUp2 = new double[DIM];
            for (int j = 0; j < DIM; j++) {
                // preserve duplicates
                vLo2[j] = e.lower()[j] + 0.1;
                vUp2[j] = e.upper()[j] + 0.1;
            }
            assertTrue(idx.update(e.lower(), e.upper(), -e2.getKey(), vLo2, vUp2));
            assertFalse(idx.update(e.lower(), e.upper(), -e2.getKey(), vLo2, vUp2));
            map.put(e2.getKey(), new PhEntrySF<>(vLo2, vUp2, e2.getKey()));
        }

        assertEquals(N * DX, idx.size());

        // remove
        for (Map.Entry<Integer, PhEntrySF<Integer>> e2 : map.entrySet()) {
            PhEntrySF<Integer> e = e2.getValue();
            if (idx.size() < N * DX / 2) {
                // The key may have already been removed
                if (idx.get(e.lower(), e.upper()).iterator().hasNext()) {
                    assertEquals(-e2.getKey(), (int) idx.remove(e.lower(), e.upper()).iterator().next());
                }
                assertFalse(idx.remove(e.lower(), e.upper()).iterator().hasNext());
                assertFalse(idx.remove(e.lower(), e.upper(), -e2.getKey()));
            } else {
                assertTrue(idx.remove(e.lower(), e.upper(), -e2.getKey()));
                assertFalse(idx.remove(e.lower(), e.upper(), -e2.getKey()));
            }
        }

        assertEquals(0, idx.size());
    }

    /**
     * Test CRUD operations with new JDK 8 functions: putIfAbsent(), computeIfAbsent(), compute(), computeIfPresent().
     */
    @Test
    public void testCRUD_JDK8() {
        PhTreeMultiMapSF2<Integer> idx = newTree(2);
        final Random rnd = new Random(0);
        final int DIM = 3;
        final int DX = 3;
        final int N = 1000;
        HashMap<Integer, PhEntrySF<Integer>> map = new HashMap<>();

        int id = 1;
        for (int i = 0; i < N; i++) {
            double[] vLo = new double[DIM];
            double[] vUp = new double[DIM];
            for (int j = 0; j < DIM; j++) {
                vLo[j] = rnd.nextDouble();
                vUp[j] = vLo[j] + 0.1 * rnd.nextDouble();
            }
            for (int x = 0; x < DX; x++) {
                map.put(id, new PhEntrySF<>(vLo, vUp, id));
                switch (rnd.nextInt(3)) {
                    case 0:
                        assertNull(idx.putIfAbsent(vLo, vUp, id));
                        assertEquals(id, (int) idx.putIfAbsent(vLo, vUp, id));
                        break;
                    case 1: {
                        final int id2 = id;
                        assertEquals(id2, (int) idx.computeIfAbsent(vLo, vUp, id2, (l2, u2) -> id2));
                        assertNull(idx.computeIfAbsent(vLo, vUp, id2, (l2, u2) -> id2));
                        break;
                    }
                    case 2:
                        final int id2 = id;
                        assertEquals(id2, (int) idx.compute(vLo, vUp, id, (l2, u2, idNull2) -> id2));
                        // idempotent operation:
                        assertEquals(id2, (int) idx.compute(vLo, vUp, id, (l2, u2, idNull2) -> id2));
                        break;
                    default:
                        throw new IllegalStateException();
                }
                assertTrue(idx.contains(vLo, vUp, id));
                assertTrue(idx.get(vLo, vUp).iterator().hasNext());
                id++;
            }
        }

        assertEquals(N * DX, map.size());
        assertEquals(N * DX, idx.size());

        for (Map.Entry<Integer, PhEntrySF<Integer>> e2 : map.entrySet()) {
            PhEntrySF<Integer> e = e2.getValue();
            assertTrue(idx.contains(e.lower(), e.upper(), e2.getKey()));
        }

        // replace values
        for (Map.Entry<Integer, PhEntrySF<Integer>> e2 : map.entrySet()) {
            PhEntrySF<Integer> e = e2.getValue();
            if (rnd.nextBoolean()) {
                assertEquals(-e2.getKey(), (int) idx.compute(e.lower(), e.upper(), e2.getKey(), (up, lo, id2) -> -id2));
            } else {
                assertEquals(-e2.getKey(), (int) idx.computeIfPresent(e.lower(), e.upper(), e2.getKey(), (lo, up, id2) -> -id2));
            }
        }

        assertEquals(N * DX, idx.size());

        // remove
        for (Map.Entry<Integer, PhEntrySF<Integer>> e2 : map.entrySet()) {
            PhEntrySF<Integer> e = e2.getValue();
            if (idx.size() <= N * DX / 2) {
                assertNull(idx.compute(e.lower(), e.upper(), -e2.getKey(), (lo, up, id2) -> null));
                assertNull(idx.compute(e.lower(), e.upper(), -e2.getKey(), (lo, up, id2) -> null));
            } else {
                assertNull(idx.computeIfPresent(e.lower(), e.upper(), -e2.getKey(), (lo, up, id2) -> null));
                assertNull(idx.computeIfPresent(e.lower(), e.upper(), -e2.getKey(), (lo, up, id2) -> null));
            }
            assertFalse(idx.contains(e.lower(), e.upper(), e2.getKey()));
        }

        assertEquals(0, idx.size());
    }


    @Test
    public void testQueryIntersectMini() {
        PhTreeMultiMapSF2<double[]> idx = newTree(2);
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{0.9, 0.9}, new double[]{1.1, 1.1}, new double[]{1, 1});
        idx.put(new double[]{0.9, 2.9}, new double[]{1.1, 3.1}, new double[]{1, 3});
        idx.put(new double[]{2.9, 0.9}, new double[]{3.1, 1.1}, new double[]{3, 1});

        List<PhEntrySF<double[]>> result;
        result = toList(idx.queryIntersect(new double[]{3, 3}, new double[]{3, 3}));
        assertTrue(result.isEmpty());

        result = toList(idx.queryIntersect(new double[]{2, 2}, new double[]{2, 2}));
        assertEquals(3, result.size());
        check(result.get(0).value(), 2, 2);
        check(result.get(1).value(), 2, 2);
        check(result.get(2).value(), 2, 2);

        result = toList(idx.queryIntersect(new double[]{1, 1}, new double[]{1, 1}));
        assertEquals(1, result.size());
        check(result.get(0).value(), 1, 1);

        result = toList(idx.queryIntersect(new double[]{1, 3}, new double[]{1, 3}));
        assertEquals(1, result.size());
        check(result.get(0).value(), 1, 3);

        result = toList(idx.queryIntersect(new double[]{3, 1}, new double[]{3, 1}));
        assertEquals(1, result.size());
        check(result.get(0).value(), 3, 1);
    }

    @Test
    public void testQueryIncludeMini() {
        PhTreeMultiMapSF2<double[]> idx = newTree(2);
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{0.9, 0.9}, new double[]{1.1, 1.1}, new double[]{1, 1});
        idx.put(new double[]{0.9, 2.9}, new double[]{1.1, 3.1}, new double[]{1, 3});
        idx.put(new double[]{2.9, 0.9}, new double[]{3.1, 1.1}, new double[]{3, 1});

        List<PhEntrySF<double[]>> result;
        result = toList(idx.queryInclude(new double[]{3, 3}, new double[]{3, 3}));
        assertTrue(result.isEmpty());

        result = toList(idx.queryInclude(new double[]{2, 2}, new double[]{2, 2}));
        assertTrue(result.isEmpty());
        result = toList(idx.queryInclude(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}));
        assertEquals(3, result.size());
        check(result.get(0).value(), 2, 2);
        check(result.get(1).value(), 2, 2);
        check(result.get(2).value(), 2, 2);

        result = toList(idx.queryInclude(new double[]{0.8, 0.8}, new double[]{1.2, 1.2}));
        assertEquals(1, result.size());
        check(result.get(0).value(), 1, 1);

        result = toList(idx.queryInclude(new double[]{0.8, 2.8}, new double[]{1.2, 3.2}));
        assertEquals(1, result.size());
        check(result.get(0).value(), 1, 3);

        result = toList(idx.queryInclude(new double[]{2.8, 0.8}, new double[]{3.2, 1.2}));
        assertEquals(1, result.size());
        check(result.get(0).value(), 3, 1);
    }

    @Test
    public void testKNN() {
        PhTreeMultiMapSF2<double[]> idx = newTree(2);
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{1.9, 1.9}, new double[]{2.1, 2.1}, new double[]{2, 2});
        idx.put(new double[]{0.9, 0.9}, new double[]{1.1, 1.1}, new double[]{1, 1});
        idx.put(new double[]{0.9, 2.9}, new double[]{1.1, 3.1}, new double[]{1, 3});
        idx.put(new double[]{2.9, 0.9}, new double[]{3.1, 1.1}, new double[]{3, 1});

        List<PhEntryDistSF<double[]>> result = toList(idx.nearestNeighbour(0, 3, 3));
        assertTrue(result.isEmpty());

        result = toList(idx.nearestNeighbour(3, 2, 2));
        check(result.get(0).lower(), 1.9, 1.9);
        check(result.get(0).upper(), 2.1, 2.1);
        check(result.get(1).lower(), 1.9, 1.9);
        check(result.get(1).upper(), 2.1, 2.1);
        check(result.get(2).lower(), 1.9, 1.9);
        check(result.get(2).upper(), 2.1, 2.1);
        assertTrue(3 <= result.size());

        result = toList(idx.nearestNeighbour(1, 1, 1));
        assertFalse(result.isEmpty());
        check(result.get(0).lower(), 0.9, 0.9);
        check(result.get(0).upper(), 1.1, 1.1);
        check(result.get(0).value(), 1, 1);

        result = toList(idx.nearestNeighbour(1, 1, 3));
        assertFalse(result.isEmpty());
        check(result.get(0).lower(), 0.9, 2.9);
        check(result.get(0).upper(), 1.1, 3.1);
        check(result.get(0).value(), 1, 3);

        result = toList(idx.nearestNeighbour(1, 3, 1));
        assertFalse(result.isEmpty());
        check(result.get(0).lower(), 2.9, 0.9);
        check(result.get(0).upper(), 3.1, 1.1);
        check(result.get(0).value(), 3, 1);
    }

    @Test
    public void testKnnLarge_distEdge() {
        PhDistanceSF distSF = new PhDistanceSFEdgeDist(new PreProcessorRangeF.IEEE(3), 3);
        testKnnLarge(distSF, (p, entry) -> distEdge(p, entry.lo, entry.up));
    }

    @Test
    public void testKnnLarge_distCenter() {
        PhDistanceSF distSF = new PhDistanceSFCenterDist(new PreProcessorRangeF.IEEE(3), 3);
        testKnnLarge(distSF, (p, entry) -> distCenter(p, entry.lo, entry.up));
    }

    private void testKnnLarge(PhDistanceSF distSF, BiFunction<double[], EntryDist<Integer>, Double> controlDist) {
        final int DIM = 3;
        final int LOOP = 10;
        final int N = 1000;
        final int N_DUPL = 3;
        final int NQ = 100;
        final int MAXV = 1000;
        final int MIN_RESULT = 10;
        final Random rnd = new Random(0);
        final ArrayList<EntryDist<Integer>> list = new ArrayList<>();
        for (int d = 0; d < LOOP; d++) {
            list.clear();
            int id = 0;
            PhTreeMultiMapSF2<Integer> ind = newTree(DIM);
            for (int i = 0; i < N; i++) {
                double[] vLo = new double[DIM];
                double[] vUp = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    vLo[j] = rnd.nextDouble() * MAXV;
                    vUp[j] = vLo[j] + 0.1 * rnd.nextDouble() * MAXV;
                }
                for (int dupl = 0; dupl <= i % N_DUPL; dupl++) {
                    ind.put(vLo, vUp, id);
                    list.add(new EntryDist<>(vLo, vUp, id, 0));
                    id++;
                }
            }
            assertEquals(id, ind.size());

            PhKnnQuerySF<Integer> q = ind.nearestNeighbour(MIN_RESULT, new double[DIM]);
            for (int i = 0; i < NQ; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = rnd.nextDouble() * MAXV;
                }
                list.forEach(entry -> entry.dist = controlDist.apply(v, entry));
                list.sort(Comparator.comparingDouble(o -> o.dist));
                List<PhEntryDistSF<Integer>> nnList = toList(q.reset(MIN_RESULT, distSF, v));
                assertFalse("i=" + i + " d=" + d, nnList.isEmpty());
                for (int x = 0; x < MIN_RESULT; ++x) {
                    assertEquals(list.get(x).dist, nnList.get(x).dist(), 0.0000001);
                    if (list.get(x).dist > 0) {
                        // Ignore for dist=0 (==overlap with box), it is likely that we have multiple matching boxes
                        assertArrayEquals(list.get(x).lo, nnList.get(x).lower(), 0.0);
                        assertArrayEquals(list.get(x).up, nnList.get(x).upper(), 0.0);
                    }
                }
            }
        }
    }

    @Test
    public void testQueryFullExtent() {
        final int DIM = 5;
        final int N = 10000;
        Random rnd = new Random(0);

        for (int d = 0; d < DIM; d++) {
            PhTreeMultiMapSF2<double[]> ind = newTree(DIM);
            for (int i = 0; i < N; i++) {
                double[] vLo = new double[DIM];
                double[] vUp = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    vLo[j] = rnd.nextDouble();
                    vUp[j] = vLo[j] + 0.1 * rnd.nextDouble();
                }
                ind.put(vLo, vUp, vLo);
                ind.put(vLo, vUp, vLo);
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
    public void testQueryIntersect() {
        testQuery(true);
    }

    @Test
    public void testQueryInclude() {
        testQuery(false);
    }

    private void testQuery(boolean intersect) {
        final int MAX_DIM = 10;
        final int N = 10000;
        Random rnd = new Random(0);

        for (int DIM = 3; DIM <= MAX_DIM; DIM++) {
            PhTreeMultiMapSF2<double[]> ind = newTree(DIM);
            for (int i = 0; i < N; i++) {
                double[] vLo = new double[DIM];
                double[] vUp = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    vLo[j] = rnd.nextDouble() * 2 - 1;
                    vUp[j] = vLo[j] + 0.1 * rnd.nextDouble();
                }
                assertTrue(ind.put(vLo, vUp, vLo));
                assertTrue(ind.put(vLo, vUp, vLo));
            }

            double[] min = new double[DIM];
            double[] max = new double[DIM];
            for (int i = 0; i < DIM; i++) {
                min[i] = -0.5;
                max[i] = 0.5;
            }

            // query
            PhQuerySF<double[]> it = intersect ? ind.queryIntersect(min, max) : ind.queryInclude(min, max);
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
    public void testQueryIntersectWithDistanceFunction() {
        testQueryWithDistanceFunction(true);
    }

    @Test
    public void testQueryIncludeWithDistanceFunction() {
        testQueryWithDistanceFunction(false);
    }

    private void testQueryWithDistanceFunction(boolean intersect) {
        final int DIM = 3;
        final int LOOP = 10;
        final int N = 1000;
        final int NQ = 100;
        final int MAXV = 1000;
        final Random rnd = new Random(0);
        for (int d = 0; d < LOOP; d++) {
            PhTreeMultiMapSF2<Integer> ind = newTree(DIM);
            PhQuerySF<Integer> q;
            if (intersect) {
                q = ind.queryIntersect(new double[DIM], new double[DIM]);
            } else {
                q = ind.queryInclude(new double[DIM], new double[DIM]);
            }
            for (int i = 0; i < N; i++) {
                double[] vLo = new double[DIM];
                double[] vUp = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    vLo[j] = rnd.nextDouble() * MAXV;
                    vUp[j] = vLo[j] + 0.01 * rnd.nextDouble() * MAXV;
                }
                ind.put(vLo, vUp, 2 * i);
                ind.put(vLo, vUp, 2 * i + 1);
            }
            for (int i = 0; i < NQ; i++) {
                double[] min = new double[DIM];
                double[] max = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    double v = rnd.nextDouble() * MAXV;
                    min[j] = v - MAXV * 0.25;
                    max[j] = v + MAXV * 0.25;
                }
                List<PhEntrySF<Integer>> expList = query(ind, min, max, intersect);
                List<PhEntrySF<Integer>> nnList = toList(q.reset(min, max));
                assertFalse("i=" + i + " d=" + d, nnList.isEmpty());
                expList.sort(new EntrySFComparator<>());
                nnList.sort(new EntrySFComparator<>());
                for (int j = 0; j < expList.size(); j++) {
                    assertEquals(expList.get(j), nnList.get(j));
                }
                assertEquals(expList.size(), nnList.size());
            }
        }
    }

    private static class EntrySFComparator<T> implements Comparator<PhEntrySF<T>> {
        @Override
        public int compare(PhEntrySF<T> o1, PhEntrySF<T> o2) {
            for (int i = 0; i < o1.lower().length; i++) {
                int c = Double.compare(o1.lower()[i], o2.lower()[i]);
                if (c != 0) {
                    return c;
                }
            }
            for (int i = 0; i < o1.upper().length; i++) {
                int c = Double.compare(o1.upper()[i], o2.upper()[i]);
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        }
    }

    private <T> ArrayList<PhEntrySF<T>> query(PhTreeMultiMapSF2<T> tree, double[] min, double[] max, boolean intersect) {
        ArrayList<PhEntrySF<T>> points = new ArrayList<>();
        PhIteratorSF<T> i = tree.queryExtent();
        while (i.hasNext()) {
            PhEntrySF<T> e = i.nextEntry();
            double[] lo = e.lower();
            double[] up = e.upper();
            boolean match = true;
            for (int j = 0; j < lo.length; j++) {
                if (intersect) {
                    if (lo[j] > max[j] || up[j] < min[j]) {
                        match = false;
                        break;
                    }
                } else {
                    if (lo[j] < min[j] || up[j] > max[j]) {
                        match = false;
                        break;
                    }
                }
            }
            if (match) {
                points.add(new PhEntrySF<>(lo.clone(), up.clone(), e.value()));
            }
        }
        return points;
    }

    private double distCenter(double[] p, double[] lo, double[] hi) {
        double d = 0;
        for (int i = 0; i < p.length; i++) {
            double dx = (hi[i] + lo[i]) / 2;
            double dl = p[i] - dx;
            d += dl * dl;
        }
        return Math.sqrt(d);
    }

    private double distEdge(double[] p, double[] lo, double[] hi) {
        double d = 0;
        for (int i = 0; i < p.length; i++) {
            double dOnAxis = 0;
            if (p[i] < lo[i]) {
                dOnAxis = lo[i] - p[i];
            } else if (p[i] > hi[i]) {
                dOnAxis = p[i] - hi[i];
            }
            d += dOnAxis * dOnAxis;
        }
        return Math.sqrt(d);
    }

    private void check(double[] t, double... ints) {
        for (int i = 0; i < ints.length; i++) {
            assertEquals("i=" + i + " | " + toBinary(ints) + " / " + toBinary(t), ints[i], t[i], 0.0);
        }
    }

    private <T> List<PhEntrySF<T>> toList(PhQuerySF<T> q) {
        ArrayList<PhEntrySF<T>> ret = new ArrayList<>();
        while (q.hasNext()) {
            PhEntrySF<T> e = q.nextEntry();
            ret.add(new PhEntrySF<>(e.lower(), e.upper(), e.value()));
        }
        return ret;
    }

    private <T> List<PhEntryDistSF<T>> toList(PhKnnQuerySF<T> q) {
        ArrayList<PhEntryDistSF<T>> ret = new ArrayList<>();
        while (q.hasNext()) {
            if (ret.size() % 2 == 0) {
                ret.add(q.nextEntry());
            } else {
                PhEntryDistSF<T> e = q.nextEntryReuse();
                ret.add(new PhEntryDistSF<>(e.lower().clone(), e.upper().clone(), e.value(), e.dist()));
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
        double[] lo;
        double[] up;
        T value;
        double dist;

        public EntryDist(double[] lo, double[] up, T v, double dist) {
            this.lo = lo;
            this.up = up;
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
        PhTreeMultiMapSF2<Integer> ind = newTree(dim);
        PhTreeStats stats = ind.getStats();
        assertEquals(0, stats.nNodes);
    }
}
