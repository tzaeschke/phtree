/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;


/**
 * Bit-stream manipulation functions.
 * 
 * @author ztilmann
 *
 */
public class BitsShort {

    private static final int UNIT_3 = 4;  			//EXP: 2^EXP = BITS
    private static final int UNIT_BITS = (1<<UNIT_3);
    private static final int UNIT_0x07 = 0x0F;  		//0x07 for byte=8 bits=3exp
    private static final long UNIT_0xFF = 0xFFFF;  		//0xFF for byte=8 bits=3exp
    private static final long UNIT_0xFF00 = 0xFFFF0000;  	//0xFF00 for byte=8 bits=3exp
    private static final int BYTES_PER_UNIT = 2;

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
    public static long readArray(short[] ba, int offsetBit, int entryLen) {
        int pA = offsetBit >>> UNIT_3;
        int startBit = UNIT_BITS -(offsetBit & UNIT_0x07);  //last three bit [0..7]
        long ret = 0;
        int bitsRead = 0;
        while (bitsRead < entryLen) {
            long mask = (1L << startBit)-1L;
            if (bitsRead+startBit > entryLen) {
                int bitsToIgnore = bitsRead + startBit - entryLen;
                long mask2 = (1L<<bitsToIgnore) - 1L;
                mask = mask & ~mask2;
                //TODO
                bitsRead += 100000;//posBit-infixLen;
                ret <<= (UNIT_BITS-bitsToIgnore);
                ret |= (ba[pA] & mask) >>> bitsToIgnore;
            } else {
                bitsRead += startBit;
                ret <<= UNIT_BITS;
                ret |= ba[pA] & mask;
            }
            startBit = UNIT_BITS;
            pA++;
        }
        return ret;
    }

