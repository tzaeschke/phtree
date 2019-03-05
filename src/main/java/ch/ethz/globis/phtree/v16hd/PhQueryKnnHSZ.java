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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhEntryDist;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.PhTreeHelperHD;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorAll;

/**
 * kNN query implementation that uses preprocessors and distance functions.
 * 
 * Implementation after Hjaltason and Samet (with some deviations: no MinDist or MaxDist used).
 * G. R. Hjaltason and H. Samet., "Distance browsing in spatial databases.", ACM TODS 24(2):265--318. 1999
 *
 * Additional modification by using HC-Address for estimating distance, actual distance is 
 * calculated only when required. 
 * 
 * @param <T> value type
 */
public class PhQueryKnnHSZ<T> implements PhKnnQuery<T> {

	private static final PhDEComp COMP = new PhDEComp();

	private final int dims;
	private PhTree16HD<T> pht;
	private PhDistance distance;
	private long[] center;
	private final ArrayList<PhEntryDist<T>> results = new ArrayList<>(); 
	private final ArrayList<PhEntryDist<Object>> pool = new ArrayList<>(); 
	private final PriorityQueue<PhEntryDist<Object>> queueEst = new PriorityQueue<>(COMP);
	private final PriorityQueue<PhEntryDist<Object>> queueLx = new PriorityQueue<>(COMP);
	private final BSTIteratorAll iterNode = new BSTIteratorAll();
	private Iterator<PhEntryDist<T>> iterResult;
	//Field, to reduce garbage collection. Gets reset for every loop in the query. 
	private final long[] relativeQuadrantOfCenter;


	/**
	 * Create a new kNN/NNS search instance.
	 * @param pht the parent tree
	 */
	public PhQueryKnnHSZ(PhTree16HD<T> pht) {
		this.dims = pht.getDim();
		this.pht = pht;
		this.relativeQuadrantOfCenter = BitsHD.newArray(dims);
	}

	@Override
	public long[] nextKey() {
		return nextEntryReuse().getKey();
	}

	@Override
	public T nextValue() {
		return nextEntryReuse().getValue();
	}

	@Override
	public PhEntryDist<T> nextEntry() {
		return iterResult.next();
	} 

	@Override
	public PhEntryDist<T> nextEntryReuse() {
		//Reusing happens only via pooling
		return iterResult.next();
	}

	@Override
	public boolean hasNext() {
		return iterResult.hasNext();
	}

	@Override
	public T next() {
		return nextValue();
	}

	@Override
	public PhKnnQuery<T> reset(int nMin, PhDistance dist, long... center) {
		this.distance = dist == null ? this.distance : dist;
		this.center = center;
		
		//TODO pool entries??/
		this.queueEst.clear();
		this.queueLx.clear();
		this.results.clear();
		
		
		if (nMin <= 0 || pht.size() == 0) {
			iterResult = Collections.<PhEntryDist<T>>emptyList().iterator();
			return this;
		}
		
		//Initialize queue
		PhEntryDist<Object> rootE = createEntry(new long[dims], pht.getRoot(), 0);
		this.queueLx.add(rootE);
		
		search(nMin);
		iterResult = results.iterator();
		
		return this;
	}

		
	/**
	 * Ensure a valid candidate at top of LxQueue.
	 * @param k number of results
	 */
	private void validateLxQueue(int k) {
		//Check with estimated distance. Ensure that there is no candidate in queueEst that may be
		//closer that the first candidate in queueLx.
		while (!queueEst.isEmpty() && (queueLx.isEmpty() || queueEst.peek().dist() <= queueLx.peek().dist())) {
			//move to queueLx
			PhEntryDist<Object> entry = queueEst.poll();
			entry.setDist(calcLxDistance(entry));
			queueLx.add( entry ); 
		}
	}
	
