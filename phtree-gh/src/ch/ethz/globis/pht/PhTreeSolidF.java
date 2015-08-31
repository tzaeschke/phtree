/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht;

import java.util.Arrays;
import java.util.List;

import ch.ethz.globis.pht.PhTree.PhIterator;
import ch.ethz.globis.pht.pre.EmptyPPRD;
import ch.ethz.globis.pht.pre.PreProcessorRangeD;
import ch.ethz.globis.pht.util.PhIteratorBase;
import ch.ethz.globis.pht.util.PhMapper;

/**
 * PH-tree for storing ranged objects with floating point coordinates.
 * Stored objects are axis-aligned hyper-rectangles defined by a 'lower left'
 * and 'upper right' corner.  
 * 
 * @author Tilmann Zaeschke
 */
public class PhTreeSolidF<T> implements Iterable<T> {

	private final int DIM;
	private final PhTree<T> pht;
	private final PreProcessorRangeD pre;
	private final double[] MIN;
	private final double[] MAX;
	
	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 */
    public static <T> PhTreeSolidF<T> create(int dim) {
    	return new PhTreeSolidF<T>(dim);
    }
	
	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 */
	private PhTreeSolidF(int dim) {
		this(PhTree.create(dim*2, 64));
	}
	
	/**
	 * Create a new {@code double} tree backed by the the specified tree.
	 * Note that the backing tree's dimensionality must be a multiple of 2.
	 * 
	 * @param tree the backing tree
	 */
	public PhTreeSolidF(PhTree<T> tree) {
		this.DIM = tree.getDIM()/2;
		if (DIM*2 != tree.getDIM()) {
			throw new IllegalArgumentException("The backing tree's DIM must be a multiple of 2");
		}
		pht = tree;
		pre = new EmptyPPRD();
		MIN = new double[DIM];
		Arrays.fill(MIN, Double.NEGATIVE_INFINITY);
		MAX = new double[DIM];
		Arrays.fill(MAX, Double.POSITIVE_INFINITY);
	}
	
