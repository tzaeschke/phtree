/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.util.List;

import ch.ethz.globis.phtree.PhTree.PhExtent;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.PhTree.PhQuery;
import ch.ethz.globis.phtree.pre.PreProcessorPointF;
import ch.ethz.globis.phtree.util.PhIteratorBase;
import ch.ethz.globis.phtree.util.PhMapper;
import ch.ethz.globis.phtree.util.PhMapperK;
import ch.ethz.globis.phtree.util.PhTreeStats;

/**
 * k-dimensional index (quad-/oct-/n-tree).
 * Supports key/value pairs.
 *
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 * @param <T> The value type of the tree 
 *
 */
public class PhTreeF<T> {

	private final PhTree<T> pht;
	private final PreProcessorPointF pre;

	protected PhTreeF(int dim, PreProcessorPointF pre) {
		this.pht = PhTree.create(dim);
		this.pre = pre;
	}

	protected PhTreeF(PhTree<T> tree) {
		this.pht = tree;
		this.pre = new PreProcessorPointF.IEEE();
	}

	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 * @return PhTreeF
	 * @param <T> value type of the tree
	 */
	public static <T> PhTreeF<T> create(int dim) {
		return new PhTreeF<>(dim, new PreProcessorPointF.IEEE());
	}

	/**
	 * Create a new tree with the specified number of dimensions and
	 * a custom preprocessor.
	 * 
	 * @param dim number of dimensions
	 * @param pre The preprocessor to be used
	 * @return PhTreeF
	 * @param <T> value type of the tree
	 */
	public static <T> PhTreeF<T> create(int dim, PreProcessorPointF pre) {
		return new PhTreeF<>(dim, pre);
	}

	/**
	 * Create a new PhTreeF as a wrapper around an existing PhTree.
	 * 
	 * @param tree another tree
	 * @return PhTreeF
	 * @param <T> value type of the tree
	 */
	public static <T> PhTreeF<T> wrap(PhTree<T> tree) {
		return new PhTreeF<>(tree);
	}

	public int size() {
		return pht.size();
	}

