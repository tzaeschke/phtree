/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.ht;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ch.ethz.globis.phtree.stuff.HTable2;

public class TestHTable2 {

	private <T> HTable2<T> create() {
		return new HTable2<>();
	}
	
	@Test
	public void testHTable() {
		int N = 1_000_000;
		HTable2<Integer> ht = create();
		
		//populate
		for (int i = 0; i < N; i++) {
			int pos = ht.append(i);
			assertEquals(i, pos);
			//if (i%1000 == 0) System.out.println("ins=" + i);
		}
		
		//lookup
		for (int i = 0; i < N; i++) {
			Integer x = ht.get(i);
			assertEquals("i=" + i, i, (int) x);
			//if (i%1000 == 0) System.out.println("lu=" + i);
		}
		
		//replace some
		for (int i = 0; i < N/2; i++) {
			assertEquals(N - i - 1, (int) ht.replaceWithLast(i));
			//if (i%1000 == 0) System.out.println("rep=" + i);
		}
		
		//remove some
		for (int i = 0; i < N/2-1; i++) {
			assertEquals(N/2 + i, (int) ht.replaceWithLast(0));
			//if (i%1000 == 0) System.out.println("rem=" + i);
		}
		
		assertEquals(null, ht.replaceWithLast(0));		
	}
	
}
