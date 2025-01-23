/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;


/**
 * Interface for PhIterator filters. A checker is continuously checked 
 * during navigation to see whether subnodes or postfixes should be traversed. 
 * 
 * This interface needs to be serializable because in the distributed version of the PhTree, 
 * it is send from the client machine to the server machine.
 * 
 * @author Tilmann Zäschke
 *
 */
public interface PhFilter {

	/**
	 * 
	 * @param key the key to check
	 * @return True if the key passes the filter.
	 */
	boolean isValid(long[] key);

	/**
	 * 
	 * @param bitsToIgnore trailing bits to ignore
	 * @param prefix the prefix to check
	 * @return False if key with the given prefix cannot pass the filter, otherwise true.
	 */
	boolean isValid(int bitsToIgnore, long[] prefix);

}
