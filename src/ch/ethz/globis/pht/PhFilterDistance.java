/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht;


/**
 * Check distance to a point using a distance function.
 * 
 * @author Tilmann Zaeschke
 *
 */
public class PhFilterDistance implements PhFilter {

	private static final long serialVersionUID = 1L;
	
	private long[] v;
	private PhDistance dist;
	private double maxDist;

	public void set(long[] v, PhDistance dist, double maxDist) {
		this.v = v;
		this.dist = dist;
		this.maxDist = maxDist;
	}

	@Override
	public boolean isValid(long[] key) {
		return dist.dist(v, key) <= maxDist;
	}

	@Override
	public boolean isValid(int bitsToIgnore, long[] prefix) {
		long maskMin = (-1L) << bitsToIgnore;
		long maskMax = ~maskMin;
		long[] buf = new long[prefix.length];
		for (int i = 0; i < buf.length; i++) {
			//if v is outside the node, return distance to closest edge,
			//otherwise return v itself (assume possible distance=0)
			long min = prefix[i] & maskMin;
			long max = prefix[i] | maskMax;
			buf[i] = min > v[i] ? min : (max < v[i] ? max : v[i]); 
		}
		return dist.dist(v, buf) <= maxDist;
	}

}
