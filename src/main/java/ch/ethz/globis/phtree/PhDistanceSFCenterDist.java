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
 * Calculate the euclidean center point distance for encoded {@code double} values.
 * 
 * @see PhDistance
 * 
 * @author ztilmann
 */
public class PhDistanceSFCenterDist implements PhDistanceSF {

	private final PreProcessorRangeF pre;
	private final double[] qMIN;
	private final double[] qMAX;

	/**
	 * @param pre the preprocessor to be used by this distance function.
	 * @param dims number of dimensions
	 */
	public PhDistanceSFCenterDist(PreProcessorRangeF pre, int dims) {
		this.pre = pre;
		qMIN = new double[dims];
		Arrays.fill(qMIN, Double.NEGATIVE_INFINITY);
		qMAX = new double[dims];
		Arrays.fill(qMAX, Double.POSITIVE_INFINITY);
	}

	/**
	 * Calculate the distance for encoded {@code double} values.
	 * This distance function calculates the distance of the center-points of rectangles,
	 * rather than the distance of the closest corners.
	 * 
	 * @see PhDistance#dist(long[], long[])
	 */
	@Override
	public double dist(long[] v1, long[] v2) {
		int dimsHalf = v1.length>>1;
		double[] d1lo = new double[dimsHalf];
		double[] d1up = new double[dimsHalf];
		double[] d2lo = new double[dimsHalf];
		double[] d2up = new double[dimsHalf];
		//center1 = (d1lo + d1up)/2   
		//center2 = (d2lo + d2up)/2
		//dist = center2-center1 = (d1lo + d1up)/2 - (d2lo + d2up)/2
		//     = (d1lo + d1up - d2lo - d2up)/2
		pre.post(v1, d1lo, d1up);
		pre.post(v2, d2lo, d2up);
		double d = 0;
		for (int i = 0; i < dimsHalf; i++) {
			double dOnAxis = d1lo[i] - d2lo[i] + d1up[i] - d2up[i];
			dOnAxis /= 2;
			d += dOnAxis*dOnAxis;
		}
		return Math.sqrt(d);
	}

	/**
	 * Calculates a MBB (minimal bounding box) for use with a query.
	 * This is not strictly a box, but generates a query that returns all rectangles whose
	 * centerpoint may be closer than the given distance.
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
			double c = (cUp[i] + cLo[i])/2; 
			min[i] = c - distance;
			max[i] = c + distance;
		}
		//outMin contains the minimum allowed values for the lower and the upper corner
		//outMax contains the maximum allowed values for the lower and the upper corner
		pre.pre(qMIN, min, outMin);
		pre.pre(max, qMAX, outMax);
	}
}