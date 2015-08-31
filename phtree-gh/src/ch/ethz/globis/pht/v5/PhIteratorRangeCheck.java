/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v5;

import ch.ethz.globis.pht.v5.PhTree5.Node;
import ch.ethz.globis.pht.v5.PhTree5.NodeIterator;

class PhIteratorRangeCheck {

	public static <T> boolean isRangeEmpty(Node<T> root, long[] rangeMin, long[] rangeMax, 
			final int DIM, final int DEPTH) {
		PhIteratorStack<T> stack = new PhIteratorStack<T>(DEPTH);
		long[] valTemplate = new long[DIM];
		if (root == null) {
			//empty index
			return true;
		}

		NodeIterator<T> p2 = NodeIterator.create(root, valTemplate, rangeMin, rangeMax, DIM, true);
		if (p2 == null) {
			return true;
		}
		stack.push(p2);
		while (!stack.isEmpty()) {
			NodeIterator<T> p = stack.peek();
			if (findNextElementInNode(p, valTemplate, stack, rangeMin, rangeMax, DIM)) {
				return false;
			} 
			stack.pop();
		}
		//finished
		return true;
	}
	
	private static <T> boolean findNextElementInNode(NodeIterator<T> p, long[] valTemplate,
			PhIteratorStack<T> stack,
			long[] rangeMin, long[] rangeMax, final int DIM) {
		while (p.hasNext()) {
			if (p.isNextSub()) {
				PhTree5.applyArrayPosToValue(p.getCurrentPos(), p.node().getPostLen(), 
						valTemplate, p.isDepth0());
				Node<T> sub = p.getCurrentSubNode();
				NodeIterator<T> p2 = NodeIterator.create(sub,  
						valTemplate, rangeMin, rangeMax, DIM, false);
				if (p2 != null) {
					p.increment();
					stack.push(p2);
					if (findNextElementInNode(p2, valTemplate, stack, rangeMin, rangeMax, DIM)) {
						return true;
					}
					stack.pop();
					// no matching (more) elements found
				} else {
					// infix comparison failed
				}
			} else {
				p.increment();
				return true;
			}
		}
		return false;
	}
}