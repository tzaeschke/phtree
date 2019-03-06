/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable. All rights reserved.
 *
 * This file is part of the PH-Tree project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.ethz.globis.phtree.v16hd;

import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.PhTree.PhExtent;
import ch.ethz.globis.phtree.util.unsynced.LongArrayOps;

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
public final class PhIteratorFullNoGC<T> implements PhExtent<T> {

	private class PhIteratorStack {
		private final NodeIteratorFullNoGC<T>[] stack;
		private int size = 0;
		
		@SuppressWarnings("unchecked")
		PhIteratorStack() {
			stack = new NodeIteratorFullNoGC[PhTree16HD.DEPTH_64];
		}

		public boolean isEmpty() {
			return size == 0;
		}

		public NodeIteratorFullNoGC<T> prepareAndPush(Node node) {
			NodeIteratorFullNoGC<T> ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIteratorFullNoGC<>();
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

	private final PhIteratorStack stack;
	private PhFilter checker;
	private final PhTree16HD<T> pht;
	
	private PhEntry<T> resultFree;
	private PhEntry<T> resultToReturn;
	private boolean isFinished = false;
	
	public PhIteratorFullNoGC(PhTree16HD<T> pht, PhFilter checker) {
		int dims = pht.getDim();
		this.checker = checker;
		this.stack = new PhIteratorStack();
		this.pht = pht;
		this.resultFree = new PhEntry<>(new long[dims], null);
		this.resultToReturn = new PhEntry<>(new long[dims], null);
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
		PhEntry<T> result = resultFree; 
		while (!stack.isEmpty()) {
			NodeIteratorFullNoGC<T> p = stack.peek();
			while (p.increment(result)) {
				if (result.hasNodeInternal()) {
					p = stack.prepareAndPush((Node) result.getNodeInternal());
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
		LongArrayOps.arraycopy(key, 0, ret, 0, key.length);
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