	/**
	 * Insert an entry associated with a k dimensional key.
	 * @param key the key to store the value to store
	 * @param value the value
	 * @return the previously associated value or {@code null} if the key was found
	 */
	public T put(double[] key, T value) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.put(lKey, value);
	}

	public boolean contains(double ... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.contains(lKey);
	}

	public T get(double ... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.get(lKey);
	}


	/**
	 * Remove the entry associated with a k dimensional key.
	 * @param key the key to remove
	 * @return the associated value or {@code null} if the key was found
	 */
	public T remove(double... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		return pht.remove(lKey);
	}

	public PhExtentF<T> queryExtent() {
		return new PhExtentF<>(pht.queryExtent(), pht.getDim(), pre);
	}


	/**
	 * Performs a rectangular window query. The parameters are the min and max keys which 
	 * contain the minimum respectively the maximum keys in every dimension.
	 * @param min Minimum values
	 * @param max Maximum values
	 * @return Result iterator.
	 */
	public PhQueryF<T> query(double[] min, double[] max) {
		long[] lMin = new long[min.length];
		long[] lMax = new long[max.length];
		pre.pre(min, lMin);
		pre.pre(max, lMax);
		return new PhQueryF<>(pht.query(lMin, lMax), pht.getDim(), pre);
	}

	/**
	 * Find all entries within a given distance from a center point.
	 * @param dist Maximum distance
	 * @param center Center point
	 * @return All entries with at most distance `dist` from `center`.
	 */
	public PhRangeQueryF<T> rangeQuery(double dist, double...center) {
		return rangeQuery(dist, PhDistanceF.THIS, center);
	}

	/**
	 * Find all entries within a given distance from a center point.
	 * @param dist Maximum distance
	 * @param optionalDist Distance function, optional, can be `null`.
	 * @param center Center point
	 * @return All entries with at most distance `dist` from `center`.
	 */
	public PhRangeQueryF<T> rangeQuery(double dist, PhDistance optionalDist, double...center) {
		if (optionalDist == null) {
			optionalDist = PhDistanceF.THIS; 
		}
		long[] lKey = new long[center.length];
		pre.pre(center, lKey);
		PhRangeQuery<T> iter = pht.rangeQuery(dist, optionalDist, lKey);
		return new PhRangeQueryF<>(iter, pht, pre);
	}

	public int getDim() {
		return pht.getDim();
	}

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param key the center point
	 * @return List of neighbours.
	 */
	public PhKnnQueryF<T> nearestNeighbour(int nMin, double... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		PhKnnQuery<T> iter = pht.nearestNeighbour(nMin, PhDistanceF.THIS, null, lKey);
		return new PhKnnQueryF<>(iter, pht.getDim(), pre);
	}

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param dist Distance function. Note that the distance function should be compatible
	 * with the preprocessor of the tree.
	 * @param key the center point
	 * @return KNN query iterator.
	 */
	public PhKnnQueryF<T> nearestNeighbour(int nMin, PhDistance dist, double... key) {
		long[] lKey = new long[key.length];
		pre.pre(key, lKey);
		PhKnnQuery<T> iter = pht.nearestNeighbour(nMin, dist, null, lKey);
		return new PhKnnQueryF<>(iter, pht.getDim(), pre);
	}

	public static class PhIteratorF<T> 
	implements PhIteratorBase<T, PhEntryF<T>> {
		private final PhIteratorBase<T, ? extends PhEntry<T>> iter;
		protected final PreProcessorPointF pre;
		private final int dims;
		private final PhEntryF<T> buffer;

		protected PhIteratorF(PhIteratorBase<T, ? extends PhEntry<T>> iter, 
				int dims, PreProcessorPointF pre) {
			this.iter = iter;
			this.pre = pre;
			this.dims = dims;
			this.buffer = new PhEntryF<>(new double[dims], null);
		}

		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public T next() {
			return nextValue();
		}

		@Override
		public PhEntryF<T> nextEntry() {
			double[] d = new double[dims];
			PhEntry<T> e = iter.nextEntryReuse();
			pre.post(e.getKey(), d);
			return new PhEntryF<T>(d, e.getValue());
		}

		@Override
		public PhEntryF<T> nextEntryReuse() {
			PhEntry<T> e = iter.nextEntryReuse();
			pre.post(e.getKey(), buffer.getKey());
			buffer.setValue( e.getValue() );
			return buffer;
		}

		public double[] nextKey() {
			double[] d = new double[dims];
			pre.post(iter.nextEntryReuse().getKey(), d);
			return d;
		}

		@Override
		public T nextValue() {
			return iter.nextValue();
		}

		@Override
		public void remove() {
			iter.remove();
		}
	}

	public static class PhExtentF<T> extends PhIteratorF<T> {
		private final PhExtent<T> iter;
		protected PhExtentF(PhExtent<T> iter, int dims, PreProcessorPointF pre) {
			super(iter, dims, pre);
			this.iter = iter;
		}		
		
		public PhExtentF<T> reset() {
			iter.reset();
			return this;
		}
	}
	
	public static class PhQueryF<T> extends PhIteratorF<T> {
		private final long[] lMin;
		private final long[] lMax;
		private final PhQuery<T> q;

		protected PhQueryF(PhQuery<T> iter, int dims, PreProcessorPointF pre) {
			super(iter, dims, pre);
			q = iter;
			lMin = new long[dims];
			lMax = new long[dims];
		}

		public void reset(double[] lower, double[] upper) {
			pre.pre(lower, lMin);
			pre.pre(upper, lMax);
			q.reset(lMin, lMax);
		}
	}

	public static class PhKnnQueryF<T> extends PhIteratorF<T> {
		private final long[] lCenter;
		private final PhKnnQuery<T> q;
		private final PhEntryDistF<T> buffer;
		private final int dims;

		protected PhKnnQueryF(PhKnnQuery<T> iter, int dims, PreProcessorPointF pre) {
			super(iter, dims, pre);
			this.dims = dims;
			q = iter;
			lCenter = new long[dims];
			buffer = new PhEntryDistF<>(new double[dims], null, Double.NaN); 
		}

		@Override
		public PhEntryDistF<T> nextEntry() {
			double[] d = new double[dims];
			PhEntryDist<T> e = q.nextEntryReuse();
			pre.post(e.getKey(), d);
			return new PhEntryDistF<>(d, e.getValue(), e.dist());
		}

		@Override
		public PhEntryDistF<T> nextEntryReuse() {
			PhEntryDist<T> e = q.nextEntryReuse();
			pre.post(e.getKey(), buffer.getKey());
			buffer.setValue( e.getValue() );
			return buffer;
		}

		public PhKnnQueryF<T> reset(int nMin, PhDistance dist, double... center) {
			pre.pre(center, lCenter);
			q.reset(nMin, dist, lCenter);
			return this;
		}
	}

	public static class PhRangeQueryF<T> extends PhIteratorF<T> {
		private final long[] lCenter;
		private final PhRangeQuery<T> q;
		private final int dims;

		protected PhRangeQueryF(PhRangeQuery<T> iter, PhTree<T> tree, PreProcessorPointF pre) {
			super(iter, tree.getDim(), pre);
			this.dims = tree.getDim();
			this.q = iter;
			this.lCenter = new long[dims];
		}

		public PhRangeQueryF<T> reset(double range, double... center) {
			pre.pre(center, lCenter);
			q.reset(range, lCenter);
			return this;
		}
	}

	/**
	 * Entry class for Double entries.
	 *
	 * @param <T> value type of the entries
	 */
	public static class PhEntryF<T> {
		protected double[] key;
		protected T value;
		
		/**
		 * @param key the key
		 * @param value the value
		 */
		public PhEntryF(double[] key, T value) {
			this.key = key;
			this.value = value;
		}

		public double[] getKey() {
			return key;
		}

		public T getValue() {
			return value;
		}

		public void setValue(T value) {
			this.value = value;
		}
	}

	/**
	 * Entry class for Double entries with distance information for nearest neighbour queries.
	 *
	 * @param <T> value type of the entries
	 */
	public static class PhEntryDistF<T> extends PhEntryF<T> {
		private double dist;

		public PhEntryDistF(double[] key, T value, double dist) {
			super(key, value);
			this.dist = dist;
		}

		public void set(T value, double dist) {
			this.value = value;
			this.dist = dist;
		}
		
		public double dist() {
			return dist;
		}
	}
	
	/**
	 * Update the key of an entry. Update may fail if the old key does not exist, or if the new
	 * key already exists.
	 * @param oldKey old key
	 * @param newKey new key
	 * @return the value (can be {@code null}) associated with the updated key if the key could be 
	 * updated, otherwise {@code null}.
	 */
	public T update(double[] oldKey, double[] newKey) {
		long[] oldL = new long[oldKey.length];
		long[] newL = new long[newKey.length];
		pre.pre(oldKey, oldL);
		pre.pre(newKey, newL);
		return pht.update(oldL, newL);
	}

	/**
	 * Same as {@link #query(double[], double[])}, except that it returns a list
	 * instead of an iterator. This may be faster for small result sets. 
	 * @param min min values
	 * @param max max values
	 * @return List of query results
	 */
	public List<PhEntryF<T>> queryAll(double[] min, double[] max) {
		return queryAll(min, max, Integer.MAX_VALUE, null,
				e -> new PhEntryF<T>(PhMapperK.toDouble(e.getKey()), e.getValue()));
	}

	/**
	 * Same as {@link PhTreeF#queryAll(double[], double[])}, except that it also accepts
	 * a limit for the result size, a filter and a mapper. 
	 * @param min min key
	 * @param max max key
	 * @param maxResults maximum result count 
	 * @param filter filter object (optional)
	 * @param mapper mapper object (optional)
	 * @return List of query results
	 * @param <R> value type
	 */
	public <R> List<R> queryAll(double[] min, double[] max, int maxResults, 
			PhFilter filter, PhMapper<T, R> mapper) {
		long[] lUpp = new long[min.length];
		long[] lLow = new long[max.length];
		pre.pre(min, lLow);
		pre.pre(max, lUpp);
		return pht.queryAll(lLow, lUpp, maxResults, filter, mapper);
	}

	/**
	 * Clear the tree.
	 */
	public void clear() {
		pht.clear();
	}

	/**
	 * 
	 * @return the internal PhTree that backs this PhTreeF.
	 */
	public PhTree<T> getInternalTree() {
		return pht;
	}

	/**
	 * 
	 * @return the preprocessor of this tree.
	 */
	public PreProcessorPointF getPreprocessor() {
		return pre;
	}

	/**
	 * @return A string tree view of all entries in the tree.
	 * @see PhTree#toStringTree()
	 */
	public String toStringTree() {
		return pht.toStringTree();
	}

	@Override
	public String toString() {
		return pht.toString(); 
	}

	public PhTreeStats getStats() {
		return pht.getStats();
	}
}

