/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16x8;

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
			stack = new NodeIteratorNoGC[PhTree16x8.DEPTH_64];
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public NodeIteratorNoGC<T> prepareAndPush(Node node, long[] prefix) {
			NodeIteratorNoGC<T> ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIteratorNoGC<>(dims);
				stack[size-1] = ni;
			}
			ni.init(rangeMin, rangeMax, node, checker, prefix);
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
	private long[] rangeMin;
	private long[] rangeMax;
	private PhFilter checker;
	private final PhTree16x8<T> pht;
	
	private PhEntry<T> resultFree;
	private PhEntry<T> resultToReturn;
	private boolean isFinished = false;
	
	public PhIteratorNoGC(PhTree16x8<T> pht, PhFilter checker) {
		this.dims = pht.getDim();
		this.checker = checker;
		this.stack = new PhIteratorStack();
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
		
		stack.prepareAndPush(pht.getRoot(), null);
		findNextElement();
	}

	private void findNextElement() {
		PhEntry<T> result = resultFree; 
		while (!stack.isEmpty()) {
			NodeIteratorNoGC<T> p = stack.peek();
			while (p.increment(result)) {
				if (result.hasNodeInternal()) {
					p = stack.prepareAndPush((Node) result.getNodeInternal(), result.getKey());
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
}
