/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v8;

import java.util.NoSuchElementException;

import ch.ethz.globis.pht.PhEntry;
import ch.ethz.globis.pht.PhTree.PhIterator;
import ch.ethz.globis.pht.PhTreeHelper;

public final class PhIteratorReuse<T> implements PhIterator<T> {

	private class PhIteratorStack {
		private final NodeIteratorReuse<T>[] stack;
		private int size = 0;
		
		@SuppressWarnings("unchecked")
		public PhIteratorStack() {
			stack = new NodeIteratorReuse[PhTree8.DEPTH_64];
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public NodeIteratorReuse<T> prepare(Node<T> node) {
			if (!PhTree8.checkAndApplyInfix(node, valTemplate, rangeMin, rangeMax)) {
				return null;
			}

			NodeIteratorReuse<T> ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIteratorReuse<>(DIM, valTemplate);
				stack[size-1] = ni;
			}
			NodeIteratorReuse.init(ni, rangeMin, rangeMax, valTemplate, node);
			return ni;
		}

		public NodeIteratorReuse<T> peek() {
			return stack[size-1];
		}

		public NodeIteratorReuse<T> pop() {
			return stack[--size];
		}
	}

	private final int DIM;
	private final PhIteratorStack stack;
	private final long[] valTemplate;
	private final long[] rangeMin;
	private final long[] rangeMax;
	private long[] nextKey = null;
	private T nextVal = null;
	
	public PhIteratorReuse(PhTree8<T> pht, long[] rangeMin, long[] rangeMax, int DIM) {
		this.DIM = DIM;
		this.stack = new PhIteratorStack();
		this.valTemplate = new long[DIM];
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		if (pht.getRoot() == null) {
			//empty index
			return;
		}


		NodeIteratorReuse<T> p2 = stack.prepare(pht.getRoot());
		if (p2 != null) {
			findNextElement();
		}
	}

	private void findNextElement() {
 		while (!stack.isEmpty()) {
			NodeIteratorReuse<T> p = stack.peek();
			if (findNextElementInNode(p)) {
				return;
			} 
			stack.pop();
		}
		//finished
		nextKey = null;
		nextVal = null;
	}
	
	private boolean findNextElementInNode(NodeIteratorReuse<T> p) {
		while (p.hasNext()) {
			if (p.isNextSub()) {
				//leave this here. We could move applyToArrayPos somewhere else, but we have to
				//take care that it is only applied AFTER the previous subNodes has been traversed,
				//otherwise we may mess up the valTemplate which is used in the previous Subnode.
				PhTreeHelper.applyHcPos(p.getCurrentPos(), p.node().getPostLen(), valTemplate);
				Node<T> sub = p.getCurrentSubNode();
				NodeIteratorReuse<T> p2 = stack.prepare(sub);
				if (p2 != null) {
					if (findNextElementInNode(p2)) {
						p.increment();
						return true;
					}
					stack.pop();
					// no matching (more) elements found
				}
				p.increment();
			} else {
				nextKey = p.getCurrentPost();
				nextVal = p.getCurrentPostVal();
				p.increment();
				return true;
			}
		}
		return false;
	}
	
	@Override
	public long[] nextKey() {
		if (!hasNext()) {
			throw new NoSuchElementException();
		}
		long[] ret = nextKey;
		findNextElement();
		return ret;
	}

	@Override
	public T nextValue() {
		T ret = nextVal;
		nextKey();
		return ret;
	}

	@Override
	public boolean hasNext() {
		return nextKey != null;
	}

	@Override
	public PhEntry<T> nextEntry() {
		PhEntry<T> ret = new PhEntry<T>(nextKey, nextVal);
		nextKey();
		return ret;
	}
	
	@Override
	public T next() {
		return nextValue();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
}