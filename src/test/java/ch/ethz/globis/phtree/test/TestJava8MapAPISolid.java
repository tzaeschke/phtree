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

import ch.ethz.globis.phtree.PhTreeSolid;
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
public class TestJava8MapAPISolid extends TestSuper {

	private static final int N_POINTS = 1000;

    private final IntFunction<PhTreeSolid<?>> constructor;

    public TestJava8MapAPISolid(IntFunction<PhTreeSolid<?>> constructor) {
        this.constructor = constructor;
    }

    @Parameterized.Parameters
    public static List<Object[]> distanceFunctions() {
        return Arrays.asList(new Object[][] {
            { (IntFunction<PhTreeSolid<?>>) (dim) -> PhTreeSolid.wrap(new PhTree13<>(dim * 2)) },
            { (IntFunction<PhTreeSolid<?>>) (dim) -> PhTreeSolid.wrap(new PhTree16<>(dim * 2)) }
        });
    }
    
	@SuppressWarnings("unchecked")
	private <T> PhTreeSolid<T> create(int dim) {
		return (PhTreeSolid<T>) constructor.apply(dim);
	}

	@Test
	public void testPutIfAbsent() {
		PhTreeSolid<Integer> ind = create(3);
		Random R = new Random(0);
		for (int i = 0; i < N_POINTS; i++) {
			long[][] v = create(R);
			assertNull(ind.putIfAbsent(v[0], v[1], i));
			assertEquals(i, (int) ind.putIfAbsent(v[0], v[1], -i));
			assertEquals(i, (int) ind.get(v[0], v[1]));
		}
		assertEquals(N_POINTS, ind.size());
	}

	@Test
	public void testComputeIfAbsent() {
		PhTreeSolid<Integer> ind = create(3);
		Random R = new Random(0);
		for (int i = 0; i < N_POINTS; i++) {
			long[][] v = create(R);
			final int i2 = i;
			assertEquals(i2, (int) ind.computeIfAbsent(v[0], v[1], (min, max) -> i2));
			assertEquals(i2, (int) ind.computeIfAbsent(v[0], v[1], (min, max) -> -i2));
			assertEquals(i2, (int) ind.get(v[0], v[1]));
		}
		assertEquals(N_POINTS, ind.size());
	}

	@Test
	public void testComputeIfPresent() {
		PhTreeSolid<Integer> ind = create(3);
		Random R = new Random(0);
		long[][][] points = new long[N_POINTS][2][];
		for (int i = 0; i < points.length; i++) {
			long[][] v = create(R);
			points[i] = v;
			final int i2 = i;
			assertNull(ind.computeIfPresent(v[0], v[1], (min, max, integer) -> i2));
			assertFalse(ind.contains(v[0], v[1]));
			ind.put(v[0], v[1], i2);
			assertEquals(-i2, (int) ind.computeIfPresent(v[0], v[1], (min, max, integer) -> -i2));
			assertEquals(-i2, (int) ind.get(v[0], v[1]));
		}
		assertEquals(N_POINTS, ind.size());
		for (long[][] v : points) {
			assertTrue(ind.contains(v[0], v[1]));
			assertNull(ind.computeIfPresent(v[0], v[1], (min, max, integer) -> null));
			assertFalse(ind.contains(v[0], v[1]));
		}
		assertEquals(0, ind.size());
	}

	@Test
	public void testCompute() {
		PhTreeSolid<Integer> ind = create(3);
		Random R = new Random(0);
		long[][][] points = new long[N_POINTS][2][];
		for (int i = 0; i < points.length; i++) {
			long[][] v = create(R);
			points[i] = v;
			final int i2 = i;
			assertNull(ind.compute(v[0], v[1], (min, max, integer) -> {
				assertNull(integer);
				return null;
			}));
			assertFalse(ind.contains(v[0], v[1]));
			assertEquals(i2, (int) ind.compute(v[0], v[1], (min, max, integer) -> {
				assertNull(integer);
				return i2;
			}));
			assertEquals(i2, (int) ind.get(v[0], v[1]));
			assertEquals(-i2, (int) ind.compute(v[0], v[1], (min, max, integer) -> {
				assertEquals(i2, (int) integer);
				return -i2;
			}));
			assertEquals(-i2, (int) ind.get(v[0], v[1]));
		}
		assertEquals(N_POINTS, ind.size());
		for (int i = 0; i < points.length; i++) {
			long[][] v = points[i];
			final int i2 = i;
			assertNull(ind.compute(v[0], v[1], (min, max, integer) -> {
				assertEquals(-i2, (int) integer);
				return null;
			}));
			assertFalse(ind.contains(v[0], v[1]));
		}
		assertEquals(0, ind.size());
	}

