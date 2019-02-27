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


/**
 * Pool for Object[] instances.
 *
 * NL is the no-locking (unsycnhronized) version.
 *
 * @author ztilmann
 *
 * @param <T> Reference type
 */
public class ObjectArrayPool<T> {

	private static final Object[] EMPTY_REF_ARRAY = {};
    private final ArrayPool POOL =
    		new ArrayPool(PhTreeHelper.ARRAY_POOLING_MAX_ARRAY_SIZE,
    				PhTreeHelper.ARRAY_POOLING_POOL_SIZE);

    private ObjectArrayPool() {
    	// empty
    }
    
    private static class ArrayPool {
    	private final int maxArraySize;
    	private final int maxArrayCount;
    	Object[][][] pool;
    	int[] poolSize;
    	ArrayPool(int maxArraySize, int maxArrayCount) {
			this.maxArraySize = maxArraySize;
			this.maxArrayCount = maxArrayCount;
			this.pool = new Object[maxArraySize+1][maxArrayCount][];
			this.poolSize = new int[maxArraySize+1];
		}
    	
    	Object[] getArray(int size) {
    		if (size == 0) {
    			return EMPTY_REF_ARRAY;
    		}
    		if (size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return new Object[size];
    		}
    		synchronized (this) {
	    		int ps = poolSize[size];
	    		if (ps > 0) {
	    			poolSize[size]--;
	    			Object[] ret = pool[size][ps-1];
	    			pool[size][ps-1] = null;
	    			return ret;
	    		}
    		}
    		return new Object[size];
    	}
    	
    	void offer(Object[] a) {
    		int size = a.length;
    		if (size == 0 || size > maxArraySize || !PhTreeHelper.ARRAY_POOLING) {
    			return;
    		}
    		synchronized (this) {
    			int ps = poolSize[size]; 
    			if (ps < maxArrayCount) {
	    			Arrays.fill(a, null);
    				pool[size][ps] = a;
    				poolSize[size]++;
    			}
    		}
    	}
    }

    public static <T> ObjectArrayPool<T> create() {
    	return new ObjectArrayPool<>();
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
    	POOL.offer(oldA);
    	return newA;
    }
    
    /**
     * Resize an array to exactly the given size.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
	@SuppressWarnings("unchecked")
	public T[] arrayExpandPrecise(T[] oldA, int newSize) {
		T[] newA = (T[]) POOL.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL.offer(oldA);
    	return newA;
	}

	/**
     * Create an array.
     * @param size size
     * @return a new array
     */
    @SuppressWarnings("unchecked")
	public T[] arrayCreate(int size) {
    	return (T[]) POOL.getArray(calcArraySize(size));
    }
    
    /**
     * Ensure capacity of an array. Expands the array if required.
     * @param oldA old array
     * @param requiredSize size
     * @return Same array or expanded array.
     */
    @Deprecated
    public T[] arrayEnsureSize(T[] oldA, int requiredSize) {
    	if (isCapacitySufficient(oldA, requiredSize)) {
    		return oldA;
    	}
    	return arrayExpand(oldA, requiredSize);
    }
    
    /**
     * Discards oldA and returns newA.
     * @param oldA old array
     * @param newA new array
     * @return newA.
     */
    public T[] arrayReplace(T[] oldA, T[] newA) {
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
    public T[] arrayClone(T[] oldA) {
    	T[] newA = arrayCreate(oldA.length);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	return newA;
    }
    
    private boolean isCapacitySufficient(T[] a, int requiredSize) {
    	return a.length >= requiredSize;
    }
    
    @Deprecated
    @SuppressWarnings("unchecked")
	public T[] arrayTrim(T[] oldA, int requiredSize) {
    	int reqSize = calcArraySize(requiredSize);
    	if (oldA.length == reqSize) {
    		return oldA;
    	}
    	T[] newA = (T[]) POOL.getArray(reqSize);
     	System.arraycopy(oldA, 0, newA, 0, reqSize);
     	POOL.offer(oldA);
    	return newA;
    }

    @Deprecated
	public void insertAtPos(T[] values, int pos, T value) {
		copyRight(values, pos, values, pos+1, values.length-pos-1);
		values[pos] = value;
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
	
	@Deprecated
	public void removeAtPos(T[] values, int pos) {
		if (pos < values.length-1) {
			copyLeft(values, pos+1, values, pos, values.length-pos-1);
		}
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
	@SuppressWarnings("unchecked")
	public T[] removeSpaceAtPos(T[] values, int pos, int requiredSize) {
    	int reqSize = calcArraySize(requiredSize);
    	T[] dst = values;
		if (reqSize < values.length) {
			dst = (T[]) POOL.getArray(reqSize);
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
		T[] ret = (T[]) POOL.getArray(size);
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
