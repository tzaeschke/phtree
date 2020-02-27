/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
 *
 * This file is part of the PH-Tree project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.ethz.globis.phtree.test;

import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.test.util.TestSuper;
import ch.ethz.globis.phtree.v13.PhTree13;
import ch.ethz.globis.phtree.v16.PhTree16;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class TestJava8MapAPI extends TestSuper {

	private static final int N_POINTS = 1000;

    private final IntFunction<PhTree<?>> constructor;

    public TestJava8MapAPI(IntFunction<PhTree<?>> constructor) {
        this.constructor = constructor;
    }

    @Parameterized.Parameters
    public static List<Object[]> distanceFunctions() {
        return Arrays.asList(new Object[][] {
            { (IntFunction<PhTree<?>>) (dim) -> new PhTree13<>(dim) },
            { (IntFunction<PhTree<?>>) (dim) -> new PhTree16<>(dim) },
        });
    }
    
	@SuppressWarnings("unchecked")
	private <T> PhTree<T> create(int dim) {
		return (PhTree<T>) constructor.apply(dim);
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
	public void testComputeMini() {
		PhTree<Integer> ind = create(3);

		long[] i1 = new long[] {0, 0, 0};
		long[] i2 = new long[] {10, 10, 10};
		assertEquals(1, (int) ind.compute(i1, (longs, integer) -> {
			assertNull(integer);
			return 1;
		}));
		assertEquals(1, (int) ind.get(i1));

		assertEquals(2, (int) ind.compute(i2, (longs, integer) -> {
			assertNull(integer);
			return 2;
		}));
		assertEquals(2, (int) ind.get(i2));
		assertEquals(1, (int) ind.get(i1));
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
