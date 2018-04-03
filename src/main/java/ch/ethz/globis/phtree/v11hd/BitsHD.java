package ch.ethz.globis.phtree.v11hd;

import ch.ethz.globis.phtree.PhTreeHelperHD;
import ch.ethz.globis.phtree.util.BitsLong;

public class BitsHD {

    private static final long UNIT_0xFF = 0xFFFFFFFFFFFFFFFFL;  	//0xFF for byte=8 bits=3exp

	public static int mod64(int n) {
		return n & 0x3F;
	}
	
	
	public static long[] bOr (long[] a, long[] b) {
		return null;
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
    public static int binarySearch(long[] ba, int startBit, int nEntries, long[] key, int keyWidth, 
    		int valueWidth) {
    	int entryWidth = keyWidth + valueWidth; 
    	int min = 0;
    	int max = nEntries - 1;

    	while (min <= max) {
    		int mid = (min + max) >>> 1;
    		int readPos = mid*entryWidth+startBit;
    		int readSize = BitsHD.mod64(keyWidth);
    		for (int i = 0; i < key.length; i++) {
	            long midKey = Bits.readArray(ba, readPos, readSize);
	
	            if (midKey < key[i]) {
	            	min = mid + 1;
	            } else if (midKey > key[i]) {
	            	max = mid - 1;
	            } else {
	            	if (i == 0) {
	            		return mid; // key found
	            	}
	            	//else: continue checking this key
	            }
	            readPos += readSize;
	            readSize = 64;
    		}
    	}
    	return -(min + 1);  // key not found.
    }


	public static boolean isLess(long[] a, long[] b) {
		for (int i = a.length-1; i >= 0; i++) {
			if (a[i] < b[i]) {
				return true;
			}
			if (a[i] > b[i]) {
				return false;
			}
		}
		return false;
	}
	   
	public static boolean isLessEq(long[] a, long[] b) {
		for (int i = a.length-1; i >= 0; i++) {
			if (a[i] < b[i]) {
				return true;
			}
			if (a[i] > b[i]) {
				return false;
			}
		}
		return true;
	}
	   
	public static boolean isEq(long[] a, long[] b) {
		for (int i = a.length-1; i >= 0; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}
	   
	
    public static long[] readArrayHD(long[] ba, int offsetBit, int entryLen) {
    	if (entryLen == 0) {
    		//TODO return [0]?
    		return null;
    	}
    	
    	long[] ret = PhTreeHelperHD.newHDPos(entryLen);
    	int subEntryLen = mod64(entryLen);
    	for (int i = 0; i < ret.length; i++) {
    		ret[i] = BitsLong.readArray(ba, offsetBit, subEntryLen);
    		//TODO make/use read64()/write64 functions?
    		offsetBit += subEntryLen;  
    		subEntryLen = 64;
    	}
    	return ret;
    }


    public static void readArrayHD(long[] ba, int offsetBit, int entryLen, long[] out) {
    	if (entryLen == 0) {
    		return;
    	}
    	
    	int subEntryLen = mod64(entryLen);
    	for (int i = 0; i < out.length; i++) {
    		out[i] = BitsLong.readArray(ba, offsetBit, subEntryLen);
    		//TODO make/use read64()/write64 functions?
    		offsetBit += subEntryLen;  
    		subEntryLen = 64;
    	}
    }


    /**
     * 
     * @param ba byte array
     * @param offsetBit offset
     * @param entryLen bits to write, starting with least significant bit (rightmost bit).
     * @param val value to write
     */
    public static void writeArrayHD(long[] ba, int offsetBit, int entryLen, final long[] val) {
    	if (entryLen == 0) {
    		return;
    	}
    	int subEntryLen = BitsHD.mod64(entryLen);
    	for (int i = val.length - 1; i >=0; i++) {
    		BitsLong.writeArray(ba, offsetBit, subEntryLen, val[i]);
    		offsetBit += subEntryLen;
    		subEntryLen = 64;
    	}
    }


	public static long[] newArray(int bits) {
    	return new long[1 + (bits >>> 6)];
	}

	public static void set(long[] dst, long[] src) {
		System.arraycopy(src, 0, dst, 0, src.length);
	}
	
	/**
	 * @param hcPos HC position
	 * @param min min position
	 * @param max max position
	 * @return 'true' if hcPos lies in the hyperrectangle (min,max). 
	 */
	public static boolean checkHcPosHD(long[] hcPos, long[] min, long[] max) {
		for (int i = 0; i < hcPos.length; i++) {
			if (((hcPos[i] | min[i]) & max[i]) != hcPos[i]) {
				return false;
			}
		}
		return true;
	}

	
	/**
	 * Best HC incrementer ever.
	 * @param v
	 * @param min
	 * @param max
	 * @return 'false' if an overflow occurred, otherwise true (meaning the return value is 
	 * larger than the input value).
	 */
	static boolean incHD(long[] v, long[] min, long[] max) {
		for (int i = 0; i < v.length; i++) {
			long in = v[i];
			long result = inc(in, min[i], max[i]);
			v[i] = result;
			if (result > in) {
				//we can abort now
				return true;
			}
		}
		return false;
	}

	/**
	 * Best HC incrementer ever.
	 * It's called 'unsafe' because it assumes that 'out' is already initialized
	 * with 'v': This method aborts without setting _all_ bits in out. It only sets those
	 * that are different from 'v'. 
	 * @param v
	 * @param min
	 * @param max
	 * @param out Return value
	 * @return 'false' if an overflow occurred, otherwise true (meaning the return value is 
	 * larger than the input value).
	 */
	static boolean incUnsafeHD(long[] v, long[] min, long[] max, long[] out) {
		for (int i = 0; i < v.length; i++) {
			long in = v[i];
			long result = inc(in, min[i], max[i]);
			out[i] = result;
			if (result > in) {
				//we can abort now
				return true;
			}
		}
		return false;
	}

	static long inc(long v, long min, long max) {
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~max);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r++;
		//remove invalid bits.
		return (r & max) | min;

		//return -1 if we exceed 'max' and cause an overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		//return (r <= v) ? -1 : r;
	}

	public static int getFilterBits(long[] maskLower, long[] maskUpper, int dims) {
		long maxHcAddr = ~((-1L)<<dims);
		for (int i = maskLower.length - 1; i >=0; i++) {
			int nSetFilterBits = Long.bitCount(maskLower[i] | ((~maskUpper[i]) & maxHcAddr));
			if (nSetFilterBits > 0) {
				return nSetFilterBits + i*64;
			}
			maxHcAddr = UNIT_0xFF;
		}
		return 0;
	}
	
}
