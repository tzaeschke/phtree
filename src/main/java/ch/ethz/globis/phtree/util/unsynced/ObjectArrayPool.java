/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util.unsynced;

import ch.ethz.globis.phtree.PhTreeHelper;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.function.IntFunction;


/**
 * Pool for Object[] instances.
 *
 * NL is the no-locking (unsynchronized) version.
 *
 * @author ztilmann
 *
 * @param <T> Reference type
 */
public class ObjectArrayPool<T> {

	@SuppressWarnings("unchecked")
	private final T[] EMPTY_REF_ARRAY = (T[]) new Object[0];

	private final int maxArraySize;
	private final int maxArrayCount;
	private T[][][] pool;
	private int[] poolSize;
	private final IntFunction<T[]> constructor;

	@SuppressWarnings("unchecked")
	public static <T> ObjectArrayPool<T> create() {
		return new ObjectArrayPool<>(PhTreeHelper.ARRAY_POOLING_MAX_ARRAY_SIZE,
				PhTreeHelper.ARRAY_POOLING_POOL_SIZE, (n) -> (T[]) new Object[n]);
	}

	public static <T> ObjectArrayPool<T> create(IntFunction<T[]> constructor) {
		return new ObjectArrayPool<>(PhTreeHelper.ARRAY_POOLING_MAX_ARRAY_SIZE,
				PhTreeHelper.ARRAY_POOLING_POOL_SIZE, constructor);
	}

	@SuppressWarnings("unchecked")
	private ObjectArrayPool(int maxArraySize, int maxArrayCount, IntFunction<T[]> constructor) {
		this.constructor = constructor;
		this.maxArraySize = PhTreeHelper.ARRAY_POOLING ? maxArraySize : 0;
		this.maxArrayCount = PhTreeHelper.ARRAY_POOLING ? maxArrayCount : 0;
		this.pool = (T[][][]) new Object[maxArraySize+1][maxArrayCount][];
		this.poolSize = new int[maxArraySize+1];
	}

	public T[] getArray(int size) {
		if (size == 0) {
			return EMPTY_REF_ARRAY;
		}
		if (size > maxArraySize) {
			return constructor.apply(size);
		}
		int ps = poolSize[size];
		if (ps > 0) {
			poolSize[size]--;
			T[] ret = pool[size][ps-1];
			pool[size][ps-1] = null;
			return ret;
		}
		return constructor.apply(size);
	}

	public void offer(T[] a) {
		int size = a.length;
		if (size == 0 || size > maxArraySize) {
			return;
		}
		int ps = poolSize[size];
		if (ps < maxArrayCount) {
			Arrays.fill(a, null);
			pool[size][ps] = a;
			poolSize[size]++;
		}
	}

	/**
     * Calculate array size for given number of bits.
     * This takes into account JVM memory management, which allocates multiples of 8 bytes.
     * @param nObjects size of the array
     * @return array size.
     */
	public int calcArraySize(int nObjects) {
		//round up to 8byte=2refs
		//return (nObjects+1) & (~1);
		int arraySize = (nObjects+PhTreeHelper.ALLOC_BATCH_REF);// & (~1);
		int size = PhTreeHelper.ALLOC_BATCH_SIZE * 2;
		//Integer div.
		arraySize = (arraySize/size) * size;
		return arraySize;
	}

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public T[] arrayExpand(T[] oldA, int newSize) {
    	T[] newA = arrayCreate(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	offer(oldA);
    	return newA;
    }
    
    /**
     * Resize an array to exactly the given size.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
	public T[] arrayExpandPrecise(T[] oldA, int newSize) {
		T[] newA = getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	offer(oldA);
    	return newA;
	}

	/**
     * Create an array.
     * @param size size
     * @return a new array
     */
	public T[] arrayCreate(int size) {
    	return getArray(calcArraySize(size));
    }
    
    /**
     * Discards oldA and returns newA.
     * @param oldA old array
     * @param newA new array
     * @return newA.
     */
    public T[] arrayReplace(T[] oldA, T[] newA) {
    	if (oldA != null) {
    		offer(oldA);
    	}
    	return newA;
    }

	/**
	 * Discards oldA.
	 *
	 * @param oldA old array
	 */
	public void arrayDiscard(T[] oldA) {
		if (oldA != null) {
			offer(oldA);
		}
	}

	/**
	 * Clones an array.
	 * @param oldA old array
	 * @return a copy or the input array
	 */
    public T[] arrayClone(T[] oldA) {
    	T[] newA = arrayCreate(oldA.length);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
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
	public T[] insertSpaceAtPos(T[] values, int pos, int requiredSize) {
    	T[] dst = values;
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
	public T[] removeSpaceAtPos(T[] values, int pos, int requiredSize) {
    	int reqSize = calcArraySize(requiredSize);
    	T[] dst = values;
		if (reqSize < values.length) {
			dst = getArray(reqSize);
			copyLeft(values, 0, dst, 0, pos);
		}
		copyLeft(values, pos+1, dst, pos, requiredSize-pos);
		return dst;
	}

	private void copyLeft(T[] src, int srcPos, T[] dst, int dstPos, int len) {
		if (len >= 7) {
			System.arraycopy(src, srcPos, dst, dstPos, len);
		} else {
			for (int i = 0; i < len; i++) {
				dst[dstPos+i] = src[srcPos+i]; 
			}
		}
	}
	
	private void copyRight(T[] src, int srcPos, T[] dst, int dstPos, int len) {
		if (len >= 7) {
			System.arraycopy(src, srcPos, dst, dstPos, len);
		} else {
			for (int i = len-1; i >= 0; i--) {
				dst[dstPos+i] = src[srcPos+i]; 
			}
		}
	}

	/**
	 * Writes an object array to a stream.
	 * @param a array
	 * @param out output stream
	 * @throws IOException if writing to stream fails
	 */
	public void write(T[] a, ObjectOutput out) throws IOException {
		out.writeInt(a.length);
		for (int i = 0; i < a.length; i++) {
			out.writeObject(a[i]);
		}
	}

	/**
	 * Reads an object array from a stream.
	 * @param in input stream
	 * @return the long array.
	 * @throws IOException if reading from stream fails
	 */
	@SuppressWarnings("unchecked")
	public T[] read(ObjectInput in) throws IOException {
		int size = in.readInt();
		T[] ret = getArray(size);
		try {
			for (int i = 0; i < size; i++) {
				ret[i] = (T) in.readObject();
			}
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
		return ret;
	}
}
