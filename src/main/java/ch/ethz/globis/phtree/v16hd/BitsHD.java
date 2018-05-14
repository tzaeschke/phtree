package ch.ethz.globis.phtree.v16hd;

import java.util.Arrays;

import ch.ethz.globis.phtree.PhTreeHelperHD;
import ch.ethz.globis.phtree.util.BitsLong;

/**
 * High-dimensional unsigned bitstrings (actually, simply unsigned bitstrings). 
 * 
 * Conventions:
 * - The most significant bit is on the far left, it is the MSB of long[0]
 * - The long[] have always the correct size (never to large, and all identical
 *   in case multiple arguments are given.
 * - Values are treated as 'unsigned' 
 * 
 * 
 * @author Tilmann Zäschke
 *
 */
public class BitsHD {

    private static final long UNIT_0xFF = 0xFFFFFFFFFFFFFFFFL;  	//0xFF for byte=8 bits=3exp
	public static final long HIGH_1 = 1<<63;

//	public static int mod64(int n) {
//		return n & 0x3F;
//	}
	
	public static int mod64(int x) {
		return x & 0x3F;
	}
	
	/**
	 * @param x input
	 * @return (n%64), except for multiples of 64, where it returns '64'
	 */
	public static int mod65x(int x) {
		return 1 + ((x-1) & 0x3F);
	}
	
	public static int div64(int x) {
		return x >> 6;
	}

	@Deprecated
	public static long[] bOr (long[] a, long[] b) {
		throw new UnsupportedOperationException();
	}


	/**
	 * The long[] equivalent of Long.bitCount(a ^ b).
	 * @param a a
	 * @param b b 
	 * @return the long[] equivalent of Long.bitCount(a ^ b)
	 */
	public static int xorBitCount(long[] a, long[] b) {
		int bitCount = 0;
		for (int i = 0; i < a.length; i++) {
			bitCount += Long.bitCount(a[i] ^ b[i]);
		}
		return bitCount;
	}


    /**
     * 
     * 
     * @param keys			key[]
     * @param fromIndex		start bit
     * @param toIndex		number of entries = number of keys
     * @param key			key to search for
     * @return				index of key or according negative index if key was not found
     */
    public static int binarySearch(long[][] keys, int fromIndex, int toIndex, long[] key) {
    	int min = fromIndex;
    	int max = toIndex - 1;
    	int maxI = key.length-1;

    	while (min <= max) {
    		int mid = (min + max) >>> 1;
    		int readPos = mid+fromIndex;
    		long[] midKey = keys[readPos];
    		for (int i = 0; i <= maxI; i++) {
	            int comp = Long.compareUnsigned(midKey[i], key[i]); 
	            if (comp < 0) {
	            	min = mid + 1;
	            	break;
	            } else if (comp > 0) {
	            	max = mid - 1;
	            	break;
	            } else {
	            	if (i == maxI) {
	            		return mid; // key found
	            	}
	            	//else: continue checking this key
	            }
    		}
    	}
    	return -(min + 1);  // key not found.
    }


	public static boolean isLess(long[] a, long[] b) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
//				if (i == a.length-1) {
//					return a[i] < b[i];
//				}
				return Long.compareUnsigned(a[i], b[i]) < 0;
			}
		}
		return false;
	}
	   
	public static boolean isLessEq(long[] a, long[] b) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