	/**
	 * Inserts a new ranged object into the tree.
	 * @param lower
	 * @param upper
	 * @param value
	 * @return the previous value or {@code null} if no entry existed
	 * 
	 * @see PhTreeNV#insert(long...)
	 */
	public T put(double[] lower, double[] upper, T value) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.put(lVal, value);
	}
	
	/**
	 * Removes a ranged object from the tree.
	 * @param lower
	 * @param upper
	 * @return the value or {@code null} if no entry existed
	 * 
	 * @see PhTreeNV#delete(long...)
	 */
	public T remove(double[] lower, double[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.remove(lVal);
	}
	
	/**
	 * Check whether an entry with the specified coordinates exists in the tree.
	 * @param lower
	 * @param upper
	 * @return true if the entry was found 
	 * 
	 * @see PhTreeNV#contains(long...)
	 */
	public boolean contains(double[] lower, double[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.contains(lVal);
	}
	
	/**
	 * @see #put(double[], double[], T)
	 */
	public T put(PhEntrySF<T> e, T value) {
		return put(e.lower(), e.upper(), value);
	}
	
	/**
	 * @see #remove(double[], double[], T)
	 */
	public T remove(PhEntrySF<T> e) {
		return remove(e.lower(), e.upper());
	}
	
	/**
	 * @see #contains(double[], double[])
	 */
	public boolean contains(PhEntrySF<T> e) {
		return contains(e.lower(), e.upper());
	}
	
	/**
	 * @see #queryInclude(double[], double[])
	 */
	public PhIteratorSF<T> queryInclude(PhEntrySF<T> e) {
		return queryInclude(e.lower(), e.upper());
	}
	
	/**
	 * @see #queryIntersect(double[], double[])
	 */
	public PhIteratorSF<T> queryIntersect(PhEntrySF<T> e) {
		return queryIntersect(e.lower(), e.upper());
	}
	
	/**
	 * Query for all bodies that are fully included in the query rectangle.
	 * @param lower 'lower left' corner of query rectangle
	 * @param upper 'upper right' corner of query rectangle
	 * @return Iterator over all matching elements.
	 */
	public PhIteratorSF<T> queryInclude(double[] lower, double[] upper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(lower, lower, lLow);
		pre.pre(upper, upper, lUpp);
		return new PhIteratorSF<T>(pht.query(lLow, lUpp), DIM, pre);
	}
	
	/**
	 * Query for all bodies that are included in or partially intersect with the query rectangle.
	 * @param lower 'lower left' corner of query rectangle
	 * @param upper 'upper right' corner of query rectangle
	 * @return Iterator over all matching elements.
	 */
	public PhIteratorSF<T> queryIntersect(double[] lower, double[] upper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(MIN, lower, lLow);
		pre.pre(upper, MAX, lUpp);
		return new PhIteratorSF<T>(pht.query(lLow, lUpp), DIM, pre);
	}
	
	public static class PhIteratorSF<T> implements PhIteratorBase<double[], T, PhEntrySF<T>> {
		private final PhIterator<T> iter;
		private final int DIM;
		private final PreProcessorRangeD pre;
		private PhIteratorSF(PhIterator<T> iter, int DIM, PreProcessorRangeD pre) {
			this.iter = iter;
			this.DIM = DIM;
			this.pre = pre;
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
		public T nextValue() {
            PhEntry<T> pvEntry = iter.nextEntry();
			return pvEntry.getValue();
		}
		@Override
		public double[] nextKey() {
			double[] lower = new double[DIM];
			double[] upper = new double[DIM];
            PhEntry<T> pvEntry = iter.nextEntry();
            pre.post(pvEntry.getKey(), lower, upper);
            double[] ret = new double[2*DIM];
            for (int i = 0; i < DIM; i++) {
            	ret[i] = lower[i];
            	ret[i+DIM] = lower[i];
            }
			return ret;
		}
		@Override
		public PhEntrySF<T> nextEntry() {
			double[] lower = new double[DIM];
			double[] upper = new double[DIM];
            PhEntry<T> pvEntry = iter.nextEntry();
            pre.post(pvEntry.getKey(), lower, upper);
			return new PhEntrySF<>(lower, upper, pvEntry.getValue());
		}
		@Override
		public void remove() {
			iter.remove();
		}
	}
	
	/**
	 * Entries in a PH-tree with ranged objects. 
	 */
	public static class PhEntrySF<T> {

		private final double[] lower;
		private final double[] upper;
		private final T value;

		/**
		 * Range object constructor.
		 * @param lower
		 * @param upper
		 */
		public PhEntrySF(double[] lower, double[] upper, T value) {
			this.lower = lower;
			this.upper = upper;
            this.value = value;
		}

		/**
		 * Range object constructor.
		 * @param point lower and upper point in one array
		 */
		public PhEntrySF(double[] point, T value) {
			int dim = point.length>>1;
			this.lower = new double[dim];
			this.upper = new double[dim];
			System.arraycopy(point, 0, lower, 0, dim);
			System.arraycopy(point, dim, upper, 0, dim);
            this.value = value;
		}

		/**
         * @return the value of the entry
         */
        public T value() {
            return value;
        }

		/**
		 * @return lower left corner of the entry
		 */
		public double[] lower() {
			return lower;
		}

		/**
		 * @return upper right corner of the entry
		 */
		public double[] upper() {
			return upper;
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean equals(Object obj) {
			if (obj == null || !(obj instanceof PhEntrySF)) {
				return false;
			}
			PhEntrySF<T> e = (PhEntrySF<T>) obj;
			return Arrays.equals(lower, e.lower) && Arrays.equals(upper, e.upper);
		}
		
		@Override
		public int hashCode() {
			return Arrays.hashCode(lower) ^ Arrays.hashCode(upper);
		}
		
		@Override
		public String toString() {
			return "{" + Arrays.toString(lower) + "," + Arrays.toString(upper) + "} => " + value;
		}
	}

	@Override
	public PhIteratorSF<T> iterator() {
		return new PhIteratorSF<T>(pht.queryExtent(), DIM, pre);
	}

	/**
	 * @param lo1
	 * @param up1
	 * @param lo2
	 * @param up2
	 * @return true, if the value could be replaced.
	 * @see PhTree#update(long[], long[])
	 */
	public T update(double[] lo1, double[] up1, double[] lo2, double[] up2) {
		long[] pOld = new long[lo1.length << 1];
		long[] pNew = new long[lo1.length << 1];
		pre.pre(lo1, up1, pOld);
		pre.pre(lo2, up2, pNew);
		return pht.update(pOld, pNew);
	}

	/**
	 * Same as {@link #queryIntersect(double[], double[])}, except that it returns a list
	 * instead of an iterator. This may be faster for small result sets. 
	 * @param lower
	 * @param upper
	 * @return List of query results
	 */
	public List<PhEntrySF<T>> queryIntersectAll(double[] lower, double[] upper) {
		return queryIntersectAll(lower, upper, Integer.MAX_VALUE, PhPredicate.ACCEPT_ALL,
				(e) -> {
					double[] lo = new double[lower.length]; 
					double[] up = new double[lower.length]; 
					pre.post(e.getKey(), lo, up);
					return new PhEntrySF<T>(lo, up, e.getValue());
				});
	}

	public <R> List<R> queryIntersectAll(double[] lower, double[] upper, int maxResults, 
			PhPredicate filter, PhMapper<T,R> mapper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(MIN, lower, lLow);
		pre.pre(upper, MAX, lUpp);
		return pht.queryAll(lLow, lUpp, maxResults, filter, mapper);
	}

	/**
	 * @return The number of entries in the tree
	 */
	public int size() {
		return pht.size();
	}

	public T get(double[] lower, double[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.get(lVal);
	}

    /**
     * Clear the tree.
     */
	void clear() {
		pht.clear();
	}
}
