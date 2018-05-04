/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.ht;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import ch.ethz.globis.phtree.v14.bst.BSTIteratorMask;
import ch.ethz.globis.phtree.v14.bst.BSTIteratorMinMax;
import ch.ethz.globis.phtree.v14.bst.BSTree;
import ch.ethz.globis.phtree.v14.bst.LLEntry;

public class TestBST {

	private static final int N1 = 1_000_000;
	private static final int N2 = 10*N1;
	
	private <T> BSTree<T> create() {
		return new BSTree<>(100, 100);
	}
	
	@Test
	public void testBSTree() {
		runTest(createData(N1), "");
		System.gc();
		runTest(createData(N2), "");
		System.gc();
		runTest(createData(N2), "");
	}
	
	
	@Test
	public void testBSTreeRND() {
//		for (int i = 0; i < 1000; i++) {
//			System.out.println("seed=" + i);
//			runTest(createDataRND(i, 44), "R-");
//		}
//		runTest(createDataRND(834, 44), "R-");
		runTest(createDataRND(0, N1), "R-");
		System.gc();
		runTest(createDataRND(0, N2), "R-");
		System.gc();
		runTest(createDataRND(0, N2), "R-");
	}
	
	
	private List<Integer> createData(int n) {
		return IntStream.range(0, n).boxed().collect(Collectors.toList());
	}
	
	private List<Integer> createDataRND(int seed, int n) {
		List<Integer> list = createData(n);
		Collections.shuffle(list, new Random(seed));
		return list;
	}
	
	private void runTest(List<Integer> list, String prefix) {
		BSTree<Integer> ht = create();
	
		
		//populate
		long l11 = System.currentTimeMillis();
		for (int i : list) {
			//if (i%1000 == 0) 
			//	System.out.println("ins=" + i);
			ht.put(i, i);
			assertEquals(i, ht.get(i).getKey());
		}
		long l12 = System.currentTimeMillis();
		assertEquals(list.size(), ht.size());
		
		println(ht.getStats().toString());
		
		//lookup
		long l21 = System.currentTimeMillis();
		for (int i : list) {
			LLEntry e = ht.get(i);
			//assertNotNull("i=" + i, e);
			int x = (int) e.getValue();
			assertEquals(i, (int) x);
		}
		long l22 = System.currentTimeMillis();
		
		//iterate
		long l51 = System.currentTimeMillis();
		BSTIteratorMinMax<Integer> iter = ht.iterator();
		long prev = -1;
		while (iter.hasNextULL()) {
			long current = iter.nextKey();
			assertEquals(prev + 1, current);
			prev = current;
		}
		assertEquals(prev, list.size() - 1);
		long l52 = System.currentTimeMillis();
		long l61 = System.currentTimeMillis();
		BSTIteratorMask<Integer> iterMask = ht.iteratorMask(0, 0xFFFFFFFFFFFEL);
		prev = -2;
		while (iterMask.hasNextULL()) {
			long current = iterMask.nextKey();
			assertEquals(prev + 2, current);
			prev = current;
		}
		assertEquals(prev, list.size() - 2);
		long l62 = System.currentTimeMillis();
		
		//replace some
		long l31 = System.currentTimeMillis();
		for (int i : list) {
			ht.put(i, -i);
			//if (i%1000 == 0) System.out.println("rep=" + i);
		}
		long l32 = System.currentTimeMillis();
		assertEquals(list.size(), ht.size());
		
		//remove some
		long l41 = System.currentTimeMillis();
		for (int i : list) {
			//if (i%1000 == 0) 
			//System.out.println("rem=" + i);
			assertEquals(-i, (int) ht.remove(i));
//			if (ht.size() % 100_000 == 0) {
//				println(ht.getStats().toString());
//			}
		}
		long l42 = System.currentTimeMillis();
		assertEquals(0, ht.size());
		
		println(prefix + "Load: " + (l12-l11));
		println(prefix + "Get:  " + (l22-l21));
		println(prefix + "Iter: " + (l52-l51));
		println(prefix + "IterM:" + (l62-l61));
		println(prefix + "Load: " + (l32-l31));
		println(prefix + "Rem : " + (l42-l41));
		println();
	}
	
	
	private static void println() {
		println("");
	}

	private static void println(String str) {
		System.out.println(str);
	}
	
}
