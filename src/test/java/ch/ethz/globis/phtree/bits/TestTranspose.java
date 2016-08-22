/*
 * Copyright 2012-2013 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.bits;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.BitsInt;

public class TestTranspose {

	@Test
	public void test1() {
		long[] v = new long[]{1, 2, 4, 8, 16};
		long[] tv = PhTreeHelper.transposeValue(v, 8);
		check(tv, 0L, 0L, 0L, 1L, 2L, 4L, 8L, 16L);
	}

	@Test
	public void test2() {
		long[] v = new long[]{16, 2, 1, 8, 16};
		long[] tv = PhTreeHelper.transposeValue(v, 8);
		check(tv, 0L, 0L, 0L, 17L, 2L, 0L, 8L, 4L);
	}

	@Test
	public void test3() {
		long[] v = new long[]{16, 2, 0, 8, 16};
		long[] tv = PhTreeHelper.transposeValue(v, 8);
		check(tv, 0L, 0L, 0L, 17L, 2L, 0L, 8L, 0L);
	}

	@Test
	public void test4() {
		long[] v = new long[]{1, 3, 5, 3, 15};
		long[] tv = PhTreeHelper.transposeValue(v, 8);
		check(tv, 0L, 0L, 0L, 0L, 1L, 5L, 11L, 31L);
	}

	private void check(long[] tv, long... v) {
		int DEPTH = tv.length;
		for (int i = 0; i < v.length; i++) {
			//this makes it much faster!
			if (v[i] != tv[i]) {
				assertEquals("i=" + i + " | " + BitsInt.toBinary(v, DEPTH) + " / " + 
						BitsInt.toBinary(tv, DEPTH), v[i], tv[i]);
			}
		}
	}

}

