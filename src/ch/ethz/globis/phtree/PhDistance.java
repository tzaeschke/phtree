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
	 * @param v1
	 * @param v2
	 * @return The distance.
	 */
	double dist(long[] v1, long[] v2);
	
	/**
	 * Calculate the minimum bounding box for all points that are less than 
	 * {@code distance} away from {@code center}.
	 * @param distance
	 * @param center
	 * @param outMin
	 * @param outMax
	 */
	void toMBB(double distance, long[] center, long[] outMin, long[] outMax);
}