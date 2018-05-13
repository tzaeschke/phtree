/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.pre;

import java.util.Arrays;

import ch.ethz.globis.phtree.util.BitTools;

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
		if (dim < 0 || dim >= disp.length) {
			throw new IllegalArgumentException("Supplied dim is out of bounds");
		}
		return disp[dim];
	}

	@Override
	public long pre(double raw) {
		throw new UnsupportedOperationException();
		//return BitTools.toSortableLong(raw + disp[d]);
	}

	@Override
	public double post(long pre) {
		throw new UnsupportedOperationException();
		//return BitTools.toDouble(pre) - disp[d];
	}

}
