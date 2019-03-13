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
import java.util.function.BiConsumer;
import java.util.function.Supplier;


public class BenchmarkJava8MapAPI extends TestSuper {

	private static final int N_POINTS = 1_000_000;
	private static final int DIMS = 3;
	private static final int LIMIT_MS = 1000;

	private static class Scenario {
		String name;
		Supplier<PhTree<int[]>> constructor;
		BiConsumer<PhTree<int[]>, long[]> action;
		Scenario(String name, Supplier<PhTree<int[]>> constructor,
				 BiConsumer<PhTree<int[]>, long[]> action) {
			this.name = name;
			this.constructor = constructor;
			this.action = action;
		}
	}


	public static void main(String[] args) {
		long[][] data = generate();

		Scenario s13getPut =
				new Scenario("PHTree13-GP: ", () -> new PhTree13<>(DIMS), BenchmarkJava8MapAPI::actionGetPut);
		Scenario s13comp =
				new Scenario("PHTree13-CO: ", () -> new PhTree13<>(DIMS), BenchmarkJava8MapAPI::actionCompute);
		Scenario s16getPut =
				new Scenario("PHTree16-GP: ", () -> new PhTree16<>(DIMS), BenchmarkJava8MapAPI::actionGetPut);
		Scenario s16comp =
				new Scenario("PHTree16-CO: ", () -> new PhTree16<>(DIMS), BenchmarkJava8MapAPI::actionCompute);

		test3(data, s13getPut);
		test3(data, s13comp);
		test3(data, s16getPut);
		test3(data, s16comp);

		test3(data, s13getPut);
		test3(data, s13comp);
		test3(data, s16getPut);
		test3(data, s16comp);
	}

	private static void actionGetPut(PhTree<int[]> tree, long[] key) {
		int[] x = tree.get(key);
		if (x == null) {
			x = new int[]{0};
			tree.put(key, x);
		} else {
			x[0]++;
		}
	}

	private static void actionCompute(PhTree<int[]> tree, long[] key) {
		tree.compute(key, (longs, ints) -> {
			if (ints == null) {
				return new int[]{0};
			}
			ints[0]++;
			return ints;
		});
	}

	private static long[][] generate() {
		long[][] data = new long[N_POINTS][DIMS];
		Random rnd = new Random(0);
		for (long[] key : data) {
			Arrays.setAll(key, v -> rnd.nextInt() % 100000);
		}
		return data;
	}

	private static void test3(long[][] data, Scenario scenario) {
		for (int i =- 0; i < 3; i++) {
			test(data, scenario.name, scenario.constructor, scenario.action);
		}
	}

	private static void test(long[][] data, String name, Supplier<PhTree<int[]>> constructor,
							 BiConsumer<PhTree<int[]>, long[]> action) {
		rest();
		PhTree<int[]> tree = constructor.get();
		long t0 = System.currentTimeMillis();
		int n = 0;
		while (System.currentTimeMillis()-t0 < LIMIT_MS) {
			for (int i = 0; i < 100; i++) {
				long[] key = data[n++ % N_POINTS];
				action.accept(tree, key);
			}
		}
		long t1 = System.currentTimeMillis();
		System.out.println(name + n/(double)(t1-t0)*1_000 + " ops/s");
	}

	private static void rest() {
		try {
			System.gc();
			Thread.sleep(100);
			System.gc();
			Thread.sleep(100);
			System.gc();
			Thread.sleep(100);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}
