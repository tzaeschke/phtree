/*
 * Copyright 2009-2023 Tilmann Zaeschke. All rights reserved.
 *
 * This file is part of TinSpin.
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
package ch.ethz.globis.phtree.util;

import java.util.Arrays;
import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * Min-max heap implementation based on:
 * <a href="https://en.wikipedia.org/wiki/Min-max_heap">https://en.wikipedia.org/wiki/Min-max_heap</a>
 * <p>
 * This heap also works as a pool for instances of T. Instances of T are never removed from the internal array.
 * Calling getObject() will return the first unused instance of T (or create one if none is available).
 *
 * @param <T> Entry type
 */
public class MinHeapPool<T> implements MinHeapI.MinHeapPoolI<T> {

    private static final int DEFAULT_SIZE = 16;
    private final Less<T> less;
    Supplier<T> supplyFn;
    // Data. The first slot is left empty, i.e. the first data element is at [1]!
    private T[] data;
    // index of first free entry
    private int size = 0;

    @SuppressWarnings("unchecked")
    private MinHeapPool(int capacity, Less<T> lessFn, Supplier<T> supplyFn) {
        data = (T[]) new Object[capacity];
        this.less = lessFn;
        this.supplyFn = supplyFn;
    }

    @FunctionalInterface
    public interface Less<T> {
        boolean less(T o1, T o2);
    }

    static class LessWrapper<C> implements Less<C> {
        private final Comparator<C> comp;
        LessWrapper (Comparator<C> comp) {
            this.comp = comp;
        }
        @Override
        public boolean less(C o1, C o2) {
            return comp.compare(o1, o2) < 0;
        }
    }

    /**
     * WARNING: This is slow, see {@link #create(Less, Supplier)}.
     * Creates a new MinMaxHeap using the compareTo method of the provided entry type.
     *
     * @param supplyFn A method that create a new instance of T.
     * @return A new MinMaxHeap
     * @param <T> The entry type. Must implement {@code Comparable<T>}.
     */
    public static <T extends Comparable<T>> MinHeapPool<T> create(Supplier<T> supplyFn) {
        return new MinHeapPool<>(DEFAULT_SIZE, new LessWrapper<>(Comparable<T>::compareTo), supplyFn);
    }

    /**
     * Providing a less() method is the preferred way to use this MinMaxHeap. Using less() is about 20% faster
     * than using Comparator/Comparable.
     *
     * @param lessFn   A method that return `true` if the first parameter is less than the second
     * @param supplyFn A method that create a new instance of T.
     * @return A new MinMaxHeap
     * @param <T> The entry type.
     */
    public static <T> MinHeapPool<T> create(Less<T> lessFn, Supplier<T> supplyFn) {
        return new MinHeapPool<>(DEFAULT_SIZE, lessFn, supplyFn);
    }

    private boolean hasChildren(int i) {
        return i * 2 <= size;
    }

    private void swap(int i1, int i2) {
        T v = data[i1];
        data[i1] = data[i2];
        data[i2] = v;
    }

    private int parent(int i) {
        return i >> 1;
    }

    private boolean hasParent(int i) {
        return i >> 1 > 0;
    }

    private int indexOfSmallestChild(int index) {
        int end = end();
        int start = index * 2;

        if (start + 1 < end) {
            return start + (less.less(data[start], data[start + 1]) ? 0 : 1);
        } else {
            return start;
        }
    }

    private void pushDown(int m) {
        while (hasChildren(m)) {
            int i = m;
            m = indexOfSmallestChild(i);
            if (less.less(data[m], data[i])) {
                swap(m, i);
            } else {
                return;
            }
        }
    }

    private void pushUp(int index, T value) {
        pushUpMin(index, value);
    }

    private void pushUpMin(int index, T value) {
        while (hasParent(index) && less.less(value, data[parent(index)])) {
            swap(index, parent(index));
            index = parent(index);
        }
    }

    private int end() {
        return size + 1;
    }

    @Override
    public void push(T value) {
        if (size == 0) {
            data[1] = value;
            size++;
            return;
        }

        // size + 2, so we always can the last field for pooling
        if (size + 2 >= data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }

        size++;
        data[size] = value;
        pushUp(size, value);
    }

    @Override
    public T getObject() {
        T obj = data[end()];
        data[end()] = null;
        return obj != null ? obj : supplyFn.get();
    }

    @Override
    public void popMin() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        size--;

        if (size == 0) {
            return;
        }
        // T value = data[end()];
        // data[1] = value;
        swap(1, end());

        pushDown(1);
    }

    @Override
    public T peekMin() {
        if (size < 1) {
            throw new NoSuchElementException();
        }
        return data[1];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    public String print() {
        StringBuilderLn s = new StringBuilderLn();
        int x = 2;
        for (int i = 1; i <= size; i++) {
            if (i % x == 0) {
                s.appendLn();
                x *= 2;
            }
            s.append(data[i] + "   ");
        }
        s.appendLn();
        return s.toString();
    }

    public void clear() {
        size = 0;
    }
}
