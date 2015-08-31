/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.util;


import java.io.Serializable;

import ch.ethz.globis.pht.PhEntry;

/**
 * A mapping function that maps long[] / T to a desired output format.
 *
 * This interface needs to be serializable because in the distributed version of the PhTree, it is send
 * from the client machine to the server machine.
 *
 * @author ztilmann
 */
@FunctionalInterface
public interface PhMapper<T, R> extends Serializable {

	static <T> PhMapper<T, PhEntry<T>> PVENTRY() {
		return e -> e;
	}

	static <T, R> PhMapper<T, R> MAP(final PhMapperKey<R> mapper) {
		return ((e) -> mapper.map(e.getKey()));
	}

	R map(PhEntry<T> e);
}