/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.util.Arrays;

import ch.ethz.globis.phtree.util.BitTools;


/**
 * Calculate the L1 (Manhatten/Taxi) distance for encoded {@code double} values.
 * 
 * @see PhDistance
 * 
 * @author ztilmann
 */
public class PhDistanceF_L1 implements PhDistance {

	public static final PhDistanceF_L1 THIS = new PhDistanceF_L1();

	/**
	 * Calculate the L1 distance for encoded {@code double} values.
	 * 
	 * @see PhDistance#dist(long[], long[])
	 */
	@Override
	public double dist(long[] v1, long[] v2) {
		double d = 0;
		for (int i = 0; i < v1.length; i++) {
			double dl = BitTools.toDouble(v1[i]) - BitTools.toDouble(v2[i]);
			d += dl;
		}
		return d;
	}

	@Override
	public void toMBB(double distance, long[] center, long[] outMin, long[] outMax) {
		for (int i = 0; i < center.length; i++) {
			double c = BitTools.toDouble(center[i]);
			outMin[i] = BitTools.toSortableLong(c - distance);
			outMax[i] = BitTools.toSortableLong(c + distance);
		}
	}

	
	@Override
	@Deprecated
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

			//TODO use unconverted input for nodeCenter???
			double dist = BitTools.toDouble(nodeCenter) - BitTools.toDouble(kNNCenter[i]);
			outDistances[i] = dist;
		}
		
		Arrays.sort(outDistances);

		//create totals
		for (int i = 1; i < outDistances.length; i++) {
			outDistances[i] += outDistances[i-1];
		}
	}

}