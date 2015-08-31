/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.util;


import java.io.Serializable;

/**
 * A mapping function that maps long[] / T to a desired output format.
 *
 * This interface needs to be serializable because in the distributed version of the PhTree, it is send
 * from the client machine to the server machine.
 *
 * @author ztilmann
 */
@FunctionalInterface
public interface PhMapperKey<R> extends Serializable{

	static PhMapperKey<long[]> LONG_ARRAY() { 
		return ((point) -> (point)); 
	}
	
	static PhMapperKey<double[]> DOUBLE_ARRAY() { 
		return ((point) -> (toDouble(point))); 
	}

	R map(long[] point);
	
	static double[] toDouble(long[] point) {
		double[] d = new double[point.length];
		for (int i = 0; i < d.length; i++) {
			d[i] = BitTools.toDouble(point[i]);
		}
		return d;
	}
}