/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.zoodb.index.critbit.BitTools;

import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.test.util.TestSuper;
import ch.ethz.globis.phtree.test.util.TestUtil;

public class TestIndexUpdate extends TestSuper {

	private <T> PhTree<T> create(int dim, int depth) {
		return TestUtil.newTree(dim, depth);
	}
	
	@Test
	public void testSmokeTest() {
		PhTree<long[]> t = create(3, 64);
		long[] p1 = new long[]{2,3,4};
		long[] p2 = new long[]{5,6,7};
		assertNull(t.update(p1, p2));
		assertFalse(t.contains(p1));
		assertFalse(t.contains(p2));
		
		t.put(p1, p1);
		assertNotNull(t.update(p1, p2));
		assertFalse(t.contains(p1));
		assertTrue(t.contains(p2));
		
		assertNull(t.update(p1, p2));
		assertFalse(t.contains(p1));
		assertTrue(t.contains(p2));
	}
	
	@Test
	public void testSmokeTest2() {
		Random R = new Random(0);
		int N = 100000;
		int K = 3;
		double MIN = 0;
		double MAX = 1000;
		double BOX_LEN = 1;
		long[][] data = new long[N][K];
		for (int i = 0; i < N; i++) {
			for (int k = 0; k < K; k++) {
				data[i][k] = BitTools.toSortableLong(R.nextDouble()*(MAX-MIN));
			}
		}
		
		PhTree<long[]> tree = create(K, 64);
		for (long[] r: data) {
			//System.out.println("Inserting: " + r);
			tree.put(r, r);
			assertTrue(tree.contains(r));
		}
		
		//update
		for (int repeat = 0; repeat < 10; repeat++) {
			for (int i = 0; i < 10000; i++) {
				long[] r = data[i];
				//System.out.println("Updating: " + BitsLong.toBinary(r));
				double delta = R.nextDouble() * BOX_LEN/10.;
				long[] rNew = r.clone();
				Arrays.setAll(rNew, x -> BitTools.toSortableLong(BitTools.toDouble(x)+delta));
				assertTrue(tree.contains(r));
				assertNotNull(tree.update(r, rNew));
				data[i] = rNew;
				assertFalse(tree.contains(r));
				assertTrue(tree.contains(rNew));
			}
		}
		
		for (long[] r: data) {
			tree.remove(r);
			assertFalse(tree.contains(r));
		}
	}
	
	@Test
	public void testSmokeTest2b() {
		Random R = new Random(0);
		int N = 100000;
		int K = 15;
		double MIN = 0;
		double MAX = 1.000;
		double BOX_LEN = MAX/1000.0;
		long[][] data = new long[N][K];
		for (int i = 0; i < N; i++) {
			for (int k = 0; k < K; k++) {
				data[i][k] = BitTools.toSortableLong(R.nextDouble()*(MAX-MIN));
			}
		}
		
		PhTree<long[]> tree = create(K, 64);
		for (long[] r: data) {
			//System.out.println("Inserting: " + r);
			tree.put(r, r);
			assertTrue(tree.contains(r));
		}
		
		//update
		for (int repeat = 0; repeat < 10; repeat++) {
			for (int i = 0; i < 10000; i++) {
				long[] r = data[i];
				//System.out.println("Updating: " + BitsLong.toBinary(r));
				double delta = R.nextDouble() * BOX_LEN/10.;
				long[] rNew = r.clone();
				Arrays.setAll(rNew, x -> BitTools.toSortableLong(BitTools.toDouble(x)+delta));
				assertTrue(tree.contains(r));
				assertNotNull(tree.update(r, rNew));
				data[i] = rNew;
				assertFalse(tree.contains(r));
				assertTrue(tree.contains(rNew));
			}
		}
		
		for (long[] r: data) {
			tree.remove(r);
			assertFalse(tree.contains(r));
		}
	}
	
	@Test
	public void testBug0() {
		int MUL = 100*1000*1000;
		double maxD = 0.00001;
		//int DELTA = (int) (0.00001 * MUL); 
		Random R = new Random(0);
		int N = 20;
		int K = 2;
		double MIN = 0;
		double MAX = 1.000;
		//double BOX_LEN = MAX/1000.0;
		long[][] data = new long[N][K];
		for (int i = 0; i < N; i++) {
			for (int k = 0; k < K; k++) {
				data[i][k] = (long) (R.nextDouble()*(MAX-MIN) * MUL);
			}
		}
		
		PhTree<long[]> tree = create(K, 64);
		for (long[] r: data) {
			//System.out.println("Inserting: " + r);
			tree.put(r, r);
			assertTrue(tree.contains(r));
		}
		
		//update
		for (int repeat = 0; repeat < 10; repeat++) {
			for (int i = 0; i < N; i++) {
				long[] r = data[i];
				//System.out.println("Updating: " + BitsLong.toBinary(r));
				double delta = (R.nextDouble()*2*maxD-maxD) * MUL;
				//double delta = R.nextDouble() * BOX_LEN/10.;
				long[] rNew = new long[K];
				Arrays.setAll(rNew, x -> (r[x] + (long)(delta)));
				assertTrue("r="+ repeat + "  i=" + i, tree.contains(r));
				assertNotNull(tree.update(r, rNew));
				data[i] = rNew;
				assertFalse("r="+ repeat + "  i=" + i,tree.contains(r));
				assertTrue(tree.contains(rNew));
			}
		}
		
		for (long[] r: data) {
			tree.remove(r);
			assertFalse(tree.contains(r));
		}
	}

	@Test
	public void testBug0_20_2() {
		int MUL = 100*1000*1000;
		double maxD = 0.00001;
		Random R = new Random(3);
		int N = 10000;
		int K = 3;
		double MIN = 0;
		double MAX = 1.000;
		long[][] data = new long[N][K];
		for (int i = 0; i < N; i++) {
			for (int k = 0; k < K; k++) {
				data[i][k] = (long) (R.nextDouble()*(MAX-MIN) * MUL);
			}
		}
		
		PhTree<long[]> tree = create(K, 64);
		for (long[] r: data) {
			//System.out.println("Inserting: " + r);
			tree.put(r, r);
			assertTrue(tree.contains(r));
		}
		
		//update
		for (int repeat = 0; repeat < 10; repeat++) {
			for (int i = 0; i < N; i++) {
				long[] r = data[i];
				double delta = R.nextDouble()*2*maxD-maxD;
				long lDelta = (long) (delta*MUL);
				long[] rNew = new long[K];
				Arrays.setAll(rNew, x -> r[x] + lDelta);
				assertNotNull(tree.update(r, rNew));
				data[i] = rNew;
				if (lDelta > 0) {
					assertFalse("r="+ repeat + "  i=" + i, tree.contains(r));
					assertTrue(tree.contains(rNew));
				}
			}
		}
		
		for (long[] r: data) {
			tree.remove(r);
			assertFalse(tree.contains(r));
		}
	}
	
    @Test
    public void testUpdateKey() {
		PhTree<String> phTree = create(2, 64);
        long[] key = {1, 2};
        long[] key2 = {-1, -2};
        String value = "Hello, world";
        phTree.put(key, value);
        assertEquals(value, phTree.get(key));
        assertNull(phTree.get(key2));
        phTree.update(key, key2);
        System.out.println(phTree.toStringTree());//TODO
        assertEquals(value, phTree.get(key2));
        assertNull(phTree.get(key));
    }
}
