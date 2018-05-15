/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16.bst;

import ch.ethz.globis.phtree.v16.Node.BSTEntry;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorMask.IteratorPos;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorMask.IteratorPosStack;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorToArray {


	private BSTEntry[] entries;
	private int nEntries;
	private final IteratorPosStack stack = new IteratorPosStack(20);

	
	public BSTIteratorToArray() {
		//nothing
	}
	
	public BSTIteratorToArray reset(BSTreePage root, BSTEntry[] entries) {
		this.stack.clear();
		this.entries = entries;
		this.nEntries = 0;
		findAll(root);
		return this;
	}


	private BSTreePage goToNextLeafPage() {
		if (stack.isEmpty()) {
			//root->leaf
			return null;
		}
		IteratorPos ip = stack.pop();
		BSTreePage currentPage = ip.page;
		short currentPos = ip.pos;
		currentPos++;
		
		//traverse to root
		while (currentPos > currentPage.getNKeys()) {
			if (stack.isEmpty()) {
				return null;
			}
			ip = stack.pop();
			currentPage = ip.page;
			currentPos = ip.pos;
			currentPos++;
		}

		//traverse to leaf
		while (!currentPage.isLeaf()) {
			//we are not on the first page here, so we can assume that pos=0 is correct to 
			//start with

			//read last page
			stack.prepareAndPush(currentPage, currentPos);
			currentPage = currentPage.getPageByPos(currentPos);
			currentPos = 0;
		}
		return currentPage;
	}
	
	
	private BSTreePage goToFirstPage(BSTreePage currentPage) {
		while (!currentPage.isLeaf()) {
			//the following is only for the initial search.
			//The stored key[i] is the min-key of the according page[i+1}
	    	if (currentPage.getNKeys() == -1) {
				return null;
	    	}
	    	
	    	short currentPos = 0;
	    	BSTreePage newPage = currentPage.getPageByPos(currentPos);
			stack.prepareAndPush(currentPage, currentPos);
			currentPage = newPage;
		}
		return currentPage;
	}
	
	private void readLeafPage(BSTreePage currentPage) {
		BSTEntry[] values = currentPage.getValues();
		System.arraycopy(values, 0, entries, nEntries, currentPage.getNKeys());
		nEntries += currentPage.getNKeys();
	}

	private void findAll(BSTreePage root) {
		//find first page
		BSTreePage page = goToFirstPage(root);
		if (page == null) {
			return;
		}

		//iterate over all pages
		do {
			readLeafPage(page);
			page = goToNextLeafPage();
		} while (page != null);
	}


	public int getNEntries() {
		return nEntries;
	}

}