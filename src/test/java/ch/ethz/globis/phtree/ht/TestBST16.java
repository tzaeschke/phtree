/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.ht;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;

import ch.ethz.globis.phtree.v16.Node;
import ch.ethz.globis.phtree.v16.Node.BSTEntry;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorMask;

public class TestBST16 {

	private static final int N1 = 100_000;
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
		BSTEntry e = new BSTEntry(i, new long[DIM], i);
		e.getKdKey()[0] = i;
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
			//System.out.println("ins=" + i);
			//ht.bstPut((Integer)i.getValue(), i);
			BSTEntry newBE = ht.bstGetOrCreate((int)i.getValue());
			newBE.set((int)i.getValue(), i.getKdKey(), i.getValue());
			
			//Check
			BSTEntry be = ht.bstGet((Integer)i.getValue());
			assertEquals((int)i.getValue(), (int)be.getValue());
			
			//TODO remove
//			BSTIteratorAll iter = ht.iterator();
//			long prev = -1;
//			while (iter.hasNextEntry()) {
//				long current = iter.nextEntry().getKey();
//				assertEquals(prev + 1, current);
//				prev = current;
//			}
//			assertEquals(i.getKey(), prev);
		}
		long l12 = System.currentTimeMillis();
		assertEquals(list.size(), ht.getEntryCount());
		
		//println(ht.getStats().toString());
		
		//lookup
		long l21 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			BSTEntry e = ht.bstGet((Integer)i.getValue());
			//assertNotNull("i=" + i, e);
			int x = (int) e.getValue();
			assertEquals(i.getValue(), (int) x);
		}
		long l22 = System.currentTimeMillis();
		
		//iterate
		long l51 = System.currentTimeMillis();
		BSTIteratorAll iter = ht.iterator();
		long prev = -1;
		while (iter.hasNextEntry()) {
			long current = iter.nextEntry().getKey();
			assertEquals(prev + 1, current);
			prev = current;
		}
		assertEquals(prev, list.size() - 1);
		long l52 = System.currentTimeMillis();

		long l61 = System.currentTimeMillis();
		BSTIteratorMask iterMask = ht.iteratorMask(0, 0xFFFFFFFFFFFEL);
		prev = -2;
		while (iterMask.hasNextEntry()) {
			long current = iterMask.nextEntry().getKey();
			assertEquals(prev + 2, current);
			prev = current;
		}
		assertEquals(prev, list.size() - 2);
		long l62 = System.currentTimeMillis();
		
//		long l71 = System.currentTimeMillis();
//		BSTIteratorLeafAll iterLeaf = new BSTIteratorLeafAll().reset(ht.getRoot());
//		prev = -1;
//		while (iterLeaf.hasNextEntry()) {
//			long current = iterLeaf.nextEntry().getKey();
//			assertEquals(prev + 1, current);
//			prev = current;
//		}
//		assertEquals(prev, list.size() - 1);
//		long l72 = System.currentTimeMillis();
		
		//replace some
		long l31 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			//ht.bstPut((Integer)i.getValue(), new BSTEntry(i.getKdKey(), -(Integer)i.getValue()));
			BSTEntry newBE = ht.bstGetOrCreate((Integer)i.getValue());
			newBE.setValue(-(Integer)i.getValue());
		}
		long l32 = System.currentTimeMillis();
		assertEquals(list.size(), ht.getEntryCount());
		
		//remove some
		long l41 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			assertEquals(-(Integer)i.getValue(), ht.bstRemove((Integer)i.getValue(), i.getKdKey(), null).getValue());
		}
		long l42 = System.currentTimeMillis();
		assertEquals(0, ht.getEntryCount());
		
		println(prefix + "Load: " + (l12-l11));
		println(prefix + "Get:  " + (l22-l21));
		println(prefix + "Iter: " + (l52-l51));
		println(prefix + "IterM:" + (l62-l61));
//		println(prefix + "IterL:" + (l72-l71));
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
	
	
	@Test
	public void testEmpty() {
		Node ht = create();
		checkEmpty(ht);
	}
	
	@Test
	public void testEmptyAfterLoad() {
		Node ht = create();
		
		for (int r = 0; r < 10; r++) {
		
			for (int i = 0; i < 100000; i++) {
				BSTEntry e = ht.bstGetOrCreate(i);
				e.set(i, new long[] {i}, i);
			}
			
			for (int i = 0; i < 100000; i++) {
				BSTEntry e = ht.bstRemove(i, new long[] {i}, null);
				assertEquals(i, (int)e.getValue());
			}
		
			checkEmpty(ht);
		}
	}
	
	private void checkEmpty(Node ht) {
		assertEquals(0, ht.getEntryCount());
		
		BSTEntry e = ht.bstGet(12345);
		assertNull(e);
		
		//iterate
		BSTIteratorAll iter = ht.iterator();
		assertFalse(iter.hasNextEntry());

		BSTIteratorMask iterMask = ht.iteratorMask(0, 0xFFFFFFFFFFFEL);
		assertFalse(iterMask.hasNextEntry());
		
//		BSTIteratorLeafAll iterLeaf = new BSTIteratorLeafAll().reset(ht.getRoot());
//		assertFalse(iterLeaf.hasNextEntry());
				
		BSTEntry e2 = ht.bstRemove(12345, null, null);
		assertNull(e2);
	}

}
