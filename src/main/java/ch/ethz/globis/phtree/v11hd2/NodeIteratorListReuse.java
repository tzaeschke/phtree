/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v11hd2;

import java.util.List;

import ch.ethz.globis.pht64kd.MaxKTreeHdI.NtEntry;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhTreeHelperHD;
import ch.ethz.globis.phtree.v11hd2.nt.NtIteratorMask;

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


		NodeIterator prepare(int dims) {
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
	private final long[] valTemplate;
	private long[] rangeMin;
	private long[] rangeMax;

	private final PhIteratorStack pool;
	
	private final class NodeIterator {
	
		private Node node;
		private NtIteratorMask<Object> niIterator;
		private final long[] maskLower;
		private final long[] maskUpper;
		private final long[] currentPosBuf;
		private boolean useHcIncrementer;

		private NodeIterator(int dims) {
			maskLower = BitsHD.newArray(dims);
			maskUpper = BitsHD.newArray(dims);
			currentPosBuf = BitsHD.newArray(dims);
		}
		
		/**
		 * 
		 * @param node
		 * @param dims
		 * @param valTemplate A null indicates that no values are to be extracted.
		 * @param minValue The minimum value that any found value should have. 
		 * 				   If the found value is lower, the search continues.
		 * @param maxValue
		 */
		void reinitAndRun(Node node) {
			this.node = node;
			this.niIterator = null;

			useHcIncrementer = false;
			if (niIterator == null) {
				niIterator = node.ntIteratorWithMask(dims, maskLower, maskUpper);
			}

			if (dims > 6) {
				initHCI();
			}

			getAll();
		}

		private void initHCI() {
			//LHC, NI, ...
			int nSetFilterBits = BitsHD.getFilterBits(maskLower, maskUpper, dims);
			//nPossibleMatch = (2^k-x)
			long nPossibleMatch = 1L << (dims - nSetFilterBits);
			//NT
			int nChild = node.ntGetSize();
			int logNChild = Long.SIZE - Long.numberOfLeadingZeros(nChild);
			//the following will overflow for k=60
			//DIM < 60 as safeguard against overflow of (nPossibleMatch*logNChild)
			useHcIncrementer = PhTreeHD11b.HCI_ENABLED && dims < 50 
					&& (nChild > nPossibleMatch*(double)logNChild*2);
			if (!useHcIncrementer) {
				niIterator.reset(node.ind());
			}
		}
		
		private void checkAndAddResult(PhEntry<T> e) {
			results.phOffer(e);
		}

		private void checkAndRunSubnode(Node sub, PhEntry<T> e) {
			if (e != null) {
				results.phReturnTemp(e);
			}
			if (results.phIsPrefixValid(valTemplate, sub.getPostLen()+1)) {
				run(sub);
			}
		}


		private void readValue(long[] pos, Object value, PhEntry<T> result) {
			if (!node.checkAndGetEntryNt(pos, value, result, valTemplate, rangeMin, rangeMax)) {
				return;
			}
			
			checkAndAddResult(result);
		}


		private void getAll() {
			niAllNext();
		}


		private void niAllNext() {
			//iterator?
			if (useHcIncrementer) {
				niAllNextHCI();
			} else {
				niAllNextIterator();
			}
		}
		
		private void niAllNextIterator() {
			//ITERATOR is used for DIM>6 or if results are dense 
			while (niIterator.hasNext() && results.size() < maxResults) {
				NtEntry<Object> e = niIterator.nextEntryReuse();
				Object v = e.value();
				if (v instanceof Node) {
					Node nextSubNode = (Node) v; 
					PhTreeHelperHD.applyHcPosHD(e.key(), node.getPostLen(), valTemplate);
					if (node.checkAndApplyInfixNt(nextSubNode.getInfixLen(), e.getKdKey(),
							valTemplate, rangeMin, rangeMax)) {
						checkAndRunSubnode(nextSubNode, null);
					}
				} else {
					PhEntry<T> resultBuffer = results.phGetTempEntry();
					System.arraycopy(e.getKdKey(), 0, resultBuffer.getKey(), 0, dims);
					readValue(e.key(), v, resultBuffer);
				}
			}
			return;
		}

		private void niAllNextHCI() {
			//HCI is used for DIM <=6 or if results are sparse
			//repeat until we found a value inside the given range
			BitsHD.set(currentPosBuf, maskLower); 
			while (results.size() < maxResults) {
				PhEntry<T> resultBuffer = results.phGetTempEntry();
				Object v = node.ntGetEntry(currentPosBuf, resultBuffer.getKey(), valTemplate);
				//sub-node?
				if (v instanceof Node) {
					Node sub = (Node) v;
					PhTreeHelperHD.applyHcPosHD(currentPosBuf, node.getPostLen(), valTemplate);
					if (node.checkAndApplyInfixNt(sub.getInfixLen(), resultBuffer.getKey(), 
							valTemplate, rangeMin, rangeMax)) {
						checkAndRunSubnode(sub, resultBuffer);
					}
				} else if (v != null) { 
					//read and check post-fix
					readValue(currentPosBuf, v, resultBuffer);
				}

				if (!BitsHD.incHD(currentPosBuf, maskLower, maskUpper)) {
				//TODO remove
				//if (currentPos <= maskLower) {
					break;
				}
			}
		}
	}
	
	NodeIteratorListReuse(int dims, PhResultList<T, R> results) {
		this.dims = dims;
		this.valTemplate = new long[dims];
		this.results = results;
		this.pool = new PhIteratorStack();
	}

	List<R> resetAndRun(Node node, long[] rangeMin, long[] rangeMax, int maxResults) {
		results.clear();
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.maxResults = maxResults;
		run(node);
		return results;
	}
	
	void run(Node node) {
		NodeIterator nIt = pool.prepare(valTemplate.length);
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
		long maskHcBit = 1L << node.getPostLen();
		long maskVT = (-1L) << node.getPostLen();
		long[] lowerLimit = nIt.maskLower;
		long[] upperLimit = nIt.maskUpper;
		BitsHD.set0(lowerLimit);
		BitsHD.set0(upperLimit);
		int maskSlot = 0;
		long mask1 = 1L << (BitsHD.mod65x(valTemplate.length) - 1);
		//to prevent problems with signed long when using 64 bit
		if (maskHcBit >= 0) { //i.e. postLen < 63
			for (int i = 0; i < valTemplate.length; i++) {
				long nodeBisection = (valTemplate[i] | maskHcBit) & maskVT; 
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

			for (int i = 0; i < valTemplate.length; i++) {
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
		nIt.reinitAndRun(node);
		pool.pop();
	}

}
