/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v11lhc;

import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.PhTree.PhQuery;
import ch.ethz.globis.phtree.PhTreeHelper;

/**
 * This PhIterator uses a loop instead of recursion in findNextElement();. 
 * It also reuses PhEntry objects to avoid unnecessary creation of objects.
 * Calls to next() and nextKey() will result in creation of new PhEntry and long[] objects
 * respectively to maintain expected behaviour. However, the nextEntryUnstable() method
 * returns the internal PhEntry without creating any new objects. The returned PhEntry and long[]
 * are valid until the next call to nextXXX().
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public final class PhIteratorNoGC<T> implements PhQuery<T> {

	private class PhIteratorStack {
		private final NodeIteratorNoGC<T>[] stack;
		private int size = 0;
		
		@SuppressWarnings("unchecked")
		public PhIteratorStack() {
			stack = new NodeIteratorNoGC[PhTreeLhc11.DEPTH_64];
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public NodeIteratorNoGC<T> prepareAndPush(Node node) {
			NodeIteratorNoGC<T> ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIteratorNoGC<>(dims, valTemplate);
				stack[size-1] = ni;
			}
			ni.init(rangeMin, rangeMax, node, checker);
			return ni;
		}

		public NodeIteratorNoGC<T> peek() {
			return stack[size-1];
		}

		public NodeIteratorNoGC<T> pop() {
			return stack[--size];
		}
	}

	private final int dims;
	private final PhIteratorStack stack;
	private final long[] valTemplate;
	private long[] rangeMin;
	private long[] rangeMax;
	private PhFilter checker;
	private final PhTreeLhc11<T> pht;
	
	private PhEntry<T> resultFree;
	private PhEntry<T> resultToReturn;
	private boolean isFinished = false;
	
	public PhIteratorNoGC(PhTreeLhc11<T> pht, PhFilter checker) {
		this.dims = pht.getDim();
		this.checker = checker;
		this.stack = new PhIteratorStack();
		this.valTemplate = new long[dims];
		this.pht = pht;
		this.resultFree = new PhEntry<>(new long[dims], null);
		this.resultToReturn = new PhEntry<>(new long[dims], null);
	}	
		
	@Override
	public void reset(long[] rangeMin, long[] rangeMax) {	
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.stack.size = 0;
		this.isFinished = false;
		
		if (pht.getRoot() == null) {
			//empty index
			isFinished = true;
			return;
		}
		
		stack.prepareAndPush(pht.getRoot());
		findNextElement();
	}

	private void findNextElement() {
		PhEntry<T> result = resultFree; 
		while (!stack.isEmpty()) {
			NodeIteratorNoGC<T> p = stack.peek();
			while (p.increment(result)) {
				if (result.hasNodeInternal()) {
					p = stack.prepareAndPush((Node) result.getNodeInternal());
					continue;
				} else {
					resultFree = resultToReturn;
					resultToReturn = result;
					return;
				}
			}
			// no matching (more) elements found
			stack.pop();
		}
		//finished
		isFinished = true;
	}
	
	@Override
	public long[] nextKey() {
		long[] key = nextEntryReuse().getKey();
		long[] ret = new long[key.length];
		if (dims > 10) {
			System.arraycopy(key, 0, ret, 0, key.length);
		} else {
			for (int i = 0; i < key.length; i++) {
				ret[i] = key[i];
			}
		}
		return ret;
	}

	@Override
	public T nextValue() {
		return nextEntryReuse().getValue();
	}

	@Override
	public boolean hasNext() {
		return !isFinished;
	}

	@Override
	public PhEntry<T> nextEntry() {
		return new PhEntry<>(nextEntryReuse());
	}
	
	@Override
	public T next() {
		T v = nextEntryReuse().getValue();
		return v == PhTreeHelper.NULL ? null : v;
	}

	/**
	 * Special 'next' method that avoids creating new objects internally by reusing Entry objects.
	 * Advantage: Should completely avoid any GC effort.
	 * Disadvantage: Returned PhEntries are not stable and are only valid until the
	 * next call to next(). After that they may change state.
	 * @return The next entry
	 */
	@Override
	public PhEntry<T> nextEntryReuse() {
		if (isFinished) {
			throw new NoSuchElementException();
		}
		PhEntry<T> ret = resultToReturn;
		findNextElement();
		return ret;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

	/**
	 * This method should be called after changing the min/max values.
	 * The method ensures that, at least for shrinking MBB, the iterator
	 * is 'popped' in case it iterates over a node that does not intersect
	 * with the new MBBs.  
	 */
	public void adjustMinMax() {
		
		//First check: does the node still intersect with the query rectangle?
		while (stack.size > 1 && !stack.peek().verifyMinMax()) {
			stack.pop();
		}
		
		//Second: does the node still intersect with the checked range? 
		if (checker != null) {
			while (!stack.isEmpty() && 
					!checker.isValid(stack.peek().node().getPostLen()+1, valTemplate)) {
				stack.pop();
			}
		}

		//Third: So if the node is still important, lets's adjust the internal masks
		//       and pop up if the local iterator is already outside the intersection.
		while (!stack.isEmpty() && !stack.peek().adjustMinMax(rangeMin, rangeMax)) {
			stack.pop();
		}
	}
	
}