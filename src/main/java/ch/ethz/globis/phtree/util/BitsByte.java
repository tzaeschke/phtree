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
public class BitsByte {

    public static final int UNIT_3 = 3;  			//EXP: 2^EXP = BITS
    public static final int UNIT_BITS = (1<<UNIT_3);
    public static final int UNIT_0x07 = 0x07;  		//0x07 for byte=8 bits=3exp
    public static final int UNIT_0xFF = 0xFF;  		//0xFF for byte=8 bits=3exp
    public static final int UNIT_0xFF00 = 0xFF00;  	//0xFF00 for byte=8 bits=3exp

	/**
     * 
     * @param ba The array to read bits from.
     * @param offsetBit The bit to start reading at.
     * @param entryLen The length of the entry in bit.
     * @return The read bits as long
     */    
    public static long readArray(byte[] ba, int offsetBit, int entryLen) {
        int pA = offsetBit >>> UNIT_3;
        int startBit = UNIT_BITS -(offsetBit & UNIT_0x07);  //last three bit [0..7]
        long ret = 0;
        int bitsRead = 0;
        while (bitsRead < entryLen) {
            int mask = (1 << startBit)-1;
            if (bitsRead+startBit > entryLen) {
                int bitsToIgnore = bitsRead + startBit - entryLen;
                int mask2 = (1<<bitsToIgnore) - 1;
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

    public static void writeArray(byte[] ba, int offsetBit, int entryLen, long val) {
        int pA = offsetBit >>> UNIT_3;
        int startBit = UNIT_BITS - (offsetBit & UNIT_0x07); //counting from right to left (low to high)
        int bitsWritten = 0;
        while (bitsWritten < entryLen) {
            //int mask = (1 << startBit)-1;
            //erase byte[] first - create mask
            int eraseMask = (1 << startBit) - 1;
            int bitsToWrite = startBit;
            if (bitsWritten+bitsToWrite > entryLen) {
                int bitsToIgnore = bitsWritten + startBit - entryLen;
                int mask2 = (1<<bitsToIgnore) - 1;
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
	 * @param ba byte array
	 * @param start start bit
	 * @param nBits amount to shift
	 */
    public static void insertBits(byte[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (nBits < 0) {
			throw new IllegalArgumentException();
		}
		//shift right
//		copyBits(ba2, start, ba2, start + nBits, ba2.length*8-start-nBits);
		int bitsToShift = ba.length*UNIT_BITS - start - nBits;
		for (int i = 0; i < bitsToShift; i++) {
			int srcBit = ba.length*UNIT_BITS - nBits - i - 1;
			int trgBit = ba.length*UNIT_BITS - i - 1;
			setBit(ba, trgBit, getBit(ba, srcBit));
		}
	}

    public static void removeBits(byte[] ba, int start, int nBits) {
		if (nBits == 0) {
			return;
		}
		if (nBits < 0) {
			throw new IllegalArgumentException();
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

    public static void copyBitsLeft(byte[] src, int posSrc, byte[] trg, int posTrg) {
    	int len = src.length*UNIT_BITS-posSrc;
    	copyBitsLeft(src, posSrc, trg, posTrg, len);
    }
    	
    public static void copyBitsLeft(byte[] src, int posSrc, byte[] trg, int posTrg, int len) {
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
            int mask = (1 << startBitS)-1;
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
        int eraseMask = (1 << startBitT) - 1;
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
    
    public static void copyBitsRight(byte[] src, int posSrc, byte[] trg, int posTrg, int len) {
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
            int mask = (1 << startBitS)-1;
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
        int eraseMask = (1 << startBitT) - 1;
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
	 * @param ba byte array
	 * @param posBit Counts from left to right!!!
	 * @return the bit
	 */
    public static boolean getBit(byte[] ba, int posBit) {
        int pA = posBit >>> UNIT_3;
        //last three bit [0..7]
        posBit &= UNIT_0x07;
        return (ba[pA] & (1L << (UNIT_BITS-1-posBit))) != 0;
	}

    public static void setBit(byte[] ba, int posBit, boolean b) {
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
    public static int binarySearch(byte[] ba, int startBit, int nValues, int val, int valWidth) {
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
    
    public static String toBinary(byte[] ba) {
        StringBuilder sb = new StringBuilder();
        for (byte l: ba) {
            for (int i = 0; i < UNIT_BITS; i++) {
                long mask = (1l << (long)(UNIT_BITS-i-1));
                if ((l & mask) != 0) { sb.append("1"); } else { sb.append("0"); }
            }
            sb.append(", ");
        }
        return sb.toString();
    }
    
    public static String toBinary(long l) {
    	final int DEPTH = 32;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < DEPTH; i++) {
            long mask = (1l << (long)(DEPTH-i-1));
            if ((l & mask) != 0) { sb.append("1"); } else { sb.append("0"); }
            if (i%8==0) sb.append('.');
        }
        return sb.toString();
    }


}
