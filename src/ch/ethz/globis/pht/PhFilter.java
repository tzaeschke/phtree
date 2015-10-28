/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht;

/**
 * Interface for PhIterator filters. A checker is continuously checked 
 * during navigation to see whether subnodes or postfixes should be traversed. 
 * 
 * 
 * @author Tilmann Zäschke
 *
 */
public interface PhFilter {

	/**
	 * 
	 * @param key
	 * @return True if the key passes the filter.
	 */
	boolean isValid(long[] key);

	/**
	 * 
	 * @param bitsToIgnore trailing bits to ignore
	 * @param prefix
	 * @return False if key with the given prefix cannot pass the filter, otherwise true.
	 */
	boolean isValid(int bitsToIgnore, long[] prefix);

}
