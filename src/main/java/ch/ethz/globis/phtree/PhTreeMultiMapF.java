/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
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

import ch.ethz.globis.phtree.PhTree.PhExtent;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.PhTree.PhQuery;
import ch.ethz.globis.phtree.pre.PreProcessorPointF;
import ch.ethz.globis.phtree.util.PhIteratorBase;
import ch.ethz.globis.phtree.util.PhMapper;
import ch.ethz.globis.phtree.util.PhMapperK;
import ch.ethz.globis.phtree.util.PhTreeStats;

/**
 * k-dimensional index (quad-/oct-/n-tree). Supports key/value pairs.
 *
 * The multi-map allows, unlike plain PH-Trees, to store more than one value per
 * coordinate. The multi-map uses a simple trick, it adds one additional
 * dimension that stores an unique identifier to distinguish mutliple values
 * with the same coordinate.
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 * @param <T> The value type of the tree
 *
 */
public class PhTreeMultiMapF<T> {

    private final PhTree<T> pht;
    private final PreProcessorPointF pre;

    protected PhTreeMultiMapF(int dim, PreProcessorPointF pre) {
        this.pht = PhTree.create(dim + 1);
        this.pre = pre;
    }

    protected PhTreeMultiMapF(PhTree<T> tree) {
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
    public static <T> PhTreeMultiMapF<T> create(int dim) {
        return new PhTreeMultiMapF<>(dim, new PreProcessorPointF.IEEE());
    }

    /**
     * Create a new tree with the specified number of dimensions and a custom
     * preprocessor.
     * 
     * @param dim number of dimensions
     * @param pre The preprocessor to be used
     * @return PhTreeF
     * @param <T> value type of the tree
     */
    public static <T> PhTreeMultiMapF<T> create(int dim, PreProcessorPointF pre) {
        return new PhTreeMultiMapF<>(dim, pre);
    }

    /**
     * Create a new PhTreeF as a wrapper around an existing PhTree.
     * 
     * @param tree another tree
     * @return PhTreeF
     * @param <T> value type of the tree
     */
    public static <T> PhTreeMultiMapF<T> wrap(PhTree<T> tree) {
        return new PhTreeMultiMapF<>(tree);
    }

    /**
     * @return the number of entries in the tree
     */
    public int size() {
        return pht.size();
    }

    /**
     * Insert an entry associated with a k dimensional key.
     * 
     * @param key   the key to store the value to store
     * @param id    unique id of the value
     * @param value the value
     * @return the previously associated value or {@code null} if the key was found
     */
    public T put(double[] key, long id, T value) {
        return pht.put(pre(key, id), value);
    }

    /**
     * @param key key
     * @param id    unique id of the value
     * @return true if the key exists in the tree
     */
    public boolean contains(double[] key, long id) {
        return pht.contains(pre(key, id));
    }

    /**
     * @param key the key
     * @param id  unique id of the value
     * @return the value associated with the key or 'null' if the key was not found
     */
    public T get(double[] key, long id) {
        return pht.get(pre(key, id));
    }

    /**
     * Remove the entry associated with a k dimensional key.
     * 
     * @param key the key to remove
     * @param id  unique id of the value
     * @return the associated value or {@code null} if the key was found
     */
    public T remove(double[] key, long id) {
        return pht.remove(pre(key, id));
    }

    /**
     * @return an iterator over all elements in the tree
     */
    public PhExtentMMF<T> queryExtent() {
        return new PhExtentMMF<>(pht.queryExtent(), pht.getDim() - 1, pre);
    }

    /**
     * Performs a rectangular window query. The parameters are the min and max keys
     * which contain the minimum respectively the maximum keys in every dimension.
     * 
     * @param min Minimum values
     * @param max Maximum values
     * @return Result iterator.
     */
    public PhQueryMMF<T> query(double[] min, double[] max) {
        long[] lMin = new long[min.length + 1];
        long[] lMax = new long[max.length + 1];
        pre.pre(min, lMin);
        pre.pre(max, lMax);
        lMin[lMin.length - 1] = Long.MIN_VALUE;
        lMax[lMax.length - 1] = Long.MAX_VALUE;
        return new PhQueryMMF<>(pht.query(lMin, lMax), pht.getDim() - 1, pre);
    }

    /**
     * Find all entries within a given distance from a center point.
     * 
     * @param dist   Maximum distance
     * @param center Center point
     * @return All entries with at most distance `dist` from `center`.
     */
    public PhRangeQueryMMF<T> rangeQuery(double dist, double ... center) {
        return rangeQuery(dist, PhDistanceMMF.THIS, center);
    }

    /**
     * Find all entries within a given distance from a center point.
     * 
     * @param dist         Maximum distance
     * @param optionalDist Distance function, optional, can be `null`.
     * @param center       Center point
     * @return All entries with at most distance `dist` from `center`.
     */
    public PhRangeQueryMMF<T> rangeQuery(double dist, PhDistance optionalDist, double ... center) {
        if (optionalDist == null) {
            optionalDist = PhDistanceMMF.THIS;
        }
        long[] lKey = new long[center.length + 1];
        pre.pre(center, lKey);
        PhRangeQuery<T> iter = pht.rangeQuery(dist, optionalDist, lKey);
        return new PhRangeQueryMMF<>(iter, pht, pre);
    }

    public int getDim() {
        return pht.getDim();
    }

    /**
     * Locate nearest neighbours for a given point in space.
     * 
     * @param nMin number of entries to be returned. More entries may or may not be
     *             returned if several points have the same distance.
     * @param key  the center point
     * @return List of neighbours.
     */
    public PhKnnQueryMMF<T> nearestNeighbour(int nMin, double ... key) {
        long[] lKey = new long[key.length + 1];
        pre.pre(key, lKey);
        PhKnnQuery<T> iter = pht.nearestNeighbour(nMin, PhDistanceMMF.THIS, null, lKey);
        return new PhKnnQueryMMF<>(iter, pht.getDim() - 1, pre);
    }

    /**
     * Locate nearest neighbours for a given point in space.
     * 
     * @param nMin number of entries to be returned. More entries may or may not be
     *             returned if several points have the same distance.
     * @param dist Distance function. Note that the distance function should be
     *             compatible with the preprocessor of the tree.
     * @param key  the center point
     * @return KNN query iterator.
     */
    public PhKnnQueryMMF<T> nearestNeighbour(int nMin, PhDistance dist, double ... key) {
        long[] lKey = new long[key.length + 1];
        pre.pre(key, lKey);
        PhKnnQuery<T> iter = pht.nearestNeighbour(nMin, dist, null, lKey);
        return new PhKnnQueryMMF<>(iter, pht.getDim() - 1, pre);
    }

    /**
     * Iterator class for floating point keys.
     * 
     * @param <T> value type
     */
    public static class PhIteratorMMF<T> implements PhIteratorBase<T, PhEntryMMF<T>> {
        private final PhIteratorBase<T, ? extends PhEntry<T>> iter;
        protected final PreProcessorPointF pre;
        protected final int dims;
        private final PhEntryMMF<T> buffer;

        protected PhIteratorMMF(PhIteratorBase<T, ? extends PhEntry<T>> iter, int dims, 
                PreProcessorPointF pre) {
            this.iter = iter;
            this.pre = pre;
            this.dims = dims;
            this.buffer = new PhEntryMMF<>(new double[dims], -1, null);
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
        public PhEntryMMF<T> nextEntry() {
            double[] d = new double[dims];
            PhEntry<T> e = iter.nextEntryReuse();
            pre.post(e.getKey(), d);
            return new PhEntryMMF<>(d, e.getKey()[dims], e.getValue());
        }

        @Override
        public PhEntryMMF<T> nextEntryReuse() {
            PhEntry<T> e = iter.nextEntryReuse();
            pre.post(e.getKey(), buffer.getKey());
            buffer.setValue(e.getValue());
            return buffer;
        }

        /**
         * @return the key of the next entry
         */
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

    /**
     * Extent iterator class for floating point keys.
     * 
     * @param <T> value type
     */
    public static class PhExtentMMF<T> extends PhIteratorMMF<T> {
        private final PhExtent<T> iter;

        protected PhExtentMMF(PhExtent<T> iter, int dims, PreProcessorPointF pre) {
            super(iter, dims, pre);
            this.iter = iter;
        }

        /**
         * Restarts the extent iterator.
         * 
         * @return this
         */
        public PhExtentMMF<T> reset() {
            iter.reset();
            return this;
        }
    }

    /**
     * Query iterator class for floating point keys.
     * 
     * @param <T> value type
     */
    public static class PhQueryMMF<T> extends PhIteratorMMF<T> {
        private final long[] lMin;
        private final long[] lMax;
        private final PhQuery<T> q;

        protected PhQueryMMF(PhQuery<T> iter, int dims, PreProcessorPointF pre) {
            super(iter, dims, pre);
            q = iter;
            lMin = new long[dims];
            lMax = new long[dims];
        }

        /**
         * Restarts the query with a new query rectangle.
         * 
         * @param lower minimum values of query rectangle
         * @param upper maximum values of query rectangle
         */
        public void reset(double[] lower, double[] upper) {
            pre.pre(lower, lMin);
            pre.pre(upper, lMax);
            q.reset(lMin, lMax);
        }
    }

    /**
     * Nearest neighbor query iterator class for floating point keys.
     * 
     * @param <T> value type
     */
    public static class PhKnnQueryMMF<T> extends PhIteratorMMF<T> {
        private final long[] lCenter;
        private final PhKnnQuery<T> q;
        private final PhEntryDistMMF<T> buffer;
        private final int dims;

        protected PhKnnQueryMMF(PhKnnQuery<T> iter, int dims, PreProcessorPointF pre) {
            super(iter, dims, pre);
            this.dims = dims;
            q = iter;
            lCenter = new long[dims];
            buffer = new PhEntryDistMMF<>(new double[dims], -1, null, Double.NaN);
        }

        @Override
        public PhEntryDistMMF<T> nextEntry() {
            double[] d = new double[dims];
            PhEntryDist<T> e = q.nextEntryReuse();
            pre.post(e.getKey(), d);
            return new PhEntryDistMMF<>(d, e.getKey()[dims], e.getValue(), e.dist());
        }

        @Override
        public PhEntryDistMMF<T> nextEntryReuse() {
            PhEntryDist<T> e = q.nextEntryReuse();
            pre.post(e.getKey(), buffer.getKey());
            buffer.set(e.getKey()[dims], e.getValue(), e.dist());
            return buffer;
        }

        /**
         * Restarts the query with a new center point.
         * 
         * @param nMin   new minimum result count, often called 'k'
         * @param dist   new distance function. Using 'null' will result in reusing the
         *               previous distance function.
         * @param center new center point
         * @return this
         */
        public PhKnnQueryMMF<T> reset(int nMin, PhDistance dist, double... center) {
            pre.pre(center, lCenter);
            q.reset(nMin, dist, lCenter);
            return this;
        }
    }

    /**
     * Range query iterator class for floating point keys.
     * 
     * @param <T> value type
     */
    public static class PhRangeQueryMMF<T> extends PhIteratorMMF<T> {
        private final long[] lCenter;
        private final PhRangeQuery<T> q;

        protected PhRangeQueryMMF(PhRangeQuery<T> iter, PhTree<T> tree, PreProcessorPointF pre) {
            super(iter, tree.getDim() - 1, pre);
            this.q = iter;
            this.lCenter = new long[dims + 1];
        }

        /**
         * Restarts the query with a new center point and range.
         * 
         * @param range  new range
         * @param center new center point
         * @return this
         */
        public PhRangeQueryMMF<T> reset(double range, double... center) {
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
    public static class PhEntryMMF<T> {
        protected double[] key;
        protected long id;
        protected T value;

        /**
         * @param key   the key
         * @param id    unique id of the value
         * @param value the value
         */
        public PhEntryMMF(double[] key, long id, T value) {
            this.key = key;
            this.id = id;
            this.value = value;
        }

        public double[] getKey() {
            return key;
        }

        public long getId() {
            return id;
        }

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }
    }

    /**
     * Entry class for Double entries with distance information for nearest
     * neighbour queries.
     *
     * @param <T> value type of the entries
     */
    public static class PhEntryDistMMF<T> extends PhEntryMMF<T> {
        private double dist;

        /**
         * @param key   the key
         * @param id    unique id of the value
         * @param value the value
         * @param dist  the distance to the center point
         */
        public PhEntryDistMMF(double[] key, long id, T value, double dist) {
            super(key, id, value);
            this.dist = dist;
        }

        /**
         * @param value new value
         * @param id    unique id
         * @param dist  new distance
         */
        public void set(long id, T value, double dist) {
            this.id = id;
            this.value = value;
            this.dist = dist;
        }

        /**
         * @return distance to center point of kNN query
         */
        public double dist() {
            return dist;
        }
    }

    /**
     * Update the key of an entry. Update may fail if the old key does not exist, or
     * if the new key already exists.
     * 
     * @param oldKey old key
     * @param id     unique id of value
     * @param newKey new key
     * @return the value (can be {@code null}) associated with the updated key if
     *         the key could be updated, otherwise {@code null}.
     */
    public T update(double[] oldKey, long id, double[] newKey) {
        return pht.update(pre(oldKey, id), pre(newKey, id));
    }

    /**
     * Same as {@link #query(double[], double[])}, except that it returns a list
     * instead of an iterator. This may be faster for small result sets.
     * 
     * @param min min values
     * @param max max values
     * @return List of query results
     */
    public List<PhEntryMMF<T>> queryAll(double[] min, double[] max) {
        return queryAll(min, max, Integer.MAX_VALUE, null,
                e -> new PhEntryMMF<>(
                        PhMapperK.toDouble(e.getKey()), e.getKey()[min.length], e.getValue()));
    }

    /**
     * Same as {@link PhTreeMultiMapF#queryAll(double[], double[])}, except that it
     * also accepts a limit for the result size, a filter and a mapper.
     * 
     * @param min        min key
     * @param max        max key
     * @param maxResults maximum result count
     * @param filter     filter object (optional)
     * @param mapper     mapper object (optional)
     * @return List of query results
     * @param <R> value type
     */
    public <R> List<R> queryAll(double[] min, double[] max, int maxResults, PhFilter filter, 
            PhMapper<T, R> mapper) {
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

    // Overrides of JDK8 Map extension methods

    /**
     * @see java.util.Map#getOrDefault(Object, Object)
     * @param key          key
     * @param id           unique id of the value
     * @param defaultValue default value
     * @return actual value or default value
     */
    public T getOrDefault(double[] key, long id, T defaultValue) {
        T t = get(key, id);
        return t == null ? defaultValue : t;
    }

    /**
     * @see java.util.Map#putIfAbsent(Object, Object)
     * @param key   key
     * @param id    unique id of the value
     * @param value new value
     * @return previous value or null
     */
    public T putIfAbsent(double[] key, long id, T value) {
        return pht.putIfAbsent(pre(key, id), value);
    }

    /**
     * @see java.util.Map#remove(Object, Object)
     * @param key   key
     * @param id    unique id of the value
     * @param value value
     * @return {@code true} if the value was removed
     */
    public boolean remove(double[] key, long id, T value) {
        return pht.remove(pre(key, id), value);
    }

    /**
     * @see java.util.Map#replace(Object, Object, Object)
     * @param key      key
     * @param id    unique id of the value
     * @param oldValue old value
     * @param newValue new value
     * @return {@code true} if the value was replaced
     */
    public boolean replace(double[] key, long id, T oldValue, T newValue) {
        return pht.replace(pre(key, id), oldValue, newValue);
    }

    /**
     * @see java.util.Map#replace(Object, Object)
     * @param key   key
     * @param id    unique id of the value
     * @param value new value
     * @return previous value or null
     */
    public T replace(double[] key, long id, T value) {
        return pht.replace(pre(key, id), value);
    }

    /**
     * @see java.util.Map#computeIfAbsent(Object, Function)
     * @param key             key
     * @param id    unique id of the value
     * @param mappingFunction mapping function
     * @return new value or null if none is associated
     */
    public T computeIfAbsent(double[] key, long id, Function<double[], ? extends T> mappingFunction) {
        return pht.computeIfAbsent(pre(key, id), longs -> mappingFunction.apply(key));
    }

    /**
     * @see java.util.Map#computeIfPresent(Object, BiFunction)
     * @param key               key
     * @param id    unique id of the value
     * @param remappingFunction mapping function
     * @return new value or null if none is associated
     */
    public T computeIfPresent(double[] key, long id, 
            BiFunction<double[], ? super T, ? extends T> remappingFunction) {
        return pht.computeIfPresent(pre(key, id), (longs, t) -> remappingFunction.apply(key, t));
    }

    /**
     * @see java.util.Map#compute(Object, BiFunction)
     * @param key               key
     * @param id    unique id of the value
     * @param remappingFunction mapping function
     * @return new value or null if none is associated
     */
    public T compute(double[] key, long id, 
            BiFunction<double[], ? super T, ? extends T> remappingFunction) {
        return pht.compute(pre(key, id), (longs, t) -> remappingFunction.apply(key, t));
    }
    
    private long[] pre(double[] key, long id) {
        long[] lKey = new long[key.length + 1];
        pre.pre(key, lKey);
        lKey[key.length] = id;
        return lKey;
    }
}
