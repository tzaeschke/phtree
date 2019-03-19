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

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

import ch.ethz.globis.phtree.PhTree.PhIterator;
import ch.ethz.globis.phtree.PhTree.PhQuery;
import ch.ethz.globis.phtree.pre.PreProcessorRange;
import ch.ethz.globis.phtree.util.PhIteratorBase;
import ch.ethz.globis.phtree.util.PhTreeStats;

/**
 * PH-tree for storing ranged objects with floating point coordinates.
 * Stored objects are axis-aligned hyper-rectangles defined by a 'lower left'
 * and 'upper right' corner.  
 * 
 * @author Tilmann Zaeschke
 * @param <T> value type
 */
public class PhTreeSolid<T> implements Iterable<T> {

	@FunctionalInterface
	public interface SolidBiFunction<T, U, R> {

		/**
		 * Applies this function to the given arguments.
		 *
		 * @param lower lower left corner
		 * @param upper upper right corner
		 * @param value the value
		 * @return the function result
		 */
		R apply(T lower, T upper, U value);
	}

	private final int dims;
	private final PhTree<T> pht;
	private final PreProcessorRange pre;
	private final long[] qMIN;
	private final long[] qMAX;


	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 */
	private PhTreeSolid(int dim) {
		this(PhTree.create(dim*2));
	}

	/**
	 * Create a new range tree backed by the the specified tree.
	 * Note that the backing tree's dimensionality must be a multiple of 2.
	 * 
	 * @param tree the backing tree
	 */
	public PhTreeSolid(PhTree<T> tree) {
		this.dims = tree.getDim()/2;
		if (dims*2 != tree.getDim()) {
			throw new IllegalArgumentException("The backing tree's DIM must be a multiple of 2");
		}
		pht = tree;
		pre = new PreProcessorRange.Simple();
		qMIN = new long[dims];
		Arrays.fill(qMIN, Long.MIN_VALUE);
		qMAX = new long[dims];
		Arrays.fill(qMAX, Long.MAX_VALUE);
	}

	/**
	 * Create a new range tree backed by the the specified tree.
	 * Note that the backing tree's dimensionality must be a multiple of 2.
	 * 
	 * @param tree the backing tree
	 */
	public static <T> PhTreeSolid<T> wrap(PhTree<T> tree) {
		return new PhTreeSolid<>(tree);
	}

	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 * @return new tree
	 * @param <T> value type
	 */
	public static <T> PhTreeSolid<T> create(int dim) {
		return new PhTreeSolid<>(dim);
	}

