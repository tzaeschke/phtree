/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.ht;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ch.ethz.globis.phtree.v14.bst.BSTree;
import ch.ethz.globis.phtree.v14.bst.BSTreeIterator.LLEntry;

public class TestBST {

	private <T> BSTree<T> create() {
		return new BSTree<>();
	}
	
	@Test
	public void testBSTree() {
		runTest(1_000_000);
		System.gc();
		runTest(10_000_000);
		System.gc();
		runTest(10_000_000);
	}
	
	private void runTest(int N) {
		BSTree<Integer> ht = create();
		
		//populate
		long l11 = System.currentTimeMillis();
		for (int i = 0; i < N; i++) {
			ht.put(i, i+100);
			//assertEquals(i, pos);
			//if (i%1000 == 0) System.out.println("ins=" + i);
		}
		long l12 = System.currentTimeMillis();
		
		//lookup
		long l21 = System.currentTimeMillis();
		for (int i = 0; i < N; i++) {
			LLEntry e = ht.get(i);
			int x = (int) e.getValue();
			assertEquals("i=" + i, i+100, (int) x);
			//if (i%1000 == 0) System.out.println("lu=" + i);
		}
		long l22 = System.currentTimeMillis();
		
		//replace some
		long l31 = System.currentTimeMillis();
		for (int i = 0; i < N; i++) {
			ht.put(i, -i);
			//if (i%1000 == 0) System.out.println("rep=" + i);
		}
		long l32 = System.currentTimeMillis();
		
		//remove some
		long l41 = System.currentTimeMillis();
		for (int i = 0; i < N; i++) {
			assertEquals(-i, (int) ht.remove(i));
			//if (i%1000 == 0) System.out.println("rem=" + i);
		}
		long l42 = System.currentTimeMillis();
		
		System.out.println("Load: " + (l12-l11));
		System.out.println("Get:  " + (l22-l21));
		System.out.println("Load: " + (l32-l31));
		System.out.println("Rem : " + (l42-l41));
		
	}
	
}
