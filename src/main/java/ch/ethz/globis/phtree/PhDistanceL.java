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
		//How do we best handle this?
		//Substraction can easily overflow, especially with floating point values that have been 
		//converted to 'long'.
		//1) cast to (double). This will lose some precision for large values, but gives a good
		//   'estimate' and is otherwise quite fault tolerant
		//2) Use Math.addExact(). This will fall early, and will often not work for converted
		//   'double' values. However, we can thus enforce using PhDistanceF instead. This
		//   would be absolutely precise and unlikely to overflow.
		//The dl*dl can be done as 'double', which is always safe.
		for (int i = 0; i < v1.length; i++) {
			//double dl = (double)v1[i] - (double)v2[i];
			long dl = Math.subtractExact(v1[i], v2[i]);
			d += Math.multiplyExact(dl, dl);
//			double dl = Math.subtractExact(v1[i], v2[i]);
//			d += dl*dl;
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