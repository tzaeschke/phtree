/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

/**
 * Distance method for the PhTree, for example used in nearest neighbor queries.
 * 
 * @author ztilmann
 */
public interface PhDistance {
	
	/**
	 * Returns the distance between v1 and v2.
	 * 
	 * @param v1 one value
	 * @param v2 other value
	 * @return The distance.
	 */
	double dist(long[] v1, long[] v2);
	
	/**
	 * Calculate the minimum bounding box for all points that are less than 
	 * {@code distance} away from {@code center}.
	 * @param distance distance
	 * @param center the center
	 * @param outMin returns the new min values
	 * @param outMax returns the new max values
	 */
	void toMBB(double distance, long[] center, long[] outMin, long[] outMax);
}