/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import ch.ethz.globis.pht.test.util.TestUtil;
import ch.ethz.globis.phtree.PhTreeSolidF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhEntrySF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhIteratorSF;

public class TestRangeVDouble {

	private PhTreeSolidF<Integer> pht;
	private PhEntrySF<Integer> e1, e2, e3;
	
	@Before
	public void before() {
		pht = PhTreeSolidF.create(2);

		e1 = new PhEntrySF<>(new double[]{2,3}, new double[]{7,8}, 1);
		e2 = new PhEntrySF<>(new double[]{-1,-2}, new double[]{1,2}, 2);
		e3 = new PhEntrySF<>(new double[]{-7,-8}, new double[]{-2,-3}, 3);
	}
	
	@Test
	public void testInsertContains() {
		assertFalse(pht.contains(e1));
		assertFalse(pht.contains(e2));
		assertFalse(pht.contains(e3));
		
		pht.put(e1, 1);
		assertTrue(pht.contains(e1));
		assertFalse(pht.contains(e2));
		assertFalse(pht.contains(e3));

		pht.put(e2, 2);
		assertTrue(pht.contains(e1));
		assertTrue(pht.contains(e2));
		assertFalse(pht.contains(e3));

		pht.put(e3, 3);
		assertTrue(pht.contains(e1));
		assertTrue(pht.contains(e2));
		assertTrue(pht.contains(e3));
	}
	
	@Test
	public void testDeleteContains() {
		pht.put(e1, 1);
		pht.put(e2, 2);
		pht.put(e3, 3);

		pht.remove(e3);
		assertTrue(pht.contains(e1));
		assertTrue(pht.contains(e2));
		assertFalse(pht.contains(e3));

		pht.remove(e2);
		assertTrue(pht.contains(e1));
		assertFalse(pht.contains(e2));
		assertFalse(pht.contains(e3));

		pht.remove(e1);
		assertFalse(pht.contains(e1));
		assertFalse(pht.contains(e2));
		assertFalse(pht.contains(e3));
	}
	
	@Test
	public void testQueryInclude() {
		pht.put(e1, 1);
		pht.put(e2, 2);
		pht.put(e3, 3);
		
		Iterator<Integer> iter = pht.queryInclude(e1);
		assertEquals(e1.value(), iter.next());
		assertFalse(iter.hasNext());
		
		iter = pht.queryInclude(e2);
		assertEquals(e2.value(), iter.next());
		assertFalse(iter.hasNext());
		
		iter = pht.queryInclude(e3);
		assertEquals(e3.value(), iter.next());
		assertFalse(iter.hasNext());
		
		
		iter = pht.queryInclude(new double[]{-1, -1}, new double[]{1, 1});
		assertFalse(iter.hasNext());

		iter = pht.queryInclude(new double[]{-5, -5}, new double[]{-4, -4});
		assertFalse(iter.hasNext());

		iter = pht.queryInclude(new double[]{4, 4}, new double[]{5, 5});
		assertFalse(iter.hasNext());

		iter = pht.queryInclude(new double[]{-5, -5}, new double[]{5, 5});
		assertEquals(e2.value(), iter.next());
		assertFalse(iter.hasNext());

		iter = pht.queryInclude(new double[]{-15, -15}, new double[]{15, 15});
		//This order can change...
		assertEquals(e1.value(), iter.next());
		assertEquals(e2.value(), iter.next());
		assertEquals(e3.value(), iter.next());
		assertFalse(iter.hasNext());
	}
	
