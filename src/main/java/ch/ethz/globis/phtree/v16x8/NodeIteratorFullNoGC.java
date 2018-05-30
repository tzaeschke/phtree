/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16x8;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.v16x8.Node.BSTEntry;
import ch.ethz.globis.phtree.v16x8.bst.BSTIteratorAll;



/**
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public class NodeIteratorFullNoGC<T> {
	
	private final BSTIteratorAll ntIterator = new BSTIteratorAll();
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
	 * @param checker result verifier, can be null.
	 */
	void init(Node node, PhFilter checker) {
		this.checker = checker;
		ntIterator.reset(node.getRoot());
	}

	/**
	 * Advances the cursor. 
	 * @return TRUE iff a matching element was found.
	 */
	boolean increment(PhEntry<T> result) {
		while (ntIterator.hasNextEntry()) {
			BSTEntry be = ntIterator.nextEntry();
			if (readValue(be, result)) {
				return true;
			}
		}
		return false;
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

}
