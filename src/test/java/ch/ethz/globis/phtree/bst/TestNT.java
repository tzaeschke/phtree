/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.bst;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ch.ethz.globis.phtree.v11.nt.NodeTreeV11;

public class TestNT {

	private static final int BITS = 64;
	
	private <T> NodeTreeV11<T> create() {
		return NodeTreeV11.create(BITS);
	}
	
	@Test
	public void testBSTree() {
		int N = 100_000;
		NodeTreeV11<Integer> ht = create();
		
		//populate
		long[] out = new long[BITS];
		for (int i = 0; i < N; i++) {
			ht.put(i, out, i+100);
			//assertEquals(i, pos);
			//if (i%1000 == 0) System.out.println("ins=" + i);
		}
		
		//lookup
		for (int i = 0; i < N; i++) {
			int x = ht.get(i, out);
			assertEquals("i=" + i, i+100, x);
			//if (i%1000 == 0) System.out.println("lu=" + i);
		}
		
		//replace some
		for (int i = 0; i < N; i++) {
			ht.put(i, out, -i);
			//if (i%1000 == 0) System.out.println("rep=" + i);
		}
		
		//remove some
		for (int i = 0; i < N; i++) {
			assertEquals(-i, (int) ht.remove(i));
			//if (i%1000 == 0) System.out.println("rem=" + i);
		}
	}
	
}
