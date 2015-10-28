/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht;

import java.util.List;

import ch.ethz.globis.pht.util.PhIteratorBase;
import ch.ethz.globis.pht.util.PhMapper;
import ch.ethz.globis.pht.util.PhTreeQStats;
import ch.ethz.globis.pht.v8.PhTree8;

/**
 * k-dimensional index (quad-/oct-/n-tree).
 * Supports key/value pairs.
 *
 * See also : T. Zaeschke, C. Zimmerli, M.C. Norrie; 
 * "The PH-Tree -- A Space-Efficient Storage Structure and Multi-Dimensional Index", 
 * (SIGMOD 2014)
 *
 * http://www.phtree.org
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 * @param <T> The value type of the tree 
 *
 */
public interface PhTree<T> {


	public int size();

	public int getNodeCount();

	public PhTreeQStats getQuality();

	public abstract PhTreeHelper.Stats getStats();

	public abstract PhTreeHelper.Stats getStatsIdealNoNode();


	/**
	 * Insert an entry associated with a k dimensional key.
	 * @param key
	 * @param value
	 * @return the previously associated value or {@code null} if the key was found
	 */
	public abstract T put(long[] key, T value);

	public abstract boolean contains(long ... key);

	public abstract T get(long ... key);


	/**
	 * Remove the entry associated with a k dimensional key.
	 * @param key
	 * @return the associated value or {@code null} if the key was found
	 */
	public abstract T remove(long... key);

	public abstract String toStringPlain();

	public abstract String toStringTree();

	public abstract PhExtent<T> queryExtent();


	/**
	 * Performs a rectangular window query. The parameters are the min and max keys which 
	 * contain the minimum respectively the maximum keys in every dimension.
	 * @param min Minimum values
	 * @param max Maximum values
	 * @return Result iterator.
	 */
	public abstract PhQuery<T> query(long[] min, long[] max);

	public abstract int getDim();

	/**
	 * 
	 * @return The bit depths for the tree. The latest versions will always return 64. 
	 */
	public abstract int getBitDepth();

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param key
	 * @return The query iterator.
	 */
	public abstract PhKnnQuery<T> nearestNeighbour(int nMin, long... key);

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param dist the distance function, can be {@code null}. The default is {@link PhDistanceL}.
	 * @param dims the dimension filter, can be {@code null}
	 * @param key
	 * @return The query iterator.
	 */
	public abstract PhKnnQuery<T> nearestNeighbour(int nMin, PhDistance dist, PhDimFilter dims, 
			long... key);

	/**
	 * Find all entries within a given distance from a center point.
	 * @param dist Maximum distance
	 * @param center Center point
	 * @return All entries with at most distance `dist` from `center`.
	 */
	public PhRangeQuery<T> rangeQuery(double dist, long... center);

	/**
	 * Find all entries within a given distance from a center point.
	 * @param dist Maximum distance
	 * @param optionalDist Distance function, optional, can be `null`.
	 * @param center Center point
	 * @return All entries with at most distance `dist` from `center`.
	 */
	public abstract PhRangeQuery<T> rangeQuery(double dist, PhDistance optionalDist, long... center);

	/**
	 * Update the key of an entry. Update may fail if the old key does not exist, or if the new
	 * key already exists.
	 * @param oldKey
	 * @param newKey
	 * @return the value (can be {@code null}) associated with the updated key if the key could be 
	 * updated, otherwise {@code null}.
	 */
	public T update(long[] oldKey, long[] newKey);

	/**
	 * Same as {@link #query(long[], long[])}, except that it returns a list
	 * instead of an iterator. This may be faster for small result sets. 
	 * @param min
	 * @param max
	 * @return List of query results
	 */
	public List<PhEntry<T>> queryAll(long[] min, long[] max);

	public <R> List<R> queryAll(long[] min, long[] max, int maxResults, 
			PhPredicate filter, PhMapper<T, R> mapper);

	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 * @return PhTree
	 */
	public static <T> PhTree<T> create(int dim) {
		return new PhTree8<T>(dim);
	}

	/**
	 * Create a new tree with a configuration instance.
	 * 
	 * @param cfg configuration instance
	 * @return PhTree
	 */
	public static <T> PhTree<T> create(PhTreeConfig cfg) {
		return new PhTree8<T>(cfg);
	}

	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 * @param depth the number of bits per dimension (1..64)
	 * @return PhTree
	 * @deprecated Depth is not required anymore.
	 */
	public static <T> PhTree<T> create(int dim, int depth) {
		return new PhTree8<T>(dim);
	}

	public static interface PhIterator<T> extends PhIteratorBase<long[], T, PhEntry<T>> {

		/**
		 * Special 'next' method that avoids creating new objects internally by reusing Entry objects.
		 * Advantage: Should completely avoid any GC effort.
		 * Disadvantage: Returned PhEntries are not stable and are only valid until the
		 * next call to next(). After that they may change state. Modifying returned entries may
		 * invalidate the backing tree.
		 * @return The next entry
		 */
		PhEntry<T> nextEntryReuse();
	}

	public static interface PhExtent<T> extends PhIterator<T> {

		/**
		 * Reset the extent iterator.
		 */
		PhExtent<T> reset();
	}

	public static interface PhQuery<T> extends PhIterator<T> {

		/**
		 * Reset the query with the new 'min' and 'max' boundaries.
		 * @param min
		 * @param max
		 */
		void reset(long[] min, long[] max);
	}

	public static interface PhKnnQuery<T> extends PhIterator<T> {

		/**
		 * Reset the query with the new parameters.
		 * @param nMin Minimum result count
		 * @param dist Distance function
		 * @param center The point to find the nearest neighbours for
		 * @return the query itself
		 */
		PhKnnQuery<T> reset(int nMin, PhDistance dist, long... center);
	}

	/**
	 *  @param <T>
	 *  @deprecated
	 */
	public static class ResetUOE<T> implements PhQuery<T> {
		private final PhIterator<T> iter;
		public ResetUOE(PhIterator<T> iter) {
			this.iter = iter;
		}
		@Override
		public long[] nextKey() {
			return iter.nextKey();
		}

		@Override
		public T nextValue() {
			return iter.nextValue();
		}

		@Override
		public PhEntry<T> nextEntry() {
			return iter.nextEntry();
		}

		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}

		@Override
		public T next() {
			return iter.next();
		}

		@Override
		public void reset(long[] min, long[] max) {
			throw new UnsupportedOperationException("reset() not supported.");
		}
		@Override
		public PhEntry<T> nextEntryReuse() {
			throw new UnsupportedOperationException("nextEntryReuse() not supported.");
		}
	}

	/**
	 * Clear the tree.
	 */
	void clear();
}

