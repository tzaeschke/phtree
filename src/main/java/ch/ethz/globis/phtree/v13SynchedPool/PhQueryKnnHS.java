/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v13SynchedPool;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhEntryDist;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;

import java.util.*;

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
	private PhTree13SP<T> pht;
	private PhDistance distance;
	private long[] center;
	private final ArrayList<PhEntryDist<T>> results = new ArrayList<>();
	private final ArrayList<PhEntryDist<T>> pool = new ArrayList<>();
	private final PriorityQueue<PhEntryDist<T>> queue = new PriorityQueue<>(COMP);
	private final NodeIteratorFullToList<T> iterNode;
	private Iterator<PhEntryDist<T>> iterResult;
	private final KnnResultList<T> candidateBuffer;



	/**
	 * Create a new kNN/NNS search instance.
	 * @param pht the parent tree
	 */
	public PhQueryKnnHS(PhTree13SP<T> pht) {
		this.dims = pht.getDim();
		this.pht = pht;
		//this.iterNode = new NodeIteratorFullNoGC<>(dims, new long[dims]);
		this.candidateBuffer = new KnnResultList<>(dims, pool);
		this.iterNode = new NodeIteratorFullToList<>(dims);
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
		this.queue.clear();
		this.results.clear();


		if (nMin <= 0 || pht.size() == 0) {
			iterResult = Collections.<PhEntryDist<T>>emptyList().iterator();
			return this;
		}

		//Initialize queue
		//d=0 (lies in Node!!!)
		PhEntryDist<T> rootE = createEntry(pool, new long[dims], null, 0);
		rootE.setNodeInternal(pht.getRoot());
		this.queue.add(rootE);

		search(nMin);
		iterResult = results.iterator();

		return this;
	}


	private void search(int k) {
		while (!queue.isEmpty()) {
			PhEntryDist<T> candidate = queue.poll();
			if (!candidate.hasNodeInternal()) {
				//data entry
				results.add(candidate);
				if (results.size() >= k) {
					return;
				}
			} else {
				//inner node
				Node node = (Node) candidate.getNodeInternal();
				candidateBuffer.clear();
				iterNode.init(node, candidateBuffer, candidate.getKey());
				for (int i = 0; i < candidateBuffer.size(); i++) {
					PhEntryDist<T> e2 = candidateBuffer.get(i);
					if (e2.hasNodeInternal()) {
						Node sub = (Node) e2.getNodeInternal();
						double d = distToNode(e2.getKey(), sub.getPostLen() + 1);
						e2.setDist(d);
					} else {
						double d = distance.dist(center, e2.getKey());
						e2.setDist(d);
					}
					queue.add(e2);
				}
				pool.add(candidate);
			}				
		}
	}
	
	
	private static <T> PhEntryDist<T> createEntry(ArrayList<PhEntryDist<T>> pool, 
			long[] key, T val, double dist) {
		if (pool.isEmpty()) {
			return new PhEntryDist<T>(key, val, dist);
		}
		PhEntryDist<T> e = pool.remove(pool.size() - 1);
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

	
	static class KnnResultList<T> extends PhResultList<T, PhEntryDist<T>> {

		private final ArrayList<PhEntryDist<T>> list;
		private PhEntryDist<T> free;
		private final ArrayList<PhEntryDist<T>> pool; 
		
		public KnnResultList(int dims, ArrayList<PhEntryDist<T>> pool) {
			this.list = new ArrayList<>();
			this.pool = pool;
			this.free = createEntry(pool, new long[dims], null, 0);
		}
		
		@Override
		public int size() {
			return list.size();
		}

		@Override
		public void clear() {
			list.clear();
		}

		@Override
		public PhEntryDist<T> get(int index) {
			return list.get(index);
		}

		@Override
		PhEntryDist<T> phGetTempEntry() {
			PhEntryDist<T> ret = free;
			free = null;
			return ret;
		}

		@Override
		void phReturnTemp(PhEntry<T> entry) {
			if (free == null) {
				free = (PhEntryDist<T>) entry;
			}
		}

		@Override
		void phOffer(PhEntry<T> e) {
			list.add((PhEntryDist<T>) e);
			free = createEntry(pool, new long[e.getKey().length], null, 0);
		}

		@Override
		boolean phIsPrefixValid(long[] prefix, int bitsToIgnore) {
			return true;
		}
	}
	

	
}
