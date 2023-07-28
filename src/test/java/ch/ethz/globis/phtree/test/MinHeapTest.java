/*
 * Copyright 2016-2023 Tilmann Zaeschke
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
package ch.ethz.globis.phtree.test;

import ch.ethz.globis.phtree.util.MinHeap;
import ch.ethz.globis.phtree.util.MinHeapI;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.*;

public class MinHeapTest {

    private static final int SEEDS = 10; // 100 for benchmarking

    private MinHeapI<Entry> create() {
        return MinHeap.create((o1, o2) -> o1.d < o2.d);
    }

    private Entry[] data(int n, int seed) {
        Random rnd = new Random(seed);
        Entry[] data = new Entry[n];
        for (int i = 0; i < data.length; i++) {
            data[i] = new Entry(rnd.nextDouble(), i);
        }
        return data;
    }

    private void populate(MinHeapI<Entry> heap, Entry[] data) {
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < data.length; i++) {
            heap.push(data[i]);
            assertFalse(heap.isEmpty());
            assertEquals(i + 1, heap.size());
            min = Math.min(min, data[i].d);
            max = Math.max(max, data[i].d);
            assertNotNull(heap.peekMin());
            if (min != heap.peekMin().d) {
                System.out.println();
            }
            assertEquals(min, heap.peekMin().d, 0.0);
        }
    }

    @Test
    public void testMin() {
        for (int seed = 0; seed < SEEDS; seed++) {
            for (int i = 1; i < 35; i++) {
                testMin(i, seed);
            }
            for (int i = 1; i < 100; i++) {
                testMin(i * 100, seed);
            }
        }
    }

    private void testMin(int n, int seed) {
        Entry[] data = data(n, seed);
        MinHeapI<Entry> heap = create();
        populate(heap, data);

        Arrays.sort(data);

        for (int i = 0; i < data.length; i++) {
//            System.out.println("pop i=" + i);
            //           ((MinMaxHeapZ)heap).print();
            assertFalse(heap.isEmpty());
            assertEquals(data.length - i, heap.size());
            assertEquals(data[i].d, heap.peekMin().d, 0.0);
            // ((MinMaxHeapZ)heap).checkConsistency();
            heap.popMin();
        }
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
    }

    private static class Entry implements Comparable<Entry> {

        double d;
        int id;
        Entry(double d, int id) {
            this.d = d;
            this.id = id;
        }

        @Override
        public int compareTo(Entry o) {
            return Double.compare(d, o.d);
        }

        @Override
        public String toString() {
            return String.format("%.3f", d);
        }
    }

//    @Test
//    public void testMin2() {
//        for (int seed = 0; seed < SEEDS; seed++) {
//            for (int i = 1; i < 35; i++) {
//                testMin(i, seed);
//            }
//            for (int i = 1; i < 100; i++) {
//                testMin(i * 100, seed);
//            }
//        }
//    }
}
