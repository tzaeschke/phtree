/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.test;
import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

import ch.ethz.globis.pht.util.BitTools;


/**
 * Test variable length splie/merge.
 * 
 * 
 * @author ztilmann
 */
public class TestBitsToolsSplitMergeVar {

	@Test
	public void testSplitMerge31() {
		Random rnd = new Random();
		for (int i = 0; i < 1000; i++) {
			long[] l = new long[]{Math.abs(rnd.nextInt()), Math.abs(rnd.nextInt())};
			int[] x = BitTools.merge(32, l);
			long[] l2 = BitTools.split(2, 32, x);
			assertArrayEquals(l, l2);
		}
	}

	@Test
	public void testSplitMerge63() {
		Random rnd = new Random();
		for (int i = 0; i < 1000; i++) {
			long[] l = new long[]{rnd.nextLong()>>>1, rnd.nextLong()>>>1};
			int[] x = BitTools.merge(63, l);
			long[] l2 = BitTools.split(2, 63, x); 
			assertArrayEquals(l, l2);
		}
	}

	@Test
	public void testSplitMerge64() {
		Random rnd = new Random();
		for (int i = 0; i < 1000; i++) {
			long[] l = new long[]{rnd.nextLong(), rnd.nextLong()};
			int[] x = BitTools.merge(64, l);
			long[] l2 = BitTools.split(2, 64, x);
			assertArrayEquals(l, l2);
		}
	}
	
	@Test
	public void testSplitMergeFloat() {
		Random rnd = new Random();
		for (int i = 0; i < 1000; i++) {
			float[] f = new float[]{rnd.nextFloat(), rnd.nextFloat()};
			long[] l = new long[]{
					BitTools.toSortableLong(f[0]),
					BitTools.toSortableLong(f[1])};
			int[] x = BitTools.merge(32, l);
			long[] l2 = BitTools.split(2, 32, x);
			assertArrayEquals(l, l2);
			float f0 = BitTools.toFloat(l2[0]);
			float f1 = BitTools.toFloat(l2[1]);
			assertEquals(f[0], f0, 0.0);
			assertEquals(f[1], f1, 0.0);
		}
	}

	
	@Test
	public void testSplitMergeDouble() {
		Random rnd = new Random();
		for (int i = 0; i < 1000; i++) {
			double[] d = new double[]{rnd.nextDouble()-0.5, rnd.nextDouble()-0.5};
			long[] l = new long[]{BitTools.toSortableLong(d[0]), BitTools.toSortableLong(d[1])};
			int[] x = BitTools.merge(64, l);
			long[] l2 = BitTools.split(2, 64, x);
			assertArrayEquals(l, l2);
			double d0 = BitTools.toDouble(l2[0]);
			double d1 = BitTools.toDouble(l2[1]);
			assertEquals(d[0], d0, 0.0);
			assertEquals(d[1], d1, 0.0);
		}
	}
}
