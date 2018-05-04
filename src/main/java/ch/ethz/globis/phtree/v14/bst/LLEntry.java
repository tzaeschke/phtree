/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14.bst;

public class LLEntry {
	
	private long key;
	private Object value;
	
	public LLEntry(long k, Object v) {
		key = k;
		value = v;
	}
	public long getKey() {
		return key;
	}
	public Object getValue() {
		return value;
	}
	public void set(long key, Object value) {
		this.key = key;
		this.value = value;
	}
}