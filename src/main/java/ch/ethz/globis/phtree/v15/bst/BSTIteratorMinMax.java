/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v15.bst;

import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.v15.bst.BSTIteratorMask.IteratorPos;
import ch.ethz.globis.phtree.v15.bst.BSTIteratorMask.IteratorPosStack;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorMinMax<T> {


	private BSTree<T> ind;
	private int modCount;
	private BSTreePage<T> currentPage = null;
	private short currentPos = 0;
	private long minKey;
	private long maxKey;
	private final IteratorPosStack<T> stack = new IteratorPosStack<>(20);
	private long nextKey;
	private Object nextValue;
	private boolean hasValue = false;
    private final LLEntry tempEntry = new LLEntry(-1, null);
	
	public BSTIteratorMinMax() {
		//nothing
	}
	
	public BSTIteratorMinMax<T> reset(BSTree<T> ind, long minKey, long maxKey) {
		this.ind = ind;
		this.modCount = ind.getModCount();
		this.minKey = minKey;
		this.maxKey = maxKey;
		this.currentPage = ind.getRoot();
		this.currentPos = 0;
		this.hasValue = false;
		this.stack.clear();
		findFirstPosInPage();
		return this;
	}


	public boolean hasNextULL() {
        checkValidity();
		return hasValue;
	}

	
	private void goToNextPage() {
		if (stack.isEmpty()) {
			//root->leaf
			currentPage = null;
			return;
		}
		IteratorPos<T> ip = stack.pop();
		currentPage = ip.page;
		currentPos = ip.pos;
		currentPos++;
		
		while (currentPos > currentPage.getNKeys()) {
			if (stack.isEmpty()) {
				close();
				return;// false;
			}
			ip = stack.pop();
			currentPage = ip.page;
			currentPos = ip.pos;
			currentPos++;
		}

		while (!currentPage.isLeaf()) {
			//we are not on the first page here, so we can assume that pos=0 is correct to 
			//start with

			//read last page
			stack.prepareAndPush(currentPage, currentPos);
			currentPage = currentPage.getPageByPos(currentPos);
			currentPos = 0;
		}
	}
	
	
	private boolean goToFirstPage() {
		while (!currentPage.isLeaf()) {
			//the following is only for the initial search.
			//The stored key[i] is the min-key of the according page[i+1}
			int pos2 = currentPage.binarySearch(currentPos, currentPage.getNKeys(), minKey);
	    	if (currentPage.getNKeys() == -1) {
				return false;
	    	}
			if (pos2 >=0) {
		        pos2++;
		    } else {
		        pos2 = -(pos2+1);
		    }
	    	currentPos = (short)pos2;

	    	BSTreePage<T> newPage = currentPage.getPageByPos(currentPos);
			//are we on the correct branch?
	    	//We are searching with LONG_MIN value. If the key[] matches exactly, then the
	    	//selected page may not actually contain any valid elements.
	    	//In any case this will be sorted out in findFirstPosInPage()
	    	
			stack.prepareAndPush(currentPage, currentPos);
			currentPage = newPage;
			currentPos = 0;
		}
		return true;
	}
	
	private void gotoPosInPage() {
		//when we get here, we are on a valid page with a valid position 
		//(TODO check for pos after goToPage())
		//we only need to check the value.
		
		if (currentPage == null || currentPage.getValues() == null || currentPage.getValues()[currentPos] == null) {
			throw new IllegalStateException();
		}
		
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
		currentPos = (short) currentPage.binarySearch(currentPos, currentPage.getNKeys(), minKey);
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
			currentPos = (short) currentPage.binarySearch(currentPos, currentPage.getNKeys(), minKey);
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
	
	
	public LLEntry nextEntryReuse() {
		if (!hasNextULL()) {
			throw new NoSuchElementException();
		}

        tempEntry.set(nextKey, nextValue);
		if (currentPage == null) {
			hasValue = false;
		} else {
			gotoPosInPage();
		}
		return tempEntry;
	}


	public LLEntry nextEntry() {
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

        long ret = nextKey;
		if (currentPage == null) {
			hasValue = false;
		} else {
			gotoPosInPage();
		}
		return ret;
	}

	
	private void close() {
		currentPage = null;
	}

	
	private void checkValidity() {
		ind.checkValidity(modCount);
	}

}