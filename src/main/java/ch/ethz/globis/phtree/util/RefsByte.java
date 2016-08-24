/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;

import ch.ethz.globis.phtree.PhTreeHelper;

/**
 * Manipulation methods and pool for long[].
 * 
 * @author ztilmann
 */
public class RefsByte {
	
	private static final byte[] EMPTY_REF_ARRAY = {};
    private static final ArrayPoolN POOL = 
    		new ArrayPoolN(PhTreeHelper.ARRAY_POOLING_MAX_ARRAY_SIZE, 
    				PhTreeHelper.ARRAY_POOLING_POOL_SIZE);

    private RefsByte() {
    	//nothing
    }
    
    private static class ArrayPoolN {
    	private final int maxArraySize;
    	private final int maxArrayCount;
    	byte[][][] pool;
    	int[] poolSize;
    	ArrayPoolN(int maxArraySize, int maxArrayCount) {
			this.maxArraySize = maxArraySize;
			this.maxArrayCount = maxArrayCount;
			this.pool = new byte[maxArraySize+1][maxArrayCount][];
			this.poolSize = new int[maxArraySize+1];
		}
    	
    	byte[] getArray(int size) {
    		if (size == 0) {
    			return EMPTY_REF_ARRAY;
    		}
    		if (size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return new byte[size];
    		}
    		synchronized (this) {
	    		int ps = poolSize[size]; 
	    		if (ps > 0) {
	    			poolSize[size]--;
	    			byte[] ret = pool[size][ps-1];
	    			Arrays.fill(ret, (byte)0);
	    			return ret;
	    		}
    		}
    		return new byte[size];
    	}
    	
