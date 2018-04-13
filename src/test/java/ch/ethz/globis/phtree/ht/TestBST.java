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
		int N = 10_000_000;
		BSTree<Integer> ht = create();
		
		//populate
		for (int i = 0; i < N; i++) {
			ht.insertLong(i, i+100);
			//assertEquals(i, pos);
			//if (i%1000 == 0) System.out.println("ins=" + i);
		}
		
		//lookup
		for (int i = 0; i < N; i++) {
			LLEntry e = ht.findValue(i);
			int x = (int) e.getValue();
			assertEquals("i=" + i, i+100, (int) x);
			//if (i%1000 == 0) System.out.println("lu=" + i);
		}
		
		//replace some
		for (int i = 0; i < N; i++) {
			ht.insertLong(i, -i);
			//if (i%1000 == 0) System.out.println("rep=" + i);
		}
		
		//remove some
		for (int i = 0; i < N; i++) {
			assertEquals(-i, (int) ht.removeLong(i));
			//if (i%1000 == 0) System.out.println("rem=" + i);
		}
	}
	
}
