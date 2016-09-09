/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.util.Comparator;

/**
 * An entry with additional distance, used for returning results from nearest neighbour queries.
 * 
 * @param <T> The value type
 */
public class PhEntryDist<T> extends PhEntry<T> {
	public static final Comparator<PhEntryDist<?>> COMP = 
			(PhEntryDist<?> o1, PhEntryDist<?> o2) -> {
			//We assume only normal positive numbers
			//We have to do it this way because the delta may exceed the 'int' value space	
			double d = o1.dist - o2.dist;
			return d > 0 ? 1 : (d < 0 ? -1 : 0);
		};

	private double dist;

	public PhEntryDist(long[] key, T value, double dist) {
		super(key, value);
		this.dist = dist;
	}

	/**
	 * Copy constructor.
	 * @param e entry to copy
	 * @param dist the distance value
	 */
	public PhEntryDist(PhEntry<T> e, double dist) {
		super(e);
		this.dist = dist;
	}

	/**
	 * Copy constructor.
	 * @param e entry to copy
	 */
	public PhEntryDist(PhEntryDist<T> e) {
		super(e);
		this.dist = e.dist();
	}

	public void setCopyKey(long[] key, T val, double dist) {
		System.arraycopy(key, 0, getKey(), 0, getKey().length);
		set(val, dist);
	}

	public void set(PhEntry<T> e, double dist) {
		super.setValue(e.getValue());
		System.arraycopy(e.getKey(), 0, getKey(), 0, getKey().length);
		this.dist = dist;
	}
	
	public void set(T val, double dist) {
		super.setValue(val);
		this.dist = dist;
	}

	public void clear() {
		dist = Double.MAX_VALUE;
	}
	
	public double dist() {
		return dist;
	}

	public void setDist(double dist) {
		this.dist = dist;
	}
	
	@Override
	public String toString() {
		return super.toString() + " dist=" + dist;
	}
}