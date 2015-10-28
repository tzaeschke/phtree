/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.pre;

/**
 * Preprocessor for integer data.
 * 
 * @author ztilmann
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
}
