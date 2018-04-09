/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v11hd2.nt;

import java.util.NoSuchElementException;

import ch.ethz.globis.pht64kd.MaxKTreeHdI;
import ch.ethz.globis.pht64kd.MaxKTreeHdI.NtEntry;
import ch.ethz.globis.pht64kd.MaxKTreeHdI.PhIterator64;
import ch.ethz.globis.phtree.v11hd.BitsHD;


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
public final class NtIteratorFull<T> implements PhIterator64<T> {

	private class PhIteratorStack {
		private final NtNodeIteratorFull<T>[] stack;
		private int size = 0;
		
		@SuppressWarnings("unchecked")
		public PhIteratorStack(int depth) {
			stack = new NtNodeIteratorFull[depth];
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public NtNodeIteratorFull<T> prepareAndPush(NtNode<T> node, long[] currentPrefix) {
			NtNodeIteratorFull<T> ni = stack[size++];
			if (ni == null)  {
				ni = new NtNodeIteratorFull<>();
				stack[size-1] = ni;
			}
			ni.init(currentPrefix, node, isRootNegative && size==1);
			return ni;
		}

		public NtNodeIteratorFull<T> peek() {
			return stack[size-1];
		}

		public NtNodeIteratorFull<T> pop() {
			return stack[--size];
		}
	}

	private final PhIteratorStack stack;
	private final boolean isRootNegative;
	
	private final NtEntry<T> resultBuf1;
	private final NtEntry<T> resultBuf2;
	private boolean isFreeBuf1;
	private boolean isFinished = false;
	private final int keyBitWidth;
	
	public NtIteratorFull(int keyBitWidth) {
		this.stack = new PhIteratorStack(NtNode.calcTreeHeight(keyBitWidth));
		this.isRootNegative = keyBitWidth == 64;
		//TODO do we need a new array here?
		this.resultBuf1 = new NtEntry<>(BitsHD.newArray(keyBitWidth), new long[keyBitWidth], null);
		this.resultBuf2 = new NtEntry<>(BitsHD.newArray(keyBitWidth), new long[keyBitWidth], null);
		this.keyBitWidth = keyBitWidth;
	}	
		
	@Override
	public void reset(MaxKTreeHdI tree, long[] minMask, long[] maxMask) {
		throw new IllegalArgumentException();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void reset(MaxKTreeHdI tree) {
		reset((NtNode<T>)tree.getRoot());
	}
	
	public PhIterator64<T> reset(NtNode<T> root) {	
		this.stack.size = 0;
		this.isFinished = false;
		
		if (root == null) {
			//empty index
			isFinished = true;
			return this;
		}
		
		//TODO really pass in array?
		stack.prepareAndPush(root, BitsHD.newArray(keyBitWidth));
		findNextElement();
		return this;
	}

	private void findNextElement() {
		NtEntry<T> result = isFreeBuf1 ? resultBuf1 : resultBuf2; 
		while (!stack.isEmpty()) {
			NtNodeIteratorFull<T> p = stack.peek();
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
	public long[] nextKey() {
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
	
}