    public static void writeArray(short[] ba, int offsetBit, int entryLen, long val) {
        int pA = offsetBit >>> UNIT_3;
        int startBit = UNIT_BITS - (offsetBit & UNIT_0x07); //counting from right to left (low to high)
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
            //this cuts of any leading bits
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
    public static void insertBits1(short[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (nBits < 0) {
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
     * 
     * The resulting array is NOT resized, bit that do do not fit in the current array are lost.  
     * @param ba
     * @param start
     * @param nBits
     */
    public static void insertBits(short[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (nBits < 0) {
			throw new IllegalArgumentException();
		}
		statOldRightShift++;
		long t1 = System.currentTimeMillis();
		//shift right
		//TODO Improve this. We now shift right by too much, then shift back left.
		//bytes to insert (not to move!)
		int nBytes = (int) Math.ceil(nBits/(double)UNIT_BITS);
		int startByte = start>>UNIT_3;
		int bytesToCopy = ba.length - (startByte+nBytes);
		short tmp = ba[ba.length-nBytes];
		if (bytesToCopy != 0) {
			//move right by some bytes/shorts
			System.arraycopy(ba, startByte, ba, startByte + nBytes, bytesToCopy);
			//move back left
			int nBitsToMoveLeft = nBytes*UNIT_BITS-nBits;
//			System.out.println("IB: s=" + start + " nBits=" + nBits + " nBLeft = " + nBitsToMoveLeft + "  baLen=" + ba.length);
			removeBits(ba, start+nBits, nBitsToMoveLeft);
			//apply tmp
			int offs = UNIT_BITS-nBitsToMoveLeft;
			copyBitsLeft(new short[]{tmp}, 0, ba, (ba.length-1)*UNIT_BITS+offs, nBitsToMoveLeft);
		} else {
			//Must be last byte in array
			//apply tmp
			int offsTmp = start&UNIT_0x07;
			//check if we should copy at all. If everything is shifted out of the array, then we 
			//don't need to copy anything.
			if (start+nBits < ba.length*UNIT_BITS) {
				int offsTrg = (start+nBits)&UNIT_0x07;
				int len = UNIT_BITS - offsTrg;
//				System.out.println("IB: s=" + start + " nBits=" + nBits + "  baLen=" + ba.length);
//				System.out.println("IB: oTmp=" + offsTmp + " oTrg=" + offsTrg + "  len=" + len + "  " + ((ba.length-1)*UNIT_BITS + offsTrg));
				copyBitsLeft(new short[]{tmp}, offsTmp, ba, (ba.length-1)*UNIT_BITS + offsTrg, len);
			}
		}

		long t2 = System.currentTimeMillis();
		statOldRightShiftTime += (t2-t1);
    }
    
    public static void removeBits(short[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (nBits < 0) {
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

    public static void copyBitsLeft(short[] src, int posSrc, short[] trg, int posTrg) {
    	int len = src.length*UNIT_BITS-posSrc;
    	copyBitsLeft(src, posSrc, trg, posTrg, len);
    }
    	
    public static void copyBitsLeft(short[] src, int posSrc, short[] trg, int posTrg, int len) {
    	if (len==0) {
    		return;
    	}
    	final boolean DBG = false; //TODO
    	if (posSrc < 0 || posTrg < 0) {
    		throw new IllegalArgumentException("s=" + posSrc + " t=" + posTrg);
    	}
    	if (posSrc + len > src.length*UNIT_BITS || posTrg + len > trg.length*UNIT_BITS) {
    		throw new IllegalArgumentException("s=" + posSrc + " t=" + posTrg + " len=" + len + 
    				"  srcLen/trgLen = " + src.length + " / " + trg.length + " // " + UNIT_BITS);
    	}
    	long buf = 0;
        int psA = posSrc >>> UNIT_3;
        int startBitS = UNIT_BITS - (posSrc & UNIT_0x07); //counting from right to left (low to high) [8..1]
        int ptA = posTrg >>> UNIT_3;
        int startBitT_lr = (posTrg & UNIT_0x07); //counting from left to right, [0..7];
        int startBitT = UNIT_BITS - (posTrg & UNIT_0x07); //counting from right to left (low to high) [8..1]
    	
        int bitsInBuffer = 0;
        final int bitsToCopy = len;
        int bitsRead = 0;
        int bitsWritten = 0;
        
    	//read bits of half-used byte into buffer
    	if (startBitS != UNIT_BITS) {
            long mask = (1L << startBitS)-1L;
            buf |= src[psA] & mask;
            if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
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
        if (DBG) System.out.println("em=" + toBinary(eraseMask));  //TODO
        int bitsToWriteThisRound = startBitT < len ? startBitT : len;
    	
    	//main loop - traverses everything but the last byte
        boolean readingFinished = false;
        boolean writingFinished = false;
        while (!(readingFinished && writingFinished) ) {
    	//while (bitsWritten <= bitsToCopy && !readingFinished) {
    		//read into buffer
        	if (bitsRead < bitsToCopy) {
	    		buf <<= UNIT_BITS;
	            if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
	            if (DBG) System.out.println("src=" + toBinary(src[psA]));  //TODO
	    		buf |= src[psA] & UNIT_0xFF; //Otherwise leading bits may be 111111111
	    		if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
	    		bitsRead += UNIT_BITS;
	    		bitsInBuffer += UNIT_BITS;
	            if (bitsRead >= bitsToCopy) {
	            	readingFinished = true;
	            	if (bitsRead > bitsToCopy) {
		            	int d = bitsRead - bitsToCopy;
		            	buf >>>= d;
	        			if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
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
            if (DBG) System.out.println("buf2=" + toBinary(buf));  //TODO

            buf2 >>>= bitsInBuffer-bitsToWriteThisRound; 
        	if (bitsToWriteThisRound > bitsInBuffer) {
        		buf2 = buf << (bitsToWriteThisRound-bitsInBuffer);
        		eraseMask = ~((~0)<<(bitsToWriteThisRound-bitsInBuffer));
        	}
            if (DBG) System.out.println("buf2=" + toBinary(buf2));  //TODO
        	
        	trg[ptA] &= eraseMask;  //TODO can we just assign buf2 here????
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO
        	
        	buf2 &= UNIT_0xFF; //TODO cut of heading bits??? Why?
            if (DBG) System.out.println("buf2=" + toBinary(buf2));  //TODO

         	startBitT = UNIT_BITS;
            trg[ptA] |= buf2;
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO
            
            //from now on, always delete everything
            eraseMask = 0;
            bitsWritten += bitsToWriteThisRound;
            bitsInBuffer -= bitsToWriteThisRound;
            //from now on, always write 8
            bitsToWriteThisRound = UNIT_BITS;
            
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
            if (DBG) System.out.println("em=" + toBinary(eraseMask));  //TODO
            if (bitsWritten == 0) {
            	//start and end in same target byte
            	eraseMask = (UNIT_0xFF >>> bitsToWrite) | UNIT_0xFF00;
            	//we are in the first byte, so writing may not start at the left side
            	eraseMask >>>= (UNIT_BITS-startBitT);
            if (DBG) System.out.println("em=" + toBinary(eraseMask));  //TODO
        		buf <<= (startBitT-bitsToWrite);
                if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
            } else {
                eraseMask = (UNIT_0xFF >>> bitsToWrite);
        		buf <<= (UNIT_BITS-bitsToWrite);
                if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
            }
            //erase bits
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO
            trg[ptA] &= eraseMask;
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO

    		trg[ptA] |= buf;
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO
    	}
    }
    
    public static void copyBitsRight(short[] src, int posSrc, short[] trg, int posTrg, int len) {
    	if (len==0) {
    		return;
    	}
    	final boolean DBG = false; //TODO
    	if (posSrc < 0 || posTrg < 0) {
    		throw new IllegalArgumentException("s=" + posSrc + " t=" + posTrg);
    	}
    	if (posSrc + len > src.length*UNIT_BITS || posTrg + len > trg.length*UNIT_BITS) {
    		throw new IllegalArgumentException("s=" + posSrc + " t=" + posTrg + " len=" + len);
    	}
    	long buf = 0;
//        int psA = posSrc >>> UNIT_3;
        int startBitS = UNIT_BITS - (posSrc & UNIT_0x07); //counting from right to left (low to high) [8..1]
        int startBitT_lr = (posTrg & UNIT_0x07); //counting from left to right, [0..7];
        int startBitT = UNIT_BITS - (posTrg & UNIT_0x07); //counting from right to left (low to high) [8..1]
    	
        int bitsInBuffer = 0;
        final int bitsToCopy = len;
        int bitsRead = 0;
        int bitsWritten = 0;
        
        int psA = (posSrc+len-1) >>> UNIT_3;  //last bit to be read
        int ptA = (posTrg+len-1) >>> UNIT_3;  //last bit to be written
        int endBitS = ((posSrc+len-1) & UNIT_0x07);   //last bit to be read
        int endBitT = ((posTrg+len-1) & UNIT_0x07);   //last bit to be written
        if (endBitS != UNIT_BITS-1) {
        	if (endBitS+1 - bitsToCopy >= 0) {
        		//just a few bits to move within a single byte
        		buf = src[psA] & UNIT_0xFF;
            	//start and end in same target byte
        		long eraseMask = (UNIT_0xFF >>> len) | UNIT_0xFF00;
            	//we are in the first byte, so writing may not start at the left side
            	eraseMask >>>= (endBitT+1-len);
        		buf >>>= endBitT-endBitS;
        		buf &= ~eraseMask;
        		trg[ptA] &= eraseMask;
        		trg[ptA] |= buf;
        		return; //?
        	} else {
        		
        	}
        }
        if (true) throw new RuntimeException();
        
        
        
    	//read bits of half-used byte into buffer
    	if (startBitS != UNIT_BITS) {
            long mask = (1L << startBitS)-1L;
            buf |= src[psA] & mask;
            if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
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
        if (DBG) System.out.println("em=" + toBinary(eraseMask));  //TODO
        int bitsToWriteThisRound = startBitT < len ? startBitT : len;
    	
    	//main loop - traverses everything but the last byte
        boolean readingFinished = false;
        boolean writingFinished = false;
        while (!(readingFinished && writingFinished) ) {
    	//while (bitsWritten <= bitsToCopy && !readingFinished) {
    		//read into buffer
        	if (bitsRead < bitsToCopy) {
	    		buf <<= UNIT_BITS;
	            if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
	            if (DBG) System.out.println("src=" + toBinary(src[psA]));  //TODO
	    		buf |= src[psA] & UNIT_0xFF; //Otherwise leading bits may be 111111111
	    		if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
	    		bitsRead += UNIT_BITS;
	    		bitsInBuffer += UNIT_BITS;
	            if (bitsRead >= bitsToCopy) {
	            	readingFinished = true;
	            	if (bitsRead > bitsToCopy) {
		            	int d = bitsRead - bitsToCopy;
		            	buf >>>= d;
	        			if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
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
            if (DBG) System.out.println("buf2=" + toBinary(buf));  //TODO

            buf2 >>>= bitsInBuffer-bitsToWriteThisRound; 
            if (DBG) System.out.println("buf2=" + toBinary(buf2));  //TODO
        	
        	trg[ptA] &= eraseMask;  //TODO can we just assign buf2 here????
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO
        	
        	buf2 &= UNIT_0xFF; //TODO cut of heading bits??? Why?
            if (DBG) System.out.println("buf2=" + toBinary(buf2));  //TODO

         	startBitT = UNIT_BITS;
            trg[ptA] |= buf2;
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO
            
            //from now on, always delete everything
            eraseMask = 0;
            bitsWritten += bitsToWriteThisRound;
            bitsInBuffer -= bitsToWriteThisRound;
            //from now on, always write 8
            bitsToWriteThisRound = UNIT_BITS;
            
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
            if (DBG) System.out.println("em=" + toBinary(eraseMask));  //TODO
            if (bitsWritten == 0) {
            	//start and end in same target byte
            	eraseMask = (UNIT_0xFF >>> bitsToWrite) | UNIT_0xFF00;
            	//we are in the first byte, so writing may not start at the left side
            	eraseMask >>>= (UNIT_BITS-startBitT);
            if (DBG) System.out.println("em=" + toBinary(eraseMask));  //TODO
        		buf <<= (startBitT-bitsToWrite);
                if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
            } else {
                eraseMask = (UNIT_0xFF >>> bitsToWrite);
        		buf <<= (UNIT_BITS-bitsToWrite);
                if (DBG) System.out.println("buf=" + toBinary(buf));  //TODO
            }
            //erase bits
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO
            trg[ptA] &= eraseMask;
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO

    		trg[ptA] |= buf;
            if (DBG) System.out.println("trg=" + toBinary(trg));  //TODO
    	}
    }
    
	//TODO this could be much faster by using a LONG (INT?) which is filled with source bytes
	//and then accordingly shifted and assigned to target bytes.
	/**
	 * @Param posBit Counts from left to right!!!
	 */
    public static boolean getBit(short[] ba, int posBit) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x07;
        return (ba[pA] & (1L << (UNIT_BITS-1-posBit))) != 0;
	}

    public static void setBit(short[] ba, int posBit, boolean b) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x07;
        if (b) {
            ba[pA] |= (1L << (UNIT_BITS-1-posBit));
        } else {
            ba[pA] &= (~(1L << (UNIT_BITS-1-posBit)));
        }
	}

    
    /**
     * This one 
     * 
     * @param ba		byte[]
     * @param startBit	start bit
     * @param nValues	number of values
     * @param val		value to search for
     * @param valWidth	bit width of the values
     * @return			index of value or according negative index if value was not found
     */
    public static int binarySearch(short[] ba, int startBit, int nValues, int val, int valWidth) {
    	int min = 0;
    	int max = nValues - 1;

    	while (min <= max) {
    		int mid = (min + max) >>> 1;
            long midVal = readArray(ba, mid*valWidth+startBit, valWidth);

            if (midVal < val) {
            	min = mid + 1;
            } else if (midVal > val) {
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
      int arraySize = (nBits)>>>3; // to bytes
      arraySize = (arraySize)>>>3; // align 8 bytes
      if (arraySize*8*8 < nBits) {
          arraySize++;
      }
      //turn it into bytes
      arraySize <<= 3;      
      //now we need to turn the required bytes into the array's unit
      arraySize = arraySize>>> (UNIT_3-3);
    	
      //old version: just calculates required size
//        int arraySize = (nBits)>>>Bits.UNIT_3;
//        if (arraySize*Bits.UNIT_BITS < nBits) {
//            arraySize++;
//        }
        return arraySize;
    }

    /**
     * Resize an array.
     * @param oldA
     * @param newSizeBits
     * @return New array larger array.
     */
    public static short[] arrayExpand(short[] oldA, int newSizeBits) {
    	short[] newA = new short[calcArraySize(newSizeBits)];
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	statAExpand++;
    	return newA;
    }
    
    public static short[] arrayCreate(int nBits) {
    	short[] newA = new short[calcArraySize(nBits)];
    	statACreate++;
    	return newA;
    }
    
    /**
     * Ensure capacity of an array. Expands the array if required.
     * @param oldA
     * @param requiredBits
     * @return Same array or expanded array.
     */
    public static short[] arrayEnsureSize(short[] oldA, int requiredBits) {
    	if (isCapacitySufficient(oldA, requiredBits)) {
    		return oldA;
    	}
    	return arrayExpand(oldA, requiredBits);
    }
    
    public static boolean isCapacitySufficient(short[] a, int requiredBits) {
    	return (a.length*UNIT_BITS >= requiredBits);
    }
    
    public static short[] arrayTrim(short[] oldA, int requiredBits) {
    	int reqSize = calcArraySize(requiredBits);
    	if (oldA.length == reqSize) {
    		return oldA;
    	} else if (oldA.length<reqSize) {
    		throw new IllegalStateException();
    	}
    	short[] newA = new short[reqSize];
    	System.arraycopy(oldA, 0, newA, 0, reqSize);
    	statATrim++;
    	return newA;
    }

	public static int arraySizeInByte(short[] ba) {
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

	public static String toBinary(long[] la, int DEPTH) {
	    StringBuilder sb = new StringBuilder();
	    for (long l: la) {
	    	sb.append(toBinary(l, DEPTH));
	        sb.append(", ");
	    }
	    return sb.toString();
	}

    public static String toBinary(short[] ba) {
        StringBuilder sb = new StringBuilder();
        for (short l: ba) {
        	sb.append(toBinary(l, UNIT_BITS));
            sb.append(", ");
        }
        return sb.toString();
    }
}
