/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.util;

import static ch.ethz.globis.pht.PhTreeHelper.*;

/**
 * Bit-stream manipulation functions.
 * 
 * @author Tilmann Zaeschke
 *
 */
public class BitsInt {

    static final int UNIT_3 = 5;  			//EXP: 2^EXP = BITS
    static final int UNIT_BITS = (1<<UNIT_3);
    private static final int UNIT_0x1F = 0x1F;  		//0x07 for byte=8 bits=3exp
    private static final long UNIT_0xFF = 0xFFFFFFFFL;  		//0xFF for byte=8 bits=3exp
    private static final long UNIT_0xFF00 = 0xFFFFFFFF00000000L;  	//0xFF00 for byte=8 bits=3exp
    private static final int BYTES_PER_UNIT = 4;

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
    public static long readArray(int[] ba, int offsetBit, int entryLen) {
        int pA = offsetBit >>> UNIT_3;
    	if (DEBUG && (pA >= ba.length || entryLen >= 64)) {
    		//last statement doesn't work with 64 bit!
    		throw new IllegalStateException("len=" + ba.length + "  ofs=" + offsetBit + 
    				" entryLen=" + entryLen);
    	}
    	
    	long ret = ba[pA] & UNIT_0xFF; //   & UNIT_0xFF avoids signed longs
    	//end, local to the possible three fields
    	int semiLocalEnd = (offsetBit & UNIT_0x1F) + entryLen;
    	if (semiLocalEnd <= UNIT_BITS) {
    		//case one, extract only from first field
    		ret >>>= UNIT_BITS - semiLocalEnd;
    	} else {
    		//extends at least to second field
    		semiLocalEnd -= UNIT_BITS;
    		ret <<= UNIT_BITS;
    		ret |= ba[pA+1] & UNIT_0xFF;  //   & UNIT_0xFF avoids signed longs
    		if (semiLocalEnd <= UNIT_BITS) {
    			//extends only to second field = 2*UNIT_BITS
    			//ret >>>= (UNIT_BITS<<1) - (ofsBitLocal1 + entryLen);
    			ret >>>= UNIT_BITS - semiLocalEnd;
    		} else {
    			//using all three fields
        		semiLocalEnd -= UNIT_BITS;
    			int buf = ba[pA+2] >>> (UNIT_BITS-semiLocalEnd);
    			ret <<= semiLocalEnd;
    			ret |= buf & UNIT_0xFF;
    		}
    	}
    	ret &= ~((-1L)<<entryLen); //TODO this works only because entryLen always smaller than 64
    	return ret;
    }
    
    //TODO adjust this to a similar style as readArray above.
    public static void writeArray(int[] ba, int offsetBit, int entryLen, final long val) {
        int pA = offsetBit >>> UNIT_3;
        int startBit = UNIT_BITS - (offsetBit & UNIT_0x1F); //counting from right to left (low to high)
        int bitsWritten = 0;
        while (bitsWritten < entryLen) {
            //int mask = (1 << startBit)-1;
            //erase byte[] first - create mask
            long eraseMask = (1L << startBit) - 1L;
            int bitsToWrite = startBit;
            if (bitsWritten+bitsToWrite > entryLen) {
                int bitsToIgnore = bitsWritten + startBit - entryLen;
                long mask2 = (1L<<bitsToIgnore) - 1L;
                eraseMask = eraseMask & ~mask2;
                bitsToWrite -= bitsToIgnore;
            }
            //erase bits
            ba[pA] &= ~eraseMask;

            int toShift = entryLen - (bitsWritten + startBit);
            long infTemp = toShift > 0 ? val >>> toShift : val << (-toShift);
            //this cuts off any leading bits
            long maskToCutOfHeadingBits = (1L << startBit) - 1L;
            infTemp &= maskToCutOfHeadingBits;
            ba[pA] |= infTemp;
            bitsWritten += bitsToWrite; //may have been less, but in that case we quit anyway
            startBit = UNIT_BITS;
            pA++;
        }
    }

