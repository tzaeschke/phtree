/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14.bst;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.v14.bst.BSTIteratorMinMax.LLEntry;


/**
 * @author Tilmann Zaeschke
 */
public class BSTree<T> {
	
	static final Object NULL = new Object();
	
	protected final int maxLeafN = 10;//340;
	/** Max number of keys in inner page (there can be max+1 page-refs) */
	protected final int maxInnerN = 11;//509;
	protected final int minLeafN = maxLeafN >> 1;  //254
	protected final int minInnerN = maxInnerN >> 1;  //170
	protected static int statNLeaves = 0;
	protected static int statNInner = 0;
	private int modCount = 0;

	private int nEntries = 0;
	
	private transient BSTreePage root;
	
	public BSTree() {
		//bootstrap index
		root = createPage(null, false);
	}

	public final Object put(long key, T value) {
		Object val = value == null ? NULL : value;
		//Depth as log(nEntries) 
		BSTreePage page = getRoot();
		Object o = page;
		while (o instanceof BSTreePage && !((BSTreePage)o).isLeaf()) {
			o = ((BSTreePage)o).put(key, val);
		}
		if (o == null) {
			//did not exist
			nEntries++;
		}
		return o;
	}

	/**
	 * @param key The key to remove
	 * @return the previous value
	 * @throws NoSuchElementException if key is not found
	 */
	public boolean remove(long key) {
		BSTreePage page = getRoot();
		Object result = null;
		if (page != null) {
			result = page.findAndRemove(key);
			if (result == null) {
				throw new NoSuchElementException("Key not found: " + key);
			}
		}

		nEntries--;
		return true;
	}

	public LLEntry get(long key) {
		BSTreePage page = getRoot();
		while (page != null && !page.isLeaf()) {
			page = page.findSubPage(key);
		}
		if (page == null) {
			return null;
		}
		return page.getValueFromLeaf(key);
	}

	BSTreePage createPage(BSTreePage parent, boolean isLeaf) {
		return new BSTreePage(this, (BSTreePage) parent, isLeaf);
	}

	BSTreePage getRoot() {
		return root;
	}

	public BSTIteratorMinMax<T> iterator(long min, long max) {
		return new BSTIteratorMinMax<>(this, min, max);
	}

	void updateRoot(BSTreePage newRoot) {
		root = newRoot;
	}
	
	public void print() {
		root.print("");
	}

	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		if (root != null) {
			root.toStringTree(sb, "");
		}
		return sb.toString();
	}

	public long getMaxKey() {
		return root.getMax();
	}

	public long getMinKey() {
		return root.getMinKey();
	}

	
	public BSTIteratorMinMax<T> iterator() {
		return iterator(Long.MIN_VALUE, Long.MAX_VALUE);
	}

	
	public void clear() {
		getRoot().clear();
		nEntries = 0;
		BTPool.reportFreePage(getRoot());
		BSTree.statNInner = 0;
		BSTree.statNLeaves = 0;
	}
	
	void checkValidity(int modCount) {
		if (this.modCount != modCount) {
			throw new ConcurrentModificationException();
		}
	}

	public static class Stats {
		int nNodesInner = 0;
		int nNodesLeaf = 0;
		int capacityInner = 0;
		int capacityLeaf = 0;
		int nEntriesInner = 0;
		int nEntriesLeaf = 0;
		
		@Override
		public String toString() {
			return "nNodesI=" + nNodesInner
					+ ";nNodesL=" + nNodesLeaf
					+ ";capacityI=" + capacityInner
					+ ";capacityL=" + capacityLeaf
					+ ";nEntriesI=" + nEntriesInner
					+ ";nEntriesL=" + nEntriesLeaf
					+ ";fillRatioI=" + round(nEntriesInner/(double)capacityInner)
					+ ";fillRatioL=" + round(nEntriesLeaf/(double)capacityLeaf)
					+ ";fillRatio=" + round((nEntriesInner+nEntriesLeaf)/(double)(capacityInner+capacityLeaf));
		}
		private static double round(double d) {
			return ((int)(d*100+0.5))/100.;
		}
	}
	
	public Stats getStats() {
		Stats stats = new Stats();
		if (root != null) {
			root.getStats(stats);
		}
		return stats;
	}

	int getModCount() {
		return modCount;
	}
	
	public int size() {
		return nEntries;
	}
	
}

