/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16hd.bst;

import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorMask.IteratorPos;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorMask.IteratorPosStack;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorAll {


	private BSTreePage currentPage = null;
	private short currentPos = 0;
	private final IteratorPosStack stack = new IteratorPosStack(20);
	private long[] nextKey;
	private BSTEntry nextValue;
	private boolean hasValue = false;
    private final LLEntry tempEntry = new LLEntry(null, null);
	
	public BSTIteratorAll() {
		//nothing
	}
	
	public BSTIteratorAll reset(BSTreePage root) {
		this.currentPage = root;
		this.currentPos = -1;
		this.hasValue = false;
		this.stack.clear();
		findFirstPosInPage();
		return this;
	}


	public boolean hasNextULL() {
		return hasValue;
	}

	
	private void goToNextPage() {
		if (stack.isEmpty()) {
			//root->leaf
			currentPage = null;
			return;
		}
		IteratorPos ip = stack.pop();
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
	    	if (currentPage.getNKeys() == -1) {
				return false;
	    	}
	    	
	    	currentPos++;
	    	BSTreePage newPage = currentPage.getPageByPos(currentPos);
			stack.prepareAndPush(currentPage, currentPos);
			currentPage = newPage;
			currentPos = -1;
		}
		return true;
	}
	
	private void gotoPosInPage() {
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
	}

	private void findFirstPosInPage() {
		//find first page
		if (!goToFirstPage()) {
			close();
			return;
		}

		//find very first element. 
		currentPos = 0;
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


	public BSTEntry nextBSTEntryReuse() {
		if (!hasNextULL()) {
			throw new NoSuchElementException();
		}

        BSTEntry ret = nextValue;
		if (currentPage == null) {
			hasValue = false;
		} else {
			gotoPosInPage();
		}
		return ret;
	}


	public long[] nextKey() {
		if (!hasNextULL()) {
			throw new NoSuchElementException();
		}

        long[] ret = nextKey;
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

}