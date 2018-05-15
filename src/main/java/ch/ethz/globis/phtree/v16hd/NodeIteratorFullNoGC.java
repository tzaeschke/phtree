/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16hd;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorAll;



/**
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public class NodeIteratorFullNoGC<T> {
	
	private boolean finished;
	private BSTIteratorAll ntIterator;
	private PhFilter checker;


	/**
	 * 
	 */
	public NodeIteratorFullNoGC() {
		// nothing
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
		finished = false;
		this.checker = checker;
	
		//Position of the current entry
		if (ntIterator == null) {
			ntIterator = new BSTIteratorAll();
		}
		ntIterator.reset(node.getRoot());
	}

	/**
	 * Advances the cursor. 
	 * @return TRUE iff a matching element was found.
	 */
	boolean increment(PhEntry<T> result) {
		getNext(result);
		return !finished;
	}


	@SuppressWarnings("unchecked")
	private boolean readValue(BSTEntry entry, PhEntry<T> result) {
		long[] kdKey = entry.getKdKey();
		Object value = entry.getValue();
		if (value instanceof Node) {
			Node sub = (Node) value;
			if (checker != null && !checker.isValid(sub.postLenStored()+1, kdKey)) {
				return false;
			}
			result.setKeyInternal(kdKey);
			result.setNodeInternal(sub);
		} else {
			if (checker != null && !checker.isValid(kdKey)) {
				return false;
			}
			result.setKeyInternal(kdKey);
			//ensure that 'node' is set to null
			result.setValueInternal((T) value);
		}
		return true;
	}


	private void getNext(PhEntry<T> result) {
		niFindNext(result);
	}
	
	
	private void niFindNext(PhEntry<T> result) {
		while (ntIterator.hasNextEntry()) {
			BSTEntry be = ntIterator.nextEntry();
			if (readValue(be, result)) {
				return;
			}
		}
		finished = true;
	}

	void init(Node node, PhFilter checker) {
		reinit(node, checker);
	}

}
