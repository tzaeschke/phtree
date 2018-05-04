/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.ht;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ch.ethz.globis.phtree.v14.HTable;

public class TestHTable {

	private <T> HTable<T> create() {
		return new HTable<>();
	}
	
	@Test
	public void testHTable() {
		int N = 2000;
		HTable<Integer> ht = create();
		
		//populate
		for (int i = 0; i < N; i++) {
//			System.out.println(i); //TODO
			int pos = ht.append(i);
			assertEquals(i, pos);
		}
		
		//lookup
		for (int i = 0; i < N; i++) {
//			System.out.println("lookup: " + i); //TODO
			Integer x = ht.get(i);
			assertEquals("i=" + i, i, (int) x);
		}
		
		//replace some
		for (int i = 0; i < N/2; i++) {
//			System.out.println("replace: " + i); //TODO
			assertEquals(N - i - 1, (int) ht.replaceWithLast(i));
		}
		
		//remove some
		for (int i = 0; i < N/2; i++) {
//			System.out.println("remove: " + i); //TODO
			assertEquals(N/2 + i, (int) ht.replaceWithLast(0));
		}
		
	}
	
}