	@SuppressWarnings("unchecked")
	private void search(int k) {
		while (!queueLx.isEmpty() || !queueEst.isEmpty()) {

			//ensure that 1st LX entry is valid
			validateLxQueue(k);

			//process 1st entry
			PhEntryDist<Object> candidate = queueLx.poll();
			
			Object val = candidate.getValue();
			if (!(val instanceof Node)) {
				//data entry
				results.add((PhEntryDist<T>) candidate);
				if (results.size() >= k) {
					return;
				}
			} else {
				//inner node
				Node node = (Node)val;
				iterNode.reset(node.getRoot());
				if (node.getEntryCount() > 4) {
					//Use estimated distances

					//current minimum (after candidate is removed)
					double currentMin = queueLx.isEmpty() ? Double.POSITIVE_INFINITY : queueLx.peek().dist();
					
					//Calculate how many permutations are at most possible -> distances
					double[] distances = new double[dims];
					distance.knnCalcDistances(center, candidate.getKey(), node.getPostLen() + 1, distances);

					if (candidate.dist() <= 0) {
						PhTreeHelperHD.posInArrayHD(center, node.getPostLen(), relativeQuadrantOfCenter);
					} else {
						calcRelativeQuadrants(node, candidate.getKey(), relativeQuadrantOfCenter);
					}

					while (iterNode.hasNextEntry()) {
						BSTEntry e2 = iterNode.nextEntry();
						double d = estimateDist(e2, relativeQuadrantOfCenter, distances);

						if (d <= currentMin) {
							//add directly to Lx queue
							PhEntryDist<Object> newLx = createLxEntry(e2);
							queueLx.add(newLx);
							currentMin = currentMin < newLx.dist() ? currentMin : newLx.dist();
						} else {
							queueEst.add( createEntry(e2.getKdKey(), e2.getValue(), d) );
						}
					}
				} else {
					//Add directly to main queue
					while (iterNode.hasNextEntry()) {
						BSTEntry e2 = iterNode.nextEntry();
						PhEntryDist<Object> subE = createLxEntry(e2);
						queueLx.add( subE ); 
					}
				}
				pool.add(candidate);
			}				
		}
	}
	
	
	private double calcLxDistance(PhEntryDist<Object> e) {
		double d;
		if (e.getValue() instanceof Node) {
			Node sub = (Node) e.getValue();
			d = distToNode(e.getKey(), sub.getPostLen() + 1);
		} else {
			d = distance.dist(center, e.getKey());
		}
		return d;
	}
	
	private PhEntryDist<Object> createLxEntry(BSTEntry e) {
		//calculate distance
		double d;
		if (e.getValue() instanceof Node) {
			Node sub = (Node) e.getValue();
			d = distToNode(e.getKdKey(), sub.getPostLen() + 1);
		} else {
			d = distance.dist(center, e.getKdKey());
		}

		//create and return entry
		return createEntry(e.getKdKey(), e.getValue(), d);
	}
	
	
	private PhEntryDist<Object> createEntry(long[] key, Object val, double dist) {
		if (pool.isEmpty()) {
			return new PhEntryDist<>(key, val, dist);
		}
		PhEntryDist<Object> e = pool.remove(pool.size() - 1);
		e.setKeyInternal(key);
		e.set(val, dist);
		return e;
	}

	
	private static final double EPS = 0.999999999;
	
	private double estimateDist(BSTEntry e2, long[] centerQuadrant, double[] distances) {
		int permCount = BitsHD.xorBitCount(centerQuadrant, e2.getKey());
		return permCount == 0 ? 0 : distances[permCount-1]*EPS;
	}
	

	private void calcRelativeQuadrants(Node node, long[] prefix, long[] relativeKNNpos) {
		
		//calc 'divePos', ie. whether prefix is below/above 'center' for each dimension
		long[] kNNCenter = center;
    	
        //We want to get the center, so no +1 for postLen
        long prefixMask = (-1L) << node.getPostLen()+1;
        long prefixBit = 1L << node.getPostLen();
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
 	}
	
	
	private double distToNode(long[] prefix, int bitsToIgnore) {
		long maskMin = (-1L) << bitsToIgnore;
		long maskMax = ~maskMin;
		long[] buf = new long[prefix.length];
		for (int i = 0; i < buf.length; i++) {
			//if v is outside the node, return distance to closest edge,
			//otherwise return v itself (assume possible distance=0)
			long min = prefix[i] & maskMin;
			long max = prefix[i] | maskMax;
			buf[i] = min > center[i] ? min : (max < center[i] ? max : center[i]); 
		}

		return distance.dist(center, buf);
	}

	
	private static class PhDEComp implements Comparator<PhEntryDist<?>> {
	    @Override
		public int compare(PhEntryDist<?> a, PhEntryDist<?> b) {
	    	double d1 = a.dist();
	    	double d2 = b.dist();
            return d1 < d2 ? -1 : d1 > d2 ? 1 : 0;
	    }
	}

}
