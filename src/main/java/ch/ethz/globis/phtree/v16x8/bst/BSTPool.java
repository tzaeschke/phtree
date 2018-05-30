/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16x8.bst;

import java.util.Arrays;

import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.v16x8.Node;
import ch.ethz.globis.phtree.v16x8.Node.BSTEntry;

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
     * Create an array.
     * @param newSize size
     * @return New array.
     */
    public static BSTEntry[] arrayCreateEntries(int newSize) {
    	return POOL_ENTRY.getArray(newSize);
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
     * Discards oldA.
     * @param oldA old array
     */
    public static void arrayDiscard(BSTEntry[] oldA) {
    	if (oldA != null) {
    		POOL_ENTRY.offer(oldA);
    	}
    }
    
	
    private static class KeyArrayPool {
    	private static final byte[] EMPTY_REF_ARRAY = {};
    	private final int maxArraySize = 100;
    	private final int maxArrayCount = 100;
    	private final byte[][][] pool;
    	private byte[] poolSize;
    	KeyArrayPool() {
			this.pool = new byte[maxArraySize+1][maxArrayCount][];
			this.poolSize = new byte[maxArraySize+1];
		}
    	
    	byte[] getArray(int size) {
    		if (size == 0) {
    			return EMPTY_REF_ARRAY;
    		}
    		if (size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return new byte[size];
    		}
    		synchronized (this) {
	    		int ps = poolSize[size]; 
	    		if (ps > 0) {
	    			poolSize[size]--;
	    			byte[] ret = pool[size][ps-1];
	    			pool[size][ps-1] = null;
	    			return ret;
	    		}
    		}
    		return new byte[size];
    	}
    	
    	void offer(byte[] a) {
    		int size = a.length;
    		if (size == 0 || size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return;
    		}
    		synchronized (this) {
    			int ps = poolSize[size]; 
    			if (ps < maxArrayCount) {
	    			Arrays.fill(a, (byte)0);
    				pool[size][ps] = a;
    				poolSize[size]++;
    			}
    		}
    	}
    }

    /**
     * Create a new array.
     * @param newSize size
     * @return New array.
     */
    public static byte[] arrayCreateLong(int newSize) {
    	return POOL_KEY.getArray(newSize);
	}

	
    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public static byte[] arrayExpand(byte[] oldA, int newSize) {
    	byte[] newA = POOL_KEY.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL_KEY.offer(oldA);
    	return newA;
	}

	
    /**
     * Discards oldA.
     * @param oldA old array
     */
    public static void arrayDiscard(byte[] oldA) {
    	if (oldA != null) {
    		POOL_KEY.offer(oldA);
    	}
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
     * Create an array.
     * @param newSize size
     * @return New array.
     */
    public static BSTreePage[] arrayCreateNodes(int newSize) {
    	return POOL_NODES.getArray(newSize);
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
     * Discards oldA.
     * @param oldA old array
     */
    public static void arrayDiscard(BSTreePage[] oldA) {
    	if (oldA != null) {
    		POOL_NODES.offer(oldA);
    	}
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
		POOL_KEY.offer(p.getKeys());
		if (p.isLeaf()) {
			p.updateNeighborsRemove();
			POOL_ENTRY.offer(p.getValues());
		} else {
			POOL_NODES.offer(p.getSubPages());
		}
		p.nullify();
		POOL_NODE.offer(p);
	}

	public static BSTreePage getNode(Node ind, BSTreePage parent, boolean isLeaf, BSTreePage leftPredecessor) {
		BSTreePage p = POOL_NODE.get();
		if (p != null) {
			p.init(ind, parent, isLeaf, leftPredecessor);
			return p;
		}
		return new BSTreePage(ind, parent, isLeaf, leftPredecessor);
	}
	

}
