/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.test.util.TestSuper;
import ch.ethz.globis.phtree.test.util.TestUtil;
import ch.ethz.globis.phtree.v16.PhTree16;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

public class TestJava8MapAPI extends TestSuper {

	private static final int N_POINTS = 1000;

	private <T> PhTree<T> create(int dim) {
		//return TestUtil.newTree(dim);
		//TODO
		return new PhTree16<>(dim);
	}

	@Test
	public void testPutIfAbsent() {
		PhTree<Integer> ind = create(3);
		Random R = new Random(0);
		for (int i = 0; i < N_POINTS; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt(), R.nextInt()};
			assertNull(ind.putIfAbsent(v, i));
			assertEquals(i, (int) ind.putIfAbsent(v, -i));
			assertEquals(i, (int) ind.get(v));
		}
		assertEquals(N_POINTS, ind.size());
	}

	@Test
	public void testComputeIfAbsent() {
		PhTree<Integer> ind = create(3);
		Random R = new Random(0);
		for (int i = 0; i < N_POINTS; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt(), R.nextInt()};
			final int i2 = i;
			assertEquals(i2, (int) ind.computeIfAbsent(v, k -> i2));
			assertEquals(i2, (int) ind.computeIfAbsent(v, k -> -i2));
			assertEquals(i2, (int) ind.get(v));
		}
		assertEquals(N_POINTS, ind.size());
	}

	@Test
	public void testComputeIfPresent() {
		PhTree<Integer> ind = create(3);
		Random R = new Random(0);
		long[][] points = new long[N_POINTS][];
		for (int i = 0; i < points.length; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt(), R.nextInt()};
			points[i] = v;
			final int i2 = i;
			assertNull(ind.computeIfPresent(v, (longs, integer) -> i2));
			assertFalse(ind.contains(v));
			ind.put(v, i2);
			assertEquals(-i2, (int) ind.computeIfPresent(v, (longs, integer) -> -i2));
			assertEquals(-i2, (int) ind.get(v));
		}
		assertEquals(N_POINTS, ind.size());
		for (long[] v : points) {
			assertTrue(ind.contains(v));
			assertNull(ind.computeIfPresent(v, (longs, integer) -> null));
			assertFalse(ind.contains(v));
		}
		assertEquals(0, ind.size());
	}

	@Test
	public void testCompute() {
		PhTree<Integer> ind = create(3);
		Random R = new Random(0);
		long[][] points = new long[N_POINTS][];
		for (int i = 0; i < points.length; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt(), R.nextInt()};
			points[i] = v;
			final int i2 = i;
			assertNull(ind.compute(v, (longs, integer) -> {
				assertNull(integer);
				return null;
			}));
			assertFalse(ind.contains(v));
			assertEquals(i2, (int) ind.compute(v, (longs, integer) -> {
				assertNull(integer);
				return i2;
			}));
			assertEquals(i2, (int) ind.get(v));
			assertEquals(-i2, (int) ind.compute(v, (longs, integer) -> {
				assertEquals(i2, (int) integer);
				return -i2;
			}));
			assertEquals(-i2, (int) ind.get(v));
		}
		assertEquals(N_POINTS, ind.size());
		for (int i = 0; i < points.length; i++) {
			long[] v = points[i];
			final int i2 = i;
			assertNull(ind.compute(v, (longs, integer) -> {
				assertEquals(-i2, (int) integer);
				return null;
			}));
			assertFalse(ind.contains(v));
		}
		assertEquals(0, ind.size());
	}

	@Test
	public void testGetOrDefault() {
		PhTree<Integer> ind = create(2);
		Random R = new Random(0);
		for (int i = 0; i < N_POINTS; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt()};
			assertEquals(i + 15, (int) ind.getOrDefault(v, i+15));
			ind.put(v, -i);
			assertEquals(-i, (int) ind.getOrDefault(v, i+17));
		}
	}

	@Test
	public void testRemoveExact() {
		PhTree<Integer> ind = create(3);
		Random R = new Random(0);
		long[][] points = new long[N_POINTS][];
		for (int i = 0; i < points.length; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt(), R.nextInt()};
			points[i] = v;
			assertFalse(ind.remove(v, i));
			ind.put(v, i);
			assertFalse(ind.remove(v, i+1));
		}
		assertEquals(N_POINTS, ind.size());
		for (int i = 0; i < points.length; i++) {
			long[] v = points[i];
			assertTrue(ind.contains(v));
			assertFalse(ind.remove(v, i+1));
			assertTrue(ind.contains(v));
			assertTrue(ind.remove(v, i));
			assertFalse(ind.contains(v));
		}
		assertEquals(0, ind.size());
	}

	@Test
	public void testReplace() {
		PhTree<Integer> ind = create(3);
		Random R = new Random(0);
		long[][] points = new long[N_POINTS][];
		for (int i = 0; i < points.length; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt(), R.nextInt()};
			points[i] = v;
			assertNull(ind.replace(v, i));
			assertFalse(ind.contains(v));
			ind.put(v, i);
			assertEquals(i, (int) ind.replace(v, i+1));
			assertEquals(i+1, (int) ind.get(v));
		}
		assertEquals(N_POINTS, ind.size());
	}

	@Test
	public void testReplaceExact() {
		PhTree<Integer> ind = create(3);
		Random R = new Random(0);
		long[][] points = new long[N_POINTS][];
		for (int i = 0; i < points.length; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt(), R.nextInt()};
			points[i] = v;
			assertFalse(ind.replace(v, null, i));
			assertFalse(ind.replace(v, i, i));
			assertFalse(ind.contains(v));
			ind.put(v, i);
			assertFalse(ind.replace(v, i+1, i));
			assertEquals(i, (int) ind.get(v));
			assertTrue(ind.replace(v, i, i+5));
			assertEquals(i+5, (int) ind.get(v));
		}
		assertEquals(N_POINTS, ind.size());
	}

}
