/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2023 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhEntryDist;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.util.MinHeap;
import ch.ethz.globis.phtree.util.MinMaxHeap;

import java.util.ArrayList;
import java.util.NoSuchElementException;

/**
 * kNN query implementation that uses preprocessors and distance functions.
 *
 * Implementation after Hjaltason and Samet (with some deviations: no MinDist or MaxDist used).
 * G. R. Hjaltason and H. Samet., "Distance browsing in spatial databases.", ACM TODS 24(2):265--318. 1999
 *
 * @param <T> value type
 */
public class PhIteratorKnn<T> implements PhKnnQuery<T> {

    private final PhTree16<T> pht;
    private PhDistance distFn;
    private PhFilter filterFn = new PhFilter() {
        @Override
        public boolean isValid(long[] key) {
            return true;
        }

        @Override
        public boolean isValid(int bitsToIgnore, long[] prefix) {
            return true;
        }
    };
    MinHeap<NodeDistT> queueN = MinHeap.create((t1, t2) -> t1.dist < t2.dist);
    MinMaxHeap<PhEntryDist<T>> queueV = MinMaxHeap.create((t1, t2) -> t1.dist() < t2.dist());
    double maxNodeDist = Double.POSITIVE_INFINITY;
    private PhEntryDist<T> resultFree;
    private PhEntryDist<T> resultToReturn;
    private boolean isFinished = false;
    private int remaining;
    private long[] center;
    private double currentDistance;
    private final NodeIteratorFullNoGC<T> nodeIter;

    PhIteratorKnn(PhTree16<T> pht, int minResults, long[] center, PhDistance distFn) {
        this.distFn = distFn;
        this.pht = pht;
        this.nodeIter  = new NodeIteratorFullNoGC<>();
        this.resultFree = new PhEntryDist<>(new long[pht.getDim()], null, 0);
        this.resultToReturn = new PhEntryDist<>(new long[pht.getDim()], null, 0);
        reset(minResults, distFn, center);
    }

    @Override
    public PhKnnQuery<T> reset(int minResults, PhDistance distFn, long[] center) {
        this.center = center;
        this.distFn = distFn == null ? this.distFn : distFn;
        this.currentDistance = Double.MAX_VALUE;
        this.remaining = minResults;
        this.maxNodeDist = Double.POSITIVE_INFINITY;
        this.isFinished = false;
        Node root = pht.getRoot();
        if (minResults <= 0 || root == null) {
            isFinished = true;
            return this;
        }
        queueN.clear();
        queueV.clear();

        queueN.push(new NodeDistT(0, root, new long[pht.getDim()]));
        findNextElement();
        return this;
    }

    @Override
    public long[] nextKey() {
        return nextEntryReuse().getKey();
    }

    @Override
    public T nextValue() {
        return nextEntryReuse().getValue();
    }

    @Override
    public PhEntryDist<T> nextEntry() {
        return new PhEntryDist<>(nextEntryReuse());
    }

    @Override
    public PhEntryDist<T> nextEntryReuse() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        PhEntryDist<T> ret = resultToReturn;
        findNextElement();
        return ret;
    }

    @Override
    public boolean hasNext() {
        return !isFinished; // TODO remove flag?
    }

    @Override
    public T next() {
        return nextValue();
    }

    public double distance() {
        return currentDistance;
    }

    private void findNextElement() {
        while (remaining > 0 && !(queueN.isEmpty() && queueV.isEmpty())) {
            boolean useV = !queueV.isEmpty();
            if (useV && !queueN.isEmpty()) {
                useV = queueV.peekMin().dist() <= queueN.peekMin().dist;
            }
            if (useV) {
                // data entry
                PhEntryDist<T> result = queueV.peekMin();
                queueV.popMin();
                --remaining;
                resultFree = resultToReturn;
                resultToReturn = result;
                currentDistance = result.dist();  // TODO remove field?
                return;
            } else {
                // inner node
                NodeDistT top = queueN.peekMin();
                queueN.popMin();
                Node node = top.node;
                double dNode = top.dist;

                if (dNode > maxNodeDist && queueV.size() >= remaining) {
                    // ignore this node
                    continue;
                }

                PhEntry<T> result = new PhEntry<>(new long[center.length], null); // TODO pool?
                nodeIter.init(node, filterFn);
                //nodeIter.setValTemplate(top.prefix);
                while (nodeIter.increment(result)) {
                    if (result.hasNodeInternal()) {
                        Node sub = (Node) result.getNodeInternal();
                        double dist = distToNode(result.getKey(), sub.getPostLen() + 1);
                        if (dist <= maxNodeDist) {
                            queueN.push(new NodeDistT(dist, sub, result.getKey()));
                        }
                    } else {
                        double d = distFn.dist(center, result.getKey());
                        // Using '<=' allows dealing with infinite distances.
                        if (d <= maxNodeDist) {
                            queueV.push(new PhEntryDist<>(result, d));
                            if (queueV.size() >= remaining) {
                                if (queueV.size() > remaining) {
                                    queueV.popMax();
                                }
                                double dMax = queueV.peekMax().dist();
                                maxNodeDist = Math.min(maxNodeDist, dMax);
                            }
                        }
                    }
                }
            }
        }
        isFinished = true;
        currentDistance = Double.MAX_VALUE;
    }

    private double distToNode(long[] prefix, int bitsToIgnore) {
        long maskMin = (-1L) << bitsToIgnore;
        long maskMax = ~maskMin;
        long[] buf = new long[prefix.length];
        for (int i = 0; i < buf.length; i++) {
            //if v is outside the node, return distance to the closest edge,
            //otherwise return v itself (assume possible distance=0)
            long min = prefix[i] & maskMin;
            long max = prefix[i] | maskMax;
            buf[i] = min > center[i] ? min : (max < center[i] ? max : center[i]);
        }

        return distFn.dist(center, buf);
    }

    // TODO use this pool!
    private static <T> PhEntryDist<T> createEntry(ArrayList<PhEntryDist<T>> pool,
                                                  long[] key, T val, double dist) {
        if (pool.isEmpty()) {
            return new PhEntryDist<>(key, val, dist);
        }
        PhEntryDist<T> e = pool.remove(pool.size() - 1);
        e.setKeyInternal(key);
        e.set(val, dist);
        return e;
    }

    private static class NodeDistT {
        double dist;
        Node node;
        long[] prefix;

        public NodeDistT(double dist, Node node, long[] prefix) {
            this.dist = dist;
            this.node = node;
            this.prefix = prefix;
        }
    }
}

