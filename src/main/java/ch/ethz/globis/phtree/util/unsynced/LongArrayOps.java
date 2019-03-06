/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util.unsynced;

import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.BitTools;
import ch.ethz.globis.phtree.v16hd.BitsHD;

import static ch.ethz.globis.phtree.PhTreeHelper.DEBUG;

/**
 * Bit-stream manipulation functions.
 * 
 * @author Tilmann Zaeschke
 *
 */
public class LongArrayOps {

	/** UNIT_3=6 (2^UNIT_3 = 64) */
    private static final int UNIT_3 = 6;  			//EXP: 2^EXP = BITS
	private static final int UNIT_BITS = (1<<UNIT_3);
    /** & UNIT_0x1F <=> % 64 */
    private static final int UNIT_0x1F = 0x3F;  		//0x07 for byte=8 bits=3exp
    private static final long UNIT_0xFF = 0xFFFFFFFFFFFFFFFFL;  	//0xFF for byte=8 bits=3exp
    private static final long UNIT_0x8000 = 0x8000000000000000L;    //only first bit is set
    private static final int BYTES_PER_UNIT = 8;

	/**
     * 
     * @param ba The array to read bits from.
     * @param offsetBit The bit to start reading at.
     * @param entryLen The length of the entry in bit.
     * @return The read bits as long
     */    
    public static long readArrayOLd(long[] ba, int offsetBit, int entryLen) {
        int pA = offsetBit >>> UNIT_3;
    	if (DEBUG && pA >= ba.length) {
    		throw new IllegalStateException("len=" + ba.length + "  ofs=" + offsetBit);
    	}
    	
    	long ret = ba[pA];
    	//end, local to the possible three fields
    	int semiLocalEnd = (offsetBit & UNIT_0x1F) + entryLen;
    	if (semiLocalEnd <= UNIT_BITS) {
    		//case one, extract only from first field
    		ret >>>= UNIT_BITS - semiLocalEnd;
    	} else {
    		//extends at least to second field
    		semiLocalEnd -= UNIT_BITS;
    		ret <<= semiLocalEnd;
    		ret |= Long.rotateRight(ba[pA+1], semiLocalEnd);
    	}
    	ret = (entryLen == UNIT_BITS) ? ret : ret & ~((-1L)<<entryLen);
    	return ret;
    }
    