//				if (i == a.length-1) {
//					return a[i] < b[i];
//				}
				return Long.compareUnsigned(a[i], b[i]) < 0;
			}
		}
		return true;
	}
	   
	public static boolean isEq(long[] a, long[] b) {
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}
	   
	@Deprecated
    public static long[] readArrayHD(long[] ba, int offsetBit, int entryLen) {
    	if (entryLen == 0) {
    		//TODO return [0]?
    		return null;
    	}
    	
    	long[] ret = PhTreeHelperHD.newHDPos(entryLen);
    	int subEntryLen = mod65x(entryLen);
    	for (int i = 0; i < ret.length; i++) {
    		ret[i] = BitsLong.readArray(ba, offsetBit, subEntryLen);
    		//TODO make/use read64()/write64 functions?
    		offsetBit += subEntryLen;  
    		subEntryLen = 64;
    	}
    	return ret;
    }


	@Deprecated
    public static void readArrayHD(long[] ba, int offsetBit, int entryLen, long[] out) {
    	if (entryLen == 0) {
    		return;
    	}

    	int iStart = out.length - BitsHD.div64(entryLen-1) - 1;
       	int subEntryLen = mod65x(entryLen);
       	
       	//first read a partial chunk
       	long mask = subEntryLen == 64 ? 0 : (-1L) << subEntryLen;
		out[iStart] = (out[iStart] & mask) | (BitsLong.readArray(ba, offsetBit, subEntryLen) & ~mask);
		offsetBit += subEntryLen;  
		
    	for (int i = iStart+1; i < out.length; i++) {
    		out[i] = BitsLong.readArray(ba, offsetBit, 64);
    		//TODO make/use read64()/write64 functions?
    		offsetBit += 64;  
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
    	int subEntryLen = BitsHD.mod65x(entryLen);
    	for (int i = 0; i < val.length; i++) {
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
	
	public static void set0(long[] dst) {
		Arrays.fill(dst, 0);
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

	public static long[] inc(long[] v) {
		for (int i = v.length-1; i >= 0; i--) {
			long prev = v[i]++;
			if (Long.compareUnsigned(prev,  v[i]) < 0) {
				return v;
			}
		}		
		return v;
	}
	
	public static long[] dec(long[] v) {
		for (int i = v.length-1; i >= 0; i--) {
			long prev = v[i]--;
			if (Long.compareUnsigned(prev,  v[i]) > 0) {
				return v;
			}
		}		
		return v;
	}
	
	/**
	 * Best HC incrementer ever.
	 * @param v value
	 * @param min min
	 * @param max max
	 * @return 'false' if an overflow occurred, otherwise true (meaning the return value is 
	 * larger than the input value).
	 */
	public static boolean incHD(long[] v, long[] min, long[] max) {
		int msb = v.length-1;
		for (int i = msb; i >= 0; i--) {
			long in = v[i];
			long result = inc(in, min[i], max[i]);
			v[i] = result;
			if (Long.compareUnsigned(result, in) > 0) {
				//we can abort now
				return true;
			}
		}
//		int i = msb;
//		long in = v[i];
//		long result = inc(in, min[i], max[i]);
//		v[i] = result;
//		return result > in;
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
		int msbDims = BitsHD.mod65x(dims);
		long maxHcAddr = msbDims == 64 ? UNIT_0xFF : ~((-1L)<<dims);
		int nSetFilterBits = 0;
		for (int i = 0; i < maskLower.length; i++) {
			nSetFilterBits += Long.bitCount(maskLower[i] | ((~maskUpper[i]) & maxHcAddr));
			maxHcAddr = UNIT_0xFF;
		}
		return nSetFilterBits;
	}
	
	
    /**
     * @param v1 value 1
     * @param v2 value 2
     * @param rangeMax range (inclusive)
     * @return Number of conflicting bits (number of highest conf. bit + 1)
     */
    public static final int getMaxConflictingBits(long[] v1, long[] v2, int rangeMax) {
    	int iMin = v1.length - BitsHD.div64(rangeMax) - 1;
    	int rmMod64 = BitsHD.mod64(rangeMax);
    	long mask = rmMod64 == 63 ? (-1L) : ~(-1L << (rmMod64+1));
    	for (int i = iMin; i < v1.length; i++) {
            long x = (v1[i] ^ v2[i]) & mask;
            if (x != 0) {
            	int cb = Long.SIZE - Long.numberOfLeadingZeros(x);
            	cb += Long.SIZE * (v1.length - i - 1);
            	return cb; 
            }
            mask = -1L;
    	}
    	return 0;
    }
	
	
    /**
     * 
     * @param v1 value 1
     * @param v2 value 2
     * @param rangeMin range (exclusive): 0 means 'no minimum'.
     * @param rangeMax range (inclusive)
     * @return Number of conflicting bits (number of highest conf. bit + 1)
     */
    public static final int getMaxConflictingBits(long[] v1, long[] v2, int rangeMin, int rangeMax) {
    	int iMin = v1.length - BitsHD.div64(rangeMax) - 1;
    	int iMax = v1.length - BitsHD.div64(rangeMin+1);
    	int rmMod64 = BitsHD.mod64(rangeMax);
    	long mask = rmMod64 == 63 ? (-1L) : ~(-1L << (rmMod64+1));
    	for (int i = iMin; i < iMax; i++) {
            long x = (v1[i] ^ v2[i]) & mask;
            if (x != 0) {
            	int cb = Long.SIZE - Long.numberOfLeadingZeros(x);
            	cb += Long.SIZE * (v1.length - i - 1);
            	return cb > rangeMin ? cb : 0; 
            }
            mask = -1L;
    	}
    	return 0;
    }
	
    /**
     * @param v1 value 1
     * @param v2 value 2
     * @param rangeMax range (inclusive)
     * @return Number of conflicting bits (number of highest conf. bit + 1)
     */
    public static final boolean hasConflictingBits(long[] v1, long[] v2, int rangeMax) {
    	int iMin = v1.length - BitsHD.div64(rangeMax) - 1;
    	int rmMod64 = BitsHD.mod64(rangeMax);
    	long mask = rmMod64 == 63 ? (-1L) : ~(-1L << (rmMod64+1));
    	for (int i = iMin; i < v1.length; i++) {
            long x = (v1[i] ^ v2[i]) & mask;
            if (x != 0) {
            	return true; 
            }
            mask = -1L;
    	}
    	return false;
    }
	
	
    /**
     * 
     * @param v1 value 1
     * @param v2 value 2
     * @param rangeMin range (exclusive): 0 means 'no minimum'.
     * @param rangeMax range (inclusive)
     * @return Number of conflicting bits (number of highest conf. bit + 1)
     */
    public static final boolean hasConflictingBits(long[] v1, long[] v2, int rangeMin, int rangeMax) {
    	int iMin = v1.length - BitsHD.div64(rangeMax) - 1;
    	int iMax = v1.length - BitsHD.div64(rangeMin+1);
    	int rmMod64 = BitsHD.mod64(rangeMax);
    	long mask = rmMod64 == 63 ? (-1L) : ~(-1L << (rmMod64+1));
    	for (int i = iMin; i < iMax; i++) {
            long x = (v1[i] ^ v2[i]) & mask;
            if (x != 0) {
            	int cb = Long.SIZE - Long.numberOfLeadingZeros(x);
            	cb += Long.SIZE * (v1.length - i - 1);
            	return cb > rangeMin; 
            }
            mask = -1L;
    	}
    	return false;
    }
	
}
