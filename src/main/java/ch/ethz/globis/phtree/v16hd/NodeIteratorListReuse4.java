/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16hd;

import java.util.Arrays;
import java.util.Comparator;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhTreeHelperHD;
import ch.ethz.globis.phtree.util.BitsLong;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorToArray;
import ch.ethz.globis.phtree.v16hd.bst.LLEntry;


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
	private final PhFilterDistance checker;

	private final PhIteratorStack pool;
	
	private final class NodeIterator {
	
		private Node node;
		private final BSTIteratorAll niIterator = new BSTIteratorAll();
		private final BSTIteratorToArray itToArray = new BSTIteratorToArray();
		private LLEntry[] buffer;
		private int bufferSize;
		private final LLEComp COMP = new LLEComp();

		/**
		 * 
		 * @param node Node
		 * @param prefix Prefix
		 */
		void reinitAndRun(Node node, long[] prefix) {
			this.node = node;
			niIterator.reset(node.getRoot());
			getAll(prefix);
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
			NodeIteratorListReuse.CE1++;
			if (v instanceof Node) {
				NodeIteratorListReuse.CE2++;
				NodeIteratorListReuse.AMMN2++;
				NodeIteratorListReuse.AMMN3++;
				checkAndRunSubnode((Node) v, be.getKdKey());
			} else if (v != null) { 
				NodeIteratorListReuse.CE3++;
				NodeIteratorListReuse.AMM2++;
				readValue(be);
			}
		}

		
		private void getAll(long[] prefix) {
			if (results.isInitialDive()) {
				niDepthFirstNeighborsSecond(prefix);
			} else {
				niAllNextIterator(prefix);
			}
		}
		
		private void niAllNextIterator(long[] prefix) {
			//TODO find limit!  3 <= limit <= 8   !!!! for CLUSTER 5
			if (prefix == null || dims > 4) {
				while (niIterator.hasNextULL()) {
					BSTEntry be = niIterator.nextBSTEntryReuse();
					checkEntry(be);
				}
				return;
			}
			
			//calc 'divePos', ie. whether prefix is below/above 'center' for each dimension
			long[] kNNCenter = checker.getCenter();
	    	
	        //We want to get the center, so no +1 for postLen
	        long prefixMask = (-1L) << node.getPostLen()+1;
	        long prefixBit = 1L << node.getPostLen();
	    	long[] relativeKNNpos = BitsHD.newArray(dims);
	        //get fraction
	        int bitsPerSlot = BitsHD.mod65x(dims);
	        int valsetPos = 0;
	        //result slot (rs)
	        for (int rs = 0; rs < relativeKNNpos.length; rs++) {
	        	long pos = 0;
	            for (int i = 0; i < bitsPerSlot; i++) {
		        	pos <<= 1;
		        	//set pos-bit if bit is set in value
		        	long nodeCenter = (prefix[valsetPos] & prefixMask) | prefixBit;
		        	pos |= (kNNCenter[valsetPos] > nodeCenter) ? 1 : 0;
		        	valsetPos++;
	            }
	            relativeKNNpos[rs] = pos;
	            bitsPerSlot = 64;
	        }
        
	        
        	iterateSortedBuffer(relativeKNNpos, prefix, 0);
        	BitsLong.arrayReplace(relativeKNNpos, null);
		}

		private void niDepthFirstNeighborsSecond(long[] prefix) {
			//First traverse the closest match (if any)
			//Second traverse direct neighbors
			//Third traverse the rest

			//First attempt deep dive
			long[] kNNCenter = checker.getCenter();
			long[] divePos = PhTreeHelperHD.posInArrayHD(kNNCenter, node.getPostLen());
			BSTEntry be = node.ntGetEntry(divePos);
			int minimumPermutations = 0;
			if (be != null) {
				if (be.getValue() instanceof Node) {
					Node sub = (Node) be.getValue();
					if (Bits.checkPrefix(be.getKdKey(), kNNCenter, sub.getPostLen()+1)) {
						run(sub, be.getKdKey());
						//ignore this quadrant from now on
						minimumPermutations = 1;
					}
				}
			}

			//Okay, deep dive is finished
			results.stopInitialDive();

			//TODO???
			if (node.getEntryCount() < 0*dims) {
				iterateUnsorted(divePos, minimumPermutations);
			} else {
				iterateSortedBuffer(divePos, prefix, minimumPermutations);
			}
		}
		
		private void iterateUnsorted(long[] divePos, int minimumPermutations) {
			niIterator.reset(node.getRoot());
			while (niIterator.hasNextULL()) {
				LLEntry le = niIterator.nextEntryReuse();
				if (BitsHD.xorBitCount(le.getKey(), divePos) >= minimumPermutations) {
					checkEntry(le.getValue());
				}
			}
		}
		
		private void iterateSortedBuffer(long[] divePos, long[] prefix, int minimumPermutations) {
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
			
			//Omit anything that we already traversed (there should be at most one entry at the moment) 
			int start = 0;
			while (start < bufferSize && BitsHD.xorBitCount(buffer[start].getKey(), divePos) < minimumPermutations) {
				start++;
			}

			NodeIteratorListReuse.HD11 += bufferSize;

			//Calculate how many permutations are at most possible
			double[] distances = new double[dims];
			PhDistance dist = checker.getDistance(); 
			dist.knnCalcDistances(checker.getCenter(), prefix, node.getPostLen() + 1, distances);
			int nMaxPermutatedBits = dist.knnCalcMaximumPermutationCount(distances, checker.getMaxDist());
			
			//Now check the rest
			double knownMaxDist = checker.getMaxDist();
			for (int i = start; i < bufferSize; i++) {
				if (checker.getMaxDist() < knownMaxDist) {
					knownMaxDist = checker.getMaxDist();
					nMaxPermutatedBits = dist.knnCalcMaximumPermutationCount(distances, checker.getMaxDist());
				}
				if (BitsHD.xorBitCount(buffer[i].getKey(), divePos) > nMaxPermutatedBits) {
					break;
				}
				NodeIteratorListReuse.HD12++;
				checkEntry(buffer[i].getValue());
			}
		}
	}

	
	NodeIteratorListReuse4(int dims, PhQueryKnnMbbPPList4<T>.KnnResultList4 results, PhFilterDistance checker) {
		this.dims = dims;
		this.results = results;
		this.pool = new PhIteratorStack();
		this.checker = checker;
	}

	void resetAndRun(Node node) {
		results.clear();
		run(node, null);
	}
	
	void run(Node node, long[] prefix) {
		NodeIterator nIt = pool.prepare();
		nIt.reinitAndRun(node, prefix);
		pool.pop();
	}

	private static class LLEComp implements Comparator<LLEntry> {
		long[] key;
	    @Override
		public int compare(LLEntry a, LLEntry b) {
	    	int h1 = BitsHD.xorBitCount(a.getKey(), key);
	    	int h2 = BitsHD.xorBitCount(b.getKey(), key);
            return h1 - h2;
	    }
	}
}
