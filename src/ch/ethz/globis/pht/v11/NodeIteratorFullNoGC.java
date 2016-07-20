/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v11;

import ch.ethz.globis.pht.PhFilter;
import ch.ethz.globis.pht.PhTreeHelper;
import ch.ethz.globis.pht.v11.PhTree11.NodeEntry;
import ch.ethz.globis.pht.v11.nt.NtIteratorMinMax;
import ch.ethz.globis.pht64kd.MaxKTreeI.NtEntry;



/**
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * 
 * @author ztilmann
 *
 * @param <T>
 */
public class NodeIteratorFullNoGC<T> {
	
	private static final long FINISHED = Long.MAX_VALUE; 
	
	private final int dims;
	private boolean isHC;
	private boolean isNI;
	private int postLen;
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
	 * @param dims
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
		this.postLen = node.getPostLen();
		nMaxEntries = node.getEntryCount();
		
		
		//Position of the current entry
		if (isNI) {
			if (ntIterator == null) {
				ntIterator = new NtIteratorMinMax<>(dims);
			}
			ntIterator.reset(node.ind(), 0, Long.MAX_VALUE);
		} else {
			currentOffsetKey = node.getBitPosIndex();
			postEntryLenLHC = Node.IK_WIDTH(dims)+dims*postLen;
		}
	}

	/**
	 * Advances the cursor. 
	 * @return TRUE iff a matching element was found.
	 */
	boolean increment(NodeEntry<T> result) {
		getNext(result);
		return next != FINISHED;
	}

	/**
	 * 
	 * @return False if the value does not match the range, otherwise true.
	 */
	@SuppressWarnings("unchecked")
	private boolean readValue(int posInNode, long hcPos, NodeEntry<T> result) {
		long[] key = result.getKey();
		Object v = node.getEntryPIN(posInNode, hcPos, valTemplate, key);
		if (v == null) {
			return false;
		}
		
		if (v instanceof Node) {
			result.setNodeKeepKey((Node) v);
		} else {
			if (checker != null && !checker.isValid(key)) {
				return false;
			}
			//ensure that 'node' is set to null
			result.setPost((T) v );
		}
		next = hcPos;
		
		return true;
	}

	@SuppressWarnings("unchecked")
	private boolean readValue(long pos, long[] kdKey, Object value, NodeEntry<T> result) {
		PhTreeHelper.applyHcPos(pos, postLen, valTemplate);
		if (value instanceof Node) {
			Node sub = (Node) value;
			result.setNodeKeepKey(sub);
			node.getInfixOfSubNt(kdKey, valTemplate);
			if (checker != null && !checker.isValid(sub.getPostLen()+1, valTemplate)) {
				return false;
			}
			result.setNodeKeepKey(sub);
		} else {
			long[] resultKey = result.getKey();
			final long mask = (~0L)<<postLen;
			for (int i = 0; i < resultKey.length; i++) {
				resultKey[i] = (valTemplate[i] & mask) | kdKey[i];
			}

			if (checker != null && !checker.isValid(resultKey)) {
				return false;
			}

			//ensure that 'node' is set to null
			result.setPost((T) value);
		}
		return true;
	}


	private void getNext(NodeEntry<T> result) {
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
	
	private void getNextAHC(NodeEntry<T> result) {
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
	
	private void getNextLHC(NodeEntry<T> result) {
		long currentPos;
		do {
			if (++nEntriesFound > nMaxEntries) {
				next = FINISHED;
				break;
			}
			currentPos = Bits.readArray(node.ba, currentOffsetKey, Node.IK_WIDTH(dims));
			currentOffsetKey += postEntryLenLHC;
			//check post-fix
		} while (!readValue(nEntriesFound-1, currentPos, result));
	}
	
	private void niFindNext(NodeEntry<T> result) {
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
