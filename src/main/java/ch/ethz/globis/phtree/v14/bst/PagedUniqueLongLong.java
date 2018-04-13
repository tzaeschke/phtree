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

import ch.ethz.globis.phtree.v14.bst.LLIterator.LLEntry;


/**
 * @author Tilmann Zaeschke
 */
public class PagedUniqueLongLong {
	
	protected final int maxLeafN = 10;
	/** Max number of keys in inner page (there can be max+1 page-refs) */
	protected final int maxInnerN = 10;
	protected final int minLeafN = maxLeafN >> 1;
	protected final int minInnerN = maxInnerN >> 1;
	protected static int statNLeaves = 0;
	protected static int statNInner = 0;
	private int modCount = 0;

	private transient LLIndexPage root;
	
	public PagedUniqueLongLong() {
		//bootstrap index
		root = createPage(null, false);
	}

	public final void insertLong(long key, long value) {
		LLIndexPage page = getRoot().locatePageForKeyUnique(key, true);
		page.put(key, value);
	}

	public final boolean insertLongIfNotSet(long key, long value) {
		LLIndexPage page = getRoot().locatePageForKeyUnique(key, true);
		if (page.binarySearch(0, page.getNKeys(), key, value) >= 0) {
			return false;
		}
		page.put(key, value);
		return true;
	}

	/**
	 * @param key The key to remove
	 * @return the previous value
	 * @throws NoSuchElementException if key is not found
	 */
	public long removeLong(long key) {
		LLIndexPage page = getRoot().locatePageForKeyUnique(key, false);
		if (page == null) {
			throw new NoSuchElementException("Key not found: " + key);
		}
		return page.remove(key);
	}

	/**
	 * @param key The key to remove
	 * @param failValue The value to return in case the key has no entry.
	 * @return the previous value
	 */
	public long removeLongNoFail(long key, long failValue) {
		LLIndexPage page = getRoot().locatePageForKeyUnique(key, false);
		if (page == null) {
			return failValue;
		}
		return page.remove(key);
	}

	
	public LLEntry findValue(long key) {
		LLIndexPage page = getRoot().locatePageForKeyUnique(key, false);
		if (page == null) {
			return null;
		}
		return page.getValueFromLeafUnique(key);
	}

	LLIndexPage createPage(LLIndexPage parent, boolean isLeaf) {
		return new LLIndexPage(this, (LLIndexPage) parent, isLeaf);
	}

	protected final LLIndexPage getRoot() {
		return root;
	}

	public LLIterator iterator(long min, long max) {
		return new LLIterator(this, min, max);
	}

	protected void updateRoot(LLIndexPage newRoot) {
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

	public LLIterator iterator() {
		return iterator(Long.MIN_VALUE, Long.MAX_VALUE);
	}
	
	final void notifyPageUpdate() {
		modCount++;
	}
	
	public void clear() {
		getRoot().clear();
		BTPool.reportFreePage(getRoot());
		PagedUniqueLongLong.statNInner = 0;
		PagedUniqueLongLong.statNLeaves = 0;
	}
	
	public void checkValidity(int modCount) {
		if (this.modCount != modCount) {
			throw new ConcurrentModificationException();
		}
	}

	protected int getModCount() {
		return modCount;
	}

	public boolean isUnique() {
		return true;
	}
}
