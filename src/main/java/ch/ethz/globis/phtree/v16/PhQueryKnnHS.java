/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhEntryDist;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.v16.Node.BSTEntry;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorAll;

/**
 * kNN query implementation that uses preprocessors and distance functions.
 * 
 * Implementation after Hjaltason and Samet (with some deviations: no MinDist or MaxDist used).
 * G. R. Hjaltason and H. Samet., "Distance browsing in spatial databases.", ACM TODS 24(2):265--318. 1999
 *
 * @param <T> value type
 */
public class PhQueryKnnHS<T> implements PhKnnQuery<T> {

	private static final PhDEComp COMP = new PhDEComp();
	
	private final int dims;
	private PhTree16<T> pht;
	private PhDistance distance;
	private long[] center;
	private final PhFilterDistance checker;
	private final ArrayList<PhEntryDist<T>> results = new ArrayList<>(); 
	private final ArrayList<PhEntryDist<Object>> pool = new ArrayList<>(); 
	private final PriorityQueue<PhEntryDist<Object>> queue = new PriorityQueue<>(COMP);
	private final BSTIteratorAll iterNode = new BSTIteratorAll();
	private Iterator<PhEntryDist<T>> iterResult;


	/**
	 * Create a new kNN/NNS search instance.
	 * @param pht the parent tree
	 */
	public PhQueryKnnHS(PhTree16<T> pht) {
		this.dims = pht.getDim();
		this.pht = pht;
		this.checker = new PhFilterDistance();
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
		//TODO
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
		this.queue.clear();
		this.results.clear();
		
		
		if (nMin <= 0 || pht.size() == 0) {
			iterResult = Collections.<PhEntryDist<T>>emptyList().iterator();
			return this;
		}
		
		//Initialize queue
		long[] rootKey = new long[dims];
		double d = this.distance.dist(rootKey, center);
		//TODO use d=0 (lies in Node!!!)
		PhEntryDist<Object> rootE = new PhEntryDist<>(rootKey, pht.getRoot(), d);
		this.queue.add(rootE);
		
		search(nMin);
		iterResult = results.iterator();
		
		return this;
	}

	
	private void search(int k) {
		while (!queue.isEmpty()) {
			PhEntryDist<Object> candidate = queue.poll();
			Object o = candidate.getValue();
			if (!(o instanceof Node)) {
				//data entry
//TODO				if (checker == null || checker.isValid(candidate.getKey())) {
					results.add((PhEntryDist<T>) candidate);
					if (results.size() >= k) {
						return;
					}
//				}
			} else {
				//inner node
				Node node = (Node)o;
				iterNode.reset(node.getRoot());
				while (iterNode.hasNextEntry()) {
					BSTEntry e2 = iterNode.nextEntry();
					if (e2.getValue() instanceof Node) {
						Node sub = (Node) e2.getValue();
						double d = distToNode(e2.getKdKey(), sub.getPostLen() + 1);
						queue.add(createEntry(e2.getKdKey(), e2.getValue(), d));
					} else {
						double d = distance.dist(center, e2.getKdKey());
						queue.add(createEntry(e2.getKdKey(), e2.getValue(), d));
					}
				}
//TODO				pool.add(candidate);
			}				
		}
	}
	
	
	private PhEntryDist<Object> createEntry(long[] key, Object val, double dist) {
		if (pool.isEmpty()) {
			return new PhEntryDist<Object>(key, val, dist);
		}
		PhEntryDist<Object> e = pool.remove(pool.size() - 1);
		e.setKeyInternal(key);
		e.set(val, dist);
		return e;
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
