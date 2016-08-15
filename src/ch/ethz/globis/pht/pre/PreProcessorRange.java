/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.pre;

/**
 * Preprocessor for range data (rectangles). 
 * 
 */
public interface PreProcessorRange {
	
	/**
	 * 
	 * @param raw1 raw data (input) of lower left corner
	 * @param raw2 raw data (input) of upper right corner
	 * @param pre pre-processed data (output, must be non-null and same size as input array)
	 */
	public void pre(long[] raw1, long[] raw2, long[] pre);
	
	
	/**
	 * @param pre pre-processed data (input)
	 * @param post1 post-processed data (output, must be non-null and same size as input array)
	 *              of lower left corner
	 * @param post2 post-processed data (output, must be non-null and same size as input array)
	 *              of upper right corner
	 */
	public void post(long[] pre, long[] post1, long[] post2);
	
	
	/**
	 * This preprocessors turns a k-dimensional rectangle into a point with 2k dimensions.
	 */
	public class Simple implements PreProcessorRange {

		public Simple() {
			//
		}
		
		@Override
		public void pre(long[] raw1, long[] raw2, long[] pre) {
			final int pDIM = raw1.length;
			for (int d = 0; d < pDIM; d++) {
				pre[d] = raw1[d];
				pre[d+pDIM] = raw2[d];
			}
		}

		@Override
		public void post(long[] pre, long[] post1, long[] post2) {
			final int pDIM = post1.length;
			for (int d = 0; d < pDIM; d++) {
				post1[d] = pre[d];
				post2[d] = pre[d+pDIM];
			}
		}
	}

}
