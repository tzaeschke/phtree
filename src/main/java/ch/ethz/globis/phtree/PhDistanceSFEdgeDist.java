/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.util.Arrays;

import ch.ethz.globis.phtree.pre.PreProcessorRangeF;


/**
 * Calculate the euclidean edge distance for encoded {@code double} values.
 * 
 * @see PhDistance
 * 
 * @author ztilmann
 */
public class PhDistanceSFEdgeDist implements PhDistanceSF {

	private final PreProcessorRangeF pre;
	private final double[] qMIN;
	private final double[] qMAX;

	/**
	 * @param pre the preprocessor to be used by this distance function.
	 * @param dims number of dimensions
	 */
	public PhDistanceSFEdgeDist(PreProcessorRangeF pre, int dims) {
		this.pre = pre;
		qMIN = new double[dims];
		Arrays.fill(qMIN, Double.NEGATIVE_INFINITY);
		qMAX = new double[dims];
		Arrays.fill(qMAX, Double.POSITIVE_INFINITY);
	}

	/**
	 * Calculate the distance for encoded {@code double} values.
	 * This distance function calculates the minimum distance between the rectangles.
	 * If the the rectangles overlap, the distance is set to '0'.
	 * 
	 * @see PhDistance#dist(long[], long[])
	 */
	@Override
	public double dist(long[] v1, long[] v2) {
		double d = 0;
		double[] d1lo = new double[v1.length>>1];
		double[] d1up = new double[v1.length>>1];
		double[] d2lo = new double[v2.length>>1];
		double[] d2up = new double[v2.length>>1];
		pre.post(v1, d1lo, d1up);
		pre.post(v2, d2lo, d2up);
		for (int i = 0; i < d1lo.length; i++) {
			double dOnAxis = 0;
			if (d1up[i] < d2lo[i]) {
				dOnAxis = d2lo[i] - d1up[i];
			} else if (d1lo[i] > d2up[i]) {
				dOnAxis = d1lo[i] - d2up[i]; 
			}
			d += dOnAxis*dOnAxis;
		}
		return Math.sqrt(d);
	}

	/**
	 * Calculates a MBB (minimal bounding box) for use with a query.
	 * This is not strictly a box, but generates a query that returns all rectangles whose
	 * edges may be closer than the given distance.
	 */
	@Override
	public void toMBB(double distance, long[] center, long[] outMin, long[] outMax) {
		int dimsHalf = center.length>>1;
		double[] cLo = new double[dimsHalf];
		double[] cUp = new double[dimsHalf];
		double[] min = new double[dimsHalf];
		double[] max = new double[dimsHalf];
		//The simplest way to get all rectangles whose centerpoint may be within the distance,
		//is to perform an 'intersect' type query around center+/-distance.
		pre.post(center, cLo, cUp);
		for (int i = 0; i < dimsHalf; i++) {
			min[i] = cLo[i] - distance;
			max[i] = cUp[i] + distance;
		}
		//outMin contains the minimum allowed values for the lower and the upper corner
		//outMax contains the maximum allowed values for the lower and the upper corner
		pre.pre(qMIN, min, outMin);
		pre.pre(max, qMAX, outMax);
	}
}
