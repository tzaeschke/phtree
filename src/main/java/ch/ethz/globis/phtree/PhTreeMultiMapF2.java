/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
 * Copyright 2022-2023 Tilmann Zäschke. All rights reserved.
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

import ch.ethz.globis.phtree.PhTree.PhExtent;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.PhTree.PhQuery;
import ch.ethz.globis.phtree.pre.PreProcessorPointF;
import ch.ethz.globis.phtree.util.*;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * k-dimensional index (quad-/oct-/n-tree). Supports key/value pairs.
 * <p>
 * The multimap allows, unlike plain PH-Trees, to store more than one value per
 * coordinate.
 * This PhTreeMultiMapF2 uses a different approach than PhTreeMultiMapF.
 * PhTreeMultiMapF2 stores a collection (list) at each coordinate in order to handle multiple entries per coordinate.
 * 
 * @author ztilmann (Tilmann Zaeschke)
 *
 * @param <T> The value type of the tree
 */
public class PhTreeMultiMapF2<T> {

    private final PhTree<LinkedList<T>> pht;
    private final PreProcessorPointF pre;
    private int size = 0;

    protected PhTreeMultiMapF2(int dim, PreProcessorPointF pre) {
        this.pht = PhTree.create(dim);
        this.pre = pre;
    }

    protected PhTreeMultiMapF2(PhTree<LinkedList<T>> tree) {
        this.pht = tree;
        this.pre = new PreProcessorPointF.IEEE();
    }

    /**
     * Create a new tree with the specified number of dimensions.
     * 
     * @param dim number of dimensions
     * @return PhTreeMultiMapF2
     * @param <T> value type of the tree
     */
    public static <T> PhTreeMultiMapF2<T> create(int dim) {
        return new PhTreeMultiMapF2<>(dim, new PreProcessorPointF.IEEE());
    }

    /**
     * Create a new tree with the specified number of dimensions and a custom
     * preprocessor.
     * 
     * @param dim number of dimensions
     * @param pre The preprocessor to be used
     * @return PhTreeMultiMapF2
     * @param <T> value type of the tree
     */
    public static <T> PhTreeMultiMapF2<T> create(int dim, PreProcessorPointF pre) {
        return new PhTreeMultiMapF2<>(dim, pre);
    }

    /**
     * Create a new PhTreeF as a wrapper around an existing PhTree.
     * 
     * @param tree another tree
     * @return PhTreeMultiMapF2
     * @param <T> value type of the tree
     */
    public static <T> PhTreeMultiMapF2<T> wrap(PhTree<LinkedList<T>> tree) {
        return new PhTreeMultiMapF2<>(tree);
    }

    /**
     * @return the number of entries in the tree
     */
    public int size() {
        return size;
    }

    /**
     * Insert an entry associated with a k dimensional key + value.
     *
     * @param key   the key to store the value to store
     * @param value the value
     * @return `true` (this implementation allows duplicate key/value entries)
     */
    public boolean put(double[] key, T value) {
        pht.compute(pre(key), (keyInternal, list) -> {
            if (list == null) {
                list = new LinkedList<>();
            }
            list.add(value);
            return list;
        });
        size++;
        return true;
    }

    /**
     * @param key key
     * @param value the value
     * @return true if the key exists in the tree
     */
    public boolean contains(double[] key, T value) {
        LinkedList<T> list = pht.get(pre(key));
        if (list != null) {
            return list.contains(value);
        }
        return false;
    }

    /**
     * @param key the key
     * @return the value associated with the key or 'null' if the key was not found
     */
    public Iterable<T> get(double[] key) {
        return pht.get(pre(key));
    }

    /**
     * @see java.util.Map#remove(Object)
     * @param key   key
     * @return {@code true} if the value was removed
     */
    public Iterable<T> remove(double[] key) {
        LinkedList<T> list = pht.remove(pre(key));
        if (list != null) {
            size -= list.size();
            return list;
        }
        return null;
    }

    /**
     * @see java.util.Map#remove(Object, Object)
     * @param key   key
     * @param value value
     * @return {@code true} if the value was removed
     */
    public boolean remove(double[] key, T value) {
        MutableInt i = new MutableInt(0);
        pht.computeIfPresent(pre(key), (keyInternal, list) -> {
            // TODO Use pooling for List?
            if (list == null) {
                return null;
            }
            if (list.remove(value)) {
                i.inc();
            }
            return list.isEmpty() ? null : list;
        });
        size -= i.get();
        return i.get() > 0;
    }

