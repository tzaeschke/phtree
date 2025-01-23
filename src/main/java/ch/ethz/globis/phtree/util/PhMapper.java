/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;


import ch.ethz.globis.phtree.PhEntry;

/**
 * A mapping function that maps long[] / T to a desired output format.
 *
 * This interface needs to be serializable because in the distributed version of the PhTree, 
 * it is send from the client machine to the server machine.
 *
 * @author ztilmann
 * 
 * @param <T> Value type
 * @param <R> Result type
 */
@FunctionalInterface
public interface PhMapper<T, R> {
	
    public static final long serialVersionUID = 1L;

	static <T> PhMapper<T, PhEntry<T>> PVENTRY() {
		return e -> e;
	}

	static <T, R> PhMapper<T, R> MAP(final PhMapperKey<R> mapper) {
		return e -> mapper.map(e.getKey());
	}

	/**
	 * Maps a PhEntry to something else. 
	 * @param e entry
	 * @return The converted entry
	 */
	R map(PhEntry<T> e);
}