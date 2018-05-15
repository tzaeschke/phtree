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
	 * @param v1 one value
	 * @param v2 other value
	 * @return The distance.
	 */
	double dist(long[] v1, long[] v2);

	
	/**
	 * Returns the distance between v1 and v2.
	 * 
	 * @param v1 one value
	 * @param v2 other value
	 * @param maximum Maximum Value. If distance is larger than the maximum value it returns Double.POSITIVE_INFINITY.
	 * @return The distance or Double.POSITIVE_INFINITY.
	 */
	default double dist(long[] v1, long[] v2, double maxValue) {
		double dist = dist(v1, v2);
		return dist > maxValue ? Double.POSITIVE_INFINITY : dist;
	}

	
	/**
	 * Calculate the minimum bounding box for all points that are less than 
	 * {@code distance} away from {@code center}.
	 * @param distance distance
	 * @param center the center
	 * @param outMin returns the new min values
	 * @param outMax returns the new max values
	 */
	void toMBB(double distance, long[] center, long[] outMin, long[] outMax);


	/**
	 * Calculate array of distances. This is used by k-nearest neighbor search to minimize search effort by filtering
	 * out quadrants that cannot possibly contain results because they are two far away. This approach is not very 
	 * accurate (many quadrants are too far away, but are not recognized as such), but it is very fast. 
	 *  
	 * Idea:<p>
	 * 1) we create a list of axis-aligned distances between the query point and the center point of the node <p>
	 * 2) we sort the distances in ascending order <p>
	 * 3) we walk through the list as if we want to calculate the distance from the query point to the node center
	 *    (it doesn't matter that the distances are sorted).
	 *    If we get to a point where the summed-up distances are larger than the known maxDist, we stop.
	 *    If we stopped at, say 3 out of 5 dimensions, we know that we only need to look at quadrants that
	 *    are at most (3-1)=2 dimensions 'away' from the the quadrant where the query point is located.
	 *    If a quadrant is 3 dimensions away, we would at least need to sum up the three smallest distance. which
	 *    would clearly exceed 'maxDist'. <p>
	 *   
	 * <p>
	 * 
	 * Obviously, due to the sorting, many quadrants may be too far away, even if they only differ in 1 or 2
	 * dimensions. But this approach allows us to simply compare the HC-positions of the quadrants to see in
	 * constant time whether a quadrant is DEFINITELY too far away. Luckily, since diagonal distances grow much
	 * faster with dimensionality than orthogonal distances (they don't grow at all), this approach should
	 * get BETTER with high dimensionality.
	 * <p>
	 * 
	 * How to use: 
	 * Steps 1) and 2) are performed in {@link #knnCalcDistances(long[], long[], int, double[])} once per node.
	 * Then, once 'k' candidates have been found, or whenever 'maxDist' changes (because a better candidate has been 
	 * found), we use step 3) {@link #knnCalcMaximumPermutationCount(double[], double)} to recalculate the maximum 
	 * allowable permutations.
	 * During node traversal, we check the HC-code (z-code) of each quadrant by XORing it with the quadrants where
	 * the kNN query center is located, and then we count the '1' bits. If the number of '1' bits is larger than
	 * the allowed permutation count, then the quadrant is definitely to far away and can be omitted. 
	 * <p>
	 * 
	 * In case implementation of this concept is not possible with a new distance metric, it is safe to use the default
	 * implementation that always returns the dimensionality as maximum permutation count, thus allowing all quadrants.
	 * In effect, this disables this optimization. 
	 * 
	 * @param kNNCenter Query center 
	 * @param prefix  Node center
	 * @param bitsToIgnore Trailing Bits to ignore when using 'prefix' as node center   
	 * @param outDistances Sorted array of distances
	 */
	default void knnCalcDistances(long[] kNNCenter, long[] prefix, int bitsToIgnore, double[] outDistances) {
		//Default implementation: Correct but inefficient
		//-> nothing
	}
	
	
	/**
	 * See {@link #knnCalcDistances(long[], long[], int, double[])}.
	 * 
	 * @param distances Distance array from {@link #knnCalcDistances(long[], long[], int, double[])}
	 * @param maxDist Known max dist.
	 * @return Maximum allowable permutation count.
	 */
	default int knnCalcMaximumPermutationCount(double[] distances, double maxDist) {
		//Default implementation: Correct but inefficient
		return distances.length;
	}
}
