/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v5;

import java.util.Iterator;
import java.util.NoSuchElementException;

import ch.ethz.globis.pht.v5.PhTree5.Node;
import ch.ethz.globis.pht.v5.PhTree5.NodeIterator;

class PhIteratorSingle<T> implements Iterator<long[]> {

	private final int DIM;
	private final PhIteratorStack<T> stack;
	private final long[] valTemplate;
	private long[] next = null;
	private final long[] rangeMin;
	private final long[] rangeMax;
	
	public PhIteratorSingle(Node<T> root, int attrID, long min, long max, int DIM, int DEPTH) {
		this.DIM = DIM;
		this.stack = new PhIteratorStack<T>(DEPTH);
		this.valTemplate = new long[DIM];
		this.rangeMin = new long[DIM];
		this.rangeMax = new long[DIM];
		if (root == null) {
			//empty index
			return;
		}
		for (int i = 0; i < DIM; i++) {
			rangeMin[i] = Long.MIN_VALUE;
			rangeMax[i] = Long.MAX_VALUE;
		}
		rangeMin[attrID] = min;
		rangeMax[attrID] = max;
		NodeIterator<T> p2 = NodeIterator.create(root, valTemplate, rangeMin, rangeMax, DIM, true);
		if (p2 != null) {
			stack.push(p2);
			findNextElement();
		}
	}

	private void findNextElement() {
		while (!stack.isEmpty()) {
			NodeIterator<T> p = stack.peek();
			if (findNextElementInNode(p)) {
				return;
			} 
			stack.pop();
		}
		//finished
		next = null;
	}
	
	private boolean findNextElementInNode(NodeIterator<T> p) {
		while (p.hasNext()) {
			if (p.isNextSub()) {
				PhTree5.applyArrayPosToValue(p.getCurrentPos(), p.node().getPostLen(), 
						valTemplate, p.isDepth0());
				Node<T> sub = p.getCurrentSubNode();
				NodeIterator<T> p2 = NodeIterator.create(sub, valTemplate, rangeMin, rangeMax, DIM, 
						false);
				p.increment();
				if (p2 != null) {
					stack.push(p2);
					if (findNextElementInNode(p2)) {
						return true;
					}
					stack.pop();
					// no matching (more) elements found
				}
			} else {
				next = p.getCurrentPost();
				p.increment();
				return true;
			}
		}
		return false;
	}
	
	@Override
	public boolean hasNext() {
		return next != null;
	}

	@Override
	public long[] next() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		long[] res = next;
		findNextElement();
		return res;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException("Not implemented yet.");
	}
}