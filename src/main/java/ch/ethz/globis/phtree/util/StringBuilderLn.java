/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;

public class StringBuilderLn {

	private static final String NL = System.lineSeparator();
	
	private final StringBuilder sb;
	
	public StringBuilderLn() {
		sb = new StringBuilder();
	}
	
	public void append(String str) {
		sb.append(str);
	}

	public void appendLn(String str) {
		sb.append(str).append(NL);
	}

	public void appendLn() {
		sb.append(NL);
	}

	@Override
	public String toString() {
		return sb.toString();
	}
	
}
