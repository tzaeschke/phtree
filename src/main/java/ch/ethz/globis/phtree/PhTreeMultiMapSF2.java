/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
 * Copyright 2022-2025 Tilmann Zäschke. All rights reserved.
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
import ch.ethz.globis.phtree.pre.PreProcessorRangeF;
import ch.ethz.globis.phtree.util.MutableInt;
import ch.ethz.globis.phtree.util.MutableRef;
import ch.ethz.globis.phtree.util.PhIteratorBase;
import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.util.unsynced.ObjectPool;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * k-dimensional index (quad-/oct-/n-tree). Supports key/value pairs.
 * <p>
 * The multimap allows, unlike plain PH-Trees, to store more than one value per
 * coordinate.
 * While this multimap allows multiple identical key/value pairs, the API is optimized for the assumption that
 * key/value pairs are unique, i.e. either the key or the value of any given pair is different.
 * The implication is that API methods that have to match key/value paris, such as `putIfAbsent()` or `update()` will
 * process (or return) at most one existing pair or value. This does not affect query methods which will always return
 * all matching pairs.
 * <p>
 * This PhTreeMultiMapF2 uses a different approach than PhTreeMultiMapF.
 * PhTreeMultiMapF2 stores either directly a value per coordinate or, if more than one value needs to be stored,
 * a collection (list) of value at a given coordinate.
 *
 * @param <T> The value type of the tree
 * @author ztilmann (Tilmann Zaeschke)
 */
public class PhTreeMultiMapSF2<T> {

    @FunctionalInterface
    public interface ComputeFn<V> {
        V apply(double[] lower, double[] upper);
    }

    @FunctionalInterface
    public interface ComputeVFn<V> {
        V apply(double[] lower, double[] upper, V value);
    }

    public static final int DEFAULT_SIZE = 2;
    private final int dims;
    private final PhTree<Object> pht;
    private final PreProcessorRangeF pre;
    private final PhDistanceSF dist;
    private final double[] qMIN;
    private final double[] qMAX;
    private final ObjectPool<ArrayList<T>> pool = ObjectPool.create(10, () -> new ArrayList<>(DEFAULT_SIZE));
    private int size = 0;

    protected PhTreeMultiMapSF2(int dim, PreProcessorRangeF pre) {
        this(PhTree.create(dim * 2), pre);
    }

    protected PhTreeMultiMapSF2(PhTree<Object> tree, PreProcessorRangeF pre) {
        this.dims = tree.getDim() / 2;
        if (dims * 2 != tree.getDim()) {
            throw new IllegalArgumentException("The backing tree's DIM must be a multiple of 2");
        }
        this.pht = tree;
        this.pre = pre;
        this.dist = new PhDistanceSFEdgeDist(pre, dims);
        // this.dist = new PhDistanceSFCenterDist(pre, dims);
        qMIN = new double[dims];
        Arrays.fill(qMIN, Double.NEGATIVE_INFINITY);
        qMAX = new double[dims];
        Arrays.fill(qMAX, Double.POSITIVE_INFINITY);
    }

    /**
     * Create a new tree with the specified number of dimensions.
     *
     * @param dim number of dimensions
     * @param <T> value type of the tree
     * @return PhTreeMultiMapF2
     */
    public static <T> PhTreeMultiMapSF2<T> create(int dim) {
        return new PhTreeMultiMapSF2<>(dim, new PreProcessorRangeF.IEEE(dim));
    }

    /**
     * Create a new tree with the specified number of dimensions and a custom
     * preprocessor.
     *
     * @param dim number of dimensions
     * @param pre The preprocessor to be used
     * @param <T> value type of the tree
     * @return PhTreeMultiMapF2
     */
    public static <T> PhTreeMultiMapSF2<T> create(int dim, PreProcessorRangeF pre) {
        return new PhTreeMultiMapSF2<>(dim, pre);
    }

    /**
     * @return the number of entries in the tree
     */
    public int size() {
        return size;
    }

    /**
     * Inserts a new ranged object into the tree.
     *
     * @param lower lower left corner
     * @param upper upper right corner
     * @param value the value
     * @return `true` (this implementation allows duplicate key/value entries)
     */
    public boolean put(double[] lower, double[] upper, T value) {
        pht.compute(pre(lower, upper), (keyInternal, entry) -> {
            if (entry == null) {
                return value;
            }
            ArrayList<T> list;
            if (entry instanceof ArrayList) {
                list = asList(entry);
            } else {
                list = newList();
                list.add(asT(entry));
            }
            list.add(value);
            return list;
        });
        size++;
        return true;
    }

