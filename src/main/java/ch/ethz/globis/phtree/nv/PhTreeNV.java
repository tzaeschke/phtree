/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.nv;

import static ch.ethz.globis.phtree.PhTreeHelper.DEBUG;

import java.util.Iterator;
import java.util.List;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.util.Bits;
import ch.ethz.globis.phtree.util.PhMapperKey;
import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.util.StringBuilderLn;

/**
 * n-dimensional index (quad-/oct-/n-tree).
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 */
public abstract class PhTreeNV {

	public static PhTreeNV create(int dim) {
		//return new PhTree1(dim, depth);
		//return new PhTree2_CB(dim, depth);
		return new PhTreeVProxy(dim);
	}

	public PhTreeNV() {
    	debugCheck();
    }
    
    protected final void debugCheck() {
    	if (DEBUG) {
    		System.err.println("*************************************");
    		System.err.println("** WARNING ** DEBUG IS ENABLED ******");
    		System.err.println("*************************************");
    	}
//    	if (BLHC_THRESHOLD_DIM > 6) {
//    		System.err.println("*************************************");
//    		System.err.println("** WARNING ** BLHC IS DISABLED ******");
//    		System.err.println("*************************************");
//    	}
    }
    
    public abstract int size();
    
    public final void printQuality() {
    	System.out.println("Tree quality");
    	System.out.println("============");
        System.out.println(getQuality());
    }

    public abstract PhTreeStats getQuality();
    
    

    
    // ===== Adrien =====
    public void accept(PhTreeVisitor v) {
    	v.visit(this);
    }
    
    public abstract static class PhTreeVisitor {
    	public abstract void visit(PhTreeNV tree);
    }
    
    
    protected final int align8(int n) {
    	return (int) (8*Math.ceil(n/8.0));
    }
    
    /**
     * A value-set is an object with n=DIM values.
     * @param valueSet the value
     * @return true if the value already existed
     */
    public abstract boolean insert(long... valueSet);

    public abstract boolean contains(long... valueSet);

    

    /**
     * Print entry. An entry is an array of DIM boolean-arrays, each of which represents a number.
     * @param sb the string builder
     * @param entry an entry to print
     */
    protected final void printEntry(StringBuilderLn sb, long[] entry) {
        sb.appendLn(Bits.toBinary(entry, getDEPTH()));
    }

    /**
     * A value-set is an object with n=DIM values.
     * @param valueSet the value to delete
     * @return true if the value was found
     */
    public abstract boolean delete(long... valueSet);


    /**
     * @see PhTree#toStringPlain()
     * @return output
     */
    public abstract String toStringPlain();
    
    
    /**
     * @see PhTree#toStringTree()
     * @return output
     */
    public abstract String toStringTree();
    
    
	public abstract Iterator<long[]> queryExtent();


	/**
	 * Performs a range query. The parameters are the min and max values.
	 * @param min min value
	 * @param max max value
	 * @return Result iterator.
	 */
	public abstract PhIteratorNV query(long[] min, long[] max);
    
 
	public abstract int getDIM();

	public abstract int getDEPTH();

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of values to be returned. More values may be returned with several have
	 * 				the same distance.
	 * @param v the value
	 * @return List of neighbours.
	 */
	public abstract PhKnnQuery<long[]> nearestNeighbour(int nMin, long... v);

	/**
	 * Update the key of an entry. Update may fail if the old key does not exist, or if the new
	 * key already exists.
	 * @param oldKey old key
	 * @param newKey new key
	 * @return true iff the key was found and could be updated, otherwise false.
	 */
	public abstract boolean update(long[] oldKey, long[] newKey);
	
	/**
	 * Same as {@link #query(long[], long[])}, except that it returns a list
	 * instead of an iterator. This may be faster for small result sets. 
	 * @param min min value
	 * @param max max value
	 * @return List of query results
	 */
	public abstract List<PhEntry<Object>> queryAll(long[] min, long[] max);

	public abstract <R> List<R> queryAll(long[] min, long[] max, int maxResults, 
			PhFilter filter, PhMapperKey<R> mapper);
	
	public interface PhIteratorNV extends Iterator<long[]> { 
		public boolean hasNextKey();
		public long[] nextKey();
	}
}

