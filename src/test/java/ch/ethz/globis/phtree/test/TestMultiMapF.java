/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import ch.ethz.globis.phtree.PhDistanceMMF;
import ch.ethz.globis.phtree.PhTreeMultiMapF;
import ch.ethz.globis.phtree.PhTreeMultiMapF.PhIteratorMMF;
import ch.ethz.globis.phtree.PhTreeMultiMapF.PhKnnQueryMMF;
import ch.ethz.globis.phtree.PhTreeMultiMapF.PhQueryMMF;
import ch.ethz.globis.phtree.PhTreeMultiMapF.PhRangeQueryMMF;
import ch.ethz.globis.phtree.util.BitTools;
import ch.ethz.globis.phtree.util.Bits;

public class TestMultiMapF {

	private <T> PhTreeMultiMapF<T> newTree(int DIM) {
		return PhTreeMultiMapF.create(DIM);
	}

    @Test
    public void testCRUD() {
        PhTreeMultiMapF<Integer> idx = newTree(2);
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
                assertNull(Bits.toBinary(v), idx.put(v, id, id));
                assertTrue(idx.contains(v, id));
                assertEquals(id, (int) idx.get(v, id));
                id++;
            }
        }
        
        assertEquals(N * DX, map.size());
        assertEquals(N * DX, idx.size());
        
        // replace values
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            if (R.nextBoolean()) {
                assertEquals(e.getKey(), idx.replace(e.getValue(), e.getKey(), -e.getKey()));
            } else {
                assertTrue(idx.replace(e.getValue(), e.getKey(), e.getKey(), -e.getKey()));
            }
        }
           
        assertEquals(N * DX, idx.size());
        
        // update keys
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            double[] v2 = new double[DIM];
            for (int j = 0; j < DIM; j++) {
                // preserve duplicates
                v2[j] = e.getValue()[j] + 0.1;
            }
            assertEquals(-e.getKey(), (int) idx.update(e.getValue(), e.getKey(), v2));
            map.put(e.getKey(), v2);
        }
           
        assertEquals(N * DX, idx.size());
        
        // remove
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            if (R.nextBoolean()) {
                assertEquals(-e.getKey(), (int) idx.remove(e.getValue(), e.getKey()));
            } else {
                assertTrue(idx.remove(e.getValue(),  e.getKey(), -e.getKey()));
            }
        }
        
        assertEquals(0, idx.size());
    }

    @Test
    public void testCRUD_JDK8() {
        PhTreeMultiMapF<Integer> idx = newTree(2);
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
                    assertNull(idx.putIfAbsent(v, id, id));
                    break;
                case 1: {
                    final int id2 = id;
                    assertEquals(id2, (int) idx.computeIfAbsent(v, id, (v2) -> id2));
                    break;
                }
                case 2:
                    final int id2 = id;
                    assertEquals(id2, (int) idx.compute(v, id, (v2, idNull2) -> id2));
                    break;
                default:
                    throw new IllegalStateException();
                }
                assertTrue(idx.contains(v, id));
                assertEquals(id, (int) idx.get(v, id));
                id++;
            }
        }
        
        assertEquals(N * DX, map.size());
        assertEquals(N * DX, idx.size());
        
        // replace values
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            if (R.nextBoolean()) {
                assertEquals(-e.getKey(), 
                        (int) idx.compute(e.getValue(), e.getKey(), (v2, id2) -> -id2));
            } else {
                assertEquals(-e.getKey(), 
                        (int) idx.computeIfPresent(e.getValue(), e.getKey(), (v2, id2) -> -id2));
            }
        }
           
        assertEquals(N * DX, idx.size());
        
        // remove
        for (Map.Entry<Integer, double[]> e : map.entrySet()) {
            if (R.nextBoolean()) {
                assertNull(idx.compute(e.getValue(), e.getKey(), (v2, id2) -> null));
            } else {
                assertNull(idx.computeIfPresent(e.getValue(), e.getKey(), (v2, id2) -> null));
            }
            assertFalse(idx.contains(e.getValue(), e.getKey()));
            assertNull(idx.get(e.getValue(), e.getKey()));
       }
        
        assertEquals(0, idx.size());
    }
	
	
	@Test
	public void testRangeQuery() {
	    PhTreeMultiMapF<double[]> idx = newTree(2);
        idx.put(new double[]{2,2}, 1, new double[]{2,2});
        idx.put(new double[]{2,2}, 2, new double[]{2,2});
        idx.put(new double[]{2,2}, 3, new double[]{2,2});
		idx.put(new double[]{1,1}, 4, new double[]{1,1});
		idx.put(new double[]{1,3}, 5, new double[]{1,3});
		idx.put(new double[]{3,1}, 6, new double[]{3,1});

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
        PhTreeMultiMapF<double[]> idx = newTree(2);
        idx.put(new double[]{2,2}, 1, new double[]{2,2});
        idx.put(new double[]{2,2}, 2, new double[]{2,2});
        idx.put(new double[]{2,2}, 3, new double[]{2,2});
        idx.put(new double[]{1,1}, 4, new double[]{1,1});
        idx.put(new double[]{1,3}, 5, new double[]{1,3});
        idx.put(new double[]{3,1}, 6, new double[]{3,1});
        
        List<double[]> result = toList(idx.nearestNeighbour(0, 3, 3));
        assertTrue(result.isEmpty());
        
        result = toList(idx.nearestNeighbour(3, 2, 2));
        assertEquals(3, result.size());
        check(result.get(0), 2, 2);
        check(result.get(1), 2, 2);
        check(result.get(2), 2, 2);
        
        result = toList(idx.nearestNeighbour(1, 1, 1));
        assertEquals(1, result.size());
        check(result.get(0), 1, 1);
        
        result = toList(idx.nearestNeighbour(1, 1, 3));
        assertEquals(1, result.size());
        check(result.get(0), 1, 3);
        
        result = toList(idx.nearestNeighbour(1, 3, 1));
        assertEquals(1, result.size());
        check(result.get(0), 3, 1);
    }
    
    @Test
    public void testQueryFullExtent() {
        final int DIM = 5;
        final int N = 10000;
        Random R = new Random(0);
       
        for (int d = 0; d < DIM; d++) {
            PhTreeMultiMapF<double[]> ind = newTree(DIM);
            long id = 1;
            for (int i = 0; i < N; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextDouble();
                }
                ind.put(v, id++, v);
                ind.put(v, id++, v);
            }
            
            //check full result
            int n = 0;
            Iterator<double[]> it = ind.queryExtent();
            for (int i = 0; i < N*2; i++) {
                it.next();
                n++;
            }
            assertFalse(it.hasNext());
            assertEquals(N * 2, n);
        }
    }

    @Test
    public void testQuery() {
        final int MAX_DIM = 10;
        final int N = 1000;
        Random R = new Random(0);
        
        for (int DIM = 3; DIM <= MAX_DIM; DIM++) {
            //System.out.println("d="+ DIM);
            int id = 1;
            PhTreeMultiMapF<double[]> ind = newTree(DIM);
            for (int i = 0; i < N; i++) {
                double[] v = new double[DIM];
                for (int j = 0; j < DIM; j++) {
                    v[j] = R.nextDouble();
                }
                assertNull(Bits.toBinary(v), ind.put(v, id++, v));
                assertNull(Bits.toBinary(v), ind.put(v, id++, v));
            }
            
            double[] min = new double[DIM];
            double[] max = new double[DIM];
            for (int i = 0; i < DIM; i++) {
                min[i] = -0.5;
                max[i] = 0.5;
            }
            
            // query
            PhQueryMMF<double[]> it = ind.query(min, max);
            int n = 0;
            while (it.hasNext()) {
                n++;
                it.next();
            }
            assertTrue(n > 0);
            assertTrue(n < N);
            assertTrue(n % 2 == 0);
            
            // reset
            it.reset(min, max);
            n = 0;
            while (it.hasNext()) {
                n++;
                it.next();
            }
            assertTrue(n > 0);
            assertTrue(n < N);
            assertTrue(n % 2 == 0);
        }
    }
    
	@Test
	public void testRangeQueryWithDistanceFunction() {
		final int DIM = 3;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 100;
		final int MAXV = 1000;
		final int range = MAXV/2;
		final Random R = new Random(0);
		for (int d = 0; d < LOOP; d++) {
		    long id = 0;
			PhTreeMultiMapF<Object> ind = newTree(DIM);
			PhRangeQueryMMF<Object> q = ind.rangeQuery(1, PhDistanceMMF.THIS, new double[DIM]);
			for (int i = 0; i < N; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
                ind.put(v, id++, null);
                ind.put(v, id++, null);
			}
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				double[] exp = rangeQuery(ind, range, v).get(0);
				List<double[]> nnList = toList(q.reset(range, v));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				double[] nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}

	private ArrayList<double[]> rangeQuery(PhTreeMultiMapF<?> tree, double range, double[] q) {
		ArrayList<double[]> points = new ArrayList<>();
		PhIteratorMMF<?> i = tree.queryExtent();
		while (i.hasNext()) {
			double[] cand = i.nextKey();
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
				double maxEps = Math.abs(d2-d1)/d1;
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
			d += dl*dl;
		}
		return Math.sqrt(d);
	}

	private void check(double[] t, double ... ints) {
		for (int i = 0; i < ints.length; i++) {
			assertEquals("i=" + i + " | " + toBinary(ints) + " / " + 
					toBinary(t), ints[i], t[i], 0.0);
		}
	}

    private List<double[]> toList(PhRangeQueryMMF<?> q) {
        ArrayList<double[]> ret = new ArrayList<>();
        while (q.hasNext()) {
            ret.add(q.nextKey());
        }
        return ret;
    }

    private List<double[]> toList(PhKnnQueryMMF<?> q) {
        ArrayList<double[]> ret = new ArrayList<>();
        while (q.hasNext()) {
            ret.add(q.nextKey());
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
}
