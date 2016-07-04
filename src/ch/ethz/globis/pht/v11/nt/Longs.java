/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v11.nt;

import java.util.Arrays;

import ch.ethz.globis.pht.PhTreeHelper;

/**
 * Manipulation methods and pool for long[].
 * 
 * @author ztilmann
 */
public class Longs {
	
	private static final long[] EMPTY_REF_ARRAY = {};
    private static final ArrayPoolN POOL_N = 
    		new ArrayPoolN(PhTreeHelper.ARRAY_POOLING_MAX_ARRAY_SIZE, 
    				PhTreeHelper.ARRAY_POOLING_POOL_SIZE);

    private Longs() {
    	//nothing
    }
    
    private static class ArrayPoolN {
    	private final int maxArraySize;
    	private final int maxArrayCount;
    	long[][][] pool;
    	int[] poolSize;
    	ArrayPoolN(int maxArraySize, int maxArrayCount) {
			this.maxArraySize = maxArraySize;
			this.maxArrayCount = maxArrayCount;
			this.pool = new long[maxArraySize+1][maxArrayCount][];
			this.poolSize = new int[maxArraySize+1];
		}
    	
    	long[] getArray(int size) {
    		if (size == 0) {
    			return EMPTY_REF_ARRAY;
    		}
    		if (size > maxArraySize) {
    			return new long[size];
    		}
    		synchronized (this) {
	    		int ps = poolSize[size]; 
	    		if (ps > 0) {
	    			poolSize[size]--;
	    			long[] ret = pool[size][ps-1];
	    			Arrays.fill(ret, 0);
	    			return ret;
	    		}
    		}
    		return new long[size];
    	}
    	
    	synchronized void offer(long[] a) {
    		int size = a.length;
    		if (size == 0 || size > maxArraySize) {
    			return;
    		}
    		synchronized (this) {
    			int ps = poolSize[size]; 
    			if (ps < maxArrayCount) {
    				pool[size][ps] = a;
    				poolSize[size]++;
    			}
    		}
    	}
    }

    
    /**
     * Calculate array size for given number of bits.
     * This takes into account JVM memory management, which allocates multiples of 8 bytes.
     * @param nLong
     * @return array size.
     */
	public static int calcArraySize(int nLong) {
		return nLong;
	}

    /**
     * Resize an array.
     * @param oldA
     * @param newSize
     * @return New array larger array.
     */
    public static long[] arrayExpand(long[] oldA, int newSize) {
    	long[] newA = arrayCreate(newSize);
    	arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL_N.offer(oldA);
    	return newA;
    }
    
	public static long[] arrayCreate(int size) {
		return POOL_N.getArray(calcArraySize(size));
    }
    
	public static long[] arrayReplace(long[] oldA, long[] newA) {
		if (oldA != null) {
			POOL_N.offer(oldA);
		}
		return newA;
    }
    
    public static long[] arrayClone(long[] oldA) {
    	long[] newA = arrayCreate(oldA.length);
    	arraycopy(oldA, 0, newA, 0, oldA.length);
    	return newA;
    }
    
    /**
     * Ensure capacity of an array. Expands the array if required.
     * @param oldA
     * @param requiredSize
     * @return Same array or expanded array.
     */
    public static long[] arrayEnsureSize(long[] oldA, int requiredSize) {
    	if (isCapacitySufficient(oldA, requiredSize)) {
    		return oldA;
    	}
    	return arrayExpand(oldA, requiredSize);
    }
    
    public static boolean isCapacitySufficient(long[] a, int requiredSize) {
    	return a.length >= requiredSize;
    }
    
	public static long[] arrayTrim(long[] oldA, int requiredSize) {
    	int reqSize = calcArraySize(requiredSize);
    	if (oldA.length == reqSize) {
    		return oldA;
    	}
    	long[] newA = POOL_N.getArray(reqSize);
     	arraycopy(oldA, 0, newA, 0, reqSize);
     	POOL_N.offer(oldA);
    	return newA;
    }
    
    public static void writeArray(long[] src, long[] dst, int dstPos, int length) {
    	arraycopy(src, 0, dst, dstPos, length);
    }

	public static void writeArray(long[] src, int srcPos, long[] dst, int dstPos, int length) {
		arraycopy(src, srcPos, dst, dstPos, length);
	}

	public static void readArray(long[] src, int srcPos, long[] dst) {
		arraycopy(src, srcPos, dst, 0, dst.length);
	}

	public static long[] insertArray(long[] oldA, long[] kdKey, int dstPos, int length) {
		long[] ret = arrayCreate(oldA.length + kdKey.length);
		arraycopy(oldA, 0, ret, 0, dstPos);
		arraycopy(kdKey, 0, ret, dstPos, length);
		arraycopy(oldA, dstPos, ret, dstPos+length, oldA.length-dstPos);
		return ret;
	}

	public static long[] arrayRemove(long[] oldA, int dstPos, int length) {
		long[] ret = arrayCreate(oldA.length - length);
		arraycopy(oldA, 0, ret, 0, dstPos);
		arraycopy(oldA, dstPos+length, ret, dstPos, ret.length-dstPos);
		return ret;
	}

	public static void arraycopy(long[] src, int srcPos, long[] dst, int dstPos, int len) {
		if (len < 10) {
			for (int i = 0; i < len; i++) {
				dst[dstPos+i] = src[srcPos+i]; 
			}
		} else {
			System.arraycopy(src, srcPos, dst, dstPos, len);
		}
	}
	
}