    public static long readArray(long[] ba, int offsetBit, int entryLen) {
    	if (entryLen == 0) {
    		return 0;
    	}
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
     * @param ba byte array
     * @param offsetBit offset
     * @param entryLen bits to write, starting with least significant bit (rightmost bit).
     * @param val value to write
     */
    public static void writeArray(long[] ba, int offsetBit, int entryLen, final long val) {
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
	 * 
	 * @param ba byte array
	 * @param start start position
	 * @param nBits amount to shift, positive to right, negative to left.
	 */
    public static void insertBits1(long[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException();
		}
		long t1 = System.currentTimeMillis();
		//shift right
//		copyBits(ba2, start, ba2, start + nBits, ba2.length*8-start-nBits);
		int bitsToShift = ba.length*UNIT_BITS - start - nBits;
		for (int i = 0; i < bitsToShift; i++) {
			int srcBit = ba.length*UNIT_BITS - nBits - i - 1;
			int trgBit = ba.length*UNIT_BITS - i - 1;
			setBit(ba, trgBit, getBit(ba, srcBit));
		}
		long t2 = System.currentTimeMillis();
	}

    /**
     * Insert bits at the given position.
     * The resulting array is NOT resized, bit that do do not fit in the current array are lost.  
     * @param ba byte array 
     * @param start start position
     * @param nBits number of bits to insert
     */
    public static void insertBits(long[] ba, final int start, final int nBits) {
		if (nBits == 0 || (start+nBits)>=ba.length*UNIT_BITS) {
			return;
		}
		if (DEBUG && (nBits < 0 || start < 0)) {
			throw new IllegalArgumentException("nBits=" + nBits + "  start=" + start);
		}
		int srcByteStart = start >>> UNIT_3;  //integer division!
		int srcLocalStart = start & UNIT_0x1F;
		int dstByteStart = (start+nBits) >>> UNIT_3;  //integer division!
		int dstLocalStart = (start+nBits) & UNIT_0x1F;
		int localShift = nBits & UNIT_0x1F; //Always positive!
		int nBytesShift = nBits >>> UNIT_3; //integer division!
		if (localShift > 0) {
	        for (int i = ba.length-1; i > dstByteStart; i--) {
				ba[i] = (ba[i-nBytesShift] >>> localShift) |
						(ba[i-nBytesShift-1] << -localShift);
						//(Integer.rotateRight(ba[i-nBytesShift-1], localShift) & ~rMask);
			}
		} else {
	        for (int i = ba.length-1; i > dstByteStart; i--) {
	        	ba[i] = ba[i-nBytesShift];
	        }
		}

		long mask0 = (UNIT_0xFF >>> dstLocalStart);
		if (dstLocalStart < srcLocalStart) {
			long buf1 = ba[srcByteStart+1];
			//write first part
			ba[dstByteStart] = (ba[dstByteStart] & ~mask0) |
					(Long.rotateRight(ba[srcByteStart], localShift) & mask0);
			//write second part
			long mask1 = mask0 >>> -srcLocalStart; //(UNIT_BITS-srcLocalStart);
			ba[dstByteStart] = (ba[dstByteStart] & ~mask1) | ((buf1 >>> localShift) & mask1);
		} else {
			//write first part
			ba[dstByteStart] = (ba[dstByteStart] & ~mask0) |
					(Long.rotateRight(ba[srcByteStart], localShift) & mask0);
		}
    }
		
    
    
    public static void removeBits(long[] ba, final int start, final int nBits) {
		if (nBits == 0 || (start+nBits)>=ba.length*UNIT_BITS) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException("nBits=" + nBits);
		}
		final int srcByteStart = (start+nBits) >>> UNIT_3;  //integer division!
		final int srcLocalStart = (start+nBits) & UNIT_0x1F;
		int dstByteStart = start >>> UNIT_3;  //integer division!
		final int dstLocalStart = start & UNIT_0x1F;
		final int localShift = nBits & UNIT_0x1F; //Always positive!

		long mask0 = (UNIT_0xFF >>> dstLocalStart);
		if (dstLocalStart <= srcLocalStart) {
			//write first part
			//TODO why is this so much more complicated than insertBits?
			ba[dstByteStart] = ((ba[dstByteStart] & ~mask0) 
					| (Long.rotateLeft(ba[srcByteStart], localShift) & mask0))
					& (UNIT_0xFF << localShift);
		} else {
			ba[dstByteStart] = (ba[dstByteStart] & ~mask0) 
					| (Long.rotateLeft(ba[srcByteStart], localShift) & mask0);
			dstByteStart++;
			ba[dstByteStart] = ba[srcByteStart] << localShift;
			//nullify remaining bits for following writes (unnecessary if loop is skipped)
			ba[dstByteStart] &= UNIT_0xFF << (UNIT_BITS-dstLocalStart);
		}
		
		if (localShift > 0) {
			for (int i = srcByteStart+1; i < ba.length; i++) {
				//this assumes that the area has been nullified
				ba[dstByteStart] |= (ba[i] >>> -localShift);
				dstByteStart++;
				ba[dstByteStart] = (ba[i] << localShift);
			}
		} else {
			for (int i = srcByteStart+1; i < ba.length; i++) {
				dstByteStart++;
				ba[dstByteStart] = ba[i];
			}
		}
    }



    public static void copyBitsLeft(long[] src, int posSrc, long[] trg, int posTrg, 
    		int nBits) {
    	if (nBits == 0) {
    		return;
    	}
		if (DEBUG && (posSrc+nBits>src.length*UNIT_BITS || posTrg+nBits>trg.length*UNIT_BITS)) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException("nBits=" + nBits);
		}
		int srcByteStart = posSrc >>> UNIT_3;  //integer division!
		int srcLocalStart = posSrc & UNIT_0x1F;
		int dstByteStart = posTrg >>> UNIT_3;  //integer division!
		int dstLocalStart = posTrg & UNIT_0x1F;

		//fully src-local write?
		if (srcLocalStart + nBits <= UNIT_BITS) {
			writeArray(trg, posTrg, nBits, src[srcByteStart] >>> (UNIT_BITS-nBits-srcLocalStart));
			return;
		}
		//now we have at least two src-slots to read
		//fully dst-local write?
		if (dstLocalStart + nBits <= UNIT_BITS) {
			int lenSrc1 = UNIT_BITS-srcLocalStart;
			writeArray(trg, posTrg, lenSrc1, src[srcByteStart]);
			writeArray(trg, posTrg+lenSrc1, nBits-lenSrc1, 
					src[srcByteStart+1] >>> (UNIT_BITS-nBits+lenSrc1)); 
			return;
		}
		
		//now we have at least two src and two dst slots.

		//shortcut for start==end
		if (posSrc == posTrg) {
			if (srcLocalStart != 0 ) {
				long mask = UNIT_0xFF >>> (srcLocalStart);
				trg[dstByteStart] = (trg[dstByteStart] & ~mask) | (src[srcByteStart] & mask);
				dstByteStart++;
				srcByteStart++;
				nBits = nBits-64+srcLocalStart;
			}
    		int byteLen = nBits >>> UNIT_3;
    		System.arraycopy(src, srcByteStart, trg, dstByteStart, byteLen);
    		int len2 = (nBits & UNIT_0x1F);
    		if (len2 > 0) {
    			//writeArray(trg, byteLen, len2, Long.rotateLeft(src[byteLen], len2));
    			long mask = UNIT_0xFF >>> len2;
    			dstByteStart += byteLen;
    			srcByteStart += byteLen;
    			trg[dstByteStart] = (trg[dstByteStart] & mask) | (src[srcByteStart] & ~mask);
    		}
    		return;
    	}

		
		//first ensure that dstLocalBit >= srcLocalBit
		long mask0 = (UNIT_0xFF >>> dstLocalStart);
		if (dstLocalStart < srcLocalStart) {
			int rotLeft = srcLocalStart-dstLocalStart;
			//writeArray(trg, dstByteStart, UNIT_BITS-srcLocalStart, src[srcByteStart]<<());
			trg[dstByteStart] = (trg[dstByteStart] & ~mask0) 
					| (Long.rotateLeft(src[srcByteStart], rotLeft) & mask0);
			srcByteStart++;
			int move = UNIT_BITS-srcLocalStart;
			dstLocalStart += move;
			srcLocalStart = 0;
			posSrc += move;
			posTrg += move;
			nBits -= move;
			mask0 = UNIT_0xFF >>> dstLocalStart;
		}
		
		int rotRight = dstLocalStart-srcLocalStart;
		trg[dstByteStart] = (trg[dstByteStart] & ~mask0) 
				| (Long.rotateRight(src[srcByteStart], rotRight) & mask0);
		dstByteStart++;

		//loop
		int lastFullSrcByte = ((posSrc+nBits)>>>UNIT_3)-1; //-1 because we start with 0
		long maskRotRight = (UNIT_0xFF >>> rotRight); 
		while (srcByteStart < lastFullSrcByte) {
			trg[dstByteStart] = (trg[dstByteStart] & maskRotRight)
					| (Long.rotateRight(src[srcByteStart], rotRight) & ~maskRotRight);
			srcByteStart++;
			trg[dstByteStart] = (trg[dstByteStart] & ~maskRotRight) 
					| (Long.rotateRight(src[srcByteStart], rotRight) & maskRotRight);
			dstByteStart++;
		}
		//We meet two conditions here:
		//- We haven't written a single bit from the current src[] slot
		//- We are on the last trg[] slot that requires its last bit overwritten
		//=> Space on trg[] slot >= remaining-bits-in-src{}-slot
//		if (dstByteStart*64+64 > posTrg+nBits) {
//			throw new RuntimeException();
//		}
		
		//see TestBitLong.copyBitsLeftBug7()
		if (dstByteStart >= trg.length) {
			return;
		}
		
		int dstLocalEnd = posTrg + nBits;
		if (srcByteStart > lastFullSrcByte) {// && rotRight >= (dstLocalEnd&UNIT_0x1F)) {
			//already on the final stretch
			long maskEnd = UNIT_0xFF >>> (dstLocalEnd & UNIT_0x1F);
			trg[dstByteStart] = ((trg[dstByteStart] & maskEnd) 
			| (Long.rotateRight(src[srcByteStart], rotRight) & ~maskEnd));
			return;
		}
		
		//What remains to be done: 
		//a) write remaining bit of currentSrc slot (=lastFullSrcSlot), then increment
		//c) write any bits in half filled follow-up slot (>lastFullSlot)   
		
		//a)
		trg[dstByteStart] = ((trg[dstByteStart] & maskRotRight)
				| (Long.rotateRight(src[srcByteStart], rotRight) & ~maskRotRight));
		srcByteStart++;
		
		//Hmm, this look ugly... TODO
		if (srcByteStart*UNIT_BITS > (posSrc+nBits-1)) {
			//TODO can this still happen?
			return;
		}
		//TODO rename srcByteStart to srcByte(current)
		
		//c)
		//There are two cases, either we are on the last dstSlot, or we are on the one before that
		//special case maskEnd=0 OR maskEnd=0xFFFFF? -> Cannot happen, see enclosing 'if'.
		long maskEnd = UNIT_0xFF >>> (dstLocalEnd & UNIT_0x1F);
		if (dstByteStart < ((dstLocalEnd-1)>>>UNIT_3)) {
			//complete current dstSlot, then fill final slot
			trg[dstByteStart] = (trg[dstByteStart] & ~maskRotRight) 
					| (Long.rotateRight(src[srcByteStart], rotRight) & maskRotRight);
			dstByteStart++;

			//if (dstByteStart <= ((dstLocalEnd-1)>>>UNIT_3)) {
				trg[dstByteStart] = (trg[dstByteStart] & maskEnd) 
						| (Long.rotateRight(src[srcByteStart], rotRight) & ~maskEnd);
			//}
		} else if (maskEnd != UNIT_0xFF) {
			//we are on the final slot already
			long maskEnd2 = ~maskEnd & maskRotRight; //0x000FFF000000
			trg[dstByteStart] = (trg[dstByteStart] & ~maskEnd2) 
					| (Long.rotateRight(src[srcByteStart], rotRight) & maskEnd2);
		} else {
			//this fills exactly the remaining bits in the trg-slot
			trg[dstByteStart] = (trg[dstByteStart] & ~maskRotRight) 
					| (Long.rotateRight(src[srcByteStart], rotRight) & maskRotRight);
		}
    }


    
	/**
	 * @param ba byte array
	 * @param posBit Counts from left to right!!!
	 * @return the bit as boolean
	 */
    public static boolean getBit(long[] ba, int posBit) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x1F;
        return (ba[pA] & (UNIT_0x8000 >>> posBit)) != 0;
	}

	/**
	 * @param ba byte array
	 * @param posBit Counts from left to right!!!
	 * @return the bit as long
	 */
    public static long getBit01(long[] ba, int posBit) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x1F;
        return (ba[pA] & (UNIT_0x8000 >>> posBit)) != 0 ? 1 : 0;
	}

	/**
	 * @param ba byte array
	 * @param posBit Counts from left to right (highest to lowest)!!!
	 * @param b bit to set 
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
	 * @param ba byte array
	 * @param posBitStart start bit
	 * @param posBitMax end bit
	 * @return returns the position delta to the next '1' bit or -1.
	 */
    public static int findNext1Bit(long[] ba, int posBitStart, int posBitMax) {
        int pA = posBitStart >>> UNIT_3;
        //last three bit [0..7]
        int posInSlot = posBitStart & UNIT_0x1F;
        long x = ba[pA] << posInSlot;
        if (x != 0) {
        	return Long.numberOfLeadingZeros(x) + posInSlot; 
        }
        int pAMax = posBitMax >>> UNIT_3;
        do {
        	pA++;
        } while (ba[pA] == 0 && pA < pAMax);
       	int lz = Long.numberOfLeadingZeros(ba[pA]); 
        int newPos = pA*UNIT_BITS + lz; 
        return (newPos <= posBitMax) ? newPos : -1;
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
            long midKey = readArray(ba, mid*entryWidth+startBit, keyWidth);

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
     * @param v1 value 1
     * @param v2 value 2
     * @param rangeMax range (inclusive)
     * @return Number of conflicting bits (number of highest conf. bit + 1)
     */
    public static boolean hasConflictingBits(long v1, long v2, int rangeMax) {
    	int rmMod64 = BitsHD.mod64(rangeMax);
    	long mask = rmMod64 == 63 ? (-1L) : ~(-1L << (rmMod64+1));
    	long x = (v1 ^ v2) & mask;
    	return x != 0;
    }

    /**
     * 
     * @param v1 value 1
     * @param v2 value 2
     * @param rangeMax range (inclusive)
     * @return Number of conflicting bits (number of highest conf. bit + 1)
     */
    public static int getMaxConflictingBits(long v1, long v2, int rangeMax) {
    	int rmMod64 = BitsHD.mod64(rangeMax);
    	long mask = rmMod64 == 63 ? (-1L) : ~(-1L << (rmMod64+1));
    	long x = (v1 ^ v2) & mask;
    	return Long.SIZE - Long.numberOfLeadingZeros(x);
    }

    /**
     * 
     * @param v1 value 1
     * @param v2 value 2
     * @param rangeMin range (exclusive): 0 means 'no minimum'.
     * @param rangeMax range (inclusive)
     * @return Number of conflicting bits (number of highest conf. bit + 1)
     */
    public static int getMaxConflictingBits(long v1, long v2, int rangeMin, int rangeMax) {
    	int rmMod64 = BitsHD.mod64(rangeMax);
    	long mask = rmMod64 == 63 ? (-1L) : ~(-1L << (rmMod64+1));
    	long x = (v1 ^ v2) & mask;
    	int cb = Long.SIZE - Long.numberOfLeadingZeros(x);
    	return cb > rangeMin ? cb : 0; 
    }

    /**
     * 
     * @param v1 value 1
     * @param v2 value 2
     * @param rangeMin range (exclusive): 0 means 'no minimum'.
     * @param rangeMax range (inclusive)
     * @return Number of conflicting bits (number of highest conf. bit + 1)
     */
    public static boolean hasConflictingBits(long v1, long v2, int rangeMin, int rangeMax) {
    	int rmMod64 = BitsHD.mod64(rangeMax);
    	long mask = rmMod64 == 63 ? (-1L) : ~(-1L << (rmMod64+1));
    	long x = (v1 ^ v2) & mask;
    	int cb = Long.SIZE - Long.numberOfLeadingZeros(x);
    	return cb > rangeMin; 
    }

	
    public static boolean checkRange(long[] candidate, long[] rangeMin, long[] rangeMax) {
		for (int i = 0; i < candidate.length; i++) {
			long k = candidate[i];
			if (k < rangeMin[i] || k > rangeMax[i]) {
				return false;
			}
		}
		return true;
	}

	
    public static boolean checkPrefix(long[] v1, long[] v2, int bitsToIgnore) {
    	if (bitsToIgnore == 64) {
    		return true;
    	}
    	long mask = (-1L) << bitsToIgnore;
		for (int i = 0; i < v1.length; i++) {
			if ( ((v1[i] ^ v2[i]) & mask) != 0) {
				return false;
			}
		}
		return true;
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

	public static int arraySizeInByte(long[] ba) {
		return ba.length*BYTES_PER_UNIT;
	}
	
	public static int arraySizeInByte(int arrayLength) {
		return arrayLength*BYTES_PER_UNIT;
	}

	/**
	 * Same as System.arraycopy(), but uses a faster copy-by-loop approach for small arrays.
	 * @param src source array
	 * @param srcPos source position
	 * @param dst destination array
	 * @param dstPos destination position
	 * @param len length
	 */
	public static void arraycopy(long[] src, int srcPos, long[] dst, int dstPos, int len) {
		if (len < 10) {
			for (int i = 0; i < len; i++) {
				dst[dstPos+i] = src[srcPos+i];
			}
		} else {
			System.arraycopy(src, srcPos, dst, dstPos, len);
		}
	}

	public static String toBinary(long l) {
    	return toBinary(l, 64);
    }

	public static String toBinary(long l, int DEPTH) {
        StringBuilder sb = new StringBuilder();
        //long mask = DEPTH < 64 ? (1<<(DEPTH-1)) : 0x8000000000000000L;
        for (int i = 0; i < DEPTH; i++) {
            long mask = (1L << (long)(DEPTH-i-1));
            if ((l & mask) != 0) { sb.append("1"); } else { sb.append("0"); }
            if ((i+1)%8==0 && (i+1)<DEPTH) sb.append('.');
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
}
