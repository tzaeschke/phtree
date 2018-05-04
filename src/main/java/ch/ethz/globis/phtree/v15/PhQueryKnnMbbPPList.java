/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v15;

import java.util.Arrays;
import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhEntryDist;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhTree.PhExtent;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;

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
	private int nMin;
	private PhTree15<T> pht;
	private PhDistance distance;
	private int currentPos = -1;
	private final long[] mbbMin;
	private final long[] mbbMax;
	private final NodeIteratorListReuse<T, PhEntryDist<T>> iter;
	private final PhFilterDistance checker;
	private final KnnResultList results; 
	private final NodeIteratorFullNoGC<T> ni;
	private final long[] niBuffer; 


	/**
	 * Create a new kNN/NNS search instance.
	 * @param pht the parent tree
	 */
	public PhQueryKnnMbbPPList(PhTree15<T> pht) {
		this.dims = pht.getDim();
		this.mbbMin = new long[dims];
		this.mbbMax = new long[dims];
		this.pht = pht;
		this.checker = new PhFilterDistance();
		this.results = new KnnResultList(dims);
		this.iter = new NodeIteratorListReuse<>(dims, results);
		this.niBuffer = new long[dims];
		ni = new NodeIteratorFullNoGC<>(dims, niBuffer);
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
		this.nMin = nMin;
		
		if (nMin > 0) {
			results.reset(nMin, center);
			nearestNeighbourBinarySearch(center, nMin);
		} else {
			results.clear();
		}

		currentPos = 0;
		return this;
	}

	private double estimateDistance(long[] key, Node node) {
		Object v = node.doIfMatching(key, true, null, null, null, pht);
		if (v == null) {
			//Okay, there is no perfect match:
			//just perform a query on the current node and return the first value that we find.
			return getDistanceToClosest(key, node);
		}
		if (v instanceof Node) {
			return estimateDistance(key, (Node) v);
		}

		//Okay, we have a perfect match!
		//But we should return it only if nMin=1, otherwise our search area is too small.
		if (nMin == 1) {
			//Never return closest key if we look for nMin>1 keys!
			//now return the key, even if it may not be an exact match (we don't check)
			return 0.0;
		}
		//Okay just perform a query on the current node and return the first value that we find.
		return getDistanceToClosest(key, node);
	}

	private double getDistanceToClosest(long[] key, Node node) {
		//This is a hack.
		//calcDiagonal() is problematic when applied to IEEE encoded
		//floating point values, especially when it the node is at the
		//level of the exponent bits.
		if (node.getPostLen() <= 52) { 
			return calcDiagonal(key, node);
		}

		//First, get correct prefix.
		long mask = (-1L) << (node.getPostLen()+1);
		for (int i = 0; i < dims; i++) {
			niBuffer[i] = key[i] & mask;
		}
		
		//This allows writing the result directly into 'ret'
		PhEntry<T> result = new PhEntry<>(niBuffer, null);
		ni.init(node, null);
		while (ni.increment(result)) {
			if (result.hasNodeInternal()) {
				//traverse sub node
				ni.init((Node) result.getNodeInternal(), null);
			} else {
				//Never return closest key if we look for nMin>1 keys!
				if (nMin > 1 && Arrays.equals(key, result.getKey())) {
					//Never return a perfect match if we look for nMin>1 keys!
					//otherwise the distance is too small.
					//This check should be cheap and will not be executed more than once anyway.
					continue;
				}
				double dist = distance.dist(key, niBuffer);
				//Problem: for rectangles with EDGE distance, the distance
				//may calculate to '0.0', which will not yield a useful search MBB
				//(unless there are more than 'k' rectangles with distance 0).
				if (dist > 0) {
					return dist;
				} else {
					return calcDiagonal(key, node);
				}
			}
		}
		throw new IllegalStateException();
	}

	private double calcDiagonal(long[] key, Node node) {
		//First, get min/max.
		long[] min = new long[dims];
		long[] max = new long[dims];
		long mask = (-1L) << (node.getPostLen()+1);
		long mask1111 = ~mask;
		for (int i = 0; i < dims; i++) {
			min[i] = key[i] & mask;
			max[i] = (key[i] & mask) | mask1111;
		}
		
		//We calculate the diagonal of the node
		double diagonal = distance.dist(min, max);
		if (diagonal <= 0 || Double.isNaN(diagonal)) {
			return 1;
		}
		//calc radius of inner circle
		return diagonal*0.5;// /Math.sqrt(dims);
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
		double estimatedDist = estimateDistance(val, pht.getRoot());

		while (!findNeighbours(estimatedDist, nMin, val)) {
			estimatedDist *= 10;
		}
	}

	private final boolean findNeighbours(double maxDist, int nMin, long[] val) {
		results.maxDistance = maxDist;
		checker.set(val, distance, maxDist);
		distance.toMBB(maxDist, val, mbbMin, mbbMax);
		iter.resetAndRun(pht.getRoot(), mbbMin, mbbMax, Integer.MAX_VALUE);

		if (results.size() < nMin) {
			//too small, we need a bigger range
			return false;
		}
		return true;
	}


	private class KnnResultList extends PhResultList<T, PhEntryDist<T>> {
		private PhEntryDist<T>[] data;
		private PhEntryDist<T> free;
		private double[] distData;
		private int size = 0;
		//Maximum value below which new values will be accepted.
		//Rule: maxD=data[max] || maxD=Double.MAX_VALUE
		private double maxDistance = Double.MAX_VALUE;
		private final int dims;
		private long[] center;
		
		KnnResultList(int dims) {
			this.free = new PhEntryDist<>(new long[dims], null, -1);
			this.dims = dims;
		}
		
		private PhEntryDist<T> createEntry() {
			return new PhEntryDist<>(new long[dims], null, 1);
		}
		
		@SuppressWarnings("unchecked")
		void reset(int newSize, long[] center) {
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
			//TODO we don;t really need PhEntryDist anymore, do we? Maybe for external access of d?
			PhEntryDist<T> e = (PhEntryDist<T>) entry;
			double d = distance.dist(center, e.getKey());
			e.setDist( d );
			if (d < maxDistance || (d <= maxDistance && size < data.length)) {
				boolean needsAdjustment = internalAdd(e);
				
				if (needsAdjustment) {
					double oldMaxD = maxDistance;
					maxDistance = distData[size-1];
					checker.setMaxDist(maxDistance);
					//This is an optimisation, seem to work for example for 10M/K3/CUBE
					//TODO we should compare with the distance when this was last changed!
					//TODO THIS work best with comparing to the CURRENT previous value, instead
					//     of using the one where we performed the last resize!!!!????
					//TODO 6 is chosen arbitrary, I only tested k3 and k10 with 10M-CUBE
					
					//TODO WHAT!!!?????? For nMin=1 we should not even get here!!!! (special case, see main method)
					if (dims < 6 || data.length > 1 || oldMaxD/maxDistance > 1.1) {
						//adjust minimum bounding box.
						distance.toMBB(maxDistance, center, mbbMin, mbbMax);
						//prevMaxDistance = oldMaxD;
					}
					//Any call to this function is triggered by entry that ended up in the
					//candidate list. 
					//Therefore, none of its parent nodes can be fully excluded by the new MBB.
					//At best, we can exclude part of a parent if the search range slips
					//'below' the center-point of a node in at least one dimension. 
					//We basically need to compare each dimension, in which case we could 
					//as well recalculate the bit-range.
				}
				if (free == e) {
					free = createEntry();
				}
			} else {
				free = e;
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
			return distance.dist(center, buf) <= maxDistance;
			//return checker.isValid(bitsToIgnore, prefix);
//			return true;
		}
	}
	
}
