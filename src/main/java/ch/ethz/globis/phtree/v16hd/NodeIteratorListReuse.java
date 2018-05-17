/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16hd;

import java.util.List;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorMask;

/**
 * A NodeIterator that returns a list instead of an Iterator AND reuses the NodeIterator.
 * 
 * This seems to be slower than the simple NodeIteratorList. Why? 
 * The reason could be that the simple NIL creates many objects, but they end up in the stack
 * memory because they can not escape.
 * The NILReuse creates much less objects, but they end up in an array for reuse, which means
 * they may not be on the stack.... 
 * 
 * @author ztilmann
 *
 * @param <T> value type
 * @param <R> result type
 */
public class NodeIteratorListReuse<T, R> {
	
	private class PhIteratorStack {
		@SuppressWarnings("unchecked")
		private final NodeIterator[] stack = new NodeIteratorListReuse.NodeIterator[64];
		private int size = 0;


		NodeIterator prepare() {
			NodeIterator ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIterator(dims);
				stack[size-1] = ni;
			}
			return ni;
		}

		NodeIterator pop() {
			return stack[--size];
		}
	}

	
	private final int dims;
	private final PhResultList<T,R> results;
	private int maxResults;
	private long[] rangeMin;
	private long[] rangeMax;

	private final PhIteratorStack pool;
	
	private final class NodeIterator {
	
		private final BSTIteratorMask niIterator = new BSTIteratorMask();
		private final long[] maskLower;
		private final long[] maskUpper;

		private NodeIterator(int dims) {
			this.maskLower = BitsHD.newArray(dims);
			this.maskUpper = BitsHD.newArray(dims);
		}
		
		/**
		 * 
		 * @param node
		 * @param dims
		 * @param valTemplate A null indicates that no values are to be extracted.
		 * @param lower The minimum HC-Pos that a value should have.
		 * @param upper
		 * @param minValue The minimum value that any found value should have. 
		 * 				   If the found value is lower, the search continues.
		 * @param maxValue
		 */
		void reinitAndRun(Node node, long[] prefix) {
			calcLimits(node, rangeMin, rangeMax, prefix);

			this.niIterator.reset(node.getRoot(), maskLower,  maskUpper);

			getAll();
		}

		
		private void checkAndRunSubnode(Node sub, long[] subPrefix) {
			if (results.phIsPrefixValid(subPrefix, sub.getPostLen()+1)) {
				run(sub, subPrefix);
			}
		}


		@SuppressWarnings("unchecked")
		private void readValue(BSTEntry candidate) {
			//TODO avoid getting/assigning element? -> Most entries fail!
			PhEntry<T> result = results.phGetTempEntry();
			result.setKeyInternal(candidate.getKdKey());
			result.setValueInternal((T) candidate.getValue());
			results.phOffer(result);
		}
		
		private void checkEntry(BSTEntry be) {
			Object v = be.getValue();
			if (v instanceof Node) {
				checkAndRunSubnode((Node) v, be.getKdKey());
			} else if (v != null) { 
				readValue(be);
			}
		}

		private void getAll() {
			niAllNext();
		}


		private void niAllNext() {
			niAllNextIterator();
		}
		
		private void niAllNextIterator() {
			while (niIterator.hasNextEntry() && results.size() < maxResults) {
				BSTEntry be = niIterator.nextEntry();
				checkEntry(be);
			}
		}


		private void calcLimits(Node node, long[] rangeMin, long[] rangeMax, long[] prefix) {
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
					if ((mask1 >>= 1) == 0) {
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
					if ((mask1 >>= 1) == 0) {
						mask1 = 1L << 63;
						maskSlot++;
					}
				}
			}
		}
	}
	
	NodeIteratorListReuse(int dims, PhResultList<T, R> results) {
		this.dims = dims;
		this.results = results;
		this.pool = new PhIteratorStack();
	}

	List<R> resetAndRun(Node node, long[] rangeMin, long[] rangeMax, int maxResults) {
		results.clear();
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.maxResults = maxResults;
		run(node, null);
		return results;
	}
	
	void run(Node node, long[] prefix) {
		NodeIterator nIt = pool.prepare();
		nIt.reinitAndRun(node, prefix);
		pool.pop();
	}

}
