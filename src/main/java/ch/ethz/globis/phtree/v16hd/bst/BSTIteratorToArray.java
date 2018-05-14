/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16hd.bst;

import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorMask.IteratorPos;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorMask.IteratorPosStack;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorToArray {


	private LLEntry[] entries;
	private int nEntries;
	private final IteratorPosStack stack = new IteratorPosStack(20);

	
	public BSTIteratorToArray() {
		//nothing
	}
	
	public BSTIteratorToArray reset(BSTreePage root, LLEntry[] entries) {
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
		long[][] keys = currentPage.getKeys();
		for (int i = 0; i < currentPage.getNKeys(); i++) {
			BSTEntry e = values[i];
			long[] key = keys[i];
			LLEntry buf = entries[nEntries]; 
			if (buf == null) {
				//TODO use pool
				buf = new LLEntry(key, e);
				entries[nEntries] = buf; 
			} else {
				buf.set(key, e);
			}
			nEntries++;
		}
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