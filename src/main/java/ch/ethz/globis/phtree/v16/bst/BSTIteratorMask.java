/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16.bst;

import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.v16.Node.BSTEntry;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorMask {

	private BSTreePage currentPage = null;
	private int currentPos = 0;
	private long minMask;
	private long maxMask;
	private BSTEntry nextValue;
 	
	public BSTIteratorMask() {
		//nothing
	}

	public BSTIteratorMask reset(BSTreePage root, long minMask, long maxMask) {
		this.minMask = minMask;
		this.maxMask = maxMask;
		this.currentPage = root;
		this.currentPos = 0;

		if (findFirstLeafPage()) {
			findNext();
		}

		return this;
	}


	private boolean findFirstLeafPage() {
		while (!currentPage.isLeaf()) {
			//the following is only for the initial search.
			//The stored key[i] is the min-key of the according page[i+1}
	    	if (currentPage.getNKeys() == -1) {
	    		currentPage = null;
				return false;
	    	}
	    	
	    	currentPage = currentPage.getPageByPos(0);
		}
		return true;
	}
	
	private void findNext() {
		while (currentPage != null ) {
			//first progress to next page, if necessary.
			if (currentPos >= currentPage.getNKeys()) {
				currentPage = currentPage.getNextLeaf();
				currentPos = 0;
				continue;
			}

			if (check(currentPage.getKeys()[currentPos])) { 
				nextValue = currentPage.getValues()[currentPos];
				currentPos++;
				return;
			}
			currentPos++;
		}
	}
	

	public boolean hasNextEntry() {
		return currentPage != null;
	}
	
	public BSTEntry nextEntry() {
		if (!hasNextEntry()) {
			throw new NoSuchElementException();
		}

        BSTEntry ret = nextValue;
		findNext();
		return ret;
	}

	public void adjustMinMax(long maskLower, long maskUpper) {
		this.minMask = maskLower;
		this.maxMask = maskUpper;
	}

	
	private boolean check(long key) {
		return ((key | minMask) & maxMask) == key;
	}
}