/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16.bst;

import ch.ethz.globis.phtree.v16.Node.BSTEntry;

public class LLEntry {
	
	private long key;
	private BSTEntry value;
	
	public LLEntry(long k, BSTEntry v) {
		key = k;
		value = v;
	}
	public long getKey() {
		return key;
	}
	public BSTEntry getValue() {
		return value;
	}
	public void set(long key, BSTEntry value) {
		this.key = key;
		this.value = value;
	}
}