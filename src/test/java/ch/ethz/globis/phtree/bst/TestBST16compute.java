/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.bst;

import ch.ethz.globis.phtree.v16.Node;
import ch.ethz.globis.phtree.v16.Node.BSTEntry;
import ch.ethz.globis.phtree.v16.PhTree16;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorMask;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class TestBST16compute {

	private static final int N1 = 100_000;
	private static final int N2 = 10*N1;
	
	private static final int DIM = 10;
	private static final PhTree16<Object> tree = new PhTree16<>(DIM);
	
	private Node create() {
		return Node.createNode(DIM, 0, 63, tree);
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
		return IntStream.range(0, n).boxed().map(TestBST16compute::createEntry).collect(Collectors.toList());
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
			ht.bstCompute((int)i.getValue(), i.getKdKey(), tree, true, true, (longs, o) -> {
				assertSame(i.getKdKey(), longs);
				assertNull(o);
				//TODO clone 'i'?
				return i.getValue();
			});

			//Check
			BSTEntry be = ht.bstGet((Integer)i.getValue());
			assertEquals((int)i.getValue(), (int)be.getValue());
		}
		long l12 = System.currentTimeMillis();
		assertEquals(list.size(), ht.getEntryCount());
		
		println(ht.getStats().toString());
		
		//lookup
		long l21 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			BSTEntry e = ht.bstGet((Integer)i.getValue());
			//assertNotNull("i=" + i, e);
			int x = (int) e.getValue();
			assertEquals(i.getValue(), x);
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
		BSTIteratorMask iterMask = new BSTIteratorMask().reset(ht.getRoot(), 0, 0xFFFFFFFFFFFEL, ht.getEntryCount());
		prev = -2;
		while (iterMask.hasNextEntry()) {
			long current = iterMask.nextEntry().getKey();
			assertEquals(prev + 2, current);
			prev = current;
		}
		assertEquals(prev, ((list.size()-1) & 0xFFFFFFFE));
		long l62 = System.currentTimeMillis();
		
		//replace some
		long l31 = System.currentTimeMillis();
		for (BSTEntry i : list) {
			//ht.bstPut((Integer)i.getValue(), new BSTEntry(i.getKdKey(), -(Integer)i.getValue()));
			ht.bstCompute((Integer)i.getValue(), i.getKdKey(), tree, true, true,
					(longs, o) -> -(Integer)i.getValue() );
		}
		long l32 = System.currentTimeMillis();
		assertEquals(list.size(), ht.getEntryCount());
		
		//remove some
		long l41 = System.currentTimeMillis();
		boolean[] found = new boolean[1];
		for (BSTEntry i : list) {
			found[0] = false;
			ht.bstCompute((Integer)i.getValue(), i.getKdKey(), tree, true, true,
					(longs, o) -> {
						found[0] = true;
						return null;
					});
			assertTrue(found[0]);
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
				BSTEntry e = ht.bstGetOrCreate(i, tree);
				e.set(i, new long[] {i}, i);
			}
			
			for (int i = 0; i < 100000; i++) {
				BSTEntry e = ht.bstRemove(i, new long[] {i}, null, tree);
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

		BSTIteratorMask iterMask = new BSTIteratorMask().reset(ht.getRoot(), 0, 0xFFFFFFFFFFFEL, ht.getEntryCount());
		assertFalse(iterMask.hasNextEntry());
				
		BSTEntry e2 = ht.bstRemove(12345, null, null, tree);
		assertNull(e2);
	}

}
