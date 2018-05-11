/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16;

import static ch.ethz.globis.phtree.v16.NodeIteratorListReuse.MMM1;
import static ch.ethz.globis.phtree.v16.NodeIteratorListReuse.MMM2;
import static ch.ethz.globis.phtree.v16.NodeIteratorListReuse.MMM3;

import java.util.Arrays;
import java.util.Comparator;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.v16.Node.BSTEntry;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorToArray;
import ch.ethz.globis.phtree.v16.bst.LLEntry;


/**
 * A NodeIterator that returns a list instead of an Iterator AND reuses the NodeIterator.
 * 
 * This seems to be slower than the simple NodeIteratorList. Why? 
 * The reason could be that the simple NIL creates many objects, but they end up in the stack
 * memory because they can not escape.
 * The NILReuse creates much less objects, but they end up in an array for reuse, which means
 * they may not be on the stack.... 
 * 
 * Version 4: Use BstAll-Iterator
 * 
 * Version 3: This implements entry ordering by Hamming distance
 * 
 * Version 2: This implements Single-Dive with search of distance=1 neighbors
 * 
 * @author ztilmann
 *
 * @param <T> value type
 * @param <R> result type
 */
public class NodeIteratorListReuse4<T, R> {
	
	private class PhIteratorStack {
		@SuppressWarnings("unchecked")
		private final NodeIterator[] stack = new NodeIteratorListReuse4.NodeIterator[64];
		private int size = 0;


		NodeIterator prepare() {
			NodeIterator ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIterator();
				stack[size-1] = ni;
			}
			return ni;
		}

