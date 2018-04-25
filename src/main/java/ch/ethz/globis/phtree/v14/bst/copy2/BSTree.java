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
package ch.ethz.globis.phtree.v14.bst.copy2;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.v14.bst.copy2.BSTreeIterator.LLEntry;


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
		int[] parentPosStack = new int[(nEntries >> (maxInnerN-2)) + 50];
		BSTreePage page = getRoot().locatePageForKey(key, true);
		page.put(key, value, parentPosStack);
		//Depth as log(nEntries) 
//		BSTreePage page = getRoot();
//		while (page != null && !page.isLeaf()) {
//			page = page.findOrCreateSubPage(key, parentPosStack);
//		}
//		page.put(key, value, parentPosStack);
	}

	/**
	 * @param key The key to remove
	 * @return the previous value
	 * @throws NoSuchElementException if key is not found
	 */
	public long remove(long key) {
		BSTreePage page = getRoot().locatePageForKey(key, false);
		if (page == null) {
			throw new NoSuchElementException("Key not found: " + key);
		}
		return page.remove(key);
	}

	public LLEntry get(long key) {
		BSTreePage page = getRoot();
		while (page != null && !page.isLeaf()) {
			page = page.findSubPage(key);
		}
		if (page == null) {
			return null;
		}
		return page.getValueFromLeafUnique(key);
	}

	BSTreePage createPage(BSTreePage parent, boolean isLeaf) {
		return new BSTreePage(this, (BSTreePage) parent, isLeaf);
	}

	protected final BSTreePage getRoot() {
		return root;
	}

	public BSTreeIterator<T> iterator(long min, long max) {
		return new BSTreeIterator<>(this, min, max);
	}

	protected void updateRoot(BSTreePage newRoot) {
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
	
	final void notifyPageUpdate() {
		modCount++;
	}
	
	public void clear() {
		getRoot().clear();
		BTPool.reportFreePage(getRoot());
		BSTree.statNInner = 0;
		BSTree.statNLeaves = 0;
	}
	
	public void checkValidity(int modCount) {
		if (this.modCount != modCount) {
			throw new ConcurrentModificationException();
		}
	}

	protected int getModCount() {
		return modCount;
	}
}
