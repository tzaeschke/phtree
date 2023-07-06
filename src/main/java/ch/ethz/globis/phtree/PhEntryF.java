/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.util.Arrays;

/**
 * Entry class for Double entries.
 *
 * @param <T> value type of the entries
 */
public class PhEntryF<T> {
	private double[] key;
	private T value;
	public PhEntryF(double[] key, T value) {
		this.key = key;
		this.value = value;
	}

	public PhEntryF(PhEntryF<T> e) {
		this.key = Arrays.copyOf(e.getKey(), e.getKey().length);
		this.value = e.getValue();
	}
	
	public double[] getKey() {
		return key;
	}
	
	public T getValue() {
		return value;
	}

	protected void set(double[] key, T value) {
		this.key = key;
		this.value = value;
	}
	
	protected void setKeyRef(double[] key) {
		this.key = key;
	}

    @SuppressWarnings("unchecked")
	@Override
    public boolean equals(Object o) {
        if (this == o) {
        	return true;
        }
        if (!(o instanceof PhEntryF)) {
        	return false;
        }

        PhEntryF<T> pvEntry = (PhEntryF<T>) o;

        if (!Arrays.equals(key, pvEntry.key)) {
        	return false;
        }
        if (value != null ? !value.equals(pvEntry.value) : pvEntry.value != null) {
        	return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = key != null ? Arrays.hashCode(key) : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }

	public void setValue(T value) {
		this.value = value;
	}
	
	@Override
	public String toString() {
		return Arrays.toString(key) + " -> " + value;
	}
}