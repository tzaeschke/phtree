/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16s.bst;

import ch.ethz.globis.phtree.v16s.Node.BSTEntry;

/**
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BSTIteratorToArray {

	private BSTEntry[] entries;
	private int nEntries;

	
	public BSTIteratorToArray() {
		//nothing
	}
	
	public BSTIteratorToArray reset(BSTreePage root, BSTEntry[] entries) {
		this.entries = entries;
		this.nEntries = 0;

		//find first page
		BSTreePage page = findFirstLeafPage(root);
		readLeafPages(page);

		return this;
	}


	private BSTreePage findFirstLeafPage(BSTreePage currentPage) {
		while (!currentPage.isLeaf()) {
			//the following is only for the initial search.
			//The stored key[i] is the min-key of the according page[i+1}
	    	if (currentPage.getNKeys() == -1) {
				return null;
	    	}
	    	
	    	currentPage = currentPage.getPageByPos(0);
		}
		return currentPage;
	}
	
	
	private void readLeafPages(BSTreePage currentPage) {
		while (currentPage != null) {
			BSTEntry[] values = currentPage.getValues();
			System.arraycopy(values, 0, entries, nEntries, currentPage.getNKeys());
			nEntries += currentPage.getNKeys();
			currentPage = currentPage.getNextLeaf();
		}
	}


	public int getNEntries() {
		return nEntries;
	}
}
