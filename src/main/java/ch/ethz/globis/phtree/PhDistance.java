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
	 * Calculate the minimum bounding box for all points that are less than 
	 * {@code distance} away from {@code center}.
	 * @param distance distance
	 * @param center the center
	 * @param outMin returns the new min values
	 * @param outMax returns the new max values
	 */
	void toMBB(double distance, long[] center, long[] outMin, long[] outMax);


	/**
	 * Calculate array of distances. This is used by the new experimental k-nearest neighbor search (HSZ) to minimize 
	 * search effort by filtering out quadrants that cannot possibly contain results because they are two far away. 
	 * This approach is not very accurate (many quadrants are too far away, but are not recognized as such), 
	 * but it is very fast. 
	 *  
	 * Idea:<p>
	 * 1) we create a list of axis-aligned distances between the query point and the center point of the node. <p>
	 * 2) we sort the distances in ascending order and add them up, so that each entry is the sum of the local 
	 *    distance and all smaller distances.<p>
	 * 3) We calculate in how many dimensions a candidate quadrant has a non-0 distance to the center quadrant.
	 *    If have, say 3 out of 5 dimensions, we know that we only need to look at quadrants that
	 *    are at most (3-1)=2 dimensions 'away' from the the quadrant where the query point is located.
	 *    That means we simply get the 2nd distance from the array of sums and know that this is a minimum
	 *    distance of how far the quadrant is away. This can be used to preorder quadrants in a queue.<p>
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
	 * found), XOR the local HC address of the candidate quadrant with the center quadrant and get the
	 * number 'p' of '1' bits. When can then use 'p' to get the p'th distance from the summed distance. This gives
	 * us the distance estimate and can potentially be used to exclude the quadrant or at least preorder them.
	 * 
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
	
}
