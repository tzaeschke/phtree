/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util.unsynced;

import ch.ethz.globis.phtree.PhTreeHelper;

import java.util.Arrays;

import static ch.ethz.globis.phtree.PhTreeHelper.DEBUG;

/**
 * long[] pool.
 * 
 * @author Tilmann Zaeschke
 *
 */
public class LongArrayPool {

	private static final long[] EMPTY_LONG_ARRAY = {};
 
	/** UNIT_3=6 (2^UNIT_3 = 64) */
    private static final int UNIT_3 = 6;  			//EXP: 2^EXP = BITS
	private static final int UNIT_BITS = (1<<UNIT_3);

    static int statACreate = 0;
    static int statAExpand = 0;
    static int statATrim = 0;

    //private static final ArrayPool POOL = new ArrayPool(100, 100);
    public final ArrayPool POOL =
    		new ArrayPool(PhTreeHelper.ARRAY_POOLING_MAX_ARRAY_SIZE, 
    				PhTreeHelper.ARRAY_POOLING_POOL_SIZE);
    
    public static class ArrayPool {
    	private final int maxArraySize;
    	private final int maxArrayCount;
    	long[][][] pool;
    	int[] poolSize;
    	int[] poolStatsNew;
    	ArrayPool(int maxArraySize, int maxArrayCount) {
			this.maxArraySize = maxArraySize;
			this.maxArrayCount = maxArrayCount;
			this.pool = new long[maxArraySize+1][maxArrayCount][];
			this.poolSize = new int[maxArraySize+1];
			if (DEBUG) {
				poolStatsNew = new int[10 * maxArraySize + 1];
			}
		}
    	
    	long[] getArray(int size) {
    		if (size == 0) {
    			return EMPTY_LONG_ARRAY;
    		}
    		if (PhTreeHelper.ARRAY_POOLING) {
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
    		}
    		if (DEBUG) {
				poolStatsNew[size]++;
			}
    		return new long[size];
    	}
    	
    	void offer(long[] a) {
    		if (PhTreeHelper.ARRAY_POOLING) {
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
    	
    	public String print() {
    		String r = "";
    		int total = 0;
    		for (int i = 0; i < poolSize.length; i++) {
    			r += "" + i + ":" + poolSize[i] + " ";
    			total += i*poolSize[i];
    		}
    		if (DEBUG) {
				r += System.lineSeparator();
				r += "Total size: " + total;
				r += System.lineSeparator();
				for (int i = 0; i < poolSize.length; i++) {
					r += "" + i + ":" + poolStatsNew[i] + " ";
				}
			}
    		return r;
    	}
    }
    
    public static LongArrayPool create() {
		return new LongArrayPool();
	}

    /**
     * Calculate array size for given number of bits.
     * This takes into account JVM memory management, which allocates multiples of 8 bytes.
     * @param nBits number of bits to store
     * @return array size.
     */
    public static int calcArraySize(int nBits) {
    	//+63  --> round up to 8 byte = 64bit alignment
    	//>>>3 --> turn bits into bytes
    	//>>>3 --> turn into 8byte units
    	//int arraySize = (nBits+63)>>>6;
    	int arraySize = (nBits+PhTreeHelper.ALLOC_BATCH_SIZE_LONG)>>>6;
    	int size = PhTreeHelper.ALLOC_BATCH_SIZE;
    	//integer division!
    	arraySize = (arraySize/size) * size;
    	return arraySize;
    }

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSizeBits new size
     * @return New array larger array.
     */
    public long[] arrayExpand(long[] oldA, int newSizeBits) {
    	long[] newA = POOL.getArray(calcArraySize(newSizeBits));//new long[calcArraySize(newSizeBits)];
    	if (newSizeBits > 0) {
    		System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	}
    	POOL.offer(oldA);
    	statAExpand++;
    	return newA;
    }
    
    public long[] arrayCreate(int nBits) {
    	long[] newA = POOL.getArray(calcArraySize(nBits));//new long[calcArraySize(nBits)];
    	statACreate++;
    	return newA;
    }
    
    /**
     * Discards oldA and returns newA.
     * @param oldA old array
     * @param newA new array
     * @return new array
     */
    public long[] arrayReplace(long[] oldA, long[] newA) {
    	POOL.offer(oldA);
    	return newA;
    }
    
    public long[] arrayClone(long[] oldA) {
    	long[] newA = POOL.getArray(oldA.length);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	statACreate++;
    	return newA;
    }
    
    /**
     * Ensure capacity of an array. Expands the array if required.
     * @param oldA old array
     * @param requiredBits required bits
     * @return Same array or expanded array.
     */
    public long[] arrayEnsureSize(long[] oldA, int requiredBits) {
    	if (isCapacitySufficient(oldA, requiredBits)) {
    		return oldA;
    	}
    	return arrayExpand(oldA, requiredBits);
    }
    
    private boolean isCapacitySufficient(long[] a, int requiredBits) {
    	return (a.length*UNIT_BITS >= requiredBits);
    }
    
    public long[] arrayTrim(long[] oldA, int requiredBits) {
    	int reqSize = calcArraySize(requiredBits);
    	if (oldA.length == reqSize) {
    		return oldA;
    	}
    	if (reqSize == 0) {
    		return EMPTY_LONG_ARRAY;
    	}
    	long[] newA = POOL.getArray(reqSize);//new long[reqSize];
    	System.arraycopy(oldA, 0, newA, 0, reqSize);
    	POOL.offer(oldA);
    	statATrim++;
    	return newA;
    }

}
