/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v8;

/**
 * Interface for PhIterator checkers. A checker is continuously checked 
 * during navigation to see whether subnodes or postfixes should be traversed. 
 * 
 * 
 * @author Tilmann Zäschke
 *
 * @param <T> The value type of the tree 
 *
 */
public interface PhTraversalChecker<T> {

	boolean isValid(long[] key);
	
	boolean isValid(Node<T> node, long[] prefix);
	
}