	@Test
	public void testQueryIntersect() {
		pht.put(e1, 1);
		pht.put(e2, 2);
		pht.put(e3, 3);
		
		PhIteratorSF<Integer> iter = pht.queryIntersect(e1);
		assertEquals(e1.value(), iter.next());
		assertFalse(iter.hasNext());
		
		iter = pht.queryIntersect(e2);
		assertEquals(e2.value(), iter.next());
		assertFalse(iter.hasNext());
		
		iter = pht.queryIntersect(e3);
		assertEquals(e3.value(), iter.next());
		assertFalse(iter.hasNext());
		
		
		iter = pht.queryIntersect(new double[]{-1, -1}, new double[]{1, 1});
		assertEquals(e2.value(), iter.next());
		assertFalse(iter.hasNext());

		iter = pht.queryIntersect(new double[]{-5, -5}, new double[]{-4, -4});
		assertEquals(e3.value(), iter.next());
		assertFalse(iter.hasNext());

		iter = pht.queryIntersect(new double[]{4, 4}, new double[]{5, 5});
		assertEquals(e1.value(), iter.next());
		assertFalse(iter.hasNext());

		iter = pht.queryIntersect(new double[]{-5, -5}, new double[]{5, 5});
		//This order can change...
		assertEquals(e1.value(), iter.next());
		assertEquals(e2.value(), iter.next());
		assertEquals(e3.value(), iter.next());
		assertFalse(iter.hasNext());

		iter = pht.queryIntersect(new double[]{-15, -15}, new double[]{15, 15});
		//This order can change...
		assertEquals(e1.value(), iter.next());
		assertEquals(e2.value(), iter.next());
		assertEquals(e3.value(), iter.next());
		assertFalse(iter.hasNext());
	}
	
	public static <T> PhTreeSolidF<T> createTree(int dim) {
    	return new PhTreeSolidF<>(TestUtil.newTree(2*dim, 64));
    }
    
	@Test
	public void testQuerySet() {
		int N = 1000;
		int DIM = 3;
		Random R = new Random(0);
		PhTreeSolidF<Integer> ind = createTree(DIM);
		double[][] keys = new double[N*2][DIM];
		for (int i = 0; i < N; i++) {
			for (int d = 0; d < DIM; d++) {
				keys[2*i][d] = R.nextDouble(); //INT!
				keys[2*i+1][d] = keys[2*i][d] + R.nextDouble(); //INT!
			}
			if (ind.contains(keys[2*i], keys[2*i+1])) {
				i--;
				continue;
			}
			//build
			assertNull(ind.put(keys[2*i], keys[2*i+1], Integer.valueOf(i)));
			assertTrue(ind.contains(keys[2*i], keys[2*i+1]));
			assertEquals(i, (int)ind.get(keys[2*i], keys[2*i+1]));
		}

		//full range query
		double[] min = new double[DIM];
		double[] max = new double[DIM];
		Arrays.fill(min, Double.NEGATIVE_INFINITY);
		Arrays.fill(max, Double.POSITIVE_INFINITY);
		List<PhEntrySF<Integer>> list = ind.queryIntersectAll(min, max);
		int n = 0;
		for (PhEntrySF<Integer> e: list) {
			assertNotNull(e);
			assertArrayEquals(keys[2*e.value()], e.lower(), 0.0);
			assertArrayEquals(keys[2*e.value()+1], e.upper(), 0.0);
			n++;
		}
		assertEquals(N, n);
		
		//spot queries
		for (int i = 0; i < N; i++) {
			list = ind.queryIntersectAll(keys[2*i], keys[2*i+1]);
			assertFalse(list.isEmpty());
			boolean found = false;
			for (PhEntrySF<Integer> e: list) {
				if (e.value() == i) {
					found = true;
					assertArrayEquals(keys[2*e.value()], e.lower(), 0.0);
					assertArrayEquals(keys[2*e.value()+1], e.upper(), 0.0);
					assertEquals(i, (int)e.value());
				}
			}
			assertTrue(found);
		}
		
		//delete
		for (int i = 0; i < N; i++) {
			//System.out.println("Removing: " + Bits.toBinary(keys[i], 64));
			//System.out.println("Tree: \n" + ind);
			assertEquals(i, (int)ind.remove(keys[2*i], keys[2*i+1]));
			assertFalse(ind.contains(keys[2*i], keys[2*i+1]));
			assertNull(ind.get(keys[2*i], keys[2*i+1]));
		}
		
		assertEquals(0, ind.size());
	}
	

}
