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
 * While this multimap allows multiple identical key/value pairs, the API is optimized for the assumption that
 * key/value pairs are unique, i.e. either the key or the value of any given pair is different.
 * The implication is that API methods that have to match key/value paris, such as `putIfAbsent()` or `update()` will
 * process or return at most one existing pair or value. This does not affect query methods which will always return
 * all matching paris.
 * <p>
 * This PhTreeMultiMapF2 uses a different approach than PhTreeMultiMapF.
 * PhTreeMultiMapF2 stores a collection (list) at each coordinate in order to handle multiple entries per coordinate.
 *
 * @param <T> The value type of the tree
 * @author ztilmann (Tilmann Zaeschke)
 */
public class PhTreeMultiMapF3<T> {

    public static int SIZE = 2;
    private final PhTree<Object> pht;
    private final PreProcessorPointF pre;
    private int size = 0;

    protected PhTreeMultiMapF3(int dim, PreProcessorPointF pre) {
        this.pht = PhTree.create(dim);
        this.pre = pre;
    }

    protected PhTreeMultiMapF3(PhTree<Object> tree) {
        this.pht = tree;
        this.pre = new PreProcessorPointF.IEEE();
    }

    /**
     * Create a new tree with the specified number of dimensions.
     *
     * @param dim number of dimensions
     * @param <T> value type of the tree
     * @return PhTreeMultiMapF2
     */
    public static <T> PhTreeMultiMapF3<T> create(int dim) {
        return new PhTreeMultiMapF3<>(dim, new PreProcessorPointF.IEEE());
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
    public static <T> PhTreeMultiMapF3<T> create(int dim, PreProcessorPointF pre) {
        return new PhTreeMultiMapF3<>(dim, pre);
    }

    /**
     * Create a new PhTreeF as a wrapper around an existing PhTree.
     *
     * @param tree another tree
     * @param <T>  value type of the tree
     * @return PhTreeMultiMapF2
     */
    public static <T> PhTreeMultiMapF3<T> wrap(PhTree<Object> tree) {
        return new PhTreeMultiMapF3<>(tree);
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
        pht.compute(pre(key), (keyInternal, entry) -> {
            if (entry == null) {
                return value;
            }
            ArrayList<T> list;
            if (entry instanceof ArrayList) {
                list = (ArrayList<T>) entry;
            } else {
                list = new ArrayList<>(SIZE);
                list.add((T) entry);
            }
            list.add(value);
            return list;
        });
        size++;
        return true;
    }

    /**
     * @param key   key
     * @param value the value
     * @return true if the key exists in the tree
     */
    public boolean contains(double[] key, T value) {
        Object v = pht.get(pre(key));
        if (v != null) {
            if (v instanceof ArrayList) {
                return ((ArrayList<T>) v).contains(value);
            }
            return Objects.equals(value, v);
        }
        return false;
    }

    /**
     * @param key the key
     * @return the value associated with the key or 'null' if the key was not found
     */
    public Iterable<T> get(double[] key) {
        Object v = pht.get(pre(key));
        if (v instanceof ArrayList) {
            return (Iterable<T>) v;
        } else if (v == null) {
            return Collections.emptyList();
        }
        ArrayList<T> list = new ArrayList<>(1);
        list.add((T) v);
        return list;
    }

    /**
     * @param key key
     * @return {@code true} if the value was removed
     * @see Map#remove(Object)
     */
    public Iterable<T> remove(double[] key) {
        Object v = pht.remove(pre(key));
        if (v instanceof ArrayList) {
            ArrayList<T> list = (ArrayList<T>) v;
            size -= list.size();
            return list;
        }
        if (v == null) {
            return Collections.emptyList();
        }
        size--;
        ArrayList<T> list = new ArrayList<>(1);
        list.add((T) v);
        return list;
    }

    /**
     * @param key   key
     * @param value value
     * @return {@code true} if the value was removed
     * @see Map#remove(Object, Object)
     */
    public boolean remove(double[] key, T value) {
        MutableInt i = new MutableInt(0);
        pht.computeIfPresent(pre(key), (keyInternal, entry) -> {
            // TODO Use pooling for List?
            if (entry == null) {
                return null;
            }
            if (entry instanceof ArrayList) {
                ArrayList<T> list = (ArrayList<T>) entry;
                if (list.remove(value)) {
                    i.inc();
                }
                if (list.size() == 1) {
                    return list.get(0);
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
    public PhRangeQueryMMF<T> rangeQuery(double dist, double... center) {
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
    public PhRangeQueryMMF<T> rangeQuery(double dist, PhDistance optionalDist, double... center) {
        if (optionalDist == null) {
            optionalDist = PhDistanceF.THIS;
        }
        long[] lKey = new long[center.length];
        pre.pre(center, lKey);
        PhRangeQuery<Object> iter = pht.rangeQuery(dist, optionalDist, lKey);
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
    public PhKnnQueryMMF<T> nearestNeighbour(int nMin, double... key) {
        long[] lKey = new long[key.length];
        pre.pre(key, lKey);
        PhKnnQuery<Object> iter = pht.nearestNeighbour(nMin, PhDistanceF.THIS, null, lKey);
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
    public PhKnnQueryMMF<T> nearestNeighbour(int nMin, PhDistance dist, double... key) {
        long[] lKey = new long[key.length];
        pre.pre(key, lKey);
        PhKnnQuery<Object> iter = pht.nearestNeighbour(nMin, dist, null, lKey);
        return new PhKnnQueryMMF<>(iter, pht.getDim(), pre);
    }

    /**
     * Update the key of an entry. Update may fail if the old key does not exist, or
     * if the new key already exists.
     *
     * @param oldKey old key
     * @param value  value
     * @param newKey new key
     * @return the value (can be {@code null}) associated with the updated key if
     * the key could be updated, otherwise {@code null}.
     */
    public boolean update(double[] oldKey, T value, double[] newKey) {
        // TODO OPTIMIZE
        if (remove(oldKey, value)) {
            put(newKey, value);
            return true;
        }
        return false;
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
        return queryAll(min, max, Integer.MAX_VALUE, null, e -> new PhEntryMMF<>(PhMapperK.toDouble(e.getKey()), e.getValue()));
    }

    /**
     * Same as {@link PhTreeMultiMapF3#queryAll(double[], double[])}, except that it
     * also accepts a limit for the result size, a filter and a mapper.
     *
     * @param min        min key
     * @param max        max key
     * @param maxResults maximum result count
     * @param filter     filter object (optional)
     * @param mapper     mapper object (optional)
     * @param <R>        value type
     * @return List of query results
     */
    public <R> List<R> queryAll(double[] min, double[] max, int maxResults, PhFilter filter, PhMapper<T, R> mapper) {
        long[] lUpp = new long[min.length];
        long[] lLow = new long[max.length];
        pre.pre(min, lLow);
        pre.pre(max, lUpp);

        ArrayList<R> list = new ArrayList<>(SIZE);
        pht.queryAll(lLow, lUpp, maxResults, filter, e2 -> e2).forEach(entry -> {
            if (filter.isValid(entry.getKey())) {
                if (entry.getValue() instanceof ArrayList) {
                    ArrayList<T> eList = (ArrayList<T>) entry.getValue();
                    eList.forEach(t -> list.add(mapper.map(new PhEntry<>(entry.getKey(), t))));
                } else {
                    list.add(mapper.map((PhEntry<T>) new PhEntry<>(entry.getKey(), entry.getValue())));
                }
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
     * @return the internal PhTree that backs this PhTreeF.
     */
    public PhTree<Object> getInternalTree() {
        return pht;
    }

    /**
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

    /**
     * Insert key/value if key/value pair does not exist yet.
     *
     * @param key   key
     * @param value new value
     * @return current value or null if there was no association.
     * @see Map#putIfAbsent(Object, Object)
     */
    public T putIfAbsent(double[] key, T value) {
        MutableRef<T> ref = new MutableRef<>();
        compute(key, value, (k,  v) -> v == null ? value : ref.set(v).get());
        return ref.get();
    }

    /**
     * Replaces an existing entry with a new value.
     *
     * @param key      key
     * @param oldValue old value
     * @param newValue new value
     * @return {@code true} if the value was replaced
     * @see Map#replace(Object, Object, Object)
     */
    public boolean replace(double[] key, T oldValue, T newValue) {
        return computeIfPresent(key, oldValue, (doubles, t) -> newValue) != null;
    }

    /**
     * @param key             key
     * @param value           value
     * @param mappingFunction mapping function
     * @return new value or null if none is associated
     * @see Map#computeIfAbsent(Object, Function)
     */
    public T computeIfAbsent(double[] key, T value, Function<double[], ? extends T> mappingFunction) {
        MutableRef<T> ref = new MutableRef<>();
        compute(key, value, (k,  v) -> v == null ? ref.set(mappingFunction.apply(k)).get() : v);
        return ref.get();
    }

    /**
     * @param key               key
     * @param value             value
     * @param remappingFunction mapping function
     * @return new value or null if none is associated
     * @see Map#computeIfPresent(Object, BiFunction)
     */
    public T computeIfPresent(double[] key, T value, BiFunction<double[], ? super T, ? extends T> remappingFunction) {
        return compute(key, value, (k, v) -> v == null ? null : remappingFunction.apply(k, v));
    }

    /**
     * @param key               key
     * @param value             value
     * @param remappingFunction mapping function
     * @return new value or null if none is associated
     * @see Map#compute(Object, BiFunction)
     */
    public T compute(double[] key, T value, BiFunction<double[], ? super T, ? extends T> remappingFunction) {
        MutableRef<T> ref = new MutableRef<>();
        MutableInt delta = new MutableInt(0);
        pht.compute(pre(key), (keyInternal, entry) -> {
            if (entry instanceof ArrayList) {
                ArrayList<T> list = (ArrayList<T>) entry;
                ListIterator<T> it = list.listIterator();
                while (it.hasNext()) {
                    T valueOld = it.next();
                    if (Objects.equals(value, valueOld)) {
                        T valueNew = remappingFunction.apply(key, valueOld);
                        if (valueNew != null) {
                            it.set(valueNew);
                            ref.set(valueNew);
                            return list;
                        }
                        it.remove();
                        delta.dec();
                        return list.size() == 1 ? list.get(0) : list;
                    }
                }
                T valueNew = remappingFunction.apply(key, null);
                if (valueNew != null) {
                    list.add(valueNew);
                    ref.set(valueNew);
                    delta.inc();
                }
                return list.isEmpty() ? null : list;
            } else {
                T arg1 = Objects.equals(value, entry) ? (T) entry : null;
                ref.set(remappingFunction.apply(key, arg1));
                if (ref.get() != null) {
                    delta.inc();
                    ArrayList<T> list = new ArrayList<>();
                    list.add((T) entry);
                    list.add(ref.get());
                    return list;
                }
                return ref.get();
            }
        });
        this.size += delta.get();
        return ref.get();
    }

    private long[] pre(double[] key) {
        long[] lKey = new long[key.length];
        pre.pre(key, lKey);
        return lKey;
    }

    /**
     * Iterator class for floating point keys.
     *
     * @param <T> value type
     */
    public static class PhIteratorMMF<T> implements PhIteratorBase<T, PhEntryMMF<T>> {
        protected final PreProcessorPointF pre;
        private final PhIteratorBase<Object, ? extends PhEntry<Object>> iter;
        private final PhEntryMMF<T> buffer;
        // For storing non-list entries
        private final ArrayList<T> bufferList = new ArrayList<>();
        private Iterator<T> iter2 = null;

        protected PhIteratorMMF(PhIteratorBase<Object, ? extends PhEntry<Object>> iter, int dims, PreProcessorPointF pre) {
            this.iter = iter;
            this.pre = pre;
            this.buffer = new PhEntryMMF<>(new double[dims], null);
            this.bufferList.add(null); // empty entry
            resetInternal();
        }

        private void resetInternal() {
            if (!iter.hasNext()) {
                iter2 = null;
                return;
            }
            PhEntry<Object> e = iter.nextEntryReuse();
            pre.post(e.getKey(), buffer.key);
            if (e.getValue() instanceof ArrayList) {
                iter2 = ((ArrayList<T>) e.getValue()).iterator();
            } else {
                bufferList.set(0, (T) e.getValue());
                iter2 = bufferList.iterator();
            }
            findNext();
        }

        private void findNext() {
            if (iter2.hasNext()) {
                return;
            }
            if (iter.hasNext()) {
                PhEntry<Object> e = iter.nextEntryReuse();
                pre.post(e.getKey(), buffer.key);
                if (e.getValue() instanceof ArrayList) {
                    iter2 = ((ArrayList<T>) e.getValue()).iterator();
                } else {
                    bufferList.set(0, (T) e.getValue());
                    iter2 = bufferList.iterator(); // TODO try to avoid creating iterator here...  Use pos/index?
                }
                return;
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
     * Extent iterator class for floating point keys.
     *
     * @param <T> value type
     */
    public static class PhExtentMMF<T> extends PhIteratorMMF<T> {
        private final PhExtent<Object> iter;

        protected PhExtentMMF(PhExtent<Object> iter, int dims, PreProcessorPointF pre) {
            super(iter, dims, pre);
            this.iter = iter;
        }

        /**
         * Restarts the extent iterator.
         *
         * @return this
         */
        @Override
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
        private final PhQuery<Object> q;

        protected PhQueryMMF(PhQuery<Object> iter, int dims, PreProcessorPointF pre) {
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

    //    /**
    //     * Replaces all entries at a given position with a new value.
    //     * @see java.util.Map#replace(Object, Object)
    //     * @param key      key
    //     * @param newValue new value
    //     * @return {@code true} if the value was replaced
    //     */
    //    public T replace(double[] key, T newValue) {
    //        MutableRef<T> ref = new MutableRef<>();
    //        MutableInt i = new MutableInt(0);
    //        pht.computeIfPresent(pre(key), (doubles, list) -> {
    //            ref.set(list.get(0));
    //            i.set(list.size());
    //            list.clear();
    //            list.add(newValue);
    //            return list;
    //        });
    //        size = size - i.get() + 1;
    //        return ref.get();
    //    }

    /**
     * Nearest neighbor query iterator class for floating point keys.
     *
     * @param <T> value type
     */
    public static class PhKnnQueryMMF<T> implements PhIteratorBase<T, PhEntryDistMMF<T>> {
        private final long[] lCenter;
        private final PreProcessorPointF pre;
        private final PhEntryDistMMF<T> buffer;
        private PhEntryDistMMF<T> bufferToReturn;
        private PhEntryDist<Object> internalEntry;
        private final PhKnnQuery<Object> iter;
        private final ArrayList<T> bufferList = new ArrayList<>();
        private Iterator<T> iter2 = null;

        protected PhKnnQueryMMF(PhKnnQuery<Object> iter, int dims, PreProcessorPointF pre) {
            this.iter = iter;
            this.pre = pre;
            this.buffer = new PhEntryDistMMF<>(new double[dims], null, Double.NaN);
            this.bufferList.add(null);
            lCenter = new long[dims];
            resetInternal();
        }

        private void resetInternal() {
            if (!iter.hasNext()) {
                iter2 = null;
                return; // empty result
            }
            internalEntry = iter.nextEntryReuse();
//            pre.post(e.getKey(), buffer.key);
//            buffer.dist = e.dist();
            if (internalEntry.getValue() instanceof ArrayList) {
                iter2 = ((ArrayList<T>) internalEntry.getValue()).iterator();
            } else {
                bufferList.set(0, (T) internalEntry.getValue());
                iter2 = bufferList.iterator();
            }
            findNextKnn();
        }

        private void findNextKnn() {
            if (iter2.hasNext()) {
                return;
            }
            if (iter.hasNext()) {
                internalEntry = iter.nextEntryReuse();
//                pre.post(e.getKey(), buffer.key);
//                buffer.dist = e.dist();
                if (internalEntry.getValue() instanceof ArrayList) {
                    iter2 = ((ArrayList<T>) internalEntry.getValue()).iterator();
                } else {
                    bufferList.set(0, (T) internalEntry.getValue());
                    iter2 = bufferList.iterator(); // TODO try to avoid creating iterator here...  Use pos/index?
                }
                return;
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
            pre.post(internalEntry.getKey(), buffer.key);
            buffer.dist = internalEntry.dist();
            buffer.set(iter2.next());
            PhEntryDistMMF<T> ret = new PhEntryDistMMF<>(buffer.key.clone(), buffer.value, buffer.dist);
            findNextKnn();
            return ret;
        }

        @Override
        public PhEntryDistMMF<T> nextEntryReuse() {
            checkNextKnn();
            pre.post(internalEntry.getKey(), buffer.key);
            buffer.dist = internalEntry.dist();
            buffer.set(iter2.next());
            findNextKnn();
            return buffer;
        }

        /**
         * @return the key of the next entry
         */
//        public double[] nextKey() {
//            checkNextKnn();
//            double[] key = buffer.key.clone();
//            iter2.next();
//            findNextKnn();
//            return key;
//        }

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
        private final PhRangeQuery<Object> q;

        protected PhRangeQueryMMF(PhRangeQuery<Object> iter, PhTree<Object> tree, PreProcessorPointF pre) {
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
}
