/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
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
package ch.ethz.globis.phtree;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import ch.ethz.globis.phtree.util.PhIteratorBase;
import ch.ethz.globis.phtree.util.PhMapper;
import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.v13.PhTree13;
import ch.ethz.globis.phtree.v16.PhTree16;
import ch.ethz.globis.phtree.v16hd.PhTree16HD;

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

	/**
	 * @return The number of entries in the tree
	 */
	int size();

	/**
	 * @return PH-Tree statistics
	 */
	PhTreeStats getStats();

	/**
	 * Insert an entry associated with a k dimensional key.
	 * This will replace any entry that uses the same key.
	 * @param key the key to insert
	 * @param value the value to insert
	 * @return the previously associated value or {@code null} if the key was found
	 */
	T put(long[] key, T value);

	/**
	 * Checks whether a give key exists in the tree.
	 * @param key the key to check
	 * @return true if the key exists, otherwise false
	 */
	boolean contains(long ... key);

	/**
	 * Get an entry associated with a k dimensional key.
	 * @param key the key to look up
	 * @return the associated value or {@code null} if the key was found
	 */
	T get(long ... key);


	/**
	 * Remove the entry associated with a k dimensional key.
	 * @param key the key to remove
	 * @return the associated value or {@code null} if the key was found
	 */
	T remove(long... key);

	/**
	 * @return A string with a list of all entries in the tree.
	 */
	String toStringPlain();

	/**
	 * @return A string tree view of all entries in the tree.
	 */
	String toStringTree();

	/**
	 * @return an iterator over all entries in the tree
	 */
	PhExtent<T> queryExtent();


	/**
	 * Performs a rectangular window query. The parameters are the min and max keys which 
	 * contain the minimum respectively the maximum keys in every dimension.
	 * @param min Minimum values
	 * @param max Maximum values
	 * @return Result iterator.
	 */
	PhQuery<T> query(long[] min, long[] max);

	/**
	 * Performs a rectangular window query. The parameters are the min and max keys which 
	 * contain the minimum respectively the maximum keys in every dimension.
	 * @param min Minimum values
	 * @param max Maximum values
	 * @param filter A filter function. The iterator will only return results that match the filter. 
	 * @return Result iterator.
	 */
	default PhQuery<T> query(long[] min, long[] max, PhFilter filter) {
		throw new UnsupportedOperationException("This is only supported in V13, V16 and V16HD.");
	}

	/**
	 * 
	 * @return the number of dimensions of the tree
	 */
	int getDim();

	/**
	 * 
	 * @return The bit depths for the tree. The latest versions will always return 64. 
	 */
	int getBitDepth();

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param key the center point
	 * @return The query iterator.
	 */
	PhKnnQuery<T> nearestNeighbour(int nMin, long... key);

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param dist the distance function, can be {@code null}. The default is {@link PhDistanceL}.
	 * @param dims the dimension filter, can be {@code null}
	 * @param key the center point
	 * @return The query iterator.
	 */
	PhKnnQuery<T> nearestNeighbour(int nMin, PhDistance dist, PhFilter dims,
			long... key);

	/**
	 * Find all entries within a given distance from a center point.
	 * @param dist Maximum distance
	 * @param center Center point
	 * @return All entries with at most distance `dist` from `center`.
	 */
	PhRangeQuery<T> rangeQuery(double dist, long... center);

	/**
	 * Find all entries within a given distance from a center point.
	 * @param dist Maximum distance
	 * @param optionalDist Distance function, optional, can be `null`.
	 * @param center Center point
	 * @return All entries with at most distance `dist` from `center`.
	 */
	PhRangeQuery<T> rangeQuery(double dist, PhDistance optionalDist, long... center);

	/**
	 * Update the key of an entry. Update may fail if the old key does not exist, or if the new
	 * key already exists.
	 * @param oldKey the old key
	 * @param newKey the new key
	 * @return the value (can be {@code null}) associated with the updated key if the key could be 
	 * updated, otherwise {@code null}.
	 */
	T update(long[] oldKey, long[] newKey);

	/**
	 * Same as {@link #query(long[], long[])}, except that it returns a list
	 * instead of an iterator. This may be faster for small result sets. 
	 * @param min the minimum values
	 * @param max the maximum values
	 * @return List of query results
	 */
	List<PhEntry<T>> queryAll(long[] min, long[] max);

	/**
	 * Same as {@link #query(long[], long[])}, except that it returns a list
	 * instead of an iterator. This may be faster for small result sets. 
	 * @param min the minimum values
	 * @param max the maximum values
	 * @param maxResults maximum results to be returned
	 * @param filter the filter function
	 * @param mapper mapper function
	 * @return List of query results
	 * @param <R> the type of the iterator value
	 */
	<R> List<R> queryAll(long[] min, long[] max, int maxResults,
			PhFilter filter, PhMapper<T, R> mapper);

	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 * @return PhTree
	 * @param <T> the type of the values
	 */
	static <T> PhTree<T> create(int dim) {
		if (dim > 60) {
			return new PhTree16HD<>(dim);
		} else if (dim >= 8) {
			return new PhTree16<>(dim);
		}
		return new PhTree13<>(dim);
	}

	/**
	 * Create a new tree with a configuration instance.
	 * 
	 * @param cfg configuration instance
	 * @return PhTree
	 * @param <T> the type of the values
	 */
	static <T> PhTree<T> create(PhTreeConfig cfg) {
		if (cfg.getDim() > 60) {
			return new PhTree16HD<>(cfg);
		} else if (cfg.getDim() >= 8) {
			return new PhTree16<>(cfg);
		}
		return new PhTree13<>(cfg);
	}

	/**
	 * Interface for iterators that can reuse entries to avoid garbage collection. 
	 * 
	 * @param <T> the type of the iterator value
	 */
	interface PhIterator<T> extends PhIteratorBase<T, PhEntry<T>> {

		/**
		 * @return the key of the next entry
		 */
		long[] nextKey();
		
		/**
		 * Special 'next' method that avoids creating new objects internally by reusing Entry objects.
		 * Advantage: Should completely avoid any GC effort.
		 * Disadvantage: Returned PhEntries are not stable and are only valid until the
		 * next call to next(). After that they may change state. Modifying returned entries may
		 * invalidate the backing tree.
		 * @return The next entry
		 */
		@Override
		PhEntry<T> nextEntryReuse();
	}

	/**
	 * Interface for extents (query over all elements). The reset methods allows
	 * reusing the iterator.
	 * 
	 * @param <T> the type of the iterator value
	 */
	interface PhExtent<T> extends PhIterator<T> {

		/**
		 * Reset the extent iterator.
		 * @return the extent itself
		 */
		PhExtent<T> reset();
	}

	/**
	 * Interface for queries. The reset methods allows reusing the query.
	 * 
	 * @param <T> the type of the iterator value
	 */
	interface PhQuery<T> extends PhIterator<T> {

		/**
		 * Reset the query with the new 'min' and 'max' boundaries.
		 * @param min min values
		 * @param max max values
		 * @return the query itself
		 */
		PhQuery<T> reset(long[] min, long[] max);
	}

	/**
	 * Interface for k nearest neighbor queries. The reset methods allows reusing the query.
	 * 
	 * @param <T> the type of the iterator value
	 */
	interface PhKnnQuery<T> extends PhIteratorBase<T, PhEntryDist<T>> {

		/**
		 * @return the next key
		 */
		long[] nextKey();

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
	 * Clear the tree.
	 */
	void clear();


	// Overrides of JDK8 Map extension methods

	/**
	 * @see java.util.Map#getOrDefault(Object, Object)
	 * @param key key
	 * @param defaultValue default value
	 * @return actual value or default value
	 */
	default T getOrDefault(long[] key, T defaultValue) {
		T t = get(key);
		return t == null ? defaultValue : t;
	}

	/**
	 * @see java.util.Map#putIfAbsent(Object, Object)
	 * @param key key
	 * @param value new value
	 * @return previous value or null
	 */
	default T putIfAbsent(long[] key, T value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * @see java.util.Map#remove(Object, Object)
	 * @param key key
	 * @param value value
	 * @return {@code true} if the value was removed
	 */
	default boolean remove(long[] key, T value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * @see java.util.Map#replace(Object, Object, Object)
	 * @param key key
	 * @param oldValue old value
	 * @param newValue new value
	 * @return {@code true} if the value was replaced
	 */
	default boolean replace(long[] key, T oldValue, T newValue) {
		throw new UnsupportedOperationException();
	}

	/**
	 * @see java.util.Map#replace(Object, Object)
	 * @param key key
	 * @param value new value
	 * @return previous value or null
	 */
	default T replace(long[] key, T value) {
		throw new UnsupportedOperationException();
	}

	/**
	 * @see java.util.Map#computeIfAbsent(Object, Function)
	 * @param key key
	 * @param mappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	default T computeIfAbsent(long[] key, Function<long[], ? extends T> mappingFunction) {
		throw new UnsupportedOperationException();
	}

	/**
	 * @see java.util.Map#computeIfPresent(Object, BiFunction)
	 * @param key key
	 * @param remappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	default T computeIfPresent(long[] key, BiFunction<long[], ? super T, ? extends T> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	/**
	 * @see java.util.Map#compute(Object, BiFunction)
	 * @param key key
	 * @param remappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	default T compute(long[] key, BiFunction<long[], ? super T, ? extends T> remappingFunction) {
		throw new UnsupportedOperationException();
	}
}

