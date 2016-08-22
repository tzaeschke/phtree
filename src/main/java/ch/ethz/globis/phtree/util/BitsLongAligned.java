/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;

import static ch.ethz.globis.phtree.PhTreeHelper.DEBUG;

import java.util.Arrays;

/**
 * Bit-stream manipulation functions.
 * Works on long[] arrays where all values are aligned to multiples of 64bit. 
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BitsLongAligned {

	/** UNIT_3=6 (2^UNIT_3 = 64) */
    static final int UNIT_3 = 6;  			//EXP: 2^EXP = BITS
    public static final int UNIT_BITS = (1<<UNIT_3);
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
    
	/**
     * 
     * @param ba The array to read bits from.
     * @param offsetBit The bit to start reading at.
     * @param entryLen The length of the entry in bit.
     * @return The read bits as long
     */    
    public static long readArray64(long[] ba, int offsetBit) {
    	return ba[offsetBit];
    }

    public static void readArray64(long[] ba, int offsetBit, long out[]) {
    	//System.arraycopy(ba, offsetBit>>>6, out, 0, out.length);
    	for (int i = 0; i < out.length; i++) {
    		out[i] = ba[offsetBit+i];
    	}
    }

    public static long readArrayByBit(long[] ba, int offsetBit, int entryLen) {
        int pA = offsetBit >>> UNIT_3;
    	if (DEBUG && pA >= ba.length) {
    		throw new IllegalStateException("len=" + ba.length + "  ofs=" + offsetBit);
    	}
    	
    	int srcLocStart = offsetBit & UNIT_0x1F;
    	long mask1 = UNIT_0xFF >>> srcLocStart;
    	long ret = ba[pA] & mask1;

    	int srcLocalEnd = (offsetBit+entryLen-1) & UNIT_0x1F;
    	srcLocalEnd++;  //bit after last bit, could be 64
    	
    	if (srcLocStart + entryLen > UNIT_BITS) {
    		//read from second slot;
    		ret <<= srcLocalEnd;
    		long mask2 = UNIT_0xFF >>> srcLocalEnd;
    		ret |= Long.rotateLeft(ba[pA+1] & ~mask2, srcLocalEnd);
    	} else {
    		ret >>>= (UNIT_BITS-srcLocalEnd);
    	}
    	return ret;
    }
    

    /**
     * 
     * @param ba
     * @param offsetBit
     * @param val
     */
    public static void writeArray64(long[] ba, int offsetBit, final long val) {
    	ba[offsetBit] = val;
    }
    
    public static void writeArray64(long[] ba, int offsetBit, final long[] val) {
    	for (int i = 0; i < val.length; i++) {
    		ba[offsetBit+i] = val[i];
    	}
    }
    
    /**
     * 
     * @param ba
     * @param offsetBit
     * @param entryLen bits to write, starting with least significant bit (rightmost bit).
     * @param val
     */
    public static void writeArrayByBit(long[] ba, int offsetBit, int entryLen, final long val) {
    	if (entryLen == 0) {
    		return;
    	}
    	int pA = offsetBit >>> UNIT_3;
       	int startBit = offsetBit & UNIT_0x1F;
       	int endPos = offsetBit+entryLen-1; 
       	int endBit = endPos & UNIT_0x1F;
       	endBit++; //Pos AFTER last bit
   		long mask1 = (-1L >>> startBit); //0x0000FFFF
   		long mask2 = endBit == UNIT_BITS ? 0 : (-1L >>> endBit);  //0x0FFFF
   		//if (endPos >>> UNIT_3 > pA) {
   		if (endBit <= startBit) {
       		//spread over two longs
       		ba[pA] &= ~mask1;
       		//we can remove the UNIT_0x1F, because that's done implicitly by >>>
       		ba[pA] |= (val >>> ((offsetBit+entryLen) /*& UNIT_0x1F*/)) & mask1;
       		ba[pA+1] &= mask2;
       		ba[pA+1] |= Long.rotateRight(val, endBit) & ~mask2;
       	} else {
       		//all in same 'long'
       		long mask = mask1 & ~mask2;
       		ba[pA] &= ~mask;
       		ba[pA] |= (val<<(UNIT_BITS-endBit)) & mask;
       	}
    }

    /**
     * Insert bits at the given position.
     * The resulting array is NOT resized, bit that do do not fit in the current array are lost.  
     * @param ba
     * @param start
     * @param nBits
     */
    public static void insertBits(long[] ba, final int start, final int nBits) {
		if (nBits == 0 || (start+nBits)>=ba.length*UNIT_BITS) {
			return;
		}
		if (DEBUG && (nBits < 0 || start < 0)) {
			throw new IllegalArgumentException("nBits=" + nBits + "  start=" + start);
		}
		int posSrc = start;
		int posDst = start+nBits;
		int len = ba.length-posDst;
		if (len > 16) {
			System.arraycopy(ba, posSrc, ba, posDst, len);
		} else {
			for (int i = len-1; i >=0; i--) {
				ba[posDst+i] = ba[posSrc+i];
			}
		}
    }
		
    
    
    public static void removeBits(long[] ba, final int start, final int nBits) {
		if (nBits == 0 || (start+nBits)>=ba.length*UNIT_BITS) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException("nBits=" + nBits);
		}
		int posSrc = start+nBits;
		int posDst = start;
		int len = ba.length-posSrc;
		if (len > 16) {
			System.arraycopy(ba, posSrc, ba, posDst, len);
		} else {
			for (int i = 0; i < len; i++) {
				ba[posDst+i] = ba[posSrc+i];
			}
		}
    }



    public static void copyBitsLeft64(long[] src, int posSrc, long[] trg, int posTrg, int nBits) {
    	if (nBits == 0) {
    		return;
    	}
		if (DEBUG && (posSrc+nBits>src.length*UNIT_BITS || posTrg+nBits>trg.length*UNIT_BITS)) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException("nBits=" + nBits);
		}

		if (nBits >= 16) {
			System.arraycopy(src, posSrc, trg, posTrg, nBits);
		} else {
			for (int i = 0; i < nBits; i++) {
				trg[posTrg+i] = src[posSrc+i];
			}
		}
    }
    	
	/**
	 * @Param posBit Counts from left to right!!!
	 */
    public static boolean getBit(long[] ba, int posBit) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x1F;
        return (ba[pA] & (UNIT_0x8000 >>> posBit)) != 0;
	}

	/**
	 * @Param posBit Counts from left to right (highest to lowest)!!!
	 */
    public static void setBit(long[] ba, int posBit, boolean b) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x1F;
        if (b) {
            ba[pA] |= (UNIT_0x8000 >>> posBit);
        } else {
            ba[pA] &= (~(UNIT_0x8000 >>> posBit));
        }
	}

    
    /**
     * 
     * 
     * @param ba			byte[]
     * @param startBit		start bit
     * @param nEntries		number of entries = number of keys
     * @param key			key to search for
     * @param keyWidth		bit width of the key
     * @param valueWidth	bit width of the value. An entry consists of key and value.
     * @return				index of key or according negative index if key was not found
     */
    public static int binarySearch(long[] ba, int startBit, int nEntries, long key, int keyWidth, 
    		int valueWidth) {
    	int entryWidth = keyWidth + valueWidth; 
    	int min = 0;
    	int max = nEntries - 1;

    	while (min <= max) {
    		int mid = (min + max) >>> 1;
            //long midKey = readArray(ba, mid*entryWidth+startBit, keyWidth);
    		long midKey = ba[mid*entryWidth+startBit];

            if (midKey < key) {
            	min = mid + 1;
            } else if (midKey > key) {
            	max = mid - 1;
            } else {
            	return mid; // key found
            }
    	}
    	return -(min + 1);  // key not found.
    }
    
    /**
     * 
     * 
     * @param ba			byte[]
     * @param startBit		start bit
     * @param nEntries		number of entries = number of keys
     * @param key			key to search for
     * @return				index of key or according negative index if key was not found
     */
    public static int binarySearch(long[] ba, int startBit, int nEntries, long key) {
//    	return Arrays.binarySearch(ba, startBit, startBit+nEntries, key);
    	int min = 0;
    	int max = nEntries - 1;

    	while (min <= max) {
    		int mid = (min + max) >>> 1;
            //long midKey = readArray(ba, mid*entryWidth+startBit, keyWidth);
    		long midKey = ba[mid + startBit];

            if (midKey < key) {
            	min = mid + 1;
            } else if (midKey > key) {
            	max = mid - 1;
            } else {
            	return mid; // key found
            }
    	}
    	return -(min + 1);  // key not found.
    }
    
    /**
     * Calculate array size for given number of bits.
     * This takes into account JVM memory management, which allocates multiples of 8 bytes.
     * @param nBits
     * @return array size.
     */
    public static int calcArraySize(int nBits) {
    	//+63  --> round up to 8 byte = 64bit alignment
    	//>>>3 --> turn bits into bytes
    	//>>>3 --> turn into 8byte units
    	return nBits;
    }

    /**
     * Resize an array.
     * @param oldA
     * @param newSizeBits
     * @return New array larger array.
     */
    public static long[] arrayExpand(long[] oldA, int newSizeBits) {
    	long[] newA = new long[calcArraySize(newSizeBits)];
    	//System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	copyBitsLeft64(oldA, 0, newA, 0, oldA.length);
    	if (DEBUG) statAExpand++;
    	return newA;
    }
    
    public static long[] arrayCreate(int nBits) {
    	long[] newA = new long[calcArraySize(nBits)];
    	if (DEBUG) statACreate++;
    	return newA;
    }
    
    /**
     * Ensure capacity of an array. Expands the array if required.
     * @param oldA
     * @param requiredBits
     * @return Same array or expanded array.
     */
    public static long[] arrayEnsureSize(long[] oldA, int requiredBits) {
    	if (isCapacitySufficient(oldA, requiredBits)) {
    		return oldA;
    	}
    	return arrayExpand(oldA, requiredBits);
    }
    
    public static boolean isCapacitySufficient(long[] a, int requiredBits) {
    	return (a.length >= requiredBits);
    }
    
    public static long[] arrayTrim(long[] oldA, int requiredBits) {
    	int reqSize = calcArraySize(requiredBits);
    	if (oldA.length == reqSize) {
    		return oldA;
    	}
    	long[] newA = new long[reqSize];
    	//System.arraycopy(oldA, 0, newA, 0, reqSize);
    	copyBitsLeft64(oldA, 0, newA, 0, reqSize);
    	if (DEBUG) statATrim++;
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
    
    public static String getStats() {
        return "Array create: " + Bits.statACreate + "  exp:" + Bits.statAExpand + 
        		"  trm:" + Bits.statATrim + 
        		"  oldRS:" + Bits.statOldRightShift + " / " + Bits.statOldRightShiftTime;
    }
}