	/**
	 * 
	 * @param ba
	 * @param dist amount to shift, positive to right, negative to left.
	 */
    public static void insertBits1(int[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException();
		}
		statOldRightShift++;
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
		statOldRightShiftTime += (t2-t1);
	}

    /**
     * Insert bits at the given position.
     * The resulting array is NOT resized, bit that do do not fit in the current array are lost.  
     * @param ba
     * @param start
     * @param nBits
     */
    public static void insertBits(int[] ba, final int start, final int nBits) {
		if (nBits == 0 || (start+nBits)>=ba.length*UNIT_BITS) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException("nBits=" + nBits);
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

		int mask0 = (int) (UNIT_0xFF >>> dstLocalStart);
		if (dstLocalStart < srcLocalStart) {
			int buf1 = ba[srcByteStart+1];
			//write first part
			ba[dstByteStart] = (ba[dstByteStart] & ~mask0) |
					(Integer.rotateRight(ba[srcByteStart], localShift) & mask0);
			//write second part
			int mask1 = (int) mask0 >>> -srcLocalStart; //(UNIT_BITS-srcLocalStart);
			ba[dstByteStart] = (ba[dstByteStart] & ~mask1) | ((buf1 >>> localShift) & mask1);
		} else {
			//write first part
			ba[dstByteStart] = (ba[dstByteStart] & ~mask0) |
					(Integer.rotateRight(ba[srcByteStart], localShift) & mask0);
		}
    }
		
    
	/**
     * Insert bits at the given position. 
     * 
     * The resulting array is NOT resized, bit that do do not fit in the current array are lost.  
     * @param ba
     * @param start
     * @param nBits
     */
    public static void insertBits0(int[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException("nBits=" + nBits);
		}
		statOldRightShift++;
		long t1 = System.currentTimeMillis();
		//shift right
		//TODO Improve this. We now shift right by too much, then shift back left.
		//bytes to insert (not to move!)
		int nBytes = (int) Math.ceil(nBits/(double)UNIT_BITS);
		int startByte = start>>UNIT_3;
		int bytesToCopy = ba.length - (startByte+nBytes);
		int tmp = ba[ba.length-nBytes];
		if (bytesToCopy != 0) {
			//move right by some bytes/ints
			System.arraycopy(ba, startByte, ba, startByte + nBytes, bytesToCopy);
			//move back left
			int nBitsToMoveLeft = nBytes*UNIT_BITS-nBits;
//			System.out.println("IB: s=" + start + " nBits=" + nBits + " nBLeft = " + nBitsToMoveLeft + "  baLen=" + ba.length);
			removeBits(ba, start+nBits, nBitsToMoveLeft);
			//apply tmp
			int offs = UNIT_BITS-nBitsToMoveLeft;
			copyBitsLeft(new int[]{tmp}, 0, ba, (ba.length-1)*UNIT_BITS+offs, nBitsToMoveLeft);
		} else {
			//Must be last byte in array
			//apply tmp
			int offsTmp = start & UNIT_0x1F;
			//check if we should copy at all. If everything is shifted out of the array, then we 
			//don't need to copy anything.
			if (start+nBits < ba.length*UNIT_BITS) {
				int offsTrg = (start+nBits)&UNIT_0x1F;
				int len = UNIT_BITS - offsTrg;
//				System.out.println("IB: s=" + start + " nBits=" + nBits + "  baLen=" + ba.length);
//				System.out.println("IB: oTmp=" + offsTmp + " oTrg=" + offsTrg + "  len=" + len + "  " + ((ba.length-1)*UNIT_BITS + offsTrg));
				copyBitsLeft(new int[]{tmp}, offsTmp, ba, (ba.length-1)*UNIT_BITS + offsTrg, len);
			}
		}

		long t2 = System.currentTimeMillis();
		statOldRightShiftTime += (t2-t1);
    }
    