    /**
     * Check whether an entry with the specified coordinates exists in the tree.
     *
     * @param lower lower left corner
     * @param upper upper right corner
     * @param value the value
     * @return true if the entry was found
     */
    public boolean contains(double[] lower, double[] upper, T value) {
        Object v = pht.get(pre(lower, upper));
        if (v != null) {
            if (v instanceof ArrayList) {
                return asList(v).contains(value);
            }
            return Objects.equals(value, v);
        }
        return false;
    }

    /**
     * @param lower lower left corner
     * @param upper upper right corner
     * @return the value associated with the key or 'null' if the key was not found
     */
    @SuppressWarnings("unchecked")
    public Iterable<T> get(double[] lower, double[] upper) {
        Object v = pht.get(pre(lower, upper));
        if (v instanceof ArrayList) {
            return (Iterable<T>) v;
        } else if (v == null) {
            return Collections.emptyList();
        }
        ArrayList<T> list = new ArrayList<>(1);
        list.add(asT(v));
        return list;
    }

    /**
     * Removes all values that exactly match lower/upper.
     *
     * @param lower lower left corner
     * @param upper upper right corner
     * @return the value or {@code null} if no entry existed
     * @see PhTree#remove(long...)
     */
    public Iterable<T> remove(double[] lower, double[] upper) {
        Object v = pht.remove(pre(lower, upper));
        if (v instanceof ArrayList) {
            ArrayList<T> list = asList(v);
            size -= list.size();
            return list;
        }
        if (v == null) {
            return Collections.emptyList();
        }
        size--;
        ArrayList<T> list = new ArrayList<>(1);
        list.add(asT(v));
        return list;
    }

    /**
     * @param lower lower left corner
     * @param upper upper right corner
     * @param value value
     * @return {@code true} if the value was removed
     * @see Map#remove(Object, Object)
     */
    public boolean remove(double[] lower, double[] upper, T value) {
        MutableInt i = new MutableInt(0);
        pht.computeIfPresent(pre(lower, upper), (keyInternal, entry) -> {
            if (entry == null) {
                return null;
            }
            if (entry instanceof ArrayList) {
                ArrayList<T> list = asList(entry);
                if (list.remove(value)) {
                    i.inc();
                }
                if (list.size() == 1) {
                    T v = list.get(0);
                    list.clear();
                    pool.offer(list);
                    return v;
                }
                return list;
            } else {
                if (Objects.equals(value, entry)) {
                    i.inc();
                    return null;
                }
                return entry;
            }
        });
        size -= i.get();
        return i.get() > 0;
    }

    /**
     * @return an iterator over all elements in the tree
     */
    public PhExtentF<T> queryExtent() {
        return new PhExtentF<>(pht.queryExtent(), dims, pre);
    }

    /**
     * @param e an entry that describes the query rectangle
     * @return a query iterator
     * @see #queryInclude(double[], double[])
     */
    public PhQuerySF<T> queryInclude(PhEntrySF<T> e) {
        return queryInclude(e.lower(), e.upper());
    }

    /**
     * @param e an entry that describes the query rectangle
     * @return a query iterator
     * @see #queryIntersect(double[], double[])
     */
    public PhQuerySF<T> queryIntersect(PhEntrySF<T> e) {
        return queryIntersect(e.lower(), e.upper());
    }

    /**
     * Query for all bodies that are fully included in the query rectangle.
     *
     * @param lower 'lower left' corner of query rectangle
     * @param upper 'upper right' corner of query rectangle
     * @return Iterator over all matching elements.
     */
    public PhQuerySF<T> queryInclude(double[] lower, double[] upper) {
        long[] lUpp = new long[lower.length << 1];
        long[] lLow = new long[lower.length << 1];
        pre.pre(lower, lower, lLow);
        pre.pre(upper, upper, lUpp);
        return new PhQuerySF<>(pht.query(lLow, lUpp), dims, pre, false);
    }

    /**
     * Query for all bodies that are included in or partially intersect with the query rectangle.
     *
     * @param lower 'lower left' corner of query rectangle
     * @param upper 'upper right' corner of query rectangle
     * @return Iterator over all matching elements.
     */
    public PhQuerySF<T> queryIntersect(double[] lower, double[] upper) {
        long[] lUpp = new long[lower.length << 1];
        long[] lLow = new long[lower.length << 1];
        pre.pre(qMIN, lower, lLow);
        pre.pre(upper, qMAX, lUpp);
        return new PhQuerySF<>(pht.query(lLow, lUpp), dims, pre, true);
    }

