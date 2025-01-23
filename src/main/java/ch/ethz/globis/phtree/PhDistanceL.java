/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.util.Arrays;

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
			double dl = (double)v1[i] - (double)v2[i];
			// long dl = Math.subtractExact(v1[i], v2[i]);
			// d += Math.multiplyExact(dl, dl);
			// double dl = Math.subtractExact(v1[i], v2[i]);
			d += dl*dl;
		}
		return Math.sqrt(d);
	}

	@Override
	public void toMBB(double distance, long[] center, long[] outMin,
			long[] outMax) {
		for (int i = 0; i < center.length; i++) {
			//casting to 'long' always rounds down (floor)
			outMin[i] = (long) (center[i] - distance);
			//casting to 'long' after adding 1.0 always rounds up (ceiling)
			outMax[i] = (long) (center[i] + distance + 1);
		}
	}

	
	@Override
	public void knnCalcDistances(long[] kNNCenter, long[] prefix, int bitsToIgnore, double[] outDistances) {
		long maskSingleBit = 1L << (bitsToIgnore-1);
		if (maskSingleBit < 0) {
			//TODO
			//can't yet deal with negative/positive of postLen==63
			return;
		}
		long maskPrefix = (-1L) << bitsToIgnore;
		long maskPostFix = (~maskPrefix) >> 1;
		for (int i = 0; i < prefix.length; i++) {
			long nodeCenter = prefix[i] & maskPrefix;
			//find coordinate closest to the node's center, however the node-center should between the
			//resulting coordinate and the kNN-center.
			boolean isLarger = kNNCenter[i] > (nodeCenter | maskPostFix);
			nodeCenter |= isLarger ? 
				//kNN center is in 'upper' quadrant
				maskPostFix
				:
				//kNN Center is in 'lower' quadrant, move buf to 'upper' quadrant
				maskSingleBit;

			double dist = nodeCenter - kNNCenter[i];
			outDistances[i] = dist * dist;
		}
		
		Arrays.sort(outDistances);

		//create totals
		for (int i = 1; i < outDistances.length; i++) {
			outDistances[i] += outDistances[i-1];
		}
		for (int i = 0; i < outDistances.length; i++) {
			outDistances[i] = Math.sqrt(outDistances[i]);
		}
	}
}
