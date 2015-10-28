/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.pre;

import java.util.Arrays;

import ch.ethz.globis.pht.util.BitTools;

public class ExponentPP implements PreProcessorPointF {
	private double[] disp;
	
	public ExponentPP(double[] displacements) {
		this.disp = Arrays.copyOf(displacements, displacements.length);		
	}

	@Override
	public void pre(double[] raw, long[] pre) {
		for (int d=0; d<raw.length; d++) {
			pre[d] = BitTools.toSortableLong(raw[d] + disp[d]);
		}
	}

	@Override
	public void post(long[] pre, double[] post) {
		for (int d=0; d<pre.length; d++) {
			post[d] = BitTools.toDouble(pre[d]) - disp[d];
		}
	}
	
	public double getDisplacement(int dim) {
		if (dim < 0 || dim >= disp.length)
			throw new IllegalArgumentException("Supplied dim is out of bounds");
		return disp[dim];
	}

}
