/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16hd.bst;

import java.util.Arrays;

import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.v16hd.Node;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;

public class BSTPool {

    private static final BSTArrayPool POOL_ENTRY = new BSTArrayPool();
    private static final KeyArrayPool POOL_KEY = new KeyArrayPool();
    private static final NodeArrayPool POOL_NODES = new NodeArrayPool();
    private static final NodePool POOL_NODE = new NodePool();

    private BSTPool() {
    	// empty
    }
    
    private static class BSTArrayPool {
    	private static final BSTEntry[] EMPTY_REF_ARRAY = {};
    	private final int maxArraySize = 100;
    	private final int maxArrayCount = 100;
    	private final BSTEntry[][][] pool;
    	private byte[] poolSize;
    	BSTArrayPool() {
			this.pool = new BSTEntry[maxArraySize+1][maxArrayCount][];
			this.poolSize = new byte[maxArraySize+1];
		}
    	
    	BSTEntry[] getArray(int size) {
    		if (size == 0) {
    			return EMPTY_REF_ARRAY;
    		}
    		if (size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return new BSTEntry[size];
    		}
    		synchronized (this) {
	    		int ps = poolSize[size]; 
	    		if (ps > 0) {
	    			poolSize[size]--;
	    			BSTEntry[] ret = pool[size][ps-1];
	    			pool[size][ps-1] = null;
	    			return ret;
	    		}
    		}
    		return new BSTEntry[size];
    	}
    	
    	void offer(BSTEntry[] a) {
    		int size = a.length;
    		if (size == 0 || size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return;
    		}
    		synchronized (this) {
    			int ps = poolSize[size]; 
    			if (ps < maxArrayCount) {
	    			Arrays.fill(a, null);
    				pool[size][ps] = a;
    				poolSize[size]++;
    			}
    		}
    	}
    }

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public static BSTEntry[] arrayExpand(BSTEntry[] oldA, int newSize) {
    	BSTEntry[] newA = POOL_ENTRY.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL_ENTRY.offer(oldA);
    	return newA;
	}

	
    /**
     * Discards oldA and returns newA.
     * @param oldA old array
     * @param newA new array
     * @return newA.
     */
    public static BSTEntry[] arrayReplace(BSTEntry[] oldA, BSTEntry[] newA) {
    	if (oldA != null) {
    		POOL_ENTRY.offer(oldA);
    	}
    	return newA;
    }
    
	
    private static class KeyArrayPool {
    	private static final long[][] EMPTY_REF_ARRAY = {};
    	private final int maxArraySize = 100;
    	private final int maxArrayCount = 100;
    	private final long[][][][] pool;
    	private byte[] poolSize;
    	KeyArrayPool() {
			this.pool = new long[maxArraySize+1][maxArrayCount][][];
			this.poolSize = new byte[maxArraySize+1];
		}
    	
    	long[][] getArray(int size) {
    		if (size == 0) {
    			return EMPTY_REF_ARRAY;
    		}
    		if (size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return new long[size][];
    		}
    		synchronized (this) {
	    		int ps = poolSize[size]; 
	    		if (ps > 0) {
	    			poolSize[size]--;
	    			long[][] ret = pool[size][ps-1];
	    			pool[size][ps-1] = null;
	    			return ret;
	    		}
    		}
    		return new long[size][];
    	}
    	
    	void offer(long[][] a) {
    		int size = a.length;
    		if (size == 0 || size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return;
    		}
    		synchronized (this) {
    			int ps = poolSize[size]; 
    			if (ps < maxArrayCount) {
	    			Arrays.fill(a, null);
    				pool[size][ps] = a;
    				poolSize[size]++;
    			}
    		}
    	}
    }

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public static long[][] arrayExpand(long[][] oldA, int newSize) {
    	long[][] newA = POOL_KEY.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL_KEY.offer(oldA);
    	return newA;
	}

	
    /**
     * Discards oldA and returns newA.
     * @param oldA old array
     * @param newA new array
     * @return newA.
     */
    public static long[][] arrayReplace(long[][] oldA, long[][] newA) {
    	if (oldA != null) {
    		POOL_KEY.offer(oldA);
    	}
    	return newA;
    }
    
	
    private static class NodeArrayPool {
    	private static final BSTreePage[] EMPTY_REF_ARRAY = {};
    	private final int maxArraySize = 100;
    	private final int maxArrayCount = 100;
    	private final BSTreePage[][][] pool;
    	private byte[] poolSize;
    	NodeArrayPool() {
			this.pool = new BSTreePage[maxArraySize+1][maxArrayCount][];
			this.poolSize = new byte[maxArraySize+1];
		}
    	
    	BSTreePage[] getArray(int size) {
    		if (size == 0) {
    			return EMPTY_REF_ARRAY;
    		}
    		if (size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return new BSTreePage[size];
    		}
    		synchronized (this) {
	    		int ps = poolSize[size]; 
	    		if (ps > 0) {
	    			poolSize[size]--;
	    			BSTreePage[] ret = pool[size][ps-1];
	    			pool[size][ps-1] = null;
	    			return ret;
	    		}
    		}
    		return new BSTreePage[size];
    	}
    	
    	void offer(BSTreePage[] a) {
    		int size = a.length;
    		if (size == 0 || size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return;
    		}
    		synchronized (this) {
    			int ps = poolSize[size]; 
    			if (ps < maxArrayCount) {
	    			Arrays.fill(a, null);
    				pool[size][ps] = a;
    				poolSize[size]++;
    			}
    		}
    	}
    }

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public static BSTreePage[] arrayExpand(BSTreePage[] oldA, int newSize) {
    	BSTreePage[] newA = POOL_NODES.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL_NODES.offer(oldA);
    	return newA;
	}

	
    /**
     * Discards oldA and returns newA.
     * @param oldA old array
     * @param newA new array
     * @return newA.
     */
    public static BSTreePage[] arrayReplace(BSTreePage[] oldA, BSTreePage[] newA) {
    	if (oldA != null) {
    		POOL_NODES.offer(oldA);
    	}
    	return newA;
    }
    
	
    private static class NodePool {
    	private final int maxArrayCount = 100;
    	private final BSTreePage[] pool;
    	private byte poolSize;
    	NodePool() {
			this.pool = new BSTreePage[maxArrayCount];
		}
    	
    	BSTreePage get() {
    		synchronized (this) {
	    		int ps = poolSize; 
	    		if (ps > 0) {
	    			poolSize--;
	    			BSTreePage ret = pool[ps-1];
	    			pool[ps-1] = null;
	    			return ret;
	    		}
    		}
    		return null;
    	}
    	
    	void offer(BSTreePage a) {
    		if (!PhTreeHelper.ARRAY_POOLING) {
    			return;
    		}
    		synchronized (this) {
    			int ps = poolSize; 
    			if (ps < maxArrayCount) {
    				pool[ps] = a;
    				poolSize++;
    			}
    		}
    	}
    }

	public static void reportFreeNode(BSTreePage p) {
		POOL_NODE.offer(p);
	}

	public static BSTreePage getNode(Node ind, BSTreePage parent, boolean isLeaf) {
		BSTreePage p = POOL_NODE.get();
		if (p != null) {
			p.init(ind, parent, isLeaf);
			return p;
		}
		return new BSTreePage(ind, parent, isLeaf);
	}
	

}
