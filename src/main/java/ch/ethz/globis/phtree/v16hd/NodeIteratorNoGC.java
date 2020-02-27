/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
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
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorMask;



/**
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public class NodeIteratorNoGC<T> {
	
	private Node node;
	private final BSTIteratorMask niIterator;
	private final long[] maskLower;
	private final long[] maskUpper;
	private long[] rangeMin;
	private long[] rangeMax;
	private PhFilter checker;

	/**
	 * 
	 * @param dims dimensions
	 */
	public NodeIteratorNoGC(int dims) {
		this.maskLower = BitsHD.newArray(dims);
		this.maskUpper = BitsHD.newArray(dims);
		this.niIterator = new BSTIteratorMask();
	}
	
	private void reinit(Node node, long[] rangeMin, long[] rangeMax, PhFilter checker) {
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.checker = checker;
	
		this.node = node;
		niIterator.reset(node.getRoot(), maskLower, maskUpper);
	}

	
	/**
	 * Advances the cursor. 
	 * @return TRUE iff a matching element was found.
	 */
	boolean increment(PhEntry<T> result) {
		while (niIterator.hasNextEntry()) {
			BSTEntry be = niIterator.nextEntry();
			if (readValue(be, result)) {
				return true;
			}
		}
		return false;
	}

	private boolean readValue(BSTEntry candidate, PhEntry<T> result) {
		if (!node.checkAndGetEntry(candidate, result, rangeMin, rangeMax)) {
			return false;
		}
		
		//subnode ?
		if (candidate.getValue() instanceof Node) {
			Node sub = (Node) candidate.getValue();
			//skip this for postLen>=63
			if (checker != null && sub.getPostLen() < (PhTree16HD.DEPTH_64-1) &&
					!checker.isValid(sub.getPostLen()+1, candidate.getKdKey())) {
				return false;
			}
			return true;
		}
		
		return checker == null || checker.isValid(candidate.getKdKey());
	}


	private void calcLimits(long[] rangeMin, long[] rangeMax, long[] prefix) {
		//create limits for the local node. there is a lower and an upper limit. Each limit
		//consists of a series of DIM bit, one for each dimension.
		//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
		//not need to be queried.
		//For the upper limit, a '0' indicates that the 'higher' half does not need to be 
		//queried.
		//
		//              ||  lowerLimit=0 || lowerLimit=1 || upperLimit = 0 || upperLimit = 1
		// =============||===================================================================
		// query lower  ||     YES             NO
		// ============ || ==================================================================
		// query higher ||                                     NO               YES
		//
		
		//Hack: if prefix==null, we simply use rangeMin. This simplifies the case postLen=63 where 
		//we are at the root node and prefix==null and where we anyway don't need any bits from prefix.
		if (prefix == null) {
			prefix = rangeMin;
		}
		
		int postLen = node.getPostLen();
		long maskHcBit = 1L << postLen;
		long maskVT = (-1L) << postLen;
		long[] lowerLimit = maskLower;
		long[] upperLimit = maskUpper;
		BitsHD.set0(lowerLimit);
		BitsHD.set0(upperLimit);
		int maskSlot = 0;
		long mask1 = 1L << (BitsHD.mod65x(prefix.length) - 1);
		//to prevent problems with signed long when using 64 bit
		if (maskHcBit >= 0) { //i.e. postLen < 63
			for (int i = 0; i < prefix.length; i++) {
				long nodeBisection = (prefix[i] | maskHcBit) & maskVT; 
				if (rangeMin[i] >= nodeBisection) {
					//==> set to 1 if lower value should not be queried 
					lowerLimit[maskSlot] |= mask1;
				}
				if (rangeMax[i] >= nodeBisection) {
					//Leave 0 if higher value should not be queried.
					upperLimit[maskSlot] |= mask1;
				}
				if ((mask1 >>>= 1) == 0) {
					mask1 = 1L << 63;
					maskSlot++;
				}
			}
		} else {
			//special treatment for signed longs
			//The problem (difference) here is that a '1' at the leading bit does indicate a
			//LOWER value, opposed to indicating a HIGHER value as in the remaining 63 bits.
			//The hypercube assumes that a leading '0' indicates a lower value.
			//Solution: We leave HC as it is.

			for (int i = 0; i < prefix.length; i++) {
				if (rangeMin[i] < 0) {
					//If minimum is positive, we don't need the search negative values 
					//==> set upperLimit to 0, prevent searching values starting with '1'.
					upperLimit[maskSlot] |= mask1;
				}
				if (rangeMax[i] < 0) {
					//Leave 0 if higher value should not be queried
					//If maximum is negative, we do not need to search positive values 
					//(starting with '0').
					//--> lowerLimit = '1'
					lowerLimit[maskSlot] |= mask1;
				}
				if ((mask1 >>>= 1) == 0) {
					mask1 = 1L << 63;
					maskSlot++;
				}
			}
		}
	}

	void init(long[] rangeMin, long[] rangeMax, Node node, PhFilter checker, long[] prefix) {
		this.node = node; //for calcLimits
		calcLimits(rangeMin, rangeMax, prefix);
		reinit(node, rangeMin, rangeMax, checker);
	}
}
