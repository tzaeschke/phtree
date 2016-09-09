/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.util.Arrays;
import java.util.List;

import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.PhTree.PhQuery;
import ch.ethz.globis.phtree.pre.PreProcessorRangeF;
import ch.ethz.globis.phtree.util.PhIteratorBase;
import ch.ethz.globis.phtree.util.PhMapper;

/**
 * PH-tree for storing ranged objects with floating point coordinates.
 * Stored objects are axis-aligned hyper-rectangles defined by a 'lower left'
 * and 'upper right' corner.  
 * 
 * @author Tilmann Zaeschke
 * @param <T> value type of the tree
 */
public class PhTreeSolidF<T> implements Iterable<T> {

	private final int dims;
	private final PhTree<T> pht;
	private final PreProcessorRangeF pre;
	private final PhDistanceSF dist;
	private final double[] qMIN;
	private final double[] qMAX;
	
	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 */
	private PhTreeSolidF(int dim) {
		this(PhTree.create(dim*2));
	}
	
	/**
	 * Create a new {@code double} tree backed by the the specified tree.
	 * Note that the backing tree's dimensionality must be a multiple of 2.
	 * 
	 * @param tree the backing tree
	 */
	public PhTreeSolidF(PhTree<T> tree) {
		this(tree, new PreProcessorRangeF.IEEE(tree.getDim()));
	}
	
	/**
	 * Create a new {@code double} tree backed by the the specified tree.
	 * Note that the backing tree's dimensionality must be a multiple of 2.
	 * 
	 * @param tree the backing tree
	 * @param pre a preprocessor instance
	 */
	public PhTreeSolidF(PhTree<T> tree, PreProcessorRangeF pre) {
		this.dims = tree.getDim()/2;
		if (dims*2 != tree.getDim()) {
			throw new IllegalArgumentException("The backing tree's DIM must be a multiple of 2");
		}
		this.pht = tree;
		this.pre = pre;
		//this.dist = new PhDistanceSFEdgeDist(pre, dims);
		this.dist = new PhDistanceSFCenterDist(pre, dims);
		qMIN = new double[dims];
		Arrays.fill(qMIN, Double.NEGATIVE_INFINITY);
		qMAX = new double[dims];
		Arrays.fill(qMAX, Double.POSITIVE_INFINITY);
	}
	
	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 * @return new tree
	 * @param <T> value type of the tree
	 */
    public static <T> PhTreeSolidF<T> create(int dim) {
    	return new PhTreeSolidF<>(dim);
    }
	
	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 * @param pre a preprocessor instance
	 * @return new tree
	 * @param <T> value type of the tree
	 */
    public static <T> PhTreeSolidF<T> create(int dim, PreProcessorRangeF pre) {
    	return new PhTreeSolidF<>(PhTree.create(dim*2), pre);
	}
	
