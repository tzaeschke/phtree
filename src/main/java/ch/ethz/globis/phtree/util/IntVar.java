/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;

/**
 * A variable integer.
 * 
 * @author ztilmann
 *
 */
public class IntVar {
	private int i;
	
	public IntVar(int i) {
		this.i = i;
	}
	
	public int inc() {
		return ++i;
	}
	
	public int dec() {
		return --i;
	}

	public int get() {
		return i;
	}
}
