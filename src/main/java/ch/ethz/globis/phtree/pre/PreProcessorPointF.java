/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.pre;

import ch.ethz.globis.phtree.util.BitTools;

/**
 * Interface for preprocessors for point data in floating point format.
 */
public interface PreProcessorPointF {
	
	/**
	 * 
	 * @param raw raw data (input)
	 * @param pre pre-processed data (output, must be non-null and same size as input array)
	 */
	public void pre(double[] raw, long[] pre);
	
	
	/**
	 * @param pre pre-processed data (input)
	 * @param post post-processed data (output, must be non-null and same size as input array)
	 */
	public void post(long[] pre, double[] post);
	
	
	/**
	 * Preprocessor with IEEE conversion. This maintains full precision including infinity.
	 */
	public class IEEE implements PreProcessorPointF {
		@Override
		public void pre(double[] raw, long[] pre) {
			for (int d=0; d<raw.length; d++) {
				pre[d] = BitTools.toSortableLong(raw[d]);
			}
		}

		@Override
		public void post(long[] pre, double[] post) {
			for (int d=0; d<pre.length; d++) {
				post[d] = BitTools.toDouble(pre[d]);
			}
		}
	}

	
	/**
	 * Preprocessing by multiplication with constant.
	 */
	public class Multiply implements PreProcessorPointF {

		private final double preMult;
		private final double postMult;
		
		public Multiply(double multiplyer) {
			preMult = multiplyer;
			postMult = 1./multiplyer;
		}
		
		@Override
		public void pre(double[] raw, long[] pre) {
			for (int d=0; d<raw.length; d++) {
				pre[d] = (long) (raw[d] * preMult);
			}
		}

		@Override
		public void post(long[] pre, double[] post) {
			for (int d=0; d<pre.length; d++) {
				post[d] = pre[d] * postMult;
			}
		}
	}

}
