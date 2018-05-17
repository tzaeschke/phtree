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

import ch.ethz.globis.phtree.v16hd.Node;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorMask;

public class TestBST16hd {

	private static final int N1 = 10_000;
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
	
	private static BSTEntry createEntry(int i) {
		BSTEntry e = new BSTEntry(new long[1], new long[DIM], new long[1]);
		e.getKdKey()[0] = i;
		((long[])e.getValue())[0] = i;
		return e;
	}
	
	private List<BSTEntry> createData(int n) {
		return IntStream.range(0, n).boxed().map(i -> createEntry(i)).collect(Collectors.toList());
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
			//ht.bstPut((Integer)i.getValue(), i);
			BSTEntry newBE = ht.bstGetOrCreate((long[])i.getValue());
			newBE.set((long[])i.getValue(), i.getKdKey(), i.getValue());
			
			//Check
			BSTEntry be = ht.bstGet((long[])i.getValue());
			assertEquals((long[])i.getValue(), (long[])be.getValue());
		}
		long l12 = System.currentTimeMillis();
		assertEquals(list.size(), ht.getEntryCount());
		
		//println(ht.getStats().toString());
		
		//lookup
		long l21 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			BSTEntry e = ht.bstGet((long[])i.getValue());
			//assertNotNull("i=" + i, e);
			long[] x = (long[]) e.getValue();
			assertEquals(i.getValue(), x);
		}
		long l22 = System.currentTimeMillis();
		
		//iterate
		long l51 = System.currentTimeMillis();
		BSTIteratorAll iter = ht.iterator();
		long[] prev = new long[] {-1};
		while (iter.hasNextEntry()) {
			long[] current = iter.nextEntry().getKey();
			assertEquals(prev[0] + 1, current[0]);
			prev[0] = current[0];
		}
		assertEquals(prev[0], list.size() - 1);
		long l52 = System.currentTimeMillis();
		long l61 = System.currentTimeMillis();
		BSTIteratorMask iterMask = ht.iteratorMask(new long[] {0}, new long[] {0xFFFFFFFFFFFEL});
		prev = new long[] {-2};
		while (iterMask.hasNextEntry()) {
			long[] current = iterMask.nextEntry().getKey();
			assertEquals(prev[0] + 2, current[0]);
			prev[0] = current[0];
		}
		assertEquals(prev[0], list.size() - 2);
		long l62 = System.currentTimeMillis();
		
		//replace some
		long l31 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			//ht.bstPut((Integer)i.getValue(), new BSTEntry(i.getKdKey(), -(Integer)i.getValue()));
			BSTEntry newBE = ht.bstGetOrCreate((long[])i.getValue());
			newBE.setValue(-((long[])i.getValue())[0]);

			//if (i%1000 == 0) System.out.println("rep=" + i);
		}
		long l32 = System.currentTimeMillis();
		assertEquals(list.size(), ht.getEntryCount());
		
		//remove some
		long l41 = System.currentTimeMillis();
		for (BSTEntry i : list) {
//			System.out.println(ht.toStringTree());
			
			//if (i%1000 == 0) 
//			System.out.println("rem=" + i.getValue());
//			System.out.println("rem2=" + ht.bstRemove((Integer)i.getValue(), i.getKdKey(), null));
			assertEquals(-((long[])i.getValue())[0], ht.bstRemove((long[])i.getValue(), i.getKdKey(), null).getValue());
//			if (ht.size() % 100_000 == 0) {
//				println(ht.getStats().toString());
//			}
		}
		long l42 = System.currentTimeMillis();
		assertEquals(0, ht.getEntryCount());
		
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
