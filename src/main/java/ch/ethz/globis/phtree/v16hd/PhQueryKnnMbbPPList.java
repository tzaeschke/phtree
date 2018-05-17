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
import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhEntryDist;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhTree.PhExtent;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;

/**
 * kNN query implementation that uses preprocessors and distance functions.
 * 
 * The algorithm works as follows:
 * 
 * First we drill down in the tree to find an entry that is 'close' to
 * desired center of the kNN query. A 'close' entry is one that is in the same node
 * where the center would be, or in one of its sub-nodes. Note that we do not use
 * the center-point itself in case it exists in the tree. The result of the first step is 
 * a guess at the initial search distance (this would be 0 if we used the center itself). 
 * 
 * We then use a combination of rectangle query (center +/- initDistance) and distance-query. 
 * The query traverses only nodes and values that lie in the query rectangle and that satisfy the
 * distance requirement (circular distance when using euclidean space).
 * 
 * While iterating through the query result, we regularly sort the returned entries 
 * to see which distance would suffice to return 'k' result. If the new distance is smaller,
 * we adjust the query rectangle and the distance function before continuing the
 * query. As a result, when the query returns no more entries, we are guaranteed to
 * have all closest neighbours.
 * 
 * The only thing that can go wrong is that we may get less than 'k' neighbours if the
 * initial distance was too small. In that case we multiply the initial distance by 10
 * and run the algorithm again. Not that multiplying the distance by 10 means a 10^k fold
 * increase in the search volume. 
 *   
 *   
 * WARNING:
 * The query rectangle is calculated using the PhDistance.toMBB() method.
 * The implementation of this method may not work with non-euclidean spaces! 
 * 
 * @param <T> value type
 */
public class PhQueryKnnMbbPPList<T> implements PhKnnQuery<T> {

	private final int dims;
	private PhTree16HD<T> pht;
	private PhDistance distance;
	private int currentPos = -1;
	private final NodeIteratorListReuseKNN<T, PhEntryDist<T>> iter;
	private final PhFilterDistance checker;
	private final KnnResultList results; 


	/**
	 * Create a new kNN/NNS search instance.
	 * @param pht the parent tree
	 */
	public PhQueryKnnMbbPPList(PhTree16HD<T> pht) {
		this.dims = pht.getDim();
		this.pht = pht;
		this.checker = new PhFilterDistance();
		this.results = new KnnResultList(dims);
		this.iter = new NodeIteratorListReuseKNN<>(dims, results, checker);
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
		return new PhEntryDist<>(nextEntryReuse());
	} 

	@Override
	public PhEntryDist<T> nextEntryReuse() {
		if (currentPos >= results.size()) {
			throw new NoSuchElementException();
		}
		return results.get(currentPos++);
	}

	@Override
	public boolean hasNext() {
		return currentPos < results.size();
	}

	@Override
	public T next() {
		return nextValue();
	}

	@Override
	public PhKnnQuery<T> reset(int nMin, PhDistance dist, long... center) {
		this.distance = dist == null ? this.distance : dist;
		
		if (nMin > 0) {
			results.reset(nMin, center);
			nearestNeighbourBinarySearch(center, nMin);
		} else {
			results.clear();
		}

		currentPos = 0;
		return this;
	}


	/**
	 * This approach applies binary search to queries.
	 * It start with a query that covers the whole tree. Then whenever it finds an entry (the first)
	 * it discards the query and starts a smaller one with half the distance to the search-point.
	 * This effectively reduces the volume by 2^k.
	 * Once a query returns no result, it uses the previous query to traverse all results
	 * and find the nearest result.
	 * As an intermediate step, it may INCREASE the query size until a non-empty query appears.
	 * Then it could decrease again, like a true binary search.
	 * 
	 * When looking for nMin > 1, one could search for queries with at least nMin results...
	 * 
	 * @param val
	 * @param nMin
	 */
	private void nearestNeighbourBinarySearch(long[] val, int nMin) {
		//special case with minDist = 0
		if (nMin == 1 && pht.contains(val)) {
			PhEntryDist<T> e = results.getFreeEntry();
			e.setCopyKey(val, pht.get(val), 0);
			checker.set(val, distance, Double.MAX_VALUE);
			results.phOffer(e);
			return;
		}

		//special case with size() <= nMin
		if (pht.size() <= nMin) {
			PhExtent<T> itEx = pht.queryExtent();
			while (itEx.hasNext()) {
				PhEntry<T> e = itEx.nextEntryReuse();
				PhEntryDist<T> e2 = results.getFreeEntry();
				e2.set(e, distance.dist(val, e.getKey()));
				checker.set(val, distance, Double.MAX_VALUE);
				results.phOffer(e2);
			}
			return;
		}

		//estimate initial distance
		findNeighbours(Double.POSITIVE_INFINITY, val);
	}

	private final void findNeighbours(double maxDist, long[] val) {
		results.maxDistance = maxDist;
		checker.set(val, distance, maxDist);
		iter.resetAndRun(pht.getRoot());
	}


