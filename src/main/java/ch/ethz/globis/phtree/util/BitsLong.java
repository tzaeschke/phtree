/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
 *
 * This file is part of the PH-Tree project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.ethz.globis.phtree.util;

import static ch.ethz.globis.phtree.PhTreeHelper.DEBUG;

import java.util.Arrays;

import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.unsynced.LongArrayOps;

/**
 * Bit-stream manipulation functions.
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BitsLong extends LongArrayOps {

	public static final long[] EMPTY_LONG_ARRAY = {};
 
	/** UNIT_3=6 (2^UNIT_3 = 64) */
    static final int UNIT_3 = 6;  			//EXP: 2^EXP = BITS
    static final int UNIT_BITS = (1<<UNIT_3);
    /** & UNIT_0x1F <=> % 64 */
    private static final int UNIT_0x1F = 0x3F;  		//0x07 for byte=8 bits=3exp
    private static final long UNIT_0xFF = 0xFFFFFFFFFFFFFFFFL;  	//0xFF for byte=8 bits=3exp
    private static final long UNIT_0x8000 = 0x8000000000000000L;    //only first bit is set
    private static final int BYTES_PER_UNIT = 8;

    static int statACreate = 0;
    static int statAExpand = 0;
    static int statATrim = 0;
    static int statOldRightShift = 0;
    static int statOldRightShiftTime = 0;
    
    //private static final ArrayPool POOL = new ArrayPool(100, 100);
    public static final ArrayPool POOL = 
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
    public static long[] arrayExpand(long[] oldA, int newSizeBits) {
    	long[] newA = POOL.getArray(calcArraySize(newSizeBits));//new long[calcArraySize(newSizeBits)];
    	if (newSizeBits > 0) {
    		System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	}
    	POOL.offer(oldA);
    	statAExpand++;
    	return newA;
    }

    public static long[] arrayCreate(int nBits) {
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
    public static long[] arrayReplace(long[] oldA, long[] newA) {
    	POOL.offer(oldA);
    	return newA;
    }

    public static long[] arrayClone(long[] oldA) {
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
    public static long[] arrayEnsureSize(long[] oldA, int requiredBits) {
    	if (isCapacitySufficient(oldA, requiredBits)) {
    		return oldA;
    	}
    	return arrayExpand(oldA, requiredBits);
    }

    public static boolean isCapacitySufficient(long[] a, int requiredBits) {
    	return (a.length*UNIT_BITS >= requiredBits);
    }

    public static long[] arrayTrim(long[] oldA, int requiredBits) {
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

	public static int arraySizeInByte(long[] ba) {
		return ba.length*BYTES_PER_UNIT;
	}

	public static int arraySizeInByte(int arrayLength) {
		return arrayLength*BYTES_PER_UNIT;
	}

	public static String toBinary(long l) {
    	return toBinary(l, 64);
    }

	public static String toBinary(long l, int DEPTH) {
        StringBuilder sb = new StringBuilder();
        //long mask = DEPTH < 64 ? (1<<(DEPTH-1)) : 0x8000000000000000L;
        for (int i = 0; i < DEPTH; i++) {
            long mask = (1l << (long)(DEPTH-i-1));
            if ((l & mask) != 0) { sb.append("1"); } else { sb.append("0"); }
            if ((i+1)%8==0 && (i+1)<DEPTH) sb.append('.');
        	mask >>>= 1;
        }
        return sb.toString();
    }

	public static String toBinary(long[] la, int DEPTH) {
	    StringBuilder sb = new StringBuilder();
	    for (long l: la) {
	    	sb.append(toBinary(l, DEPTH));
	        sb.append(", ");
	    }
	    return sb.toString();
	}

	public static String toBinary(int[] la, int DEPTH) {
	    StringBuilder sb = new StringBuilder();
	    for (long l: la) {
	    	sb.append(toBinary(l, DEPTH));
	        sb.append(", ");
	    }
	    return sb.toString();
	}

    public static String toBinary(long[] ba) {
        StringBuilder sb = new StringBuilder();
        for (long l: ba) {
        	sb.append(toBinary(l, UNIT_BITS));
            sb.append(", ");
        }
        return sb.toString();
    }

	   public static String toBinary(double[] ba) {
	        StringBuilder sb = new StringBuilder();
	        for (double d: ba) {
	        	sb.append(toBinary(BitTools.toSortableLong(d), UNIT_BITS));
	            sb.append(", ");
	        }
	        return sb.toString();
	    }

    public static String getStats() {
        return "Array create: " + Bits.statACreate + "  exp:" + Bits.statAExpand +
        		"  trm:" + Bits.statATrim +
        		"  oldRS:" + Bits.statOldRightShift + " / " + Bits.statOldRightShiftTime;
    }
}
