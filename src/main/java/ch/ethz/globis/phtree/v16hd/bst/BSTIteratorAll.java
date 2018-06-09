/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16hd.bst;

import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorAll {


	private BSTreePage currentPage;
	private int currentPos;
	private BSTEntry nextValue;
	
	public BSTIteratorAll() {
		//nothing
	}
	
	public BSTIteratorAll reset(BSTreePage root) {
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
		//first progress to next page, if necessary.
		if (currentPos >= currentPage.getNKeys()) {
			currentPage = currentPage.getNextLeaf();
			if (currentPage == null) {
				return;
			}
			currentPos = 0;
		}

		nextValue = currentPage.getValues()[currentPos];
		currentPos++;
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

}