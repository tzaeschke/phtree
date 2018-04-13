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

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;

import ch.ethz.globis.phtree.v14.bst.LongLongIndex.LLEntryIterator;


/**
 * @author Tilmann Zaeschke
 */
public abstract class AbstractPagedIndex {

	protected transient final int maxLeafN = 10;
	/** Max number of keys in inner page (there can be max+1 page-refs) */
	protected transient final int maxInnerN = 10;
	//TODO if we ensure that maxXXX is a multiple of 2, then we could scrap the minXXX values
	// minLeafN = maxLeafN >> 1 
	protected transient final int minLeafN;
	// minInnerN = maxInnerN >> 1 
	protected transient final int minInnerN;
	protected int statNLeaves = 0;
	protected int statNInner = 0;
	
	protected final int keySize;
	protected final int valSize;
	
	private int modCount = 0;
	

	/**
	 * In case this is an existing index, read() should be called afterwards.
	 * Key and value length are used to calculate the man number of entries on a page.
	 * 
	 * @param keyLen The number of bytes required for the key.
	 * @param valLen The number of bytes required for the value.
	 */
	public AbstractPagedIndex(int keyLen, int valLen) {
		keySize = keyLen;
		valSize = valLen;
		
		minLeafN = maxLeafN >> 1;
		minInnerN = maxInnerN >> 1;
	}

	abstract AbstractIndexPage createPage(AbstractIndexPage parent, boolean isLeaf);

	protected abstract AbstractIndexPage getRoot();
	
	protected abstract void updateRoot(AbstractIndexPage newRoot);

	public int statsGetInnerN() {
		return statNInner;
	}

	public int statsGetLeavesN() {
		return statNLeaves;
	}
	
	final void notifyPageUpdate() {
		modCount++;
	}
	
	public List<Integer> debugPageIds() {
	    ArrayList<Integer> pages = new ArrayList<Integer>();
	    AbstractIndexPage root = getRoot();
	    
	    pages.add(root.pageId());
	    debugGetSubPageIDs(root, pages);
	    
	    return pages;
	}
	
	private void debugGetSubPageIDs(AbstractIndexPage page, ArrayList<Integer> pages) {
	    if (page.isLeaf) {
	        return;
	    }
        for (int i = 0; i <= page.getNKeys(); i++) {
            pages.add(page.subPageIds[i]);
            debugGetSubPageIDs(page.readPage(i), pages);
        }
	}

	public void clear() {
		getRoot().clear();
		BTPool.reportFreePage(getRoot());
		this.statNInner = 0;
		this.statNLeaves = 0;
	}
	
	public int getMaxLeafN() {
		return maxLeafN;
	}

	public int getMaxInnerN() {
		return maxInnerN;
	}

	public void checkValidity(int modCount) {
		if (this.modCount != modCount) {
			throw new ConcurrentModificationException();
		}
	}

	protected int getModCount() {
		return modCount;
	}

	//TODO move this to LongLongIndex?
	//TODO remove?
	abstract LLEntryIterator iterator(long min, long max);

	public boolean isUnique() {
		return true;
	}
}
