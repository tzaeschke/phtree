/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.nv;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import ch.ethz.globis.pht.PhPredicate;
import ch.ethz.globis.pht.PhTree;
import ch.ethz.globis.pht.nv.PhTreeNV.PhIteratorNV;
import ch.ethz.globis.pht.pre.EmptyPPRF;
import ch.ethz.globis.pht.pre.PreProcessorRangeF;
import ch.ethz.globis.pht.util.PhMapperK;
import ch.ethz.globis.pht.util.PhMapperKey;

/**
 * PH-tree for storing ranged objects with floating point coordinates.
 * Stored objects are axis-aligned hyper-rectangles defined by a 'lower left'
 * and 'upper right' corner.  
 * 
 * @author Tilmann Zaeschke
 */
public class PhTreeNVSolidF implements Iterable<PhTreeNVSolidF.PHREntry> {

	private final int DIM;
	private final PhTreeNV pht;
	private final PreProcessorRangeF pre;
	private final double[] MIN;
	private final double[] MAX;
	
	/**
	 * Create a new tree with the specified number of dimensions.
	 * 
	 * @param dim number of dimensions
	 */
	public PhTreeNVSolidF(int dim) {
		this(PhTreeNV.create(dim*2, 64));
	}
	
	/**
	 * Create a new {@code double} tree backed by the the specified tree.
	 * Note that the backing tree's dimensionality must be a multiple of 2.
	 * 
	 * @param tree the backing tree
	 */
	public PhTreeNVSolidF(PhTreeNV tree) {
		this(tree, new EmptyPPRF());
	}
	
	/**
	 * Create a new {@code double} tree backed by the the specified tree.
	 * Note that the backing tree's dimensionality must be a multiple of 2.
	 * 
	 * @param tree the backing tree
	 */
	public PhTreeNVSolidF(PhTreeNV tree, PreProcessorRangeF preprocessor) {
		this.DIM = tree.getDIM()/2;
		if (DIM*2 != tree.getDIM()) {
			throw new IllegalArgumentException("The backing tree's DIM must be a multiple of 2");
		}
		pht = tree;
		pre = preprocessor;
		MIN = new double[DIM];
		Arrays.fill(MIN, Double.NEGATIVE_INFINITY);
		MAX = new double[DIM];
		Arrays.fill(MAX, Double.POSITIVE_INFINITY);
	}
	
