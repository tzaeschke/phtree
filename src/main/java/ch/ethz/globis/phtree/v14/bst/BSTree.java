/*
 * Copyright 2009-2016 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
 * 
 * ZooDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ZooDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ZooDB.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * See the README and COPYING files for further information. 
 */
package ch.ethz.globis.phtree.v14.bst;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.v14.bst.BSTreeIterator.LLEntry;


/**
 * @author Tilmann Zaeschke
 */
public class BSTree<T> {
	
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

	public final void put(long key, long value) {
		//Depth as log(nEntries) 
		BSTreePage page = getRoot();
		while (page != null && !page.isLeaf()) {
			page = page.put(key, value);
		}
	}

	/**
	 * @param key The key to remove
	 * @return the previous value
	 * @throws NoSuchElementException if key is not found
	 */
	public boolean remove(long key) {
		BSTreePage page = getRoot();
		while (page != null && !page.isLeaf()) {
			page = page.findSubPage(key, true);
		}

		if (page == null) {
			throw new NoSuchElementException("Key not found: " + key);
		}
		return true;
	}

	public LLEntry get(long key) {
		BSTreePage page = getRoot();
		while (page != null && !page.isLeaf()) {
			page = page.findSubPage(key, false);
		}
		if (page == null) {
			return null;
		}
		return page.getValueFromLeafUnique(key);
	}

	BSTreePage createPage(BSTreePage parent, boolean isLeaf) {
		return new BSTreePage(this, (BSTreePage) parent, isLeaf);
	}

	BSTreePage getRoot() {
		return root;
	}

	public BSTreeIterator<T> iterator(long min, long max) {
		return new BSTreeIterator<>(this, min, max);
	}

	void updateRoot(BSTreePage newRoot) {
		root = newRoot;
	}
	
	public void print() {
		root.print("");
	}

	public long getMaxKey() {
		return root.getMax();
	}

	public long getMinKey() {
		return root.getMinKey();
	}

	public long size() {
		throw new UnsupportedOperationException();
	}

	public BSTreeIterator<T> iterator() {
		return iterator(Long.MIN_VALUE, Long.MAX_VALUE);
	}

	
	public void clear() {
		getRoot().clear();
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
	
}

