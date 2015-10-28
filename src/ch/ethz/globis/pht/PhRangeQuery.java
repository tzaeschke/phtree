/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht;


import ch.ethz.globis.pht.PhTree.PhIterator;
import ch.ethz.globis.pht.PhTree.PhQuery;

/**
 * Range query.
 * 
 * @author Tilmann Zaeschke
 *
 * @param <T>
 */
public class PhRangeQuery<T> implements PhIterator<T> {

  private final long[] min, max;
  private final PhQuery<T> q;
  private final int DIM;
  private final PhDistance dist;
  private final PhFilterDistance filter;

  public PhRangeQuery(PhQuery<T> iter, PhTree<T> tree, 
      PhDistance dist, PhFilterDistance filter) {
    this.DIM = tree.getDim();
    this.q = iter;
    this.dist = dist;
    this.filter = filter;
    this.min = new long[DIM];
    this.max = new long[DIM];
  }

  public PhRangeQuery<T> reset(double range, long... center) {
    filter.set(center, dist, range);
    dist.toMBB(range, center, min, max);
    q.reset(min, max);
    return this;
  }

  @Override
  public long[] nextKey() {
    return q.nextKey();
  }

  @Override
  public T nextValue() {
    return q.nextValue();
  }

  @Override
  public PhEntry<T> nextEntry() {
    return q.nextEntry();
  }

  @Override
  public boolean hasNext() {
    return q.hasNext();
  }

  @Override
  public T next() {
    return q.next();
  }

  @Override
  public void remove() {
    q.remove();
  }

  @Override
  public PhEntry<T> nextEntryReuse() {
    return q.nextEntryReuse();
  }

}
