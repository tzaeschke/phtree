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

/**
 * Min-max heap implementation based on:
 * <a href="https://en.wikipedia.org/wiki/Min-max_heap">https://en.wikipedia.org/wiki/Min-max_heap</a>
 *
 * @param <T> Entry type
 */
public class MinMaxHeap<T> implements MinMaxHeapI<T> {

    private static final int DEFAULT_SIZE = 16;
    // Data. The first slot is left empty, i.e. the first data element is at [1]!
    private T[] data;
    // index of first free entry
    private int size = 0;
    private final Less<T> less;

    @SuppressWarnings("unchecked")
    private MinMaxHeap(int capacity, Less<T> lessFn) {
        data = (T[]) new Object[capacity];
        this.less = lessFn;
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
     * WARNING: This is slow, see {@link #create(Less)}.
     * Creates a new MinMaxHeap using the compareTo method of the provided entry type.
     * @return A new MinMaxHeap
     * @param <T> The entry type. Must implement {@code Comparable<T>}.
     */
    public static <T extends Comparable<T>> MinMaxHeap<T> create() {
        return new MinMaxHeap<>(DEFAULT_SIZE, new LessWrapper<>(Comparable<T>::compareTo));
    }

    /**
     * WARNING: This is slow, see {@link #create(Less)}.
     * Creates a new MinMaxHeap using the provided comparator for entries.
     * @param compareFn Comparator for T
     * @return A new MinMaxHeap
     * @param <T> The entry type.
     */
    public static <T> MinMaxHeap<T> createWithComparator(Comparator<T> compareFn) {
        return new MinMaxHeap<>(DEFAULT_SIZE, new LessWrapper<>(compareFn));
    }

    /**
     * Providing a less() method is the preferred way to use this MinMaxHeap. Using less() is about 20% faster
     * than using Comparator/Comparable.
     * @param less A method that return `true` if the first parameter is less than the second
     * @return A new MinMaxHeap
     * @param <T> The entry type.
     */
    public static <T> MinMaxHeap<T> create(Less<T> less) {
        return new MinMaxHeap<>(DEFAULT_SIZE, less);
    }

    private static boolean isMinLevel(int index) {
        //        // 0 -> 0; 1 -> 1; 2-3 -> 2; 4-7 -> 3; 8-15 -> 4; ...
        //        int highestBit = 32 - Integer.numberOfLeadingZeros(index);
        //        // min levels are 'odd'
        //        return (highestBit & 1) != 0;
        // We can remove the "32 - x" because it doesn't change the trailing bit
        return (Integer.numberOfLeadingZeros(index) & 1) != 0;
    }

    private boolean hasChildren(int i) {
        return i * 2 <= size;
    }

    private boolean isGrandchildOf(int m, int i) {
        return m >> 2 == i;
    }

    private void swap(int i1, int i2) {
        T v = data[i1];
        data[i1] = data[i2];
        data[i2] = v;
    }

    private int parent(int i) {
        return i >> 1;
    }

    private boolean hasGrandparent(int i) {
        return i >> 2 > 0;
    }

    private int grandparent(int i) {
        return i >> 2;
    }

    private int indexOfSmallestChildOrGrandchild(int index) {
        int end = end();
        int start = index * 4;

        final int firstChild = index * 2;
        if (start >= end) {
            // no grandchildren
            if (firstChild + 1 < end) {
                return less.less(data[firstChild], data[firstChild + 1]) ? firstChild : firstChild + 1;
            }
            return firstChild;
        }

        int min12;
        int min34;
        if (start + 1 < end) {
            min12 = start + (less.less(data[start], data[start + 1]) ? 0 : 1);
            start += 2;
            if (start + 1 < end) {
                min34 = less.less(data[start], data[start + 1]) ? start : start + 1;
            } else {
                min34 = start < end ? start : index * 2 + 1;
            }
        } else {
            min12 = start;
            start += 2;
            min34 = start < end ? start : index * 2 + 1;
        }
        return less.less(data[min12], data[min34]) ? min12 : min34;
    }

    private int indexOfLargestChildOrGrandchild(int index) {
        int end = end();
        int start = index * 4;

        final int firstChild = index * 2;
        if (start >= end) {
            // no grandchildren
            if (firstChild + 1 < end) {
                return less.less(data[firstChild], data[firstChild + 1]) ? firstChild + 1 : firstChild;
            }
            return firstChild;
        }

        int max12;
        int max34;
        if (start + 1 < end) {
            max12 = start + (less.less(data[start], data[start + 1]) ? 1 : 0);
            start += 2;
            if (start + 1 < end) {
                max34 = less.less(data[start], data[start + 1]) ? start + 1 : start;
            } else {
                max34 = start < end ? start : index * 2 + 1;
            }
        } else {
            max12 = start;
            start += 2;
            max34 = start < end ? start : index * 2 + 1;
        }
        return less.less(data[max12], data[max34]) ? max34 : max12;
    }

    private void pushDown(int m) {
        while (hasChildren(m)) {
            int i = m;
            if (isMinLevel(i)) {
                m = indexOfSmallestChildOrGrandchild(i);
                if (less.less(data[m], data[i])) {
                    swap(m, i);
                    if (isGrandchildOf(m, i)) {
                        if (!less.less(data[m], data[parent(m)])) {
                            swap(m, parent(m));
                        }
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else {
                m = indexOfLargestChildOrGrandchild(i);
                if (!less.less(data[m], data[i])) {
                    swap(m, i);
                    if (isGrandchildOf(m, i)) {
                        if (less.less(data[m], data[parent(m)])) {
                            swap(m, parent(m));
                        }
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            }
        }
    }

    private void pushUp(int index, T value) {
        if (isMinLevel(index)) {
            if (!less.less(value, data[parent(index)])) {
                data[index] = data[parent(index)];
                pushUpMax(parent(index), value);
            } else {
                pushUpMin(index, value);
            }
        } else {
            if (less.less(value, data[parent(index)])) {
                data[index] = data[parent(index)];
                pushUpMin(parent(index), value);
            } else {
                pushUpMax(index, value);
            }
        }
    }

    private void pushUpMin(int index, T value) {
        while (hasGrandparent(index) && less.less(value, data[grandparent(index)])) {
            data[index] = data[grandparent(index)];
            index = grandparent(index);
        }
        data[index] = value;
    }

    private void pushUpMax(int index, T value) {
        while (hasGrandparent(index) && !less.less(value, data[grandparent(index)])) {
            data[index] = data[grandparent(index)];
            index = grandparent(index);
        }
        data[index] = value;
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

        if (size + 1 >= data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }

        size++;
        pushUp(size, value);
    }

    @Override
    public void popMin() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        int end = end();
        T value = data[end - 1];
        data[end - 1] = null;
        size--;

        if (size == 0) {
            return;
        }
        data[1] = value;

        pushDown(1);
    }

    @Override
    public void popMax() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        if (size == 1) {
            size--;
            data[1] = null;
            return;
        } else if (size == 2) {
            size--;
            data[2] = null;
            return;
        }
        int max = less.less(data[2], data[3]) ? 3 : 2;

        if (size == 3) {
            size--;
            if (max == 2) {
                data[2] = data[3];
            }
            data[3] = null;
            return;
        }

        int end = end();
        T value = data[end - 1];
        data[end - 1] = null;
        size--;

        data[max] = value;
        pushDown(max);
    }

    @Override
    public T peekMin() {
        if (size < 1) {
            throw new NoSuchElementException();
        }
        return data[1];
    }

    @Override
    public T peekMax() {
        if (size < 1) {
            throw new NoSuchElementException();
        }
        if (size == 1) {
            return data[1];
        } else if (size == 2) {
            return data[2];
        }
        return less.less(data[2], data[3]) ? data[3] : data[2];
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

    @SuppressWarnings("unchecked")
    public void clear() {
        size = 0;
        if (data.length > DEFAULT_SIZE) {
            data = (T[]) new Object[DEFAULT_SIZE];
        } else {
            Arrays.fill(data, null);
        }
    }
}