    /**
     * Locate nearest neighbours for a given point in space.
     *
     * @param nMin   number of entries to be returned. More entries may or may not be returned if
     *               several points have the same distance.
     * @param center the center point
     * @return The query iterator.
     */
    public PhKnnQuerySF<T> nearestNeighbour(int nMin, double... center) {
        return nearestNeighbour(nMin, dist, center);
    }

    /**
     * Locate nearest neighbours for a given point in space.
     *
     * @param nMin             number of entries to be returned. More entries may or may not be returned if
     *                         several points have the same distance.
     * @param distanceFunction A distance function for rectangle data. This parameter is optional,
     *                         passing a {@code null} will use the default distance function.
     * @param center           the center point
     * @return The query iterator.
     */
    public PhKnnQuerySF<T> nearestNeighbour(int nMin, PhDistanceSF distanceFunction, double... center) {
        long[] lCenter = new long[2 * dims];
        pre.pre(center, center, lCenter);
        PhDistanceSF df = distanceFunction == null ? dist : distanceFunction;
        return new PhKnnQuerySF<>(pht.nearestNeighbour(nMin, df, null, lCenter), dims, pre);
    }

    public int getDim() {
        return dims;
    }

    /**
     * Update the key of an entry. Update may fail if the old key does not exist, or
     * if the new key already exists.
     *
     * @param oldLower old lower left corner
     * @param oldUpper old upper right corner
     * @param value    value
     * @param newLower new lower left corner
     * @param newUpper new upper right corner
     * @return the value (can be {@code null}) associated with the updated key if
     * the key could be updated, otherwise {@code null}.
     */
    public boolean update(double[] oldLower, double[] oldUpper, T value, double[] newLower, double[] newUpper) {
        // TODO OPTIMIZE
        if (remove(oldLower, oldUpper, value)) {
            put(newLower, newUpper, value);
            return true;
        }
        return false;
    }

    /**
     * Clear the tree.
     */
    public void clear() {
        pht.clear();
    }

    /**
     * @return the internal PhTree that backs this PhTreeF.
     */
    public PhTree<Object> getInternalTree() {
        return pht;
    }