		NodeIterator pop() {
			return stack[--size];
		}
	}

	
	private final int dims;
	private final PhQueryKnnMbbPPList4<T>.KnnResultList4 results;
	private long[] rangeMin;
	private long[] rangeMax;
	private final PhFilterDistance checker;

	private final PhIteratorStack pool;
	
	private final class NodeIterator {
	
		private Node node;
		private BSTIteratorAll niIterator;
		private final BSTIteratorToArray itToArray = new BSTIteratorToArray();
		private long maskLower;
		private long maskUpper;
		private LLEntry[] buffer;
		private int bufferSize;
		private final LLEComp COMP = new LLEComp();

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
		void reinitAndRun(Node node) {
			this.node = node;

			if (niIterator == null) {
				niIterator = node.ntIteratorAll(buffer);
			} else {
				niIterator.reset(node.getRoot(), buffer);
			}
			getAll();
		}

		
		private void checkAndAddResult(PhEntry<T> e) {
			results.phOffer(e);
			//TODO when accepted, adapt min/max?!?!?!?!
		}

		private void checkAndRunSubnode(Node sub, long[] subPrefix) {
			if (results.phIsPrefixValid(subPrefix, sub.getPostLen()+1)) {
				NodeIteratorListReuse.AMMN4++;
				run(sub, subPrefix);
			}
		}


		@SuppressWarnings("unchecked")
		private void readValue(BSTEntry candidate) {
			NodeIteratorListReuse.AMM3++;
			//TODO avoid getting/assigning element? -> Most entries fail!
			PhEntry<T> result = results.phGetTempEntry();
			result.setKeyInternal(candidate.getKdKey());
			result.setValueInternal((T) candidate.getValue());
			checkAndAddResult(result);
		}
		
		private void checkEntry(BSTEntry be) {
			Object v = be.getValue();
			NodeIteratorListReuse.AMM1++;
			if (v instanceof Node) {
				NodeIteratorListReuse.AMMN2++;
				NodeIteratorListReuse.AMMN3++;
				checkAndRunSubnode((Node) v, be.getKdKey());
			} else if (v != null) { 
				NodeIteratorListReuse.AMM2++;
				readValue(be);
			}
		}

		private void getAll() {
			niAllNext();
		}


		private void niAllNext() {
			if (results.isInitialDive()) {
				niDepthFirstNeighborsSecond();
			} else {
				niAllNextIterator();
			}
		}
		
		private void niAllNextIterator() {
			//ITERATOR is used for DIM>6 or if results are dense 
			while (niIterator.hasNextULL()) {
				BSTEntry be = niIterator.nextBSTEntryReuse();
				checkEntry(be);
			}
			//TODO Adapt BST-Iterator to skip sub-nodes if check(current)==false -> searchNext(inc(current))  
			return;
		}

		private void niDepthFirstNeighborsSecond() {
			//First traverse the closest match (if any)
			//Second traverse direct neighbors
			//Third traverse the rest

			//First attempt deep dive
			long divePos = PhTreeHelper.posInArray(checker.getCenter(), node.getPostLen());
			BSTEntry be = node.ntGetEntry(divePos);
			if (be != null) {
				checkEntry(be);
				//adjust masks
				//TODO??/
				recalcMasks(node, checker.getCenter());
			}

			//Okay, deep dive is finished
			results.stopInitialDive();

			if (buffer == null || buffer.length < node.getEntryCount()) {
				if (buffer == null) {
					buffer = new LLEntry[node.getEntryCount()];
				} else {
					buffer = Arrays.copyOf(buffer, node.getEntryCount());
				}
			}
			bufferSize = 0;
		
			//Get all entries into the buffer array
			itToArray.reset(node.getRoot(), buffer);
			bufferSize = itToArray.getNEntries();
			
			//Sort
			COMP.key = divePos;
			Arrays.sort(buffer, 0, bufferSize, COMP);

			int start = 0;
			while (start < bufferSize && Long.bitCount(buffer[start].getKey() ^ divePos) < 1) {
				start++;
			}
			
			//Now check the rest
			for (int i = start; i < bufferSize; i++) {
				checkEntry(buffer[i].getValue());
			}
		}

		private void recalcMasks(Node node, long[] prefix) {
			//TODO remove this? It appears to make no sense for DIM


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
//			long maskHcBit = 1L << node.getPostLen();
//			long maskVT = (-1L) << node.getPostLen();
			long lowerLimit = 0;
			long upperLimit = 0;
//			//to prevent problems with signed long when using 64 bit
//			if (maskHcBit >= 0) { //i.e. postLen < 63
//				for (int i = 0; i < rangeMin.length; i++) {
//					lowerLimit <<= 1;
//					upperLimit <<= 1;
//					long nodeBisection = (prefix[i] | maskHcBit) & maskVT; 
//					if (rangeMin[i] >= nodeBisection) {
//						//==> set to 1 if lower value should not be queried 
//						lowerLimit |= 1L;
//					}
//					if (rangeMax[i] >= nodeBisection) {
//						//Leave 0 if higher value should not be queried.
//						upperLimit |= 1L;
//					}
//				}
//			} else {
//				//special treatment for signed longs
//				//The problem (difference) here is that a '1' at the leading bit does indicate a
//				//LOWER value, opposed to indicating a HIGHER value as in the remaining 63 bits.
//				//The hypercube assumes that a leading '0' indicates a lower value.
//				//Solution: We leave HC as it is.
//
//				for (int i = 0; i < rangeMin.length; i++) {
//					lowerLimit <<= 1;
//					upperLimit <<= 1;
//					if (rangeMin[i] < 0) {
//						//If minimum is positive, we don't need the search negative values 
//						//==> set upperLimit to 0, prevent searching values starting with '1'.
//						upperLimit |= 1L;
//					}
//					if (rangeMax[i] < 0) {
//						//Leave 0 if higher value should not be queried
//						//If maximum is negative, we do not need to search positive values 
//						//(starting with '0').
//						//--> lowerLimit = '1'
//						lowerLimit |= 1L;
//					}
//				}
//			}
			upperLimit = (1L<<dims)-1;
			MMM1++;
			if (lowerLimit > 0) {
				MMM2++;
			}
			if (upperLimit < ((1L<<dims)-1)) {
				MMM3++;
			}
			maskLower = lowerLimit;
			maskUpper = upperLimit;
		}
	
	}
	
	NodeIteratorListReuse4(int dims, PhQueryKnnMbbPPList4<T>.KnnResultList4 results, PhFilterDistance checker) {
		this.dims = dims;
		this.results = results;
		this.pool = new PhIteratorStack();
		this.checker = checker;
	}

	void resetAndRun(Node node, long[] rangeMin, long[] rangeMax) {
		results.clear();
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		run(node, null);
	}
	
	void run(Node node, long[] prefix) {
		NodeIterator nIt = pool.prepare();
		nIt.recalcMasks(node, prefix);
		nIt.reinitAndRun(node);
		pool.pop();
	}

	private static class LLEComp implements Comparator<LLEntry> {
		long key;
	    @Override
		public int compare(LLEntry a, LLEntry b) {
	    	int h1 = Long.bitCount(a.getKey() ^ key);
	    	int h2 = Long.bitCount(b.getKey() ^ key);
            return h1 - h2;
	    }
	}
}
