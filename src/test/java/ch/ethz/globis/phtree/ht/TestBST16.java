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

import ch.ethz.globis.phtree.v16.BSTHandler.BSTEntry;
import ch.ethz.globis.phtree.v16.Node;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorMask;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorMinMax;
import ch.ethz.globis.phtree.v16.bst.LLEntry;

public class TestBST16 {

	private static final int N1 = 1_00_000;
	private static final int N2 = 10*N1;
	
	private static final int DIM = 10;
	
	private Node create() {
		return Node.createNode(DIM, 0, 63);
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
	
	
	private List<BSTEntry> createData(int n) {
		return IntStream.range(0, n).boxed().map(i -> new BSTEntry(new long[DIM], i)).collect(Collectors.toList());
	}
	
	private List<BSTEntry> createDataRND(int seed, int n) {
		List<BSTEntry> list = createData(n);
		Collections.shuffle(list, new Random(seed));
		return list;
	}
	
	private void runTest(List<BSTEntry> list, String prefix) {
		Node ht = create();
	
		
		//populate
		long l11 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			//if (i%1000 == 0) 
			//	System.out.println("ins=" + i);
			ht.put((Integer)i.getValue(), i);
			//Check
			LLEntry le = ht.get((Integer)i.getValue());
			assertEquals((int)i.getValue(), (long)le.getKey());
		}
		long l12 = System.currentTimeMillis();
		assertEquals(list.size(), ht.size());
		
		println(ht.getStats().toString());
		
		//lookup
		long l21 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			LLEntry e = ht.get((Integer)i.getValue());
			//assertNotNull("i=" + i, e);
			int x = (int) e.getValue().getValue();
			assertEquals(i.getValue(), (int) x);
		}
		long l22 = System.currentTimeMillis();
		
		//iterate
		long l51 = System.currentTimeMillis();
		BSTIteratorMinMax iter = ht.iterator();
		long prev = -1;
		while (iter.hasNextULL()) {
			long current = iter.nextKey();
			assertEquals(prev + 1, current);
			prev = current;
		}
		assertEquals(prev, list.size() - 1);
		long l52 = System.currentTimeMillis();
		long l61 = System.currentTimeMillis();
		BSTIteratorMask iterMask = ht.iteratorMask(0, 0xFFFFFFFFFFFEL);
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
		for (BSTEntry i : list) {
			ht.put((Integer)i.getValue(), new BSTEntry(i.getKdKey(), -(Integer)i.getValue()));
			//if (i%1000 == 0) System.out.println("rep=" + i);
		}
		long l32 = System.currentTimeMillis();
		assertEquals(list.size(), ht.size());
		
		//remove some
		long l41 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			//if (i%1000 == 0) 
			//System.out.println("rem=" + i);
			assertEquals(-(Integer)i.getValue(), (int) ht.remove((Integer)i.getValue()).getValue());
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