    public static void removeBits0(int[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (DEBUG && nBits < 0) {
			throw new IllegalArgumentException("nBits = " + nBits);
		}
		//shift left
		copyBitsLeft(ba, start + nBits, ba, start, ba.length*UNIT_BITS-start-nBits);
//		int bitsToShift = ba.length*8 - start - (nBits);
//		for (int i = 0; i < bitsToShift; i++) {
//			int srcBit = start + (nBits) + i;
//			int trgBit = start + i;
//			setBit(ba, trgBit, getBit(ba, srcBit));
//		}
	}
    
    public static void removeBits(int[] ba, final int start, final int nBits) {
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

		int mask0 = (int) (UNIT_0xFF >>> dstLocalStart);
		if (dstLocalStart <= srcLocalStart) {
			//write first part
			ba[dstByteStart] = ((ba[dstByteStart] & ~mask0) 
					| (Integer.rotateLeft(ba[srcByteStart], localShift) & mask0))
					& (int) (UNIT_0xFF << localShift);
		} else {
			ba[dstByteStart] = (ba[dstByteStart] & ~mask0) 
					| (Integer.rotateLeft(ba[srcByteStart], localShift) & mask0);
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
				ba[dstByteStart] = (ba[i] << localShift);
			}
		}
    }


    public static void copyBitsLeft1(int[] src, int posSrc, int[] dst, int posDst, int len) {
    	if (len==0) {
    		return;
    	}
    	if (DEBUG && (posSrc < 0 || posDst < 0)) {
    		throw new IllegalArgumentException("s=" + posSrc + " t=" + posDst);
    	}
    	if (DEBUG && 
    			(posSrc + len > src.length*UNIT_BITS || posDst + len > dst.length*UNIT_BITS)) {
    		throw new IllegalArgumentException("s=" + posSrc + " t=" + posDst + " len=" + len + 
    				"  srcLen/trgLen = " + src.length + " / " + dst.length + " // " + UNIT_BITS);
    	}
       	if (posSrc < posDst) {
       		//TODO replace with copyBitsRight
    		copyBitsLeft1(src, posSrc, dst, posDst, len);
    		return;
    	}
		int srcByteStart = posSrc >>> UNIT_3;  //integer division!
		int srcByteEnd = (posSrc + len - 1) >>> UNIT_3;
		int srcLocalStart = posSrc & UNIT_0x1F;
		int dstByteStart = posDst >>> UNIT_3;  //integer division!
		int dstLocalStart = posDst & UNIT_0x1F;
		int localShift = (posSrc-posDst) & UNIT_0x1F; //Always positive!

		int mask0 = (int) (UNIT_0xFF >>> dstLocalStart);
		//buffer, in case src and dst are identical.
		int buf0 = src[srcByteStart];
		if (dstLocalStart <= srcLocalStart) {
			if (dstLocalStart+len < UNIT_BITS) {
				//TODO not yet supported
				throw new UnsupportedOperationException();
			}
			//write first part
			dst[dstByteStart] &= ~mask0;
			dst[dstByteStart] |= Integer.rotateLeft(buf0, localShift) & mask0;
			dst[dstByteStart] &= UNIT_0xFF << localShift;
		} else {
			dst[dstByteStart] &= ~mask0;
			dst[dstByteStart] |= Integer.rotateLeft(buf0, localShift) & mask0;
			dstByteStart++;
			dst[dstByteStart] = buf0 << localShift;
			//nullify remaining bits for following writes (unnecessary if loop is skipped)
			dst[dstByteStart] &= UNIT_0xFF << (UNIT_BITS-dstLocalStart);
		}

		if (srcByteEnd > srcByteStart) {
			//main loop
			//0x0000001FFF or similar
			int rMask = (int) (UNIT_0xFF << localShift);
			for (int i = srcByteStart+1; i < srcByteEnd; i++) {
				//this assumes that the area has been nullified
				dst[dstByteStart] |= Integer.rotateLeft(src[i], localShift) & ~rMask;
				dstByteStart++;
				dst[dstByteStart] = (src[i] << localShift) & rMask;
			}
		
			//The following part also covers the case if the last src-byte is fully copied.
			//TODO if the second part would also allow srcLocalPos==dstLocalPos then we could omit
			//     this calculation and use 'if(srcLocalStart<=srcLocalEnd)'.
			int srcLocalEnd = (srcLocalStart+len) & UNIT_0x1F;
			int dstLocalEnd = (dstLocalStart+len) & UNIT_0x1F;
			//int maskPost = (int) (UNIT_0xFF >>> (dstLocalStart + (len&UNIT_0x1F)) );
			int buf1 = src[srcByteEnd];
			if (srcLocalEnd <= dstLocalEnd) {
				//TODO? if (localEnd==0) localEnd==32;
				int leftMask = (int) (dstLocalEnd==0 ? UNIT_0xFF : ~(UNIT_0xFF >>> dstLocalEnd)); // e.g. 1111.1110
				int rightMask = (int) (UNIT_0xFF >>> (dstLocalEnd-srcLocalEnd));
				int maskPost = (int) leftMask & rightMask;
				//TODO
				//TODO local mask should go from dstLocalStart to dstLocalEnd only.
				//TODO
				if (localShift==0) {
					//-->local dst pos must be 0
					dstByteStart++;
				}
				//write first part
				dst[dstByteStart] &= ~maskPost;
				dst[dstByteStart] |= Integer.rotateLeft(buf1, localShift) & maskPost;
				dst[dstByteStart] &= UNIT_0xFF << localShift;
			} else {
				int leftMask = (int) ~(UNIT_0xFF >>> dstLocalEnd); // e.g. 1111.1110
				int rightMask = (int) (UNIT_0xFF >>> (dstLocalEnd-srcLocalEnd+UNIT_BITS));
				int shifted = Integer.rotateLeft(buf1, localShift);
				//TODO
				//TODO local masks should go from dstLocalStart to 32 and from 0 to dstLocalEnd.
				//TODO
				dst[dstByteStart] &= ~rightMask;
				dst[dstByteStart] |= shifted & rightMask;
				dstByteStart++;
				dst[dstByteStart] &= ~leftMask;
				dst[dstByteStart] |= shifted & leftMask;
				//nullify remaining bits for following writes (unnecessary if loop is skipped)
			}
		}
    }


