/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.zoodb.index.critbit.BitTools;

import ch.ethz.globis.pht.test.util.TestSuper;
import ch.ethz.globis.pht.test.util.TestUtil;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.nv.PhTreeNV;
import ch.ethz.globis.phtree.util.Bits;

public class TestIndexQuerieSet extends TestSuper {

	private PhTreeNV createNV(int dim, int depth) {
		return TestUtil.newTreeNV(dim, depth);
	}

	@Test
	public void testQueryND() {
		final int DIM = 5;
		final int N = 100;
		final int DEPTH = 3;
		
		for (int d = 0; d < DIM; d++) {
			PhTreeNV ind = createNV(DIM, DEPTH);
			for (int i = 0; i < N; i++) {
				long[] v = new long[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = (i >> (j*2)) & 0x03;  //mask off sign bits
				}
				assertFalse(Bits.toBinary(v, DEPTH), ind.insert(v));
			}
			
			//check empty result
			List<PhEntry<Object>> it;
			int n = 0;
			it = ind.queryAll(new long[]{0, 0, 0, 0, 0}, new long[]{0, 0, 0, 0, 0});
			for (PhEntry<?> v: it) {
				assertEquals(v.getKey()[0], 0);
				n++;
			}

			//lies outside value range...
//			it = ind.query(50, 500, 50, 500, 50, 500, 50, 500, 50, 500);
//			assertFalse(it.hasNext());

			//check full result
			//it = ind.query(0, 50, 0, 50, 0, 50, 0, 50, 0, 50);
			n = 0;
			it = ind.queryAll(new long[]{0, 0, 0, 0, 0}, new long[]{50, 50, 50, 50, 50});
			for (PhEntry<?> v: it) {
				assertNotNull(v);
				n++;
			}
			assertEquals(N, n);

			//check partial result
			n = 0;
			//it = ind.query(0, 0, 0, 50, 0, 50, 0, 50, 0, 50);
			it = ind.queryAll(new long[]{0, 0, 0, 0, 0}, new long[]{0, 50, 50, 50, 50});
			for (PhEntry<?> v: it) {
				n++;
				assertEquals(0, v.getKey()[0]);
			}
			assertEquals(25, n);

			n = 0;
			//it = ind.query(1, 1, 0, 50, 0, 50, 0, 50, 0, 50);
			it = ind.queryAll(new long[]{1, 0, 0, 0, 0}, new long[]{1, 50, 50, 50, 50});
			for (PhEntry<?> v: it) {
				n++;
				assertEquals(1, v.getKey()[0]);
			}
			assertEquals(25, n);
			
			
			n = 0;
			//it = ind.query(3, 3, 0, 50, 0, 50, 0, 50, 0, 50);
			it = ind.queryAll(new long[]{3, 0, 0, 0, 0}, new long[]{3, 50, 50, 50, 50});
			for (PhEntry<?> v: it) {
				n++;
				assertEquals(3, v.getKey()[0]);
			}
			assertEquals(25, n);
			
			
		}
	}
	
	
	/**
	 * Testing only positive 64 bit values (effectively 63 bit).
	 */
	@SuppressWarnings("unused")
	@Test
	public void testQueryND64RandomPos() {
		final int DIM_MAX = 5;
		final int N = 100;
		final Random R = new Random(0);
		
		for (int DIM = 1; DIM < DIM_MAX; DIM++) {
			PhTreeNV ind = createNV(DIM, 64);
			for (int i = 0; i < N; i++) {
				long[] v = new long[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = Math.abs(R.nextLong());
				}
				ind.insert(v);
			}
			
			//check empty result
			long[] NULL = new long[DIM];
			long[] MAX = new long[DIM];
			Arrays.fill(MAX, Long.MAX_VALUE);
			List<PhEntry<Object>> it;
			it = ind.queryAll(NULL, NULL);
			assertTrue(it.isEmpty());

			//check full result
			it = ind.queryAll(NULL, MAX);
			int n = 0;
			for (PhEntry<?> o: it) {
				n++;
				//System.out.println("v=" + Bits.toBinary(v, 64));
			}
			assertEquals(N, n);

			//check partial result
			n = 0;
			it = ind.queryAll(NULL, MAX);
			for (PhEntry<?> o: it) {
				n++;
			}
			assertTrue("n=" + n, n > N/10.);
		}
	}
	
	
	@SuppressWarnings("unused")
	@Test
	public void testQueryND64Random() {
		final int DIM_MAX = 5;
		final int N = 1000;
		final Random R = new Random(0);
		
		for (int DIM = 1; DIM < DIM_MAX; DIM++) {
			PhTreeNV ind = createNV(DIM, 64);
			for (int i = 0; i < N; i++) {
				long[] v = new long[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextLong();
				}
				ind.insert(v);
			}
			
			long[] NULL = new long[DIM];
			long[] MIN = new long[DIM];
			long[] MAX = new long[DIM];
			for (int i = 0; i < DIM; i++) {
				MIN[i] = Long.MIN_VALUE;
				MAX[i] = Long.MAX_VALUE;
			}
			
			//check empty result
			List<PhEntry<Object>> it;
			it = ind.queryAll(NULL, NULL);
			assertTrue(it.isEmpty());

			//check full result
			int n = 0;
			it = ind.queryAll(MIN, MAX);
			for (PhEntry<?> v: it) {
//				System.out.println("v=" + Bits.toBinary(v, 64));
				assertNotNull(v);
				n++;
			}
			assertEquals(N, n);

			//check partial result
			int nTotal = 0;
			n = 0;
			it = ind.queryAll(NULL, MAX);
			for (PhEntry<?> e: it) {
				n++;
				nTotal++;
			}
			assertTrue("n=" + n, n > 1);

			n = 0;
			it = ind.queryAll(MIN, NULL);
			for (PhEntry<?> e: it) {
				n++;
				nTotal++;
			}
			assertTrue("n=" + n, n > 1);
			//In average this is N/(2^DIM)
			assertTrue("nTotal=" + nTotal, nTotal > 10);
		}
	}
	
	
	@Test
	public void testQueryND64Random2() {
		final int DIM = 8;
		final int N = 100*1000;
		final Random R = new Random(0);
		long[][] data = new long[N][DIM];
		
		PhTreeNV ind = createNV(DIM, 64);
		for (int i = 0; i < N; i++) {
			long[] v = data[i];
			for (int j = 0; j < DIM; j++) {
				v[j] = BitTools.toSortableLong(R.nextDouble());
			}
			ind.insert(v);
		}

		for (int i = 0; i < N; i++) {
			long[] v = data[i];
			List<PhEntry<Object>> it;
			it = ind.queryAll(v, v);
			assertTrue(!it.isEmpty());
			assertEquals(1, it.size());
			long[] v2 = it.get(0).getKey();
			for (int j = 0; j < DIM; j++) {
				assertEquals(v[j], v2[j]);
			}
		}
	}
	
}
