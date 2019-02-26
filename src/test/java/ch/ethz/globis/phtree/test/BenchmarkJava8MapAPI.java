/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.test.util.TestSuper;
import ch.ethz.globis.phtree.v13.PhTree13;
import ch.ethz.globis.phtree.v16.PhTree16;

import java.util.Arrays;
import java.util.Random;


public class BenchmarkJava8MapAPI extends TestSuper {

	private static final int N_POINTS = 1_000_000;
	private static final int DIMS = 3;
	private static final int LIMIT_MS = 1000;

	public static void main(String[] args) {
		long[][] data = generate();
		testGetPut13(data);
		testGetPut13(data);
		testGetPut13(data);
		testGetPut16(data);
		testGetPut16(data);
		testGetPut16(data);
		testCompute16(data);
		testCompute16(data);
		testCompute16(data);
		testGetPut13(data);
		testGetPut13(data);
		testGetPut13(data);
		testGetPut16(data);
		testGetPut16(data);
		testGetPut16(data);
		testCompute16(data);
		testCompute16(data);
		testCompute16(data);
	}

	private static long[][] generate() {
		long[][] data = new long[N_POINTS][DIMS];
		Random rnd = new Random(0);
		for (long[] key : data) {
			Arrays.setAll(key, v -> rnd.nextInt() % 100000);
		}
		return data;
	}

	private static void testGetPut13(long[][] data) {
		PhTree13<int[]> tree = new PhTree13<>(DIMS);
		long t0 = System.currentTimeMillis();
		int n = 0;
		while (n % 100 != 0 || System.currentTimeMillis()-t0 < LIMIT_MS) {
			long[] key = data[n++ % N_POINTS];
			int[] x = tree.get(key);
			if (x == null) {
				x = new int[]{0};
				tree.put(key, x);
			} else {
				x[0]++;
			}
		}
		long t1 = System.currentTimeMillis();
		System.out.println("PHTree13-GP: " + (t1-t0)/(double)n*1_000_000 + " ops/s");
	}

	private static void testGetPut16(long[][] data) {
		PhTree16<int[]> tree = new PhTree16<>(DIMS);
		long t0 = System.currentTimeMillis();
		int n = 0;
		while (n % 100 != 0 || System.currentTimeMillis()-t0 < LIMIT_MS) {
			long[] key = data[n++ % N_POINTS];
			int[] x = tree.get(key);
			if (x == null) {
				x = new int[]{0};
				tree.put(key, x);
			} else {
				x[0]++;
			}
		}
		long t1 = System.currentTimeMillis();
		System.out.println("PHTree16-GP: " + (t1-t0)/(double)n*1_000_000 + " ops/s");
	}

	private static void testCompute16(long[][] data) {
		PhTree16<int[]> tree = new PhTree16<>(DIMS);
		long t0 = System.currentTimeMillis();
		int n = 0;
		while (n % 100 != 0 || System.currentTimeMillis()-t0 < LIMIT_MS) {
			long[] key = data[n++ % N_POINTS];
			tree.compute(key, (longs, ints) -> {
				if (ints == null) {
					return new int[]{0};
				}
				ints[0]++;
				return ints;
			});
		}
		long t1 = System.currentTimeMillis();
		System.out.println("PHTree16-CO: " + (t1-t0)/(double)n*1_000_000 + " ops/s");
	}

}