	/**
	 * Inserts a new ranged object into the tree.
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param value value
	 * @return the previous value or {@code null} if no entry existed
	 * 
	 * @see PhTree#put(long[], Object)
	 */
	public T put(long[] lower, long[] upper, T value) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.put(lVal, value);
	}

	/**
	 * Removes a ranged object from the tree.
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @return the value or {@code null} if no entry existed
	 * 
	 * @see PhTree#remove(long...)
	 */
	public T remove(long[] lower, long[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.remove(lVal);
	}

	/**
	 * Check whether an entry with the specified coordinates exists in the tree.
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @return true if the entry was found 
	 * 
	 * @see PhTree#contains(long...)
	 */
	public boolean contains(long[] lower, long[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.contains(lVal);
	}

	/**
	 * Return a value for the specified key coordinates.
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @return the value or null if it was not found 
	 * 
	 * @see PhTree#get(long...)
	 */
	public T get(long[] lower, long[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.get(lVal);
	}

	/**
	 * @param e entry to insert
	 * @return any previous value for that key
	 * 
	 * @see #put(long[], long[], Object)
	 */
	public T put(PhEntryS<T> e) {
		return put(e.lower(), e.upper(), e.value());
	}

	/**
	 * @param e entry to remove
	 * @return the value for the key
	 * @see #remove(long[], long[])
	 */
	public T remove(PhEntryS<T> e) {
		return remove(e.lower(), e.upper());
	}

	/**
	 * @param e entry to check
	 * @return 'true' if the key exists
	 * @see #contains(long[], long[])
	 */
	public boolean contains(PhEntryS<T> e) {
		return contains(e.lower(), e.upper());
	}

	/**
	 * @param e entry object that describes the query rectangle
	 * @return a query iterator
	 * @see #queryInclude(long[], long[])
	 */
	public PhQueryS<T> queryInclude(PhEntryS<T> e) {
		return queryInclude(e.lower(), e.upper());
	}

	/**
	 * @param e entry object that describes the query rectangle
	 * @return a query iterator
	 * @see #queryIntersect(long[], long[])
	 */
	public PhQueryS<T> queryIntersect(PhEntryS<T> e) {
		return queryIntersect(e.lower(), e.upper());
	}

	/**
	 * Query for all bodies that are fully included in the query rectangle.
	 * @param lower 'lower left' corner of query rectangle
	 * @param upper 'upper right' corner of query rectangle
	 * @return Iterator over all matching elements.
	 */
	public PhQueryS<T> queryInclude(long[] lower, long[] upper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(lower, lower, lLow);
		pre.pre(upper, upper, lUpp);
		return new PhQueryS<>(pht.query(lLow, lUpp), dims, pre, false);
	}

	/**
	 * Query for all bodies that are included in or partially intersect with the query rectangle.
	 * @param lower 'lower left' corner of query rectangle
	 * @param upper 'upper right' corner of query rectangle
	 * @return Iterator over all matching elements.
	 */
	public PhQueryS<T> queryIntersect(long[] lower, long[] upper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(qMIN, lower, lLow);
		pre.pre(upper, qMAX, lUpp);
		return new PhQueryS<>(pht.query(lLow, lUpp), dims, pre, true);
	}

	/**
	 * Iterator class for solids/rectangles. 
	 * @param <T> value type
	 */
	public static class PhIteratorS<T> implements PhIteratorBase<T, PhEntryS<T>> {
		private final PhIterator<T> iter;
		private final int dims;
		protected final PreProcessorRange pre;
		private final PhEntryS<T> buffer;
		
		private PhIteratorS(PhIterator<T> iter, int dims, PreProcessorRange pre) {
			this.iter = iter;
			this.dims = dims;
			this.pre = pre;
			this.buffer = new PhEntryS<>(new long[dims], new long[dims], null);
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
		public T nextValue() {
			return iter.nextValue();
		}
		@Override
		public PhEntryS<T> nextEntry() {
			long[] lower = new long[dims];
			long[] upper = new long[dims];
			PhEntry<T> pvEntry = iter.nextEntryReuse();
			pre.post(pvEntry.getKey(), lower, upper);
			return new PhEntryS<>(lower, upper, pvEntry.getValue());
		}
		@Override
		public PhEntryS<T> nextEntryReuse() {
			PhEntry<T> pvEntry = iter.nextEntryReuse();
			pre.post(pvEntry.getKey(), buffer.lower, buffer.upper);
			buffer.setValue( pvEntry.getValue() );
			return buffer;
		}
		@Override
		public void remove() {
			iter.remove();
		}
	}

	/**
	 * Query class for solids/rectangles. 
	 * @param <T> value type
	 */
	public static class PhQueryS<T> extends PhIteratorS<T> {
		private final long[] lLow;
		private final long[] lUpp;
		private final PhQuery<T> q;
		private final long[] qMIN;
		private final long[] qMAX;
		private final boolean intersect;
		
		private PhQueryS(PhQuery<T> iter, int dims, PreProcessorRange pre, boolean intersect) {
			super(iter, dims, pre);
			q = iter;
			qMIN = new long[dims];
			Arrays.fill(qMIN, Long.MIN_VALUE);
			qMAX = new long[dims];
			Arrays.fill(qMAX, Long.MAX_VALUE);
			this.intersect = intersect;
			lLow = new long[dims*2];
			lUpp = new long[dims*2];
		}

		/**
		 * Restarts the query with a new query rectangle.
		 * @param lower minimum values of query rectangle
		 * @param upper maximum values of query rectangle
		 */
		public void reset(long[] lower, long[] upper) {
			if (intersect) {
				pre.pre(qMIN, lower, lLow);
				pre.pre(upper, qMAX, lUpp);
			} else {
				//include
				pre.pre(lower, lower, lLow);
				pre.pre(upper, upper, lUpp);
			}
			q.reset(lLow, lUpp);
		}
	}
	
	/**
	 * Entries in a PH-tree with ranged objects. 
	 * @param <T> value type of the entry
	 */
	public static class PhEntryS<T> {

		private final long[] lower;
		private final long[] upper;
		private T value;

		/**
		 * Range object constructor.
		 * @param lower lower left corner
		 * @param upper upper right corner
		 * @param value the value
		 */
		public PhEntryS(long[] lower, long[] upper, T value) {
			this.lower = lower;
			this.upper = upper;
			this.value = value;
		}

		/**
		 * Range object copy constructor.
		 * @param e the entry to copy
		 */
		public PhEntryS(PhEntryS<T> e) {
			this.lower = Arrays.copyOf(e.lower, e.lower.length);
			this.upper = Arrays.copyOf(e.upper, e.upper.length);
			this.value = e.value;
		}

		/**
		 * @return the value of the entry
		 */
		public T value() {
			return value;
		}

		/**
		 * @return lower left corner of the entry
		 */
		public long[] lower() {
			return lower;
		}

		/**
		 * @return upper right corner of the entry
		 */
		public long[] upper() {
			return upper;
		}

		public void setValue(T value) {
			this.value = value;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof PhEntryS)) {
				return false;
			}
			PhEntryS<T> e = (PhEntryS<T>) obj;
			return Arrays.equals(lower, e.lower) && Arrays.equals(upper, e.upper);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(lower) ^ Arrays.hashCode(upper);
		}

		@Override
		public String toString() {
			return "{" + Arrays.toString(lower) + "," + Arrays.toString(upper) + "} => " + value;
		}
	}

	@Override
	public PhIteratorS<T> iterator() {
		return new PhIteratorS<>(pht.queryExtent(), dims, pre);
	}

	/**
	 * @param lo1 old lower left corner
	 * @param up1 old upper right corner
	 * @param lo2 new lower left corner
	 * @param up2 new upper right corner
	 * @return true, if the value could be replaced.
	 * @see PhTree#update(long[], long[])
	 */
	public T update(long[] lo1, long[] up1, long[] lo2, long[] up2) {
		long[] pOld = new long[lo1.length << 1];
		long[] pNew = new long[lo1.length << 1];
		pre.pre(lo1, up1, pOld);
		pre.pre(lo2, up2, pNew);
		return pht.update(pOld, pNew);
	}

	/**
	 * @return The number of entries in the tree
	 */
	public int size() {
		return pht.size();
	}

    /**
     * Clear the tree.
     */
	public void clear() {
		pht.clear();
	}

	/**
	 * Get dimensionality of the tree.
	 * @return dimensionality
	 */
	public int getDim() {
		return dims;
	}

	/**
	 * @return PH-Tree statistics
	 */
	public PhTreeStats getStats() {
		return pht.getStats();
	}

	/**
	 * @return A string tree view of all entries in the tree.
	 */
	public String toStringTree() {
		return pht.toStringTree();
	}

	// Overrides of JDK8 Map extension methods

	/**
	 * @see java.util.Map#getOrDefault(Object, Object)
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param defaultValue default value
	 * @return actual value or default value
	 */
	public T getOrDefault(long[] lower, long[] upper, T defaultValue) {
		T t = get(lower, upper);
		return t == null ? defaultValue : t;
	}

	/**
	 * @see java.util.Map#putIfAbsent(Object, Object)
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param value new value
	 * @return previous value or null
	 */
	public T putIfAbsent(long[] lower, long[] upper, T value) {
		long[] key = new long[lower.length*2];
		pre.pre(lower, upper, key);
		return pht.putIfAbsent(key, value);
	}

	/**
	 * @see java.util.Map#remove(Object, Object)
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param value value
	 * @return {@code true} if the value was removed
	 */
	public boolean remove(long[] lower, long[] upper, T value) {
		long[] key = new long[lower.length*2];
		pre.pre(lower, upper, key);
		return pht.remove(key, value);
	}

	/**
	 * @see java.util.Map#replace(Object, Object, Object)
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param oldValue old value
	 * @param newValue new value
	 * @return {@code true} if the value was replaced
	 */
	public boolean replace(long[] lower, long[] upper, T oldValue, T newValue) {
		long[] key = new long[lower.length*2];
		pre.pre(lower, upper, key);
		return pht.replace(key, oldValue, newValue);
	}

	/**
	 * @see java.util.Map#replace(Object, Object)
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param value new value
	 * @return previous value or null
	 */
	public T replace(long[] lower, long[] upper, T value) {
		long[] key = new long[lower.length*2];
		pre.pre(lower, upper, key);
		return pht.replace(key, value);
	}

	/**
	 * @see java.util.Map#computeIfAbsent(Object, Function)
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param mappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	public T computeIfAbsent(long[] lower, long[] upper, BiFunction<long[], long[], ? extends T> mappingFunction) {
		long[] key = new long[lower.length*2];
		pre.pre(lower, upper, key);
		return pht.computeIfAbsent(key, (longs) -> mappingFunction.apply(lower, upper));
	}

	/**
	 * @see java.util.Map#computeIfPresent(Object, BiFunction)
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param remappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	public T computeIfPresent(long[] lower, long[] upper,
							  SolidBiFunction<long[], ? super T, ? extends T> remappingFunction) {
		long[] key = new long[lower.length*2];
		pre.pre(lower, upper, key);
		return pht.computeIfPresent(key, (longs, value) -> remappingFunction.apply(lower, upper, value));
	}

	/**
	 * @see java.util.Map#compute(Object, BiFunction)
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param remappingFunction mapping function
	 * @return new value or null if none is associated
	 */
	public T compute(long[] lower, long[] upper, SolidBiFunction<long[], ? super T, ? extends T> remappingFunction) {
		long[] key = new long[lower.length*2];
		pre.pre(lower, upper, key);
		return pht.compute(key, (longs, value) -> remappingFunction.apply(lower, upper, value));
	}

}
