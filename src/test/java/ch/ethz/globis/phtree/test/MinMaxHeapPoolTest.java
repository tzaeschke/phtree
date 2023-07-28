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

import ch.ethz.globis.phtree.util.MinMaxHeapI.MinMaxHeapPoolI;
import ch.ethz.globis.phtree.util.MinMaxHeapPool;
import org.junit.Test;

import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Random;

import static org.junit.Assert.*;

public class MinMaxHeapPoolTest {

    private static final int SEEDS = 100; // 100 for benchmarking

    private MinMaxHeapPoolI<Entry> create() {
        return MinMaxHeapPool.create((o1, o2) -> o1.d < o2.d, Entry::new);
    }

    private DataEntry[] data(int n, int seed) {
        Random rnd = new Random(seed);
        DataEntry[] data = new DataEntry[n];
        for (int i = 0; i < data.length; i++) {
            data[i] = new DataEntry(rnd.nextDouble(), i);
        }
        return data;
    }

    private void populate(MinMaxHeapPoolI<Entry> heap, DataEntry[] data) {
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < data.length; i++) {
            DataEntry e = data[i];
            //heap.push(new Entry(e.d, e.id));
            heap.push(heap.getObject().set(e.d, e.id));
            assertFalse(heap.isEmpty());
            assertEquals(i + 1, heap.size());
            min = Math.min(min, data[i].d);
            max = Math.max(max, data[i].d);
            assertNotNull(heap.peekMin());
            assertEquals(min, heap.peekMin().d, 0.0);
            assertEquals(max, heap.peekMax().d, 0.0);
        }
    }

    @Test
    public void testMin() {
        long time = 0;
        Entry.N = 0;
        MinMaxHeapPoolI<Entry> heap = create();
        for (int seed = 0; seed < SEEDS; seed++) {
            for (int i = 1; i < 35; i++) {
                time += testMin(heap, i, seed);
                heap.clear();
            }
            for (int i = 1; i < 100; i++) {
                time += testMin(heap, i * 100, seed);
                heap.clear();
            }
        }
        System.out.println("N=" + Entry.N + "    time = " + (time / 1_000_000) + "ms");
    }

    private long testMin(MinMaxHeapPoolI<Entry> heap, int n, int seed) {
        DataEntry[] data = data(n, seed);
        populate(heap, data);

        Arrays.sort(data);

        long t0 = System.nanoTime();
        for (int i = 0; i < data.length; i++) {
//            System.out.println("pop i=" + i);
            //           ((MinMaxHeapZ)heap).print();
            assertFalse(heap.isEmpty());
            assertEquals(data.length - i, heap.size());
            assertEquals(data[i].d, heap.peekMin().d, 0.0);
            assertEquals(data[n - 1].d, heap.peekMax().d, 0.0);
            // ((MinMaxHeapZ)heap).checkConsistency();
            heap.popMin();
        }
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
        long t1 = System.nanoTime();
        return t1 - t0;
    }

    @Test
    public void testMax() {
        long time = 0;
        Entry.N = 0;
        MinMaxHeapPoolI<Entry> heap = create();
        for (int seed = 0; seed < SEEDS; seed++) {
            for (int i = 1; i < 35; i++) {
                time += testMax(heap, i, seed);
                heap.clear();
            }
            for (int i = 1; i < 100; i++) {
                time += testMax(heap, i * 100, seed);
                heap.clear();
            }
        }
        System.out.println("N=" + Entry.N + "    time = " + (time / 1_000_000) + "ms");
    }

    private long testMax(MinMaxHeapPoolI<Entry> heap, int n, int seed) {
        DataEntry[] data = data(n, seed);
        populate(heap, data);

        Arrays.sort(data);

        long t0 = System.nanoTime();
        for (int i = 0; i < data.length; i++) {
            assertFalse(heap.isEmpty());
            assertEquals(data.length - i, heap.size());
            assertEquals(data[0].d, heap.peekMin().d, 0.0);
            assertEquals(data[n - 1 - i].d, heap.peekMax().d, 0.0);
            heap.popMax();
        }
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
        long t1 = System.nanoTime();
        return t1 - t0;
    }

    @Test
    public void testOscillate() {
        PriorityQueue<DataEntry> pq = new PriorityQueue<>();
        MinMaxHeapPoolI<Entry> heap = create();
        Random rnd = new Random(0);
        int n = 0;
        for (int round = 0; round < 1_000_000; round++) {
            int MAX = rnd.nextInt(10);
            for (int i = 0; i < MAX; i++) {
                DataEntry de = new DataEntry(rnd.nextDouble(), n++);
                pq.add(de);
                heap.push(heap.getObject().set(de.d, de.id));
            }
            for (int i = 0; i < MAX * 0.9; i++) {
                DataEntry de = pq.poll();
                Entry e = heap.peekMin();
                heap.popMin();
                assertEquals(de.d, e.d, 0.0);
                assertEquals(de.id, e.id);
                assertEquals(pq.size(), heap.size());
            }
        }
    }

    private static class Entry {

        static int N = 0;
        double d;
        int id;

        Entry() {
            N++;
            this.d = -1;
            this.id = -1;
        }

        Entry(double d, int id) {
            N++;
            this.d = d;
            this.id = id;
        }

        @Override
        public String toString() {
            return String.format("%.3f", d);
        }

        public Entry set(double d, int id) {
            this.d = d;
            this.id = id;
            return this;
        }
    }

    private static class DataEntry implements Comparable<DataEntry> {

        double d;
        int id;

        DataEntry(double d, int id) {
            this.d = d;
            this.id = id;
        }

        @Override
        public int compareTo(DataEntry o) {
            return Double.compare(d, o.d);
        }

    }
}
