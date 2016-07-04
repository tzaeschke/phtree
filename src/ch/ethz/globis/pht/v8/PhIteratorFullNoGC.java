/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v8;

import java.util.NoSuchElementException;

import ch.ethz.globis.pht.PhEntry;
import ch.ethz.globis.pht.PhFilter;
import ch.ethz.globis.pht.PhTree.PhExtent;
import ch.ethz.globis.pht.PhTreeHelper;

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
			stack = new NodeIteratorFullNoGC[PhTree8.DEPTH_64];
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public boolean prepare(Node<T> node) {
			node.getInfix(valTemplate);

			NodeIteratorFullNoGC<T> ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIteratorFullNoGC<>(DIM, valTemplate);
				stack[size-1] = ni;
			}
			
			ni.init(node, checker);
			return true;
		}

		public NodeIteratorFullNoGC<T> peek() {
			return stack[size-1];
		}

		public NodeIteratorFullNoGC<T> pop() {
			return stack[--size];
		}
	}

	public static int STAT_NODES_CHECKED = 0;
	public static int STAT_NODES_IGNORED = 0;
	public static int STAT_NODES_PREFIX_FAILED = 0;
	public static int STAT_NODES_EARLY_IRE_CHECK = 0;
	public static int STAT_NODES_EARLY_IRE_ABORT_I = 0;
	public static int STAT_NODES_EARLY_IRE_ABORT_E = 0;
	public static long MBB_TIME = 0;
	
	private final int DIM;
	private final PhIteratorStack stack;
	private final long[] valTemplate;
	private PhFilter checker;
	private final PhTree8<T> pht;
	
	private PhEntry<T> result;
	boolean isFinished = false;
	
	public PhIteratorFullNoGC(PhTree8<T> pht, PhFilter checker) {
		this.DIM = pht.getDim();
		this.checker = checker;
		this.stack = new PhIteratorStack();
		this.valTemplate = new long[DIM];
		this.pht = pht;
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
		
		if (stack.prepare(pht.getRoot())) {
			findNextElement();
		} else {
			isFinished = true;
		}
		return this;
	}

	private void findNextElement() {
		stackLoop:
		while (!stack.isEmpty()) {
			NodeIteratorFullNoGC<T> p = stack.peek();
			while (p.increment()) {
				if (p.isNextSub()) {
					//leave this here. We could move applyToArrayPos somewhere else, but we have to
					//take care that it is only applied AFTER the previous subNodes has been traversed,
					//otherwise we may mess up the valTemplate which is used in the previous Subnode.
					PhTreeHelper.applyHcPos(p.getCurrentPos(), p.node().getPostLen(), valTemplate);
					if (stack.prepare(p.getCurrentSubNode())) {
						continue stackLoop;
					} else {
						// infix comparison failed or node has no matching entries
						continue;
					}
				} else {
					result = p.getCurrentPost();
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
		if (DIM > 10) {
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
		return new PhEntry<T>(nextEntryReuse());
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
		PhEntry<T> ret = result;
		findNextElement();
		return ret;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
	
}