	@Test
	public void testComputeMini() {
		PhTreeSolid<Integer> ind = create(3);

		long[] i11 = new long[] {0, 0, 0};
		long[] i12 = new long[] {0, 0, 0};
		long[] i21 = new long[] {10, 10, 10};
		long[] i22 = new long[] {10, 10, 10};
		assertEquals(1, (int) ind.compute(i11, i12, (min, max, integer) -> {
			assertNull(integer);
			return 1;
		}));
		assertEquals(1, (int) ind.get(i11, i12));

		assertEquals(2, (int) ind.compute(i21, i22, (min, max, integer) -> {
			assertNull(integer);
			return 2;
		}));
		assertEquals(2, (int) ind.get(i21, i22));
		assertEquals(1, (int) ind.get(i11, i12));
	}

	@Test
	public void testGetOrDefault() {
		PhTreeSolid<Integer> ind = create(2);
		Random R = new Random(0);
		for (int i = 0; i < N_POINTS; i++) {
			long[][] v = create2(R);
			assertEquals(i + 15, (int) ind.getOrDefault(v[0], v[1], i+15));
			ind.put(v[0], v[1], -i);
			assertEquals(-i, (int) ind.getOrDefault(v[0], v[1], i+17));
		}
	}

	@Test
	public void testRemoveExact() {
		PhTreeSolid<Integer> ind = create(3);
		Random R = new Random(0);
		long[][][] points = new long[N_POINTS][2][];
		for (int i = 0; i < points.length; i++) {
			long[][] v = create(R);
			points[i] = v;
			assertFalse(ind.remove(v[0], v[1], i));
			ind.put(v[0], v[1], i);
			assertFalse(ind.remove(v[0], v[1], i+1));
		}
		assertEquals(N_POINTS, ind.size());
		for (int i = 0; i < points.length; i++) {
			long[][] v = points[i];
			assertTrue(ind.contains(v[0], v[1]));
			assertFalse(ind.remove(v[0], v[1], i+1));
			assertTrue(ind.contains(v[0], v[1]));
			assertTrue(ind.remove(v[0], v[1], i));
			assertFalse(ind.contains(v[0], v[1]));
		}
		assertEquals(0, ind.size());
	}

	@Test
	public void testReplace() {
		PhTreeSolid<Integer> ind = create(3);
		Random R = new Random(0);
		long[][][] points = new long[N_POINTS][2][];
		for (int i = 0; i < points.length; i++) {
			long[][] v = create(R);
			points[i] = v;
			assertNull(ind.replace(v[0], v[1], i));
			assertFalse(ind.contains(v[0], v[1]));
			ind.put(v[0], v[1], i);
			assertEquals(i, (int) ind.replace(v[0], v[1], i+1));
			assertEquals(i+1, (int) ind.get(v[0], v[1]));
		}
		assertEquals(N_POINTS, ind.size());
	}

	@Test
	public void testReplaceExact() {
		PhTreeSolid<Integer> ind = create(3);
		Random R = new Random(0);
		long[][][] points = new long[N_POINTS][2][];
		for (int i = 0; i < points.length; i++) {
			long[][] v = create(R);
			points[i] = v;
			assertFalse(ind.replace(v[0], v[1], null, i));
			assertFalse(ind.replace(v[0], v[1], i, i));
			assertFalse(ind.contains(v[0], v[1]));
			ind.put(v[0], v[1], i);
			assertFalse(ind.replace(v[0], v[1], i+1, i));
			assertEquals(i, (int) ind.get(v[0], v[1]));
			assertTrue(ind.replace(v[0], v[1], i, i+5));
			assertEquals(i+5, (int) ind.get(v[0], v[1]));
		}
		assertEquals(N_POINTS, ind.size());
	}

	private static long[][] create2(Random R) {
		return new long[][] {
			{Math.min(R.nextInt(), R.nextInt()), Math.min(R.nextInt(), R.nextInt())},
			{Math.max(R.nextInt(), R.nextInt()), Math.max(R.nextInt(), R.nextInt())}
		};
	}

	private static long[][] create(Random R) {
		return new long[][] {
			{Math.min(R.nextInt(), R.nextInt()), Math.min(R.nextInt(), R.nextInt()), Math.min(R.nextInt(), R.nextInt())},
			{Math.max(R.nextInt(), R.nextInt()), Math.max(R.nextInt(), R.nextInt()), Math.max(R.nextInt(), R.nextInt())}
		};
	}
}
