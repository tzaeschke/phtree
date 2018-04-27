/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14.bst;

import java.util.NoSuchElementException;

import ch.ethz.globis.pht64kd.MaxKTreeI;
import ch.ethz.globis.pht64kd.MaxKTreeI.NtEntry;
import ch.ethz.globis.pht64kd.MaxKTreeI.PhIterator64;
import ch.ethz.globis.phtree.v14.nt.NtNode;


/**
 * Iterator for the individual nodes in a NodeTree.
 * 
 * This PhIterator reuses PhEntry objects to avoid unnecessary creation of objects.
 * Calls to next() and nextKey() will result in creation of new PhEntry and long[] objects
 * respectively to maintain expected behaviour. However, the nextEntryUnstable() method
 * returns the internal PhEntry without creating any new objects. The returned PhEntry and long[]
 * are valid until the next call to nextXXX().
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public final class NtIteratorMask<T> implements PhIterator64<T> {

	private class PhIteratorStack {
		private final NtNodeIteratorMask<T>[] stack;
		private int size = 0;
		
		@SuppressWarnings("unchecked")
		public PhIteratorStack(int depth) {
			stack = new NtNodeIteratorMask[depth];
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public NtNodeIteratorMask<T> prepareAndPush(NtNode<T> node, long currentPrefix) {
			NtNodeIteratorMask<T> ni = stack[size++];
			if (ni == null)  {
				ni = new NtNodeIteratorMask<>();
				stack[size-1] = ni;
			}
			ni.init(minMask, maxMask, currentPrefix, node);
			return ni;
		}

		public NtNodeIteratorMask<T> peek() {
			return stack[size-1];
		}

		public NtNodeIteratorMask<T> pop() {
			return stack[--size];
		}
	}

	private final PhIteratorStack stack;
	private long minMask;
	private long maxMask;
	
	private final NtEntry<T> resultBuf1;
	private final NtEntry<T> resultBuf2;
	private boolean isFreeBuf1;
	boolean isFinished = false;
	
	public NtIteratorMask(int keyBitWidth) {
		this.stack = new PhIteratorStack(NtNode.calcTreeHeight(keyBitWidth));
		this.resultBuf1 = new NtEntry<>(0, new long[keyBitWidth], null);
		this.resultBuf2 = new NtEntry<>(0, new long[keyBitWidth], null);
	}	
		
	@SuppressWarnings("unchecked")
	@Override
	public void reset(MaxKTreeI tree, long minMask, long maxMask) {
		reset((NtNode<T>)tree.getRoot(), minMask, maxMask);
	}
	
	@Override
	public void reset(MaxKTreeI tree) {
		throw new UnsupportedOperationException();
	}
	
	public NtIteratorMask<T> reset(NtNode<T> root, long minMask, long maxMask) {	
		this.minMask = minMask;
		this.maxMask = maxMask;
		this.stack.size = 0;
		this.isFinished = false;
		
		if (root == null) {
			//empty index
			isFinished = true;
			return this;
		}
		
		stack.prepareAndPush(root, 0);
		findNextElement();
		return this;
	}

	private void findNextElement() {
		NtEntry<T> result = isFreeBuf1 ? resultBuf1 : resultBuf2; 
		while (!stack.isEmpty()) {
			NtNodeIteratorMask<T> p = stack.peek();
			while (p.increment(result)) {
				if (p.isNextSub()) {
					p = stack.prepareAndPush(p.getCurrentSubNode(), p.getPrefix());
					continue;
				} else {
					isFreeBuf1 = !isFreeBuf1;
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
	public long nextKey() {
		return nextEntryReuse().getKey();
	}

	@Override
	public long[] nextKdKey() {
		long[] key = nextEntryReuse().getKdKey();
		long[] ret = new long[key.length];
		System.arraycopy(key, 0, ret, 0, key.length);
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
	@Deprecated
	public NtEntry<T> nextEntry() {
		return new NtEntry<>(nextEntryReuse());
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
	public NtEntry<T> nextEntryReuse() {
		if (isFinished) {
			throw new NoSuchElementException();
		}
		NtEntry<T> ret = isFreeBuf1 ? resultBuf2 : resultBuf1;
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
	 * 
	 * @param newGlobalMinMask global min mask 
	 * @param newGlobalMaxMask global max mask
	 */
	public void adjustMinMax(long newGlobalMinMask, long newGlobalMaxMask) {
		while (stack.size > 1 && !stack.peek().verifyMinMax(newGlobalMinMask, newGlobalMaxMask)) {
			stack.pop();
		}
		
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
//		if (checker != null) {
//			while (!stack.isEmpty() && 
//					!checker.isValid(stack.peek().valTemplate, stack.peek().postLen)) {
//				stack.pop();
//			}
//		}

		while (!stack.isEmpty() 
				&& !stack.peek().adjustMinMax(newGlobalMinMask, newGlobalMaxMask)) {
			stack.pop();
		}
	}
	
}