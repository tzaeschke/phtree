/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util.unsynced;

import ch.ethz.globis.phtree.PhTreeHelper;

import java.util.Arrays;


/**
 * Pool for long[][] instances.
 *
 * NL is the no-locking (unsynchronized) version.
 *
 * @author ztilmann
 */
public class LongArrayArrayPool {

	private static final long[][] EMPTY_REF_ARRAY = {};

	private final int maxArraySize;
	private final int maxArrayCount;
	private long[][][][] pool;
	private int[] poolSize;

	public static LongArrayArrayPool create() {
		return new LongArrayArrayPool(PhTreeHelper.ARRAY_POOLING_MAX_ARRAY_SIZE,
				PhTreeHelper.ARRAY_POOLING_POOL_SIZE);
	}

	private LongArrayArrayPool(int maxArraySize, int maxArrayCount) {
		this.maxArraySize = maxArraySize;
		this.maxArrayCount = maxArrayCount;
		this.pool = new long[maxArraySize+1][maxArrayCount][][];
		this.poolSize = new int[maxArraySize+1];
	}

	private long[][] getArray(int size) {
		if (size == 0) {
			return EMPTY_REF_ARRAY;
		}
		if (size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
			return new long[size][];
		}
		int ps = poolSize[size];
		if (ps > 0) {
			poolSize[size]--;
			long[][] ret = pool[size][ps-1];
			pool[size][ps-1] = null;
			return ret;
		}
		return new long[size][];
	}

	private void offer(long[][] a) {
		int size = a.length;
		if (size == 0 || size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
			return;
		}
		int ps = poolSize[size];
		if (ps < maxArrayCount) {
			Arrays.fill(a, null);
			pool[size][ps] = a;
			poolSize[size]++;
		}
	}

	/**
     * Calculate array size for given number of bits.
     * This takes into account JVM memory management, which allocates multiples of 8 bytes.
     * @param nObjects size of the array
     * @return array size.
     */
	public int calcArraySize(int nObjects) {
		//round up to 8byte=2refs
		//return (nObjects+1) & (~1);
		int arraySize = (nObjects+PhTreeHelper.ALLOC_BATCH_REF);// & (~1);
		int size = PhTreeHelper.ALLOC_BATCH_SIZE * 2;
		//Integer div.
		arraySize = (arraySize/size) * size;
		return arraySize;
	}

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public long[][] arrayExpand(long[][] oldA, int newSize) {
    	long[][] newA = arrayCreate(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	offer(oldA);
    	return newA;
    }

	/**
     * Create an array.
     * @param size size
     * @return a new array
     */
	public long[][] arrayCreate(int size) {
    	return getArray(calcArraySize(size));
    }
    
    /**
     * Discards oldA and returns newA.
     * @param oldA old array
     * @param newA new array
     * @return newA.
     */
    public long[][] arrayReplace(long[][] oldA, long[][] newA) {
    	if (oldA != null) {
    		offer(oldA);
    	}
    	return newA;
    }

	/**
	 * Discards oldA.
	 *
	 * @param oldA old array
	 */
	public void arrayDiscard(long[][] oldA) {
		if (oldA != null) {
			offer(oldA);
		}
	}

	/**
	 * Clones an array.
	 * @param oldA old array
	 * @return a copy or the input array
	 */
    public long[][] arrayClone(long[][] oldA) {
		long[][] newA = arrayCreate(oldA.length);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	return newA;
    }
}
