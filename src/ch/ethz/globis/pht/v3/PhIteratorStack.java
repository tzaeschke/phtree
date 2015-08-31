/*
 * Copyright 2011-2014 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v3;

import ch.ethz.globis.pht.v3.PhTree3.NodeIterator;

public class PhIteratorStack<T> {

	private final NodeIterator<T>[] stack;
	private int size = 0;
	
	@SuppressWarnings("unchecked")
	public PhIteratorStack(int DEPTH) {
		stack = new NodeIterator[DEPTH];
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public void push(NodeIterator<T> p) {
		stack[size++] = p;
	}

	public NodeIterator<T> peek() {
		return stack[size-1];
	}

	public NodeIterator<T> pop() {
		NodeIterator<T> ret = stack[--size];
		stack[size] = null;
		return ret;
	}

}
