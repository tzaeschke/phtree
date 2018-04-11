/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14;

import ch.ethz.globis.pht64kd.MaxKTreeI.NtEntry;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.v14.nt.NtIteratorMinMax;



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
	private boolean isNI;
	private long next = -1;
	private Node node;
	private int currentOffsetKey;
	private NtIteratorMinMax<Object> ntIterator;
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
	 * @param node
	 * @param rangeMin The minimum value that any found value should have. If the found value is
	 *  lower, the search continues.
	 * @param rangeMax
	 * @param lower The minimum HC-Pos that a value should have.
	 * @param upper
	 * @param checker result verifier, can be null.
	 */
	private void reinit(Node node, PhFilter checker) {
		next = -1;
		nEntriesFound = 0;
		this.checker = checker;
	
		this.node = node;
		this.isHC = node.isAHC();
		this.isNI = node.isNT();
		nMaxEntries = node.getEntryCount();
		
		
		//Position of the current entry
		if (isNI) {
			if (ntIterator == null) {
				ntIterator = new NtIteratorMinMax<>(dims);
			}
			ntIterator.reset(node.ind(), 0, Long.MAX_VALUE);
		} else {
			currentOffsetKey = node.getBitPosIndex();
			postEntryLenLHC = Node.IK_WIDTH(dims)+dims*node.postLenStored();
		}
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
		if (isNI) {
			niFindNext(result);
			return;
		}

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
	
	private void niFindNext(PhEntry<T> result) {
		while (ntIterator.hasNext()) {
			NtEntry<Object> e = ntIterator.nextEntryReuse();
			if (readValue(e.key(), e.getKdKey(), e.value(), result)) {
				next = e.key();
				return;
			}
		}
		next = FINISHED;
	}

	void init(Node node, PhFilter checker) {
		reinit(node, checker);
	}

}