    	synchronized void offer(byte[] a) {
    		int size = a.length;
    		if (size == 0 || size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
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

    
    /**
     * Calculate array size for given number of bits.
     * This takes into account JVM memory management, which allocates multiples of 8 bytes.
     * @param nLong
     * @return array size.
     */
	private static int calcArraySize(int nLong) {
		//round up to 8byte
		return (nLong+7) & 0xFFFFFFF8;
	}

    /**
     * Create an array.
     * @param size size
     * @return a new array
     */
	public static byte[] arrayCreate(int size) {
		return POOL.getArray(calcArraySize(size));
    }
    
	/**
	 * Replaces an array with another array. The replaced array is returned to the pool.
	 * @param oldA old array
	 * @param newA new array
	 * @return new array
	 */
	public static byte[] arrayReplace(byte[] oldA, byte[] newA) {
		if (oldA != null) {
			POOL.offer(oldA);
		}
		return newA;
    }
    
	/**
	 * Clones an array.
	 * @param oldA old array
	 * @return a copy or the input array
	 */
	public static byte[] arrayClone(byte[] oldA) {
    	byte[] newA = arrayCreate(oldA.length);
    	arraycopy(oldA, 0, newA, 0, oldA.length);
    	return newA;
    }
    
	/**
	 * Inserts an empty field at position 'pos'. If the required size is larger than the current
	 * size, the array is copied to a new array. The new array is returned and the old array is
	 * given to the pool.
	 * @param values array
	 * @param pos position
	 * @param requiredSize required size
	 * @return the modified array
	 */
	public static byte[] insertSpaceAtPos(byte[] values, int pos, int requiredSize) {
    	byte[] dst = values;
		if (requiredSize > values.length) {
			dst = arrayCreate(requiredSize);
			copyRight(values, 0, dst, 0, pos);
		}
		copyRight(values, pos, dst, pos+1, requiredSize-1-pos);
		return dst;
	}
	
	/**
	 * Removes a field at position 'pos'. If the required size is smaller than the current
	 * size, the array is copied to a new array. The new array is returned and the old array is
	 * given to the pool.
	 * @param values array
	 * @param pos position
	 * @param requiredSize required size
	 * @return the modified array
	 */
	public static byte[] removeSpaceAtPos(byte[] values, int pos, int requiredSize) {
    	int reqSize = calcArraySize(requiredSize);
    	byte[] dst = values;
		if (reqSize < values.length) {
			dst = POOL.getArray(reqSize);
			copyLeft(values, 0, dst, 0, pos);
		}
		if (pos < values.length-1) {
			copyLeft(values, pos+1, dst, pos, requiredSize-pos);
		}
		return dst;
	}
	
	private static void copyLeft(byte[] src, int srcPos, byte[] dst, int dstPos, int len) {
		if (len >= 7) {
			System.arraycopy(src, srcPos, dst, dstPos, len);
		} else {
			for (int i = 0; i < len; i++) {
				dst[dstPos+i] = src[srcPos+i]; 
			}
		}
	}
	
	private static void copyRight(byte[] src, int srcPos, byte[] dst, int dstPos, int len) {
		if (len >= 7) {
			System.arraycopy(src, srcPos, dst, dstPos, len);
		} else {
			for (int i = len-1; i >= 0; i--) {
				dst[dstPos+i] = src[srcPos+i]; 
			}
		}
	}

    /**
     * Write the src array into the dst array at position dstPos. 
     * @param src source array
     * @param dst destination array
     * @param dstPos destination position
     */
	public static void writeArray(byte[] src, byte[] dst, int dstPos) {
    	arraycopy(src, 0, dst, dstPos, src.length);
    }

    /**
     * Same a {@link #arraycopy(byte[], int, byte[], int, int)}. 
     * @param src source array
     * @param srcPos source position
     * @param dst destination array
     * @param dstPos destination position
     * @param length length
     */
	public static void writeArray(byte[] src, int srcPos, byte[] dst, int dstPos, int length) {
		arraycopy(src, srcPos, dst, dstPos, length);
	}

	/**
	 * Reads data from srcPos in src[] into dst[]. 
	 * @param src source array
	 * @param srcPos source position
	 * @param dst destination array
	 */
	public static void readArray(byte[] src, int srcPos, byte[] dst) {
		arraycopy(src, srcPos, dst, 0, dst.length);
	}

	/**
	 * Creates a new array with from copying oldA and inserting insertA at position pos.
	 * The old array is returned to the pool. 
	 * @param oldA old array
	 * @param insertA array to insert
	 * @param dstPos destination position
	 * @return new array
	 */
	public static byte[] insertArray(byte[] oldA, byte[] insertA, int dstPos) {
		byte[] ret = arrayCreate(oldA.length + insertA.length);
		arraycopy(oldA, 0, ret, 0, dstPos);
		arraycopy(insertA, 0, ret, dstPos, insertA.length);
		arraycopy(oldA, dstPos, ret, dstPos+insertA.length, oldA.length-dstPos);
		POOL.offer(oldA);
		return ret;
	}

	/**
	 * Creates a new array with from copying oldA and removing 'length' entries at position pos.
	 * The old array is returned to the pool. 
	 * @param oldA old array
	 * @param dstPos destination position
	 * @param length length
	 * @return new array
	 */
	public static byte[] arrayRemove(byte[] oldA, int dstPos, int length) {
		byte[] ret = arrayCreate(oldA.length - length);
		arraycopy(oldA, 0, ret, 0, dstPos);
		arraycopy(oldA, dstPos+length, ret, dstPos, ret.length-dstPos);
		POOL.offer(oldA);
		return ret;
	}

	/**
	 * Same as System.arraycopy(), but uses a faster copy-by-loop approach for small arrays.
	 * @param src source array
	 * @param srcPos source position
	 * @param dst destination array
	 * @param dstPos destination position
	 * @param len length
	 */
	public static void arraycopy(byte[] src, int srcPos, byte[] dst, int dstPos, int len) {
		if (len < 10) {
			for (int i = 0; i < len; i++) {
				dst[dstPos+i] = src[srcPos+i]; 
			}
		} else {
			System.arraycopy(src, srcPos, dst, dstPos, len);
		}
	}

	/**
	 * Writes a byte array to a stream.
	 * @param a array
	 * @param out output stream
	 * @throws IOException if writing fails
	 */
	public static void write(byte[] a, ObjectOutput out) throws IOException {
		out.writeInt(a.length);
		for (int i = 0; i < a.length; i++) {
			out.writeByte(a[i]);
		}
	}

	/**
	 * Reads a byte array from a stream.
	 * @param in input stream
	 * @return the long array.
	 * @throws IOException if reading fails
	 */
	public static byte[] read(ObjectInput in) throws IOException {
		int size = in.readInt();
		byte[] ret = POOL.getArray(size);
		in.readFully(ret);
		return ret;
	}
}
