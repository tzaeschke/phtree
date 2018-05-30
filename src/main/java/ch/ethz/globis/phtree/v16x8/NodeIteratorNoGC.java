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
import ch.ethz.globis.phtree.v16x8.bst.BSTIteratorMask;



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
	private long maskLower;
	private long maskUpper;
	private long[] rangeMin;
	private long[] rangeMax;
	private PhFilter checker;

	/**
	 * 
	 * @param dims dimensions
	 */
	public NodeIteratorNoGC(int dims) {
		niIterator = new BSTIteratorMask();
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
	private void reinit(Node node, long[] rangeMin, long[] rangeMax, PhFilter checker) {
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.checker = checker;
		this.node = node;
		this.niIterator.reset(node.getRoot(), maskLower, maskUpper);
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
			if (checker != null && sub.getPostLen() < (PhTree16x8.DEPTH_64-1) &&
					!checker.isValid(sub.getPostLen()+1, candidate.getKdKey())) {
				return false;
			}
			return true;
		}
		
		return checker == null || checker.isValid(candidate.getKdKey());
	}

	
	/**
	 * 
	 * @param rangeMin
	 * @param rangeMax
	 * @param prefix
	 */
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
		int postLen = node.getPostLen();
		long maskHcBit = 1L << postLen;
		long maskVT = (-1L) << postLen;
		long lowerLimit = 0;
		long upperLimit = 0;
		//to prevent problems with signed long when using 64 bit
		if (maskHcBit >= 0) { //i.e. postLen < 63
			for (int i = 0; i < rangeMin.length; i++) {
				lowerLimit <<= 1;
				upperLimit <<= 1;
				long nodeBisection = (prefix[i] | maskHcBit) & maskVT; 
				if (rangeMin[i] >= nodeBisection) {
					//==> set to 1 if lower value should not be queried 
					lowerLimit |= 1L;
				}
				if (rangeMax[i] >= nodeBisection) {
					//Leave 0 if higher value should not be queried.
					upperLimit |= 1L;
				}
			}
		} else {
			//special treatment for signed longs
			//The problem (difference) here is that a '1' at the leading bit does indicate a
			//LOWER value, opposed to indicating a HIGHER value as in the remaining 63 bits.
			//The hypercube assumes that a leading '0' indicates a lower value.
			//Solution: We leave HC as it is.

			for (int i = 0; i < rangeMin.length; i++) {
				lowerLimit <<= 1;
				upperLimit <<= 1;
				if (rangeMin[i] < 0) {
					//If minimum is positive, we don't need the search negative values 
					//==> set upperLimit to 0, prevent searching values starting with '1'.
					upperLimit |= 1L;
				}
				if (rangeMax[i] < 0) {
					//Leave 0 if higher value should not be queried
					//If maximum is negative, we do not need to search positive values 
					//(starting with '0').
					//--> lowerLimit = '1'
					lowerLimit |= 1L;
				}
			}
		}

		this.maskLower = lowerLimit;
		this.maskUpper = upperLimit;
	}
	
	void init(long[] rangeMin, long[] rangeMax, Node node, PhFilter checker, long[] prefix) {
		this.node = node; //for calcLimits
		calcLimits(rangeMin, rangeMax, prefix);
		reinit(node, rangeMin, rangeMax, checker);
	}
}