    /**
     * @return the preprocessor of this tree.
     */
    public PreProcessorRangeF getPreprocessor() {
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

    /**
     * Insert key/value if key/value pair does not exist yet.
     *
     * @param lower lower left corner
     * @param upper upper right corner
     * @param value new value
     * @return current value or null if there was no association.
     * @see Map#putIfAbsent(Object, Object)
     */
    public T putIfAbsent(double[] lower, double[] upper, T value) {
        MutableRef<T> ref = new MutableRef<>();
        compute(lower, upper, value, (lo, up, v) -> v == null ? value : ref.set(v).get());
        return ref.get();
    }

    /**
     * Replaces an existing entry with a new value.
     *
     * @param lower    lower left corner
     * @param upper    upper right corner
     * @param oldValue old value
     * @param newValue new value
     * @return {@code true} if the value was replaced
     * @see Map#replace(Object, Object, Object)
     */
    public boolean replace(double[] lower, double[] upper, T oldValue, T newValue) {
        return computeIfPresent(lower, upper, oldValue, (lo, up, t) -> newValue) != null;
    }

    /**
     * @param lower           lower left corner
     * @param upper           upper right corner
     * @param value           value
     * @param mappingFunction mapping function
     * @return new value or null if none is associated
     * @see Map#computeIfAbsent(Object, Function)
     */
    public T computeIfAbsent(double[] lower, double[] upper, T value, ComputeFn<T> mappingFunction) {
        MutableRef<T> ref = new MutableRef<>();
        compute(lower, upper, value, (lo, up, v) -> v == null ? ref.set(mappingFunction.apply(lo, up)).get() : v);
        return ref.get();
    }

    /**
     * @param lower             lower left corner
     * @param upper             upper right corner
     * @param value             value
     * @param remappingFunction mapping function
     * @return new value or null if none is associated
     * @see Map#computeIfPresent(Object, BiFunction)
     */
    public T computeIfPresent(double[] lower, double[] upper, T value, ComputeVFn<T> remappingFunction) {
        return compute(lower, upper, value, (lo, up, v) -> v == null ? null : remappingFunction.apply(lo, up, v));
    }

    /**
     * @param lower             lower left corner
     * @param upper             upper right corner
     * @param value             value
     * @param remappingFunction mapping function
     * @return new value or null if none is associated
     * @see Map#compute(Object, BiFunction)
     */
    public T compute(double[] lower, double[] upper, T value, ComputeVFn<T> remappingFunction) {
        MutableRef<T> ref = new MutableRef<>();
        MutableInt delta = new MutableInt(0);
        pht.compute(pre(lower, upper), (keyInternal, entry) -> {
            if (entry instanceof ArrayList) {
                ArrayList<T> list = asList(entry);
                ListIterator<T> it = list.listIterator();
                while (it.hasNext()) {
                    T valueOld = it.next();
                    if (Objects.equals(value, valueOld)) {
                        T valueNew = remappingFunction.apply(lower, upper, valueOld);
                        if (valueNew != null) {
                            it.set(valueNew);
                            ref.set(valueNew);
                            return list;
                        }
                        it.remove();
                        delta.dec();
                        if (list.size() == 1) {
                            T v = list.get(0);
                            list.clear();
                            pool.offer(list);
                            return v;
                        }
                        return list;
                    }
                }
                T valueNew = remappingFunction.apply(lower, upper, null);
                if (valueNew != null) {
                    list.add(valueNew);
                    ref.set(valueNew);
                    delta.inc();
                }
                return list.isEmpty() ? null : list;
            } else {
                T arg1 = Objects.equals(value, entry) ? asT(entry) : null;
                ref.set(remappingFunction.apply(lower, upper, arg1));
                if (ref.get() != null) {
                    delta.inc();
                    ArrayList<T> list = newList();
                    list.add(asT(entry));
                    list.add(ref.get());
                    return list;
                }
                return ref.get();
            }
        });
        this.size += delta.get();
        return ref.get();
    }

    private long[] pre(double[] lower, double[] upper) {
        long[] lKey = new long[lower.length * 2];
        pre.pre(lower, upper, lKey);
        return lKey;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<T> asList(Object obj) {
        return (ArrayList<T>) obj;
    }

    @SuppressWarnings("unchecked")
    private T asT(Object obj) {
        return (T) obj;
    }

    private ArrayList<T> newList() {
        return pool.get();
    }

    /**
     * Iterator class for floating point keys.
     *
     * @param <T> value type
     */
    public static class PhIteratorSF<T> implements PhIteratorBase<T, PhEntrySF<T>> {
        protected final PreProcessorRangeF pre;
        private final PhIteratorBase<Object, ? extends PhEntry<Object>> iter;
        private final PhEntrySF<T> buffer;
        // For storing non-list entries
        private final ArrayList<T> bufferList = new ArrayList<>();
        private PhEntry<Object> internalEntry;
        private ArrayList<T> currentList;
        private int pos = Integer.MAX_VALUE;

        protected PhIteratorSF(PhIteratorBase<Object, ? extends PhEntry<Object>> iter, int dims, PreProcessorRangeF pre) {
            this.iter = iter;
            this.pre = pre;
            this.buffer = new PhEntrySF<>(new double[dims], new double[dims], null);
            this.bufferList.add(null); // empty entry
            findNextInternal();
        }

        private void findNext() {
            if (pos < currentList.size()) {
                return;
            }
            findNextInternal();
        }

        @SuppressWarnings("unchecked")
        private void findNextInternal() {
            if (iter.hasNext()) {
                internalEntry = iter.nextEntryReuse();
                pos = 0;
                if (internalEntry.getValue() instanceof ArrayList) {
                    currentList = (ArrayList<T>) internalEntry.getValue();
                } else {
                    bufferList.set(0, (T) internalEntry.getValue());
                    currentList = bufferList;
                }
                return;
            }
            pos = Integer.MAX_VALUE; // end of iterator
        }

        private T getNextValue() {
            return currentList.get(pos++);
        }

        @Override
        public boolean hasNext() {
            return pos < Integer.MAX_VALUE;
        }

        @Override
        public T next() {
            return nextValue();
        }

        @Override
        public PhEntrySF<T> nextEntry() {
            checkNext();
            pre.post(internalEntry.getKey(), buffer.lower(), buffer.upper());
            buffer.setValue(getNextValue());
            PhEntrySF<T> ret = new PhEntrySF<>(buffer.lower().clone(), buffer.upper().clone(), buffer.value());
            findNext();
            return ret;
        }

        @Override
        public PhEntrySF<T> nextEntryReuse() {
            checkNext();
            pre.post(internalEntry.getKey(), buffer.lower(), buffer.upper());
            buffer.setValue(getNextValue());
            findNext();
            return buffer;
        }

        @Override
        public T nextValue() {
            checkNext();
            T value = getNextValue();
            findNext();
            return value;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        protected PhIteratorSF<T> reset() {
            pos = Integer.MAX_VALUE;
            findNextInternal();
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
    public static class PhExtentF<T> extends PhIteratorSF<T> {
        private final PhExtent<Object> iter;

        protected PhExtentF(PhExtent<Object> iter, int dims, PreProcessorRangeF pre) {
            super(iter, dims, pre);
            this.iter = iter;
        }

        /**
         * Restarts the extent iterator.
         *
         * @return this
         */
        @Override
        public PhExtentF<T> reset() {
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
    public static class PhQuerySF<T> extends PhIteratorSF<T> {
        private final long[] lMin;
        private final long[] lMax;
        private final PhQuery<Object> q;
        private final double[] qMIN;
        private final double[] qMAX;
        private final boolean intersect;

        protected PhQuerySF(PhQuery<Object> iter, int dims, PreProcessorRangeF pre, boolean intersect) {
            super(iter, dims, pre);
            q = iter;
            qMIN = new double[dims];
            Arrays.fill(qMIN, Double.NEGATIVE_INFINITY);
            qMAX = new double[dims];
            Arrays.fill(qMAX, Double.POSITIVE_INFINITY);
            this.intersect = intersect;
            lMin = new long[dims * 2];
            lMax = new long[dims * 2];
        }

        /**
         * Restarts the query with a new query rectangle.
         *
         * @param lower minimum values of query rectangle
         * @param upper maximum values of query rectangle
         * @return this
         */
        public PhQuerySF<T> reset(double[] lower, double[] upper) {
            if (intersect) {
                pre.pre(qMIN, lower, lMin);
                pre.pre(upper, qMAX, lMax);
            } else {
                //include
                pre.pre(lower, lower, lMin);
                pre.pre(upper, upper, lMax);
            }
            q.reset(lMin, lMax);
            super.reset();
            return this;
        }
    }

    /**
     * Nearest neighbor query iterator class for floating point keys.
     *
     * @param <T> value type
     */
    public static class PhKnnQuerySF<T> implements PhIteratorBase<T, PhEntryDistSF<T>> {
        private final PreProcessorRangeF pre;
        private final PhKnnQuery<Object> iter;
        private final PhEntryDistSF<T> buffer;
        private final ArrayList<T> bufferList = new ArrayList<>();
        private PhEntryDist<Object> internalEntry;
        private ArrayList<T> currentList;
        private int pos = Integer.MAX_VALUE;

        protected PhKnnQuerySF(PhKnnQuery<Object> iter, int dims, PreProcessorRangeF pre) {
            this.iter = iter;
            this.pre = pre;
            this.buffer = new PhEntryDistSF<>(new double[dims], new double[dims], null, Double.NaN);
            this.bufferList.add(null);
            findNextInternal();
        }

        private void findNextKnn() {
            if (pos < currentList.size()) {
                return;
            }
            findNextInternal();
        }

        @SuppressWarnings("unchecked")
        private void findNextInternal() {
            if (iter.hasNext()) {
                internalEntry = iter.nextEntryReuse();
                pos = 0;
                if (internalEntry.getValue() instanceof ArrayList) {
                    currentList = (ArrayList<T>) internalEntry.getValue();
                } else {
                    bufferList.set(0, (T) internalEntry.getValue());
                    currentList = bufferList;
                }
                return;
            }
            pos = Integer.MAX_VALUE; // end of iterator
        }

        private T getNextValue() {
            return currentList.get(pos++);
        }

        @Override
        public boolean hasNext() {
            return pos < Integer.MAX_VALUE;
        }

        @Override
        public T next() {
            return nextValue();
        }

        @Override
        public PhEntryDistSF<T> nextEntry() {
            checkNextKnn();
            pre.post(internalEntry.getKey(), buffer.lower(), buffer.upper());
            buffer.setValueDist(getNextValue(), internalEntry.dist());
            PhEntryDistSF<T> ret = new PhEntryDistSF<>(buffer.lower().clone(), buffer.upper().clone(), buffer.value(), buffer.dist());
            findNextKnn();
            return ret;
        }

        @Override
        public PhEntryDistSF<T> nextEntryReuse() {
            checkNextKnn();
            pre.post(internalEntry.getKey(), buffer.lower(), buffer.upper());
            buffer.setValueDist(getNextValue(), internalEntry.dist());
            findNextKnn();
            return buffer;
        }

        @Override
        public T nextValue() {
            checkNextKnn();
            T value = getNextValue();
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
        public PhKnnQuerySF<T> reset(int nMin, PhDistance dist, double[] center) {
            pos = Integer.MAX_VALUE;
            long[] lCenter = new long[center.length * 2];
            pre.pre(center, center, lCenter);
            iter.reset(nMin, dist, lCenter);
            findNextInternal();
            return this;
        }
    }
}
