/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v8;


public interface PhOperations<T> {
	
    public Node<T> createNode(Node<T> original, int dim);

	public Node<T> createNode(PhTree8<T> parent, int infixLen, int postLen, 
			int estimatedPostCount);

    public T put(long[] key, T value);

    public T remove(long... key);

    public T update(long[] oldKey, long[] newKey);
}