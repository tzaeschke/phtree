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
 * 
 * @see PhDistance
 * 
 * @author ztilmann
 */
public class PhDistanceF implements PhDistance {

	public static final PhDistanceF THIS = new PhDistanceF();

	/**
	 * Calculate the euclidean distance for encoded {@code double} values.
	 * 
	 * @see PhDistance#dist(long[], long[])
	 */
	@Override
	public double dist(long[] v1, long[] v2) {
		double d = 0;
		for (int i = 0; i < v1.length; i++) {
			double dl = BitTools.toDouble(v1[i]) - BitTools.toDouble(v2[i]);
			d += dl*dl;
		}
		return Math.sqrt(d);
	}

	@Override
	public double dist(long[] v1, long[] v2, double maxValue) {
		double max2 = maxValue * maxValue;
		double d = 0;
		for (int i = 0; i < v1.length; i++) {
			double dl = BitTools.toDouble(v1[i]) - BitTools.toDouble(v2[i]);
			d += dl*dl;
			if (d > max2) {
				return Double.POSITIVE_INFINITY;
			}
		}
		return Math.sqrt(d);
	}

	@Override
	public void toMBB(double distance, long[] center, long[] outMin,
			long[] outMax) {
		for (int i = 0; i < center.length; i++) {
			double c = BitTools.toDouble(center[i]);
			outMin[i] = BitTools.toSortableLong(c - distance);
			outMax[i] = BitTools.toSortableLong(c + distance);
		}
	}

	@Override
	public double dist(long v1, long v2) {
		return BitTools.toDouble(v2) - BitTools.toDouble(v1);
	}
}