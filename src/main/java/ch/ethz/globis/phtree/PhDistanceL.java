/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

/**
 * Calculate the euclidean distance for integer values.
 * 
 * @see PhDistance
 * 
 * @author ztilmann
 */
public class PhDistanceL implements PhDistance {

	public static final PhDistanceL THIS = new PhDistanceL();

	/**
	 * Calculate the distance for integer values.
	 * 
	 * @see PhDistance#dist(long[], long[])
	 */
	@Override
	public double dist(long[] v1, long[] v2) {
		double d = 0;
		for (int i = 0; i < v1.length; i++) {
			double dl = (double)v1[i] - (double)v2[i];
			d += dl*dl;
		}
		return Math.sqrt(d);
	}

	@Override
	public void toMBB(double distance, long[] center, long[] outMin,
			long[] outMax) {
		for (int i = 0; i < center.length; i++) {
			outMin[i] = (long) (center[i] - distance);
			outMax[i] = (long) (center[i] + distance);
		}
	}
}