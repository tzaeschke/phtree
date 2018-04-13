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
import java.util.NoSuchElementException;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
class LLIterator {

	static class IteratorPos {
		IteratorPos(LLIndexPage page, short pos) {
			this.page = page;
			this.pos = pos;
		}
		LLIndexPage page;
		short pos;
	}

	public static class LLEntry {
		private final long key;
		private final long value;
		public LLEntry(long k, long v) {
			key = k;
			value = v;
		}
		public long getKey() {
			return key;
		}
		public long getValue() {
			return value;
		}
	}

	protected final PagedUniqueLongLong ind;
	private final int modCount;
	private LLIndexPage currentPage = null;
	private short currentPos = 0;
	private final long minKey;
	private final long maxKey;
	private final ArrayList<IteratorPos> stack = new ArrayList<IteratorPos>(20);
	private long nextKey;
	private long nextValue;
	private boolean hasValue = false;
	
	public LLIterator(PagedUniqueLongLong ind, long minKey, long maxKey) {
		this.ind = ind;
		this.modCount = ind.getModCount();
		this.minKey = minKey;
		this.maxKey = maxKey;
		this.currentPage = (LLIndexPage) ind.getRoot();

		findFirstPosInPage();
	}


	public boolean hasNextULL() {
        checkValidity();
		return hasValue;
	}

	
	private void goToNextPage() {
		IteratorPos ip = stack.remove(stack.size()-1);
		currentPage = ip.page;
		currentPos = ip.pos;
		currentPos++;
		
		while (currentPos > currentPage.getNKeys()) {
			if (stack.isEmpty()) {
				close();
				return;// false;
			}
			ip = stack.remove(stack.size()-1);
			currentPage = ip.page;
			currentPos = ip.pos;
			currentPos++;
		}

		while (!currentPage.isLeaf) {
			//we are not on the first page here, so we can assume that pos=0 is correct to 
			//start with

			//read last page
			stack.add(new IteratorPos(currentPage, currentPos));
			currentPage = findPage(currentPage, currentPos);
			currentPos = 0;
		}
	}
	
	
	private boolean goToFirstPage() {
		while (!currentPage.isLeaf) {
			//the following is only for the initial search.
			//The stored key[i] is the min-key of the according page[i+1}
			int pos2 = currentPage.binarySearch(
					currentPos, currentPage.getNKeys(), minKey, Long.MIN_VALUE);
	    	if (currentPage.getNKeys() == -1) {
				return false;
	    	}
			if (pos2 >=0) {
		        pos2++;
		    } else {
		        pos2 = -(pos2+1);
		    }
	    	currentPos = (short)pos2;

	    	LLIndexPage newPage = findPage(currentPage, currentPos);
			//are we on the correct branch?
	    	//We are searching with LONG_MIN value. If the key[] matches exactly, then the
	    	//selected page may not actually contain any valid elements.
	    	//In any case this will be sorted out in findFirstPosInPage()
	    	
			stack.add(new IteratorPos(currentPage, currentPos));
			currentPage = newPage;
			currentPos = 0;
		}
		return true;
	}
	
	private void gotoPosInPage() {
		//when we get here, we are on a valid page with a valid position 
		//(TODO check for pos after goToPage())
		//we only need to check the value.
		
		nextKey = currentPage.getKeys()[currentPos];
		nextValue = currentPage.getValues()[currentPos];
		hasValue = true;
		currentPos++;
		
		//now progress to next element
		
		//first progress to next page, if necessary.
		if (currentPos >= currentPage.getNKeys()) {
			goToNextPage();
			if (currentPage == null) {
				return;
			}
		}
		
		//check for invalid value
		if (currentPage.getKeys()[currentPos] > maxKey) {
			close();
		}
	}

	private void findFirstPosInPage() {
		//find first page
		if (!goToFirstPage()) {
			close();
			return;
		}

		//find very first element. 
		currentPos = (short) currentPage.binarySearch(currentPos, currentPage.getNKeys(), 
				minKey, Long.MIN_VALUE);
		if (currentPos < 0) {
			currentPos = (short) -(currentPos+1);
		}
		
		//check position
		if (currentPos >= currentPage.getNKeys()) {
			//maybe we walked down the wrong branch?
			goToNextPage();
			if (currentPage == null) {
				close();
				return;
			}
			//okay, try again.
			currentPos = (short) currentPage.binarySearch(currentPos, currentPage.getNKeys(), 
					minKey, Long.MIN_VALUE);
			if (currentPos < 0) {
				currentPos = (short) -(currentPos+1);
			}
		}
		if (currentPos >= currentPage.getNKeys() 
				|| currentPage.getKeys()[currentPos] > maxKey) {
			close();
			return;
		}
		gotoPosInPage();
	}
	
	
	/**
	 * Dirty trick to avoid delays from finding the correct method.
	 */
	public LLEntry nextULL() {
		if (!hasNextULL()) {
			throw new NoSuchElementException();
		}

        LLEntry e = new LLEntry(nextKey, nextValue);
		if (currentPage == null) {
			hasValue = false;
		} else {
			gotoPosInPage();
		}
		return e;
	}
	
	public long nextKey() {
		if (!hasNextULL()) {
			throw new NoSuchElementException();
		}
        checkValidity();

        long ret = nextKey;
		if (currentPage == null) {
			hasValue = false;
		} else {
			gotoPosInPage();
		}
		return ret;
	}

	
	private void close() {
		
	}

	
	
	
	protected final LLIndexPage findPage(LLIndexPage currentPage, short pagePos) {
		return currentPage.readCachedPage(pagePos);
	}

	protected void checkValidity() {
		ind.checkValidity(modCount);
	}

}