	/**
	 * Inserts a new ranged object into the tree.
	 * @param lower
	 * @param upper
	 * @return true if the entry already exists
	 * 
	 * @see PhTreeNV#insert(long...)
	 */
	public boolean insert(double[] lower, double[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.insert(lVal);
	}
	
	/**
	 * Removes a ranged object from the tree.
	 * @param lower
	 * @param upper
	 * @return true if the entry was found 
	 * 
	 * @see PhTreeNV#delete(long...)
	 */
	public boolean delete(double[] lower, double[] upper) {
		long[] lVal = new long[lower.length*2];
		pre.pre(lower, upper, lVal);
		return pht.delete(lVal);
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
	 * @see #insert(double[], double[])
	 */
	public boolean insert(PHREntry e) {
		return insert(e.lower(), e.upper());
	}
	
	/**
	 * @see #delete(double[], double[])
	 */
	public boolean delete(PHREntry e) {
		return delete(e.lower(), e.upper());
	}
	
	/**
	 * @see #contains(double[], double[])
	 */
	public boolean contains(PHREntry e) {
		return contains(e.lower(), e.upper());
	}
	
	/**
	 * @see #queryInclude(double[], double[])
	 */
	public PHREntryIterator queryInclude(PHREntry e) {
		return queryInclude(e.lower(), e.upper());
	}
	
	/**
	 * @see #queryIntersect(double[], double[])
	 */
	public PHREntryIterator queryIntersect(PHREntry e) {
		return queryIntersect(e.lower(), e.upper());
	}
	
	/**
	 * Query for all bodies that are fully included in the query rectangle.
	 * @param lower 'lower left' corner of query rectangle
	 * @param upper 'upper right' corner of query rectangle
	 * @return Iterator over all matching elements.
	 */
	public PHREntryIterator queryInclude(double[] lower, double[] upper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(lower, lower, lLow);
		pre.pre(upper, upper, lUpp);
		return new PHREntryIterator((PhIteratorNV) pht.query(lLow, lUpp), DIM);
	}
	
	/**
	 * Query for all bodies that are included in or partially intersect with the query rectangle.
	 * @param lower 'lower left' corner of query rectangle
	 * @param upper 'upper right' corner of query rectangle
	 * @return Iterator over all matching elements.
	 */
	public PHREntryIterator queryIntersect(double[] lower, double[] upper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(MIN, lower, lLow);
		pre.pre(upper, MAX, lUpp);
		return new PHREntryIterator((PhIteratorNV) pht.query(lLow, lUpp), DIM);
	}
	
	public class PHREntryIterator implements Iterator<PHREntry> {
		private final PhIteratorNV iter;
		private final int DIM;
		private PHREntryIterator(PhIteratorNV iter, int DIM) {
			this.iter = iter;
			this.DIM = DIM;
		}
		@Override
		public boolean hasNext() {
			return iter.hasNextKey();
		}
		@Override
		public PHREntry next() {
			double[] lower = new double[DIM];
			double[] upper = new double[DIM];
			pre.post(iter.nextKey(), lower, upper);
			return new PHREntry(lower, upper);
		}
		@Override
		public void remove() {
			iter.remove();
		}
	}
	
	/**
	 * Entries in a PH-tree with ranged objects. 
	 */
	public static class PHREntry {

		private final double[] lower;
		private final double[] upper;
		
		/**
		 * Range object constructor.
		 * @param lower
		 * @param upper
		 */
		public PHREntry(double[] lower, double[] upper) {
			this.lower = lower;
			this.upper = upper;
		}

		/**
		 * Range object constructor.
		 * @param point lower and upper point in one array
		 */
		public PHREntry(double[] point) {
			int dim = point.length>>1;
			this.lower = new double[dim];
			this.upper = new double[dim];
			System.arraycopy(point, 0, lower, 0, dim);
			System.arraycopy(point, dim, upper, 0, dim);
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

		@Override
		public boolean equals(Object obj) {
			if (obj == null || !(obj instanceof PHREntry)) {
				return false;
			}
			PHREntry e = (PHREntry) obj;
			return Arrays.equals(lower, e.lower) && Arrays.equals(upper, e.upper);
		}
		
		@Override
		public int hashCode() {
			return Arrays.hashCode(lower) ^ Arrays.hashCode(upper);
		}
		
		@Override
		public String toString() {
			return "{" + Arrays.toString(lower) + "," + Arrays.toString(upper) + "}";
		}
	}


	@Override
	public Iterator<PHREntry> iterator() {
		return new PHREntryIteratorI(pht.queryExtent(), DIM);
	}

	public class PHREntryIteratorI implements Iterator<PHREntry> {
		private final Iterator<long[]> iter;
		private final int DIM;
		private PHREntryIteratorI(Iterator<long[]> iter, int DIM) {
			this.iter = iter;
			this.DIM = DIM;
		}
		@Override
		public boolean hasNext() {
			return iter.hasNext();
		}
		@Override
		public PHREntry next() {
			double[] lower = new double[DIM];
			double[] upper = new double[DIM];
			pre.post(iter.next(), lower, upper);
			return new PHREntry(lower, upper);
		}
		@Override
		public void remove() {
			iter.remove();
		}
	}
	
	public PhTreeNV getInternalTree() {
		return pht;
	}

	/**
	 * @param lo1
	 * @param up1
	 * @param lo2
	 * @param up2
	 * @return true, if the value could be replaced.
	 * @see PhTree#update(long[], long[])
	 */
	public boolean update(double[] lo1, double[] up1, double[] lo2, double[] up2) {
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
	public List<PHREntry> queryIntersectAll(double[] lower, double[] upper) {
		return queryIntersectAll(lower, upper, Integer.MAX_VALUE, PhPredicate.ACCEPT_ALL,
				((point) -> (new PHREntry(PhMapperK.toDouble(point)))));
	}

	public <R> List<R> queryIntersectAll(double[] lower, double[] upper, int maxResults, 
			PhPredicate filter, PhMapperKey<R> mapper) {
		long[] lUpp = new long[lower.length << 1];
		long[] lLow = new long[lower.length << 1];
		pre.pre(MIN, lower, lLow);
		pre.pre(upper, MAX, lUpp);
		return ((PhTreeVProxy)pht).queryAll(lLow, lUpp, maxResults, filter, mapper);
	}

	/**
	 * @return The number of entries in the tree.
	 */
	public int size() {
		return pht.size();
	}
}
