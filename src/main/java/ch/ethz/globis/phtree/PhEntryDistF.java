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
public class PhEntryDistF<T> extends PhEntryF<T> {
	public static final Comparator<PhEntryDistF<?>> COMP =
			(PhEntryDistF<?> o1, PhEntryDistF<?> o2) -> {
			//We assume only normal positive numbers
			//We have to do it this way because the delta may exceed the 'int' value space
			double d = o1.dist - o2.dist;
			return d > 0 ? 1 : (d < 0 ? -1 : 0);
		};

	private double dist;

	public PhEntryDistF(double[] key, T value, double dist) {
		super(key, value);
		this.dist = dist;
	}

	/**
	 * Copy constructor.
	 * @param e entry to copy
	 * @param dist the distance value
	 */
	public PhEntryDistF(PhEntryF<T> e, double dist) {
		super(e);
		this.dist = dist;
	}

	/**
	 * Copy constructor.
	 * @param e entry to copy
	 */
	public PhEntryDistF(PhEntryDistF<T> e) {
		super(e);
		this.dist = e.dist();
	}

	public void setCopyKey(double[] key, T val, double dist) {
		System.arraycopy(key, 0, getKey(), 0, getKey().length);
		set(val, dist);
	}

	public void set(PhEntryF<T> e, double dist) {
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