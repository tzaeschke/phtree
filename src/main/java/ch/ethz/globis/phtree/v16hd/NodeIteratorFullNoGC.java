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
	
	private final BSTIteratorAll ntIterator;
	private PhFilter checker;


	/**
	 * 
	 */
	public NodeIteratorFullNoGC() {
		this.ntIterator = new BSTIteratorAll();
	}
	
	/**
	 * @param node node
	 * @param checker result verifier, can be null.
	 */
	void init(Node node, PhFilter checker) {
		this.checker = checker;
		this.ntIterator.reset(node.getRoot());
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
