/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;


/**
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 */
public abstract class PhTreeHelper {

	public static final boolean DEBUG_FULL = false; //even more debug info, gets expensive
	public static final boolean DEBUG = false || DEBUG_FULL;
	public static final Object NULL = new Object();
    
    private PhTreeHelper() {
    	//
    }

    /**
     * Size of object pools, currently only used for node objects.
     */
	public static int MAX_OBJECT_POOL_SIZE = 100;
    
    /** 
	 * Determines how much memory should be allocated on array resizing. The batch
	 * size designates multiples of 16byte on a JVM with less than 32GB. Higher values
	 * result in higher fixed memory requirements but reduce the number of arrays
	 * to be created, copied and garbage collected when modifying the tree.
	 * Recommended values are 1, 2, 3, 4.  Default is 1 for 64bit values and 2 for references.
	 * @param size batch size
	 */
	public static void setAllocBatchSize(int size) {
		//This works as follows: For a long[] we always allocate even numbers, i.e. we allocate
		//2*size slots.
		//The exception is size=0, where we allocate just one slot. This is mainly for debugging.
		if (size == 0) {
			ALLOC_BATCH_SIZE_LONG = 63;
			ALLOC_BATCH_SIZE = 1;
			ALLOC_BATCH_REF = 1;
		} else {
			ALLOC_BATCH_SIZE_LONG = 64*size-1;
			ALLOC_BATCH_SIZE = size;
			ALLOC_BATCH_REF = 2*size-1;
		}
	}
	public static int ALLOC_BATCH_SIZE;// = 1;
	public static int ALLOC_BATCH_SIZE_LONG;// = 127;
	public static int ALLOC_BATCH_REF;// = 1;
	static {
		setAllocBatchSize(1);
	}
	
	/**
	 * Enable pooling of arrays and node objects. This should reduce garbage collection 
	 * during insert()/put(), update() and delete() operations.
	 * We call POOL_SIZE=PS and ARRAY_SIZE=AS.
	 * The maximum memory allocation of the pool is 
	 * approx. (AS*AS)/2*PS*8byte = 1000*1000*100/2*8 = 400MB for the long[] pool and half the 
	 * size (200MB) for the Object[] pool, however the total size is typically much smaller, 
	 * around 1.2M*8=10MB.
	 * For DEPTH=64, suggested values  
	 */
	public static boolean ARRAY_POOLING = true;
	
	/** The maximum size of arrays that will be stored in the pool. The default is 1000, which
	 * mean 8KB for long[] and 4KB for Object[]. Also, there a separate pools for long[] and 
	 * Object[]. */
	public static int ARRAY_POOLING_MAX_ARRAY_SIZE = 10000;
	
	/** The maximum size of the pool (per array). The pool consists of several sub-pool, one for
	 * each size of arrays. A max size of 100 means that there will be at most 100 arrays of each
	 * size in the pool. */
	public static int ARRAY_POOLING_POOL_SIZE = 100;
	
	/**
	 * Enable pooling of arrays. This should reduce garbage collection during inert()/put(),
	 * update() and delete() operations.
	 * @param flag whether pooling should be enabled or not
	 */
	public static void enablePooling(boolean flag) {
		ARRAY_POOLING = flag;
	}
	
    public static final void debugCheck() {
    	if (DEBUG) {
    		System.err.println("*************************************");
    		System.err.println("** WARNING ** DEBUG IS ENABLED ******");
    		System.err.println("*************************************");
    	}
//    	if (BLHC_THRESHOLD_DIM > 6) {
//    		System.err.println("*************************************");
//    		System.err.println("** WARNING ** BLHC IS DISABLED ******");
//    		System.err.println("*************************************");
//    	}
    }
    
	public static final int align8(int n) {
    	return (int) (8*Math.ceil(n/8.0));
    }

	/**
	 * 
	 * @param v1 one vector
	 * @param v2 another vector
	 * @param bitsToCheck number of bits to check (starting with least significant bit)
     * @return Position of the highest conflicting bit (counted from the right) or 0 if none.
	 */
    public static final int getMaxConflictingBits(long[] v1, long[] v2, int bitsToCheck) {
    	if (bitsToCheck == 0) {
    		return 0;
    	}
    	long mask = bitsToCheck==64 ? ~0L : ~(-1L << bitsToCheck); //mask, because value2 may not have leading bits set
    	return getMaxConflictingBitsWithMask(v1, v2, mask);
    }
    
