/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v15;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.v15.BSTHandler.BSTEntry;
import ch.ethz.globis.phtree.v15.bst.BSTIteratorMinMax;
import ch.ethz.globis.phtree.v15.bst.LLEntry;



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
	
	private long next = -1;
	private BSTIteratorMinMax<BSTEntry> ntIterator;
	private final long[] valTemplate;
	private PhFilter checker;


	/**
	 * 
	 * @param dims dimensions
	 * @param valTemplate A null indicates that no values are to be extracted.
	 */
	public NodeIteratorFullNoGC(int dims, long[] valTemplate) {
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
		this.checker = checker;
	
		//Position of the current entry
		if (ntIterator == null) {
			ntIterator = new BSTIteratorMinMax<>();
		}
		ntIterator.reset(node.ind(), 0, Long.MAX_VALUE);
	}

	/**
	 * Advances the cursor. 
	 * @return TRUE iff a matching element was found.
	 */
	boolean increment(PhEntry<T> result) {
		getNext(result);
		return next != FINISHED;
	}


	@SuppressWarnings("unchecked")
	private boolean readValue(long[] kdKey, Object value, PhEntry<T> result) {
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
		niFindNext(result);
	}
	
	
	private void niFindNext(PhEntry<T> result) {
		while (ntIterator.hasNextULL()) {
			LLEntry le = ntIterator.nextEntryReuse();
			BSTEntry be = (BSTEntry) le.getValue();
			if (readValue(be.getKdKey(), be.getValue(), result)) {
				next = le.getKey();
				return;
			}
		}
		next = FINISHED;
	}

	void init(Node node, PhFilter checker) {
		reinit(node, checker);
	}

}