    /**
     * @return an iterator over all elements in the tree
     */
    public PhExtentMMF<T> queryExtent() {
        return new PhExtentMMF<>(pht.queryExtent(), pht.getDim(), pre);
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
        long[] lMin = new long[min.length];
        long[] lMax = new long[max.length];
        pre.pre(min, lMin);
        pre.pre(max, lMax);
        return new PhQueryMMF<>(pht.query(lMin, lMax), pht.getDim(), pre);
    }

    /**
     * Find all entries within a given distance from a center point.
     * 
     * @param dist   Maximum distance
     * @param center Center point
     * @return All entries with at most distance `dist` from `center`.
     */
    public PhRangeQueryMMF<T> rangeQuery(double dist, double ... center) {
        return rangeQuery(dist, PhDistanceF.THIS, center);
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
            optionalDist = PhDistanceF.THIS;
        }
        long[] lKey = new long[center.length];
        pre.pre(center, lKey);
        PhRangeQuery<LinkedList<T>> iter = pht.rangeQuery(dist, optionalDist, lKey);
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
        long[] lKey = new long[key.length];
        pre.pre(key, lKey);
        PhKnnQuery<LinkedList<T>> iter = pht.nearestNeighbour(nMin, PhDistanceF.THIS, null, lKey);
        return new PhKnnQueryMMF<>(iter, pht.getDim(), pre);
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
        long[] lKey = new long[key.length];
        pre.pre(key, lKey);
        PhKnnQuery<LinkedList<T>> iter = pht.nearestNeighbour(nMin, dist, null, lKey);
        return new PhKnnQueryMMF<>(iter, pht.getDim(), pre);
    }

    /**
     * Iterator class for floating point keys.
     * 
     * @param <T> value type
     */
    public static class PhIteratorMMF<T> implements PhIteratorBase<T, PhEntryMMF<T>> {
        private final PhIteratorBase<LinkedList<T>, ? extends PhEntry<LinkedList<T>>> iter;
        private Iterator<T> iter2 = null;
        protected final PreProcessorPointF pre;
        private final PhEntryMMF<T> buffer;

        protected PhIteratorMMF(PhIteratorBase<LinkedList<T>, ? extends PhEntry<LinkedList<T>>> iter, int dims,
                PreProcessorPointF pre) {
            this.iter = iter;
            this.pre = pre;
            this.buffer = new PhEntryMMF<>(new double[dims], null);
            resetInternal();
         }

        private void findNext() {
            if (iter2.hasNext()) {
                return;
            }
            while (iter.hasNext()) {
                PhEntry<LinkedList<T>> e = iter.nextEntryReuse();
                iter2 = e.getValue().iterator();
                if (iter2.hasNext()) {
                    pre.post(e.getKey(), buffer.key);
                    return;
                }
            }
            iter2 = null; // end of iterator
        }

        @Override
        public boolean hasNext() {
            return iter2 != null;
        }

        @Override
        public T next() {
            return nextValue();
        }

        @Override
        public PhEntryMMF<T> nextEntry() {
            checkNext();
            T value = iter2.next();
            findNext();
            return new PhEntryMMF<>(buffer.key.clone(), value);
        }

        @Override
        public PhEntryMMF<T> nextEntryReuse() {
            checkNext();
            T value = iter2.next();
            findNext();
            buffer.setValue(value);
            return buffer;
        }

        /**
         * @return the key of the next entry
         */
        public double[] nextKey() {
            checkNext();
            iter2.next();
            findNext();
            return buffer.getKey();
        }

        @Override
        public T nextValue() {
            checkNext();
            T value = iter2.next();
            findNext();
            return value;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private void resetInternal() {
            if (!iter.hasNext()) {
                iter2 = null;
                return;
            }
            PhEntry<LinkedList<T>> e = iter.nextEntryReuse();
            pre.post(e.getKey(), buffer.key);
            iter2 = e.getValue().iterator();
            findNext();
        }

        protected PhIteratorMMF<T> reset() {
            resetInternal();
            return this;
        }

        private void checkNext() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
        }
    }

    /**
     * Extent iterator class for floating point keys.
     * 
     * @param <T> value type
     */
    public static class PhExtentMMF<T> extends PhIteratorMMF<T> {
        private final PhExtent<LinkedList<T>> iter;

        protected PhExtentMMF(PhExtent<LinkedList<T>> iter, int dims, PreProcessorPointF pre) {
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
            super.reset();
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
        private final PhQuery<LinkedList<T>> q;

        protected PhQueryMMF(PhQuery<LinkedList<T>> iter, int dims, PreProcessorPointF pre) {
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
            super.reset();
        }
    }

    /**
     * Nearest neighbor query iterator class for floating point keys.
     * 
     * @param <T> value type
     */
    public static class PhKnnQueryMMF<T> implements PhIteratorBase<T, PhEntryDistMMF<T>> {
        private final long[] lCenter;
        private final PreProcessorPointF pre;
        private final PhEntryDistMMF<T> buffer;
        private final PhKnnQuery<LinkedList<T>> iter;
        private Iterator<T> iter2 = null;

        protected PhKnnQueryMMF(PhKnnQuery<LinkedList<T>> iter, int dims, PreProcessorPointF pre) {
            this.iter = iter;
            this.pre = pre;
            lCenter = new long[dims];
            buffer = new PhEntryDistMMF<>(new double[dims], null, Double.NaN);
            resetInternal();
        }

        private void resetInternal() {
            if (!iter.hasNext()) {
                iter2 = null;
                return; // empty result
            }
            PhEntry<LinkedList<T>> e = iter.nextEntryReuse();
            pre.post(e.getKey(), buffer.key);
            iter2 = e.getValue().iterator();
            findNextKnn();
        }

        private void findNextKnn() {
            if (iter2.hasNext()) {
                return;
            }
            while (iter.hasNext()) {
                PhEntryDist<LinkedList<T>> e = iter.nextEntryReuse();
                iter2 = e.getValue().iterator();
                if (iter2.hasNext()) {
                    pre.post(e.getKey(), buffer.key);
                    buffer.dist = e.dist();
                    return;
                }
            }
            iter2 = null; // end of iterator
        }

        @Override
        public boolean hasNext() {
            return iter2 != null;
        }

        @Override
        public T next() {
            return nextValue();
        }

        @Override
        public PhEntryDistMMF<T> nextEntry() {
            checkNextKnn();
            T value = iter2.next();
            findNextKnn();
            return new PhEntryDistMMF<>(buffer.key.clone(), value, buffer.dist);
        }

        @Override
        public PhEntryDistMMF<T> nextEntryReuse() {
            checkNextKnn();
            T value = iter2.next();
            findNextKnn();
            buffer.set(value);
            return buffer;
        }

        /**
         * @return the key of the next entry
         */
        public double[] nextKey() {
            checkNextKnn();
            iter2.next();
            findNextKnn();
            return buffer.getKey().clone();
        }

        @Override
        public T nextValue() {
            checkNextKnn();
            T value = iter2.next();
            findNextKnn();
            return value;
        }

        private void checkNextKnn() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
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
            iter.reset(nMin, dist, lCenter);
            resetInternal();
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
        private final PhRangeQuery<LinkedList<T>> q;

        protected PhRangeQueryMMF(PhRangeQuery<LinkedList<T>> iter, PhTree<LinkedList<T>> tree, PreProcessorPointF pre) {
            super(iter, tree.getDim(), pre);
            this.q = iter;
            this.lCenter = new long[tree.getDim()];
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
            super.reset();
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
        protected T value;

        /**
         * @param key   the key
         * @param value the value
         */
        public PhEntryMMF(double[] key, T value) {
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
     * Entry class for Double entries with distance information for nearest
     * neighbour queries.
     *
     * @param <T> value type of the entries
     */
    public static class PhEntryDistMMF<T> extends PhEntryMMF<T> {
        private double dist;

        /**
         * @param key   the key
         * @param value the value
         * @param dist  the distance to the center point
         */
        public PhEntryDistMMF(double[] key, T value, double dist) {
            super(key, value);
            this.dist = dist;
        }

        /**
         * @param value new value
         */
        public void set(T value) {
            this.value = value;
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
     * @param value  value
     * @param newKey new key
     * @return the value (can be {@code null}) associated with the updated key if
     *         the key could be updated, otherwise {@code null}.
     */
    public T update(double[] oldKey, T value, double[] newKey) {
        // TODO OPTIMIZE
        if (remove(oldKey, value)) {
            put(newKey, value);
            return value;
        }
        return null;
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
                e -> new PhEntryMMF<>(PhMapperK.toDouble(e.getKey()), e.getValue()));
    }

    /**
     * Same as {@link PhTreeMultiMapF2#queryAll(double[], double[])}, except that it
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

        ArrayList<R> list = new ArrayList<>();
        pht.queryAll(lLow, lUpp, maxResults, filter, e2 -> e2).forEach(eList -> {
            if (filter.isValid(eList.getKey())) {
                eList.getValue().forEach(t -> list.add(mapper.map(new PhEntry<>(eList.getKey(), t))));
            }
        });
        return list;
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
    public PhTree<LinkedList<T>> getInternalTree() {
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

    //    /**
    //     * @see java.util.Map#getOrDefault(Object, Object)
    //     * @param key          key
    //     * @param id           unique id of the value
    //     * @param defaultValue default value
    //     * @return actual value or default value
    //     */
    //    public T getOrDefault(double[] key, long id, T defaultValue) {
    //        T t = get(key, id);
    //        return t == null ? defaultValue : t;
    //    }

    /**
     * Insert key/value if key is not associated with any value yet.
     * @see java.util.Map#putIfAbsent(Object, Object)
     * @param key   key
     * @param value new value
     * @return current value or null if there was no association.
     */
    public Iterable<T> putIfAbsent(double[] key, T value) {
        MutableRef<LinkedList<T>> ref = new MutableRef<>();
        pht.compute(pre(key), (keyInternal, list) -> {
            if (list == null) {
                list = new LinkedList<>();
                list.add(value);
            } else {
                ref.set(list);
            }
            return list;
        });
        size += ref.get() == null ? 1 : 0;
        return ref.get();
    }

    /**
     * Replaces an existing entry with a new value.
     * @see java.util.Map#replace(Object, Object, Object)
     * @param key      key
     * @param oldValue old value
     * @param newValue new value
     * @return {@code true} if the value was replaced
     */
    public boolean replace(double[] key, T oldValue, T newValue) {
        // TODO size?
        return computeIfPresent(key, oldValue, (doubles, t) -> newValue) != null;
    }

    /**
     * Replaces all entries at a given position with a new value.
     * @see java.util.Map#replace(Object, Object)
     * @param key      key
     * @param newValue new value
     * @return {@code true} if the value was replaced
     */
    public T replace(double[] key, T newValue) {
        MutableRef<T> ref = new MutableRef<>();
        MutableInt i = new MutableInt(0);
        pht.computeIfPresent(pre(key), (doubles, list) -> {
            ref.set(list.get(0));
            i.set(list.size());
            list.clear();
            list.add(newValue);
            return list;
        });
        size = size - i.get() + 1;
        return ref.get();
    }

    /**
     * @see java.util.Map#computeIfAbsent(Object, Function)
     * @param key             key
     * @param mappingFunction mapping function
     * @return new value or null if none is associated
     */
    public T computeIfAbsent(double[] key, Function<double[], ? extends T> mappingFunction) {
        MutableRef<T> ref = new MutableRef<>();
        pht.compute(pre(key), (keyInternal, list) -> {
            if (list == null) {
                list = new LinkedList<>();
                T value = mappingFunction.apply(key);
                if (value != null) {
                    list.add(value);
                    ref.set(value);
                }
            }
            return list;
        });
        size += ref.get() != null ? 1 : 0;
        return ref.get();
    }

    /**
     * @see java.util.Map#computeIfPresent(Object, BiFunction)
     * @param key               key
     * @param value             value
     * @param remappingFunction mapping function
     * @return new value or null if none is associated
     */
    public T computeIfPresent(double[] key, T value,
            BiFunction<double[], ? super T, ? extends T> remappingFunction) {
        MutableRef<T> ref = new MutableRef<>();
        pht.computeIfPresent(pre(key), (keyInternal, list) -> {
            if (list == null) {
                return null; // not present
            }
            Iterator<T> it = list.iterator();
            while (it.hasNext()) {
                T valueOld = it.next();
                if (Objects.equals(value, valueOld)) {
                    T valueNew = remappingFunction.apply(key, valueOld);
                    it.remove();
                    list.add(valueNew);
                    ref.set(valueNew);
                }
            }
            return list;
        });
        return ref.get();
    }

    /**
     * @see java.util.Map#compute(Object, BiFunction)
     * @param key               key
     * @param value             value
     * @param remappingFunction mapping function
     * @return new value or null if none is associated
     */
    public T compute(double[] key, T value,
            BiFunction<double[], ? super T, ? extends T> remappingFunction) {
        MutableRef<T> ref = new MutableRef<>();
        pht.compute(pre(key), (keyInternal, list) -> {
                    if (list == null) {
                        T valueNew = remappingFunction.apply(key, null);
                        if (valueNew != null) {
                            list = new LinkedList<>();
                            list.add(valueNew);
                            ref.set(valueNew);
                        }
                    } else {
                        Iterator<T> it = list.iterator();
                        T valueOld = null;
                        while (it.hasNext() && !Objects.equals(value, valueOld = it.next()));
                        T valueNew = remappingFunction.apply(key, null);
                        if (valueNew != null) {
                            if (valueOld != null) {
                                it.remove();
                            }
                            list.add(valueNew);
                            ref.set(valueNew);
                        }
                    }
                    return list;
                }
        );
        size++;
        return ref.get();
    }

    private long[] pre(double[] key) {
        long[] lKey = new long[key.length];
        pre.pre(key, lKey);
        return lKey;
    }
}
