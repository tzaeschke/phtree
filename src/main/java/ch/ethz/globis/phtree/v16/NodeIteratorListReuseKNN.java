/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16;

import java.util.Arrays;
import java.util.Comparator;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.v16.Node.BSTEntry;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorToArray;


/**
 * A NodeIterator that returns a list instead of an Iterator AND reuses the NodeIterator.
 * 
 * This seems to be slower than the simple NodeIteratorList. Why? 
 * The reason could be that the simple NIL creates many objects, but they end up in the stack
 * memory because they can not escape.
 * The NILReuse creates much less objects, but they end up in an array for reuse, which means
 * they may not be on the stack.... 
 * 
 * This implements entry ordering by Hamming distance.
 * 
 * @author ztilmann
 *
 * @param <T> value type
 * @param <R> result type
 */
public class NodeIteratorListReuseKNN<T, R> {
	
	private class PhIteratorStack {
		@SuppressWarnings("unchecked")
		private final NodeIterator[] stack = new NodeIteratorListReuseKNN.NodeIterator[64];
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
	private final PhQueryKnnMbbPPList<T>.KnnResultList results;
	private final PhFilterDistance checker;

	private final PhIteratorStack pool;
	
	private final class NodeIterator {
	
		private Node node;
		private final BSTIteratorAll niIterator = new BSTIteratorAll();
		private final BSTIteratorToArray itToArray = new BSTIteratorToArray();
		private BSTEntry[] buffer;
		private int bufferSize;
		private final BSTEComp COMP = new BSTEComp();

		/**
		 * 
		 * @param node Node
		 * @param prefix Prefix
		 */
		void reinitAndRun(Node node, long[] prefix) {
			this.node = node;
			this.niIterator.reset(node.getRoot());
			getAll(prefix);
		}

		
		private void checkEntry(BSTEntry be) {
			Object v = be.getValue();
			if (v instanceof Node) {
				Node sub = (Node) v;
				if (results.phIsPrefixValid( be.getKdKey(), sub.getPostLen()+1)) {
					run(sub, be.getKdKey());
				}
			} else if (v != null) { 
				results.phOffer(be);
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
				while (niIterator.hasNextEntry()) {
					BSTEntry be = niIterator.nextEntry();
					checkEntry(be);
				}
				return;
			}
			
			//calc 'divePos', ie. whether prefix is below/above 'center' for each dimension
			long[] kNNCenter = checker.getCenter();
	    	
	        long relativeKNNpos = 0;
	        //We want to get the center, so no +1 for postLen
	        long prefixMask = (-1L) << node.getPostLen()+1;
	        long prefixBit = 1L << node.getPostLen();
	        for (int i = 0; i < prefix.length; i++) {
	        	relativeKNNpos <<= 1;
	        	//set pos-bit if bit is set in value
	        	long nodeCenter = (prefix[i] & prefixMask) | prefixBit;
	        	relativeKNNpos |= (kNNCenter[i] > nodeCenter) ? 1 : 0;
	        }
			
        	iterateSortedBuffer(relativeKNNpos, prefix, 0);
		}

		private void niDepthFirstNeighborsSecond(long[] prefix) {
			//First traverse the closest match (if any)
			//Second traverse direct neighbors
			//Third traverse the rest

			//First attempt deep dive
			long[] kNNCenter = checker.getCenter();
			long divePos = PhTreeHelper.posInArray(kNNCenter, node.getPostLen());
			BSTEntry be = node.getEntry(divePos, null);
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

			if (node.getEntryCount() <= 2) {
				iterateUnsorted(divePos, minimumPermutations);
			} else {
				iterateSortedBuffer(divePos, prefix, minimumPermutations);
			}
		}
		
		private void iterateUnsorted(long divePos, int minimumPermutations) {
			niIterator.reset(node.getRoot());
			while (niIterator.hasNextEntry()) {
				BSTEntry be = niIterator.nextEntry();
				if (Long.bitCount(be.getKey() ^ divePos) >= minimumPermutations) {
					checkEntry(be);
				}
			}
		}
		
		private void iterateSortedBuffer(long divePos, long[] prefix, int minimumPermutations) {
			if (buffer == null || buffer.length < node.getEntryCount()) {
				buffer = new BSTEntry[node.getEntryCount()];
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
			while (start < bufferSize && Long.bitCount(buffer[start].getKey() ^ divePos) < minimumPermutations) {
				start++;
			}
		
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
					nMaxPermutatedBits = dist.knnCalcMaximumPermutationCount(distances, knownMaxDist);
				}
				if (Long.bitCount(buffer[i].getKey() ^ divePos) > nMaxPermutatedBits) {
					break;
				}
				checkEntry(buffer[i]);
			}
		}
	}

	
	NodeIteratorListReuseKNN(int dims, PhQueryKnnMbbPPList<T>.KnnResultList results, PhFilterDistance checker) {
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

	private static class BSTEComp implements Comparator<BSTEntry> {
		long key;
	    @Override
		public int compare(BSTEntry a, BSTEntry b) {
	    	int h1 = Long.bitCount(a.getKey() ^ key);
	    	int h2 = Long.bitCount(b.getKey() ^ key);
            return h1 - h2;
	    }
	}
}