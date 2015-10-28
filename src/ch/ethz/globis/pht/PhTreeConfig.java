/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht;

public class PhTreeConfig {

	/** Concurrency via copy on write. */
	public static final int CONCURRENCY_NONE = 0;
	/** Concurrency via copy on write. */
	public static final int CONCURRENCY_COW = 1;
	/** Concurrency via copy on write and optimistic locking. */
	public static final int CONCURRENCY_OL_COW = 2;
	/** Concurrency via copy on write and hand over hand locking. */
	public static final int CONCURRENCY_HOH_COW = 3;
	
	private int dimUser;
	private int dimActual;
	private boolean[] unique; 
	private int concurrencyType = CONCURRENCY_NONE;
	
	public PhTreeConfig(int dim) {
		this.dimUser = dim;
		this.dimActual = dim;
		this.unique = new boolean[dimUser];
	}
	
	/**
	 * Mark a dimension as unique
	 * @param dim
	 */
	public void setUnique(int dim) {
		unique[dim] = true;
		dimActual++;
	}
	
	public int getDimActual() {
		return dimActual;
	}
	
	/**
	 * 
	 * @return Dimensionality as defined by user.
	 */
	public int getDim() {
		return dimUser;
	}
	
	/**
	 * 
	 * @return Depth in bits.
	 */
	public int getDepth() {
		return 64;
	}

	public int[] getDimsToSplit() {
		int[] ret = new int[dimActual-dimUser];
		int n = 0;
		for (int i = 0; i < unique.length; i++) {
			if (unique[i]) {
				ret[n] = i;
				n++;
			}
		}
		return ret;
	}

	public void setConcurrencyType(int concurrencyType) {
		this.concurrencyType = concurrencyType;
	}

	public int getConcurrencyType() {
		return concurrencyType;
	}
}