    public static void copyBitsLeft(int[] src, int posSrc, int[] trg, int posTrg, int len) {
    	if (len==0) {
    		return;
    	}
    	if (DEBUG && (posSrc < 0 || posTrg < 0)) {
    		throw new IllegalArgumentException("s=" + posSrc + " t=" + posTrg);
    	}
    	if (DEBUG && 
    			(posSrc + len > src.length*UNIT_BITS || posTrg + len > trg.length*UNIT_BITS)) {
    		throw new IllegalArgumentException("s=" + posSrc + " t=" + posTrg + " len=" + len + 
    				"  srcLen/trgLen = " + src.length + " / " + trg.length + " // " + UNIT_BITS);
    	}
    	long buf = 0;
        int psA = posSrc >>> UNIT_3;
        int startBitS = UNIT_BITS - (posSrc & UNIT_0x1F); //counting from right to left (low to high) [8..1]
        int ptA = posTrg >>> UNIT_3;
        int startBitT_lr = (posTrg & UNIT_0x1F); //counting from left to right, [0..7];
        int startBitT = UNIT_BITS - (posTrg & UNIT_0x1F); //counting from right to left (low to high) [8..1]
    	
        int bitsInBuffer = 0;
        final int bitsToCopy = len;
        int bitsRead = 0;
        int bitsWritten = 0;
        
    	//read bits of half-used byte into buffer
    	if (startBitS != UNIT_BITS) {
            long mask = (1L << startBitS)-1L;
            buf |= src[psA] & mask;
            bitsInBuffer = startBitS;
            bitsRead = startBitS;
            psA++;
            if (bitsRead > bitsToCopy) {
            	buf >>>= (bitsRead - bitsToCopy);
            	bitsRead = bitsToCopy;
            	bitsInBuffer = bitsToCopy;
            }
    	}
    	
    	//erase-mask for setting target-bits to 0 (necessary for overwriting via OR/|=)
        long eraseMask = (1L << startBitT) - 1L;
        eraseMask = ~eraseMask;  // 00 for all bit that need overwriting.
        int bitsToWriteThisRound = startBitT < len ? startBitT : len;
    	
    	//main loop - traverses everything but the last byte
        boolean readingFinished = false;
        boolean writingFinished = false;
        while (!(readingFinished && writingFinished) ) {
    	//while (bitsWritten <= bitsToCopy && !readingFinished) {
    		//read into buffer
        	if (bitsRead < bitsToCopy) {
	    		buf <<= UNIT_BITS;
	    		buf |= src[psA] & UNIT_0xFF; //Otherwise leading bits may be 111111111
	    		bitsRead += UNIT_BITS;
	    		bitsInBuffer += UNIT_BITS;
	            if (bitsRead >= bitsToCopy) {
	            	readingFinished = true;
	            	if (bitsRead > bitsToCopy) {
		            	int d = bitsRead - bitsToCopy;
		            	buf >>>= d;
		            	bitsRead -= d;
		            	bitsInBuffer -= d;
	            	}
	            }
        	} else {
        		readingFinished = true;
        	}
    		
    		
    		//write
           	//Only write, if last written bit is last bit of current byte. ->End-aligned.
        	if (startBitT_lr + bitsInBuffer < UNIT_BITS) {
        		break;
        	}
        	
        	long buf2 = buf;

            buf2 >>>= bitsInBuffer-bitsToWriteThisRound; 
        	if (bitsToWriteThisRound > bitsInBuffer) {
        		buf2 = buf << (bitsToWriteThisRound-bitsInBuffer);
        		eraseMask = ~((~0)<<(bitsToWriteThisRound-bitsInBuffer));
        	}
        	
        	trg[ptA] &= eraseMask;  
        	
        	buf2 &= UNIT_0xFF; // cut of heading bits

         	startBitT = UNIT_BITS;
            trg[ptA] |= buf2;
            
            //from now on, always delete everything
            eraseMask = 0;
            bitsWritten += bitsToWriteThisRound;
            bitsInBuffer -= bitsToWriteThisRound;
            //from now on, always write 8
            bitsToWriteThisRound = UNIT_BITS;
            
            //Verify that buffer is less than half filled, otherwise adding 32 bit would cause
            //an overflow.
            if (bitsInBuffer > UNIT_BITS) {
            	ptA++;
            	buf2 = buf;
                buf2 >>>= bitsInBuffer-bitsToWriteThisRound; 
            	
            	trg[ptA] &= eraseMask;  
            	
            	buf2 &= UNIT_0xFF; // cut of heading bits

                trg[ptA] |= buf2;
                
                bitsWritten += bitsToWriteThisRound;
                bitsInBuffer -= bitsToWriteThisRound;
            }
            
    		psA++;
    		ptA++;
    		
    		if (bitsToCopy-bitsWritten <= UNIT_BITS) {
    			writingFinished = true;
    		}
    	}

    	//write remaining bits - this should be less than 8.
    	if (bitsWritten < bitsToCopy) {
            //erase byte[] first - create mask
            int bitsToWrite = bitsToCopy-bitsWritten;
            if (bitsWritten == 0) {
            	//start and end in same target byte
            	eraseMask = (UNIT_0xFF >>> bitsToWrite) | UNIT_0xFF00;
            	//we are in the first byte, so writing may not start at the left side
            	eraseMask >>>= (UNIT_BITS-startBitT);
        		buf <<= (startBitT-bitsToWrite);
           } else {
                eraseMask = (UNIT_0xFF >>> bitsToWrite);
        		buf <<= (UNIT_BITS-bitsToWrite);
            }
            //erase bits
            trg[ptA] &= eraseMask;

    		trg[ptA] |= buf;
    	}
    }

     
	//TODO this could be much faster by using a LONG (INT?) which is filled with source bytes
	//and then accordingly shifted and assigned to target bytes.
	/**
	 * @Param posBit Counts from left to right!!!
	 */
    public static boolean getBit(int[] ba, int posBit) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x1F;
        return (ba[pA] & (1L << (UNIT_BITS-1-posBit))) != 0;
	}

	/**
	 * @Param posBit Counts from left to right (highest to lowest)!!!
	 */
    public static void setBit(int[] ba, int posBit, boolean b) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x1F;
        if (b) {
            ba[pA] |= (1L << (UNIT_BITS-1-posBit));
        } else {
            ba[pA] &= (~(1L << (UNIT_BITS-1-posBit)));
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
    public static int binarySearch(int[] ba, int startBit, int nEntries, long key, int keyWidth, 
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
     * Calculate array size for given number of bits.
     * This takes into account JVM memory management, which allocates multiples of 8 bytes.
     * @param nBits
     * @return array size.
     */
    public static int calcArraySize(int nBits) {
    	//+63  --> round up to 8 byte = 64bit alignment
    	//>>>3 --> turn bits into bytes
    	//>>>3 --> turn into 8byte units
    	int arraySize = (nBits+63)>>>6;
        //<<<1 --> turn into UNIT/64bit units
        arraySize <<= (6-UNIT_3);
        return arraySize;
    }

    /**
     * Resize an array.
     * @param oldA
     * @param newSizeBits
     * @return New array larger array.
     */
    public static int[] arrayExpand(int[] oldA, int newSizeBits) {
    	int[] newA = new int[calcArraySize(newSizeBits)];
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	if (DEBUG) statAExpand++;
    	return newA;
    }
    
    public static int[] arrayCreate(int nBits) {
    	int[] newA = new int[calcArraySize(nBits)];
    	if (DEBUG) statACreate++;
    	return newA;
    }
    
    /**
     * Ensure capacity of an array. Expands the array if required.
     * @param oldA
     * @param requiredBits
     * @return Same array or expanded array.
     */
    public static int[] arrayEnsureSize(int[] oldA, int requiredBits) {
    	if (DEBUG) {
    		if (calcArraySize(requiredBits) < oldA.length) {
//    			throw new IllegalStateException(
//    					"req=" + calcArraySize(requiredBits) + " size=" + oldA.length);
    		}
    	}
    	if (isCapacitySufficient(oldA, requiredBits)) {
    		return oldA;
    	}
    	return arrayExpand(oldA, requiredBits);
    }
    
    public static boolean isCapacitySufficient(int[] a, int requiredBits) {
    	return (a.length*UNIT_BITS >= requiredBits);
    }
    
    public static int[] arrayTrim(int[] oldA, int requiredBits) {
    	int reqSize = calcArraySize(requiredBits);
    	if (oldA.length == reqSize) {
    		return oldA;
    	}
    	int[] newA = new int[reqSize];
    	System.arraycopy(oldA, 0, newA, 0, reqSize);
    	if (DEBUG) statATrim++;
    	return newA;
    }

	public static int arraySizeInByte(int[] ba) {
		return ba.length*BYTES_PER_UNIT;
	}
	
	public static int arraySizeInByte(int arrayLength) {
		return arrayLength*BYTES_PER_UNIT;
	}

	public static String toBinary(long l) {
    	return toBinary(l, 32);
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

	public static String toBinary(long[] la) {
		return toBinary(la, 64);
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

    public static String toBinary(int[] ba) {
        StringBuilder sb = new StringBuilder();
        for (int l: ba) {
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
