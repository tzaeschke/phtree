/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v11;

import java.util.NoSuchElementException;

import ch.ethz.globis.pht.PhEntry;
import ch.ethz.globis.pht.PhFilter;
import ch.ethz.globis.pht.PhTree.PhExtent;
import ch.ethz.globis.pht.v11.PhTree11.NodeEntry;

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
 * @param <T>
 */
public final class PhIteratorFullNoGC<T> implements PhExtent<T> {

	private class PhIteratorStack {
		private final NodeIteratorFullNoGC<T>[] stack;
		private int size = 0;
		
		@SuppressWarnings("unchecked")
		public PhIteratorStack() {
			stack = new NodeIteratorFullNoGC[PhTree11.DEPTH_64];
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public NodeIteratorFullNoGC<T> prepareAndPush(Node node) {
			NodeIteratorFullNoGC<T> ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIteratorFullNoGC<>(dims, valTemplate);
				stack[size-1] = ni;
			}
			
			ni.init(node, checker);
			return ni;
		}

		public NodeIteratorFullNoGC<T> peek() {
			return stack[size-1];
		}

		public NodeIteratorFullNoGC<T> pop() {
			return stack[--size];
		}
	}

	private final int dims;
	private final PhIteratorStack stack;
	private final long[] valTemplate;
	private PhFilter checker;
	private final PhTree11<T> pht;
	
	private NodeEntry<T> resultFree;
	private NodeEntry<T> resultToReturn;
	private boolean isFinished = false;
	
	public PhIteratorFullNoGC(PhTree11<T> pht, PhFilter checker) {
		this.dims = pht.getDim();
		this.checker = checker;
		this.stack = new PhIteratorStack();
		this.valTemplate = new long[dims];
		this.pht = pht;
		this.resultFree = new NodeEntry<>(new long[dims], null);
		this.resultToReturn = new NodeEntry<>(new long[dims], null);
	}	
		
	@Override
	public PhIteratorFullNoGC<T> reset() {	
		this.stack.size = 0;
		this.isFinished = false;
		
		if (pht.getRoot() == null) {
			//empty index
			isFinished = true;
			return this;
		}
		
		stack.prepareAndPush(pht.getRoot());
		findNextElement();
		return this;
	}

	private void findNextElement() {
		NodeEntry<T> result = resultFree; 
		while (!stack.isEmpty()) {
			NodeIteratorFullNoGC<T> p = stack.peek();
			while (p.increment(result)) {
				if (result.node != null) {
					p = stack.prepareAndPush(result.node);
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
		return nextEntryReuse().getValue();
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