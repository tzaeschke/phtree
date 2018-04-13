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
import java.util.Iterator;
import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.v14.bst.LongLongIndex.LLEntryIterator;

/**
 * Some thoughts on Iterators:
 * 
 * JDO has a usecase like this:
 * Iterator iter = extent.iterator();
 * while (iter.hasNext()) {
 * 	   pm.deletePersistent(iter.next());
 * }
 * 
 * That means:
 * The iterator needs to support deletion without introducing duplicates and without skipping 
 * objects. It needs to be a perfect iterator.
 * 
 * According to the spec 2.2., the extent should contain whatever existed a the time of the 
 * execution of the query or creation of the iterator (JDO 2.2).
 * 
 * So:
 * - Different sessions should use COW to create locally valid 'copies' of the traversed index.
 * - Within the same session, iterators should support deletion as described above.
 * 
 * To support the deletion locally, there are several option:
 * - one could use COW as well, which would mean that bidirectional iterators would not work,
 *   because the iterator iterates over copies of the original list. 
 *   Basically the above example would work, but deletions ahead of the iterator would not
 *   be recognized (desirable?). TODO Check spec.
 * - Alternative: Update iterator with callbacks from index modification.
 *   This would mean ahead-of-iterator modifications would be recognized (desirable?)
 *   
 *    
 *    
 *    
 * Version 2.0:
 * Iterator stores currentElement and immediately moves to next element. For unique indices
 * this has the advantage, that the will never be buffer pages created, because the index
 * is invalidated, as soon as it is created.
 * 
 * @author Tilmann Zaeschke
 *
 */
class LLIterator extends AbstractPageIterator<LongLongIndex.LLEntry> 
implements LLEntryIterator, Iterator<LongLongIndex.LLEntry> {

	static class IteratorPos {
		IteratorPos(LLIndexPage page, short pos) {
			this.page = page;
			this.pos = pos;
		}
		//This is for the iterator, do _not_ use WeakRefs here.
		LLIndexPage page;
		short pos;
	}

	private LLIndexPage currentPage = null;
	private short currentPos = 0;
	private final long minKey;
	private final long maxKey;
	private final ArrayList<IteratorPos> stack = new ArrayList<IteratorPos>(20);
	private long nextKey;
	private long nextValue;
	private boolean hasValue = false;
	
	public LLIterator(AbstractPagedIndex ind, long minKey, long maxKey) {
		super(ind);
		this.minKey = minKey;
		this.maxKey = maxKey;
		this.currentPage = (LLIndexPage) ind.getRoot();

		findFirstPosInPage();
	}

	@Override
	public boolean hasNext() {
		return hasNextULL();
	}
	/**
	 * Dirty trick to avoid delays from finding the correct method.
	 */
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
			currentPage = (LLIndexPage) findPage(currentPage, currentPos);
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

	    	LLIndexPage newPage = (LLIndexPage) findPage(currentPage, currentPos);
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
	
	
	@Override
	public LongLongIndex.LLEntry next() {
		return nextULL();
	}
	
	/**
	 * Dirty trick to avoid delays from finding the correct method.
	 */
	public LongLongIndex.LLEntry nextULL() {
		if (!hasNextULL()) {
			throw new NoSuchElementException();
		}

        LongLongIndex.LLEntry e = new LongLongIndex.LLEntry(nextKey, nextValue);
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


	@Override
	public void remove() {
		//As defined in the JDO 2.2. spec:
		throw new UnsupportedOperationException();
	}
	
	private void close() {
		
	}
	
}