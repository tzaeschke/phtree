/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
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
package ch.ethz.globis.phtree.v13us;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;



/**
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public class NodeIteratorFullNoGC<T> {
	
	private static final long FINISHED = Long.MAX_VALUE; 
	
	private final int dims;
	private boolean isHC;
	private long next = -1;
	private Node node;
	private int currentOffsetKey;
	private int nMaxEntries;
	private int nEntriesFound = 0;
	private int postEntryLenLHC;
	private final long[] valTemplate;
	private PhFilter checker;
	private final long maxPos;


	/**
	 * 
	 * @param dims dimensions
	 * @param valTemplate A null indicates that no values are to be extracted.
	 */
	public NodeIteratorFullNoGC(int dims, long[] valTemplate) {
		this.dims = dims;
		this.maxPos = (1L << dims) -1;
		this.valTemplate = valTemplate;
	}
	
	/**
	 * 
	 * @param node node
	 * @param checker result verifier, can be null.
	 */
	private void reinit(Node node, PhFilter checker) {
		next = -1;
		nEntriesFound = 0;
		this.checker = checker;
	
		this.node = node;
		this.isHC = node.isAHC();
		nMaxEntries = node.getEntryCount();
		
		
		//Position of the current entry
		currentOffsetKey = node.getBitPosIndex();
		postEntryLenLHC = Node.IK_WIDTH(dims)+dims*node.postLenStored();
	}

	/**
	 * Advances the cursor. 
	 * @return TRUE iff a matching element was found.
	 */
	boolean increment(PhEntry<T> result) {
		getNext(result);
		return next != FINISHED;
	}

	/**
	 * 
	 * @return False if the value does not match the range, otherwise true.
	 */
	@SuppressWarnings("unchecked")
	private boolean readValue(int posInNode, long hcPos, PhEntry<T> result) {
		long[] key = result.getKey();
		Object v = node.getEntryPIN(posInNode, hcPos, valTemplate, key);
		if (v == null) {
			return false;
		}
		
		if (v instanceof Node) {
			result.setNodeInternal(v);
		} else {
			if (checker != null && !checker.isValid(key)) {
				return false;
			}
			//ensure that 'node' is set to null
			result.setValueInternal((T) v );
		}
		next = hcPos;
		
		return true;
	}

	@SuppressWarnings("unchecked")
	private boolean readValue(long pos, long[] kdKey, Object value, PhEntry<T> result) {
		if (value instanceof Node) {
			Node sub = (Node) value;
			if (checker != null && !checker.isValid(sub.postLenStored()+1, kdKey)) {
				return false;
			}
			System.arraycopy(kdKey, 0, valTemplate, 0, kdKey.length);
			result.setNodeInternal(sub);
		} else {
			if (checker != null && !checker.isValid(kdKey)) {
				return false;
			}
			System.arraycopy(kdKey, 0, result.getKey(), 0, kdKey.length);
			//ensure that 'node' is set to null
			result.setValueInternal((T) value);
		}
		return true;
	}


	private void getNext(PhEntry<T> result) {
		if (isHC) {
			getNextAHC(result);
		} else {
			getNextLHC(result);
		}
	}
	
	private void getNextAHC(PhEntry<T> result) {
		//while loop until 1 is found.
		long currentPos = next; 
		do {
			currentPos++;  //pos w/o bit-offset
			if (currentPos > maxPos) {
				next = FINISHED;
				break;
			}
		} while (!readValue((int) currentPos, currentPos, result));
	}
	
	private void getNextLHC(PhEntry<T> result) {
		long currentPos;
		do {
			if (++nEntriesFound > nMaxEntries) {
				next = FINISHED;
				break;
			}
			currentPos = Bits.readArray(node.ba(), currentOffsetKey, Node.IK_WIDTH(dims));
			currentOffsetKey += postEntryLenLHC;
			//check post-fix
		} while (!readValue(nEntriesFound-1, currentPos, result));
	}
	
	void init(Node node, PhFilter checker) {
		reinit(node, checker);
	}

}