	/**
	 * Inserts a new ranged object into the tree.
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @param value the value
	 * @return the previous value or {@code null} if no entry existed
	 * 
	 * @see PhTree#put(long[], Object)
	 */
	public T put(double[] lower, double[] upper, T value) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.put(lVal, value);
	}
	
	/**
	 * Removes a ranged object from the tree.
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @return the value or {@code null} if no entry existed
	 * 
	 * @see PhTree#remove(long...)
	 */
	public T remove(double[] lower, double[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.remove(lVal);
	}
	
	/**
	 * Check whether an entry with the specified coordinates exists in the tree.
	 * @param lower lower left corner
	 * @param upper upper right corner
	 * @return true if the entry was found 
	 * 
	 * @see PhTree#contains(long...)
	 */
	public boolean contains(double[] lower, double[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.contains(lVal);
	}
	
	/**
	 * @param e the entry
	 * @return any previous value for the key
	 * @see #put(double[], double[], Object)
	 */
	public T put(PhEntrySF<T> e) {
		return put(e.lower(), e.upper(), e.value());
	}
	
	/**
	 * @param e the entry
	 * @return the value for the key
	 * @see #remove(double[], double[])
	 */
	public T remove(PhEntrySF<T> e) {
		return remove(e.lower(), e.upper());
	}
	
	/**
	 * @param e the entry
	 * @return whether the key exists
	 * @see #contains(double[], double[])
	 */
	public boolean contains(PhEntrySF<T> e) {
		return contains(e.lower(), e.upper());
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
	 * @param nMin number of entries to be returned. More entries may or may not be returned if 
	 * several points have the same distance.
	 * @param distanceFunction A distance function for rectangle data. This parameter is optional,
	 * passing a {@code null} will use the default distance function.
	 * @param center the center point
	 * @return The query iterator.
	 */
	public PhKnnQuerySF<T> nearestNeighbour(int nMin, PhDistanceSF distanceFunction,
			double ... center) {
		long[] lCenter = new long[2*dims];
		pre.pre(center, center, lCenter);
		PhDistanceSF df = distanceFunction == null ? dist : distanceFunction;
		return new PhKnnQuerySF<>(pht.nearestNeighbour(nMin, df, null, lCenter), dims, pre);
	}

	/**
	 * Resetable query result iterator.
	 * @param <T> value type
	 */
	public static class PhIteratorSF<T> implements PhIteratorBase<double[], T, PhEntrySF<T>> {
		protected final PhIteratorBase<long[], T, ? extends PhEntry<T>> iter;
		private final int dims;
		protected final PreProcessorRangeF pre;
		private final PhEntrySF<T> buffer;
		private PhIteratorSF(PhIteratorBase<long[], T, ? extends PhEntry<T>> iter, 
				int dims, PreProcessorRangeF pre) {
			this.iter = iter;
			this.dims = dims;
			this.pre = pre;
			this.buffer = new PhEntrySF<>(new double[dims], new double[dims], null);
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
            return iter.nextValue();
		}
		@Override
		public PhEntrySF<T> nextEntry() {
			double[] lower = new double[dims];
			double[] upper = new double[dims];
            PhEntry<T> pvEntry = iter.nextEntryReuse();
            pre.post(pvEntry.getKey(), lower, upper);
			return new PhEntrySF<>(lower, upper, pvEntry.getValue());
		}
		@Override
		public PhEntrySF<T> nextEntryReuse() {
			PhEntry<T> pvEntry = iter.nextEntryReuse();
            pre.post(pvEntry.getKey(), buffer.lower, buffer.upper);
            buffer.setValue( pvEntry.getValue() );
			return buffer;
		}
		@Override
		public void remove() {
			iter.remove();
		}
	}
	
	public static class PhQuerySF<T> extends PhIteratorSF<T> {
		private final long[] lLow;
		private final long[] lUpp;
		private final PhQuery<T> q;
		private final double[] qMIN;
		private final double[] qMAX;
		private final boolean intersect;
		
		private PhQuerySF(PhQuery<T> iter, int dims, PreProcessorRangeF pre, boolean intersect) {
			super(iter, dims, pre);
			q = iter;
			qMIN = new double[dims];
			Arrays.fill(qMIN, Double.NEGATIVE_INFINITY);
			qMAX = new double[dims];
			Arrays.fill(qMAX, Double.POSITIVE_INFINITY);
			this.intersect = intersect;
			lLow = new long[dims*2];
			lUpp = new long[dims*2];
		}

		public PhQuerySF<T> reset(double[] lower, double[] upper) {
			if (intersect) {
				pre.pre(qMIN, lower, lLow);
				pre.pre(upper, qMAX, lUpp);
			} else {
				//include
				pre.pre(lower, lower, lLow);
				pre.pre(upper, upper, lUpp);
			}
			q.reset(lLow, lUpp);
			return this;
		}
	}
	
	public static class PhKnnQuerySF<T> extends PhIteratorSF<T> {
		private final long[] lCenterBuffer;
		private final PhKnnQuery<T> q;
		private final double[] qMIN;
		private final double[] qMAX;
		private final int dims;
		protected final PreProcessorRangeF pre;
		private final PhEntryDistSF<T> buffer;
		
		private PhKnnQuerySF(PhKnnQuery<T> iter, int dims, PreProcessorRangeF pre) {
			super(iter, dims, pre);
			this.q = iter;
			this.qMIN = new double[dims];
			Arrays.fill(qMIN, Double.NEGATIVE_INFINITY);
			this.qMAX = new double[dims];
			Arrays.fill(qMAX, Double.POSITIVE_INFINITY);
			this.lCenterBuffer = new long[dims*2];
			this.dims = dims;
			this.pre = pre;
			this.buffer = new PhEntryDistSF<>(new double[dims], new double[dims], null, Double.NaN);
		}

	/**
		 * Resets the current kNN query with new parameters.
		 * @param nMin minimum results to be returned
		 * @param newDist Distance function. Supplying 'null' uses the default distance function
		 * for the current preprocessor.
		 * @param center the center point
		 * @return this query instance
		 */
		public PhKnnQuerySF<T> reset(int nMin, PhDistance newDist, double[] center) {
			pre.pre(center, center, lCenterBuffer);
			q.reset(nMin, newDist, lCenterBuffer);
			return this;
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
			return iter.nextValue();
		}
		@Override
		public PhEntryDistSF<T> nextEntry() {
			double[] lower = new double[dims];
			double[] upper = new double[dims];
            PhEntryDist<T> pvEntry = q.nextEntryReuse();
            pre.post(pvEntry.getKey(), lower, upper);
			return new PhEntryDistSF<>(lower, upper, pvEntry.getValue(), pvEntry.dist());
		}
		@Override
		public PhEntryDistSF<T> nextEntryReuse() {
			PhEntryDist<T> pvEntry = q.nextEntryReuse();
            pre.post(pvEntry.getKey(), buffer.lower(), buffer.upper());
            buffer.setValueDist( pvEntry.getValue(), pvEntry.dist() );
			return buffer;
		}
		@Override
		public void remove() {
			iter.remove();
		}
	}
	
	/**
	 * Entries in a PH-tree with ranged objects.
	 * @param <T> value tyype of the entry 
	 */
	public static class PhEntrySF<T> {

		private final double[] lower;
		private final double[] upper;
		private T value;

		/**
		 * Range object constructor.
		 * @param lower lower left corner
		 * @param upper upper right corner
		 * @param value The value associated with the point
		 */
		public PhEntrySF(double[] lower, double[] upper, T value) {
			this.lower = lower;
			this.upper = upper;
            this.value = value;
		}

		/**
		 * Range object constructor.
		 * @param point lower and upper point in one array
		 * @param value The value associated with the point
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

		void setValue(T value) {
			this.value = value;
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

	public static class PhEntryDistSF<T> extends PhEntrySF<T> {
		private double dist;

		PhEntryDistSF(double[] lower, double[] upper, T value, double dist) {
			super(lower, upper, value);
			this.dist = dist;
		}

		void setValueDist(T value, double dist) {
			setValue(value);
			this.dist = dist;
		}

		public double dist() {
			return dist;
		}
		
	@Override
		public String toString() {
			return super.toString() + " dist=" + dist;
		}
	}
	
	@Override
	public PhIteratorSF<T> iterator() {
		return new PhIteratorSF<>(pht.queryExtent(), dims, pre);
	}

	/**
	 * @param lo1 old min value
	 * @param up1 old max value
	 * @param lo2 new min value
	 * @param up2 new max value
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
	 * @param lower min value
	 * @param upper max value
	 * @return List of query results
	 */
	public List<PhEntrySF<T>> queryIntersectAll(double[] lower, double[] upper) {
		return queryIntersectAll(lower, upper, Integer.MAX_VALUE, null,
				e -> {
					double[] lo = new double[lower.length]; 
					double[] up = new double[lower.length]; 
					pre.post(e.getKey(), lo, up);
					return new PhEntrySF<>(lo, up, e.getValue());
				});
	}

	/**
	 * Same as {@link #queryIntersectAll(double[], double[], int, PhFilter, PhMapper)}, 
	 * except that it returns a list instead of an iterator. 
	 * This may be faster for small result sets. 
	 * @param lower min value
	 * @param upper max value
	 * @param maxResults max result count
	 * @param filter filter instance
	 * @param mapper mapper instance for mapping double[] to long[]
	 * @return List of query results
	 * @param <R> result type
	 */
	public <R> List<R> queryIntersectAll(double[] lower, double[] upper, int maxResults, 
			PhFilter filter, PhMapper<T,R> mapper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(qMIN, lower, lLow);
		pre.pre(upper, qMAX, lUpp);
		return pht.queryAll(lLow, lUpp, maxResults, filter, mapper);
	}

	/**
	 * @return The number of entries in the tree
	 */
	public int size() {
		return pht.size();
	}

	/**
	 * @param lower min value
	 * @param upper max value
	 * @return the element that has 'upper' and 'lower' as key. 
	 */
	public T get(double[] lower, double[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.get(lVal);
	}

    /**
     * Clear the tree.
     */
	public void clear() {
		pht.clear();
	}

	/**
	 * @return The PhTree that backs this tree.
	 */
	public PhTree<T> getInternalTree() {
		return pht;
	}
	
	/**
	 * @return the preprocessor of this tree. 
	 */
	public PreProcessorRangeF getPreProcessor() {
		return pre;
	}
	
	@Override
	public String toString() {
		return pht.toString(); 
	}

	/**
	 * 
	 * @return the dimensionality of the tree.
	 */
	public int getDims() {
		return dims;
	}
}
