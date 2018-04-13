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

import java.util.NoSuchElementException;


/**
 * @author Tilmann Zaeschke
 */
public class PagedUniqueLongLong extends AbstractPagedIndex implements LongLongIndex.LongLongUIndex {
	
	private transient LLIndexPage root;
	
	public PagedUniqueLongLong(int keySize, int valSize) {
		super(keySize, valSize);
		//bootstrap index
		root = createPage(null, false);
	}

	@Override
	public final void insertLong(long key, long value) {
		LLIndexPage page = getRoot().locatePageForKeyUnique(key, true);
		page.put(key, value);
	}

	@Override
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
	@Override
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
	@Override
	public long removeLongNoFail(long key, long failValue) {
		LLIndexPage page = getRoot().locatePageForKeyUnique(key, false);
		if (page == null) {
			return failValue;
		}
		return page.remove(key);
	}

	@Override
	public long removeLong(long key, long value) {
		return removeLong(key);
	}

	@Override
	public LongLongIndex.LLEntry findValue(long key) {
		LLIndexPage page = getRoot().locatePageForKeyUnique(key, false);
		if (page == null) {
			return null;
		}
		return page.getValueFromLeafUnique(key);
	}

	@Override
	LLIndexPage createPage(AbstractIndexPage parent, boolean isLeaf) {
		return new LLIndexPage(this, (LLIndexPage) parent, isLeaf);
	}

	@Override
	protected final LLIndexPage getRoot() {
		return root;
	}

	@Override
	public LLEntryIterator iterator(long min, long max) {
		return new LLIterator(this, min, max);
	}

	@Override
	protected void updateRoot(AbstractIndexPage newRoot) {
		root = (LLIndexPage) newRoot;
	}
	
	@Override
	public void print() {
		root.print("");
	}

	@Override
	public long getMaxKey() {
		return root.getMax();
	}

	@Override
	public long getMinKey() {
		return root.getMinKey();
	}

	@Override
	public long size() {
		throw new UnsupportedOperationException();
	}

	@Override
	public LLEntryIterator iterator() {
		return iterator(Long.MIN_VALUE, Long.MAX_VALUE);
	}

	/**
	 * This is used in zoodb-server-btree tests.
	 * @return maxLeafN
	 */
	@Override
	public int getMaxLeafN() {
		return maxLeafN;
	}

	/**
	 * This is used in zoodb-server-btree tests.
	 * @return maxInnerN
	 */
	@Override
	public int getMaxInnerN() {
		return maxInnerN;
	}

}
