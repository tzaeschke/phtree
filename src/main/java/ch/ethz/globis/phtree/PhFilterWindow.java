/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;


/**
 * Filter for window queries.
 * 
 * @author Tilmann Zaeschke
 *
 */
public class PhFilterWindow implements PhFilter {

	/**  */
	private static final long serialVersionUID = 1L;
	
	private long[] min;
	private long[] max;

	/**
	 * Set the parameters for this filter.
	 * @param min the lower left corner of the window
	 * @param max the upper right corner of the window
	 */
	public void set(long[] min, long[] max) {
		this.min = min;
		this.max = max;
	}

	@Override
	public boolean isValid(long[] key) {
		for (int i = 0; i < key.length; i++) {
			if (key[i] < min[i] || key[i] > max[i]) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean isValid(int bitsToIgnore, long[] key) {
		long compMask = bitsToIgnore == 64 ? 0 : ((-1L) << bitsToIgnore);
		for (int dim = 0; dim < key.length; dim++) {
			long in = key[dim] & compMask;
			if (in > max[dim] || in < (min[dim]&compMask)) {
				return false;
			}
		}
		return true;
	}

}