	public class KnnResultList extends PhResultList<T, PhEntryDist<T>> {
		private PhEntryDist<T>[] data;
		private PhEntryDist<T> free;
		private double[] distData;
		private int size = 0;
		//Maximum value below which new values will be accepted.
		//Rule: maxD=data[max] || maxD=Double.MAX_VALUE
		private double maxDistance = Double.MAX_VALUE;
		private final int dims;
		private long[] center;
		private boolean initialDive;
		
		KnnResultList(int dims) {
			this.free = new PhEntryDist<>(new long[dims], null, -1);
			this.dims = dims;
		}
		
		private PhEntryDist<T> createEntry() {
			return new PhEntryDist<>(new long[dims], null, 1);
		}
		
		@SuppressWarnings("unchecked")
		void reset(int newSize, long[] center) {
			initialDive = true;
			size = 0;
			this.center = center;
			maxDistance = Double.MAX_VALUE;
			if (data == null) {
				data = new PhEntryDist[newSize];
				distData = new double[newSize];
				for (int i = 0; i < data.length; i++) {
					data[i] = createEntry();
				}
			}
			if (newSize != data.length) {
				int len = data.length;
				data = Arrays.copyOf(data, newSize);
				distData = new double[newSize];
				for (int i = len; i < newSize; i++) {
					data[i] = createEntry();
				}
			}
		}
		
		PhEntryDist<T> getFreeEntry() {
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
		void phOffer(PhEntry<T> entry) {
			PhEntryDist<T> e = (PhEntryDist<T>) entry;
			double d = distance.dist(center, e.getKey(), maxDistance);
			e.setDist( d );
			if (d < maxDistance || (d <= maxDistance && size < data.length)) {
				internalPreAdd(e);
			} else {
				free = e;
			}
		}
		
		@SuppressWarnings("unchecked")
		void phOffer(BSTEntry candidate) {
			double d = distance.dist(center, candidate.getKdKey(), maxDistance);
			if (d < maxDistance || (d <= maxDistance && size < data.length)) {
				PhEntryDist<T> e = results.phGetTempEntry();
				e.setKeyInternal(candidate.getKdKey());
				e.setValueInternal((T) candidate.getValue());
				e.setDist( d );
				internalPreAdd(e);
			}
		}
		
		private void internalPreAdd(PhEntryDist<T> e) {
			boolean needsAdjustment = internalAdd(e);
			if (needsAdjustment) {
				maxDistance = distData[size-1];
				checker.setMaxDist(maxDistance);
			}
			if (free == e) {
				free = createEntry();
			}
		}
		
		private boolean internalAdd(PhEntryDist<T> e) {
			if (size == 0) {
				free = data[size];
				data[size] = e;
				distData[size] = e.dist();
				size++;
				if (size == data.length) {
					return true;
				}
				return false;
			}
			if (e.dist() > distData[size-1] && size == distData.length) {
				//this should never happen.
				throw new UnsupportedOperationException(e.dist() + " > " + distData[size-1]);
			}

			if (size == data.length) {
				//We use -1 to allow using the same copy loop when inserting in the beginning
				for (int i = size-1; i >= -1; i--) {
					if (i==-1 || distData[i] < e.dist()) {
						//purge and reuse last entry
						free = data[size-1];
						//insert after i
						for (int j = size-2; j >= i+1; j--) {
							data[j+1] = data[j];
							distData[j+1] = distData[j];
						}
						data[i+1] = e;
						distData[i+1] = e.dist();
						return true;
					}
				}
			} else {
				for (int i = size-1; i >= -1; i--) {
					if (i==-1 || distData[i] < e.dist()) {
						//purge and reuse entry after last
						free = data[size];
						//insert after i
						for (int j = size-1; j >= i+1; j--) {
							data[j+1] = data[j];
							distData[j+1] = distData[j];
						}
						data[i+1] = e;
						distData[i+1] = e.dist();
						size++;
						if (size == data.length) {
							return true;
						}
						return false;
					}
				}
			}
			
			//This should never happen
			throw new IllegalStateException();
		}

		@Override
		public int size() {
			return size;
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		@Override
		public void clear() {
			size = 0;
		}

		@Override
		public PhEntryDist<T> get(int index) {
			if (index < 0 || index >= size) {
				throw new NoSuchElementException();
			}
			return data[index];
		}

		@Override
		PhEntryDist<T> phGetTempEntry() {
			return free;
		}

		@Override
		boolean phIsPrefixValid(long[] prefix, int bitsToIgnore) {
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
					
			//TODO if buf==center -> no need to check distance 
			//TODO return true for dim < 3????
			return distance.dist(center, buf, maxDistance) <= maxDistance;
			//return checker.isValid(bitsToIgnore, prefix);
//			return true;
		}

		/**
		 * During the initial 'dive', the algorithm attempts to find the center point in the tree.
		 * Once the succeeds or fails, the initial dive is over, but we are in a node full of good
		 * candidates and may even have the perfect first candidate (the center point).
		 * 
		 * @return True during the initial dive.
		 */
		public boolean isInitialDive() {
			return initialDive;
		}
		
		public void stopInitialDive() {
			initialDive = false;
		}
	}
	
}
