/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import ch.ethz.globis.phtree.util.BitTools;


/**
 * Calculate the euclidean distance for encoded {@code double} values.
 * This is a special distance function for PH-Tree multi-maps which use
 * an additional dimension for unique identifiers.
 * This distance function ignores this additional dimension.
 * 
 * @see PhDistance
 * 
 * @author ztilmann
 */
public class PhDistanceMMF implements PhDistance {

	public static final PhDistanceMMF THIS = new PhDistanceMMF();

	/**
	 * Calculate the euclidean distance for encoded {@code double} values.
	 * 
	 * @see PhDistance#dist(long[], long[])
	 */
	@Override
	public double dist(long[] v1, long[] v2) {
		double d = 0;
		for (int i = 0; i < v1.length - 1; i++) {
			double dl = BitTools.toDouble(v1[i]) - BitTools.toDouble(v2[i]);
			d += dl*dl;
		}
		return Math.sqrt(d);
	}

	@Override
	public void toMBB(double distance, long[] center, long[] outMin, long[] outMax) {
		for (int i = 0; i < center.length - 1; i++) {
			double c = BitTools.toDouble(center[i]);
			outMin[i] = BitTools.toSortableLong(c - distance);
			outMax[i] = BitTools.toSortableLong(c + distance);
		}
		outMin[outMin.length - 1] = Long.MIN_VALUE;
		outMax[outMax.length - 1] = Long.MAX_VALUE;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}