    /**
     * Calculates the number of conflicting bits, consisting of the most significant bit
     * and all bit 'right'of it (all less significant bits).
     * @param v1 one vector
     * @param v2 another vector
     * @param mask Mask that indicates which bits to check. Only bits where mask=1 are checked.
     * @return Number of conflicting bits or 0 if none.
     */
    public static final int getMaxConflictingBitsWithMask(long[] v1, long[] v2, long mask) {
        long x = 0;
        for (int i = 0; i < v1.length; i++) {
        	//write all differences to x, we just check x afterwards
            x |= v1[i] ^ v2[i];
        }
        x &= mask;
        return Long.SIZE - Long.numberOfLeadingZeros(x);
    }
    

    
    /**
     * Encode the bits at the given position of all attributes into a hyper-cube address.
     * Currently, the first attribute determines the left-most (high-value) bit of the address 
     * (left to right ordered)
     * 
     * @param valSet one vector
     * @param currentDepth current depth
     * @param DEPTH total bit depth, usually 64
     * @return Encoded HC position
     */
    public static final long posInArray(long[] valSet, int currentDepth, int DEPTH) {
        //n=DIM,  i={0..n-1}
        // i = 0 :  |0|1|0|1|0|1|0|1|
        // i = 1 :  | 0 | 1 | 0 | 1 |
        // i = 2 :  |   0   |   1   |
        //len = 2^n
        //Following formula was for inverse ordering of current ordering...
        //pos = sum (i=1..n, len/2^i) = sum (..., 2^(n-i))

    	long valMask = (1L << (DEPTH-1-currentDepth));
    	
        long pos = 0;
        for (long v: valSet) {
        	pos <<= 1;
        	//set pos-bit if bit is set in value
            if ((valMask & v) != 0) {
                pos |= 1L;
            }
        }
        return pos;
    }

    /**
     * Encode the bits at the given position of all attributes into a hyper-cube address.
     * Currently, the first attribute determines the left-most (high-value) bit of the address 
     * (left to right ordered)
     * 
     * @param valSet vector
     * @param postLen the postfix length
     * @return Encoded HC position
     */
    public static final long posInArray(long[] valSet, int postLen) {
        //n=DIM,  i={0..n-1}
        // i = 0 :  |0|1|0|1|0|1|0|1|
        // i = 1 :  | 0 | 1 | 0 | 1 |
        // i = 2 :  |   0   |   1   |
        //len = 2^n
        //Following formula was for inverse ordering of current ordering...
        //pos = sum (i=1..n, len/2^i) = sum (..., 2^(n-i))

    	long valMask = 1l << postLen;
    	
        long pos = 0;
        for (int i = 0; i < valSet.length; i++) {
        	pos <<= 1;
        	//set pos-bit if bit is set in value
            pos |= (valMask & valSet[i]) >>> postLen;
            }
        return pos;
    }

    /**
     * Transpose the value from long[DIM] to long[DEPTH].
     * Transposition occurs such that high-order bits end up in the first value of 'tv'.
     * Value from DIM=0 end up as highest order bits in 'tv'.
     * 0001,
     * 0010, 
     * 0011
     * becomes
     * 000, 000, 011, 101
     * 
     * @param valSet vector
     * @param DEPTH total number of bits, usually 64 
     * @return Transposed value
     */
    public static long[] transposeValue(long[] valSet, int DEPTH) {
    	long[] tv = new long[DEPTH];
    	long valMask = 1L << (DEPTH-1);
    	int rightShift = DEPTH-1;
    	for (int j = 0; j < DEPTH; j++) {
	    	long pos = 0;
	        for (long v: valSet) {
	        	pos <<= 1;
	        	//set pos-bit if bit is set in value
//	            if ((valMask & v) != 0) {
//	                pos |= 1L;
//	            }
	        	pos |= (valMask & v) >>> rightShift;
	            }
	        tv[j] = pos;
	        valMask >>>= 1;
    		rightShift--;
    	}
        return tv;
    }
    

   /**
     * Apply a HC-position to a value. This means setting one bit for each dimension.
     * Leading and trailing bits in the value remain untouched.
     * @param pos hc-position
     * @param currentPostLen current postfix length
     * @param val value
     */
    public static void applyHcPos(long pos, int currentPostLen, long[] val) {
    	long mask = 1L << currentPostLen;
    	pos = Long.rotateLeft(pos, currentPostLen); //leftmost bit is at position of mask
    	for (int d = val.length-1; d >= 0; d--) {
			//Hack to avoid branching. However, this is faster than rotating 'pos' i.o. posMask
			val[d] = (val[d] & ~mask) | (mask & pos);
			pos = Long.rotateRight(pos, 1);
			
//			if (x != 0) {
//				val[d] |= mask;
//			} else {
//				val[d] &= ~mask;
//			}
		}
    }

}

