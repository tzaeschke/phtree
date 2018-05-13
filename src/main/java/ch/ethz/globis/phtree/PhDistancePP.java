/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import ch.ethz.globis.phtree.pre.PreProcessorPointF;


/**
 * Calculate the euclidean distance for encoded {@code double} values.
 * 
 * @see PhDistance
 * 
 * @author ztilmann
 */
public class PhDistancePP implements PhDistance {

	/** Euclidean distance with standard `double` encoding. */ 
	public static final PhDistance DOUBLE = 
			new PhDistancePP(new PreProcessorPointF.IEEE());

	private final PreProcessorPointF pre;

	public PhDistancePP(PreProcessorPointF pre) {
		this.pre = pre;
	}

	/**
	 * Calculate the distance for encoded {@code double} values.
	 * 
	 * @see PhDistance#dist(long[], long[])
	 */
	@Override
	public double dist(long[] v1, long[] v2) {
		double d = 0;
		double[] d1 = new double[v1.length];
		double[] d2 = new double[v2.length];
		pre.post(v1, d1);
		pre.post(v2, d2);
		for (int i = 0; i < v1.length; i++) {
			double dl = d1[i] - d2[i];
			d += dl*dl;
		}
		return Math.sqrt(d);
	}

	@Override
	public void toMBB(double distance, long[] center, long[] outMin,
			long[] outMax) {
		double[] c = new double[center.length];
		double[] min = new double[outMin.length];
		double[] max = new double[outMax.length];
		pre.post(center, c);
		for (int i = 0; i < center.length; i++) {
			min[i] = c[i] - distance;
			max[i] = c[i] + distance;
		}
		pre.pre(min, outMin);
		pre.pre(max, outMax);
	}

	@Override
	public double dist(long l, long m) {
		double d1 = pre.post(l);
		double d2 = pre.post(m);
		return d2 - d1;
	}
}