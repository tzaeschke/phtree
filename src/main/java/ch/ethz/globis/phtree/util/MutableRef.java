/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2022-2023 Tilmann Zäschke.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;

/**
 * A variable reference.
 * 
 * @author ztilmann
 *
 */
public class MutableRef<T> {
	private T ref;

	public MutableRef() {
		this.ref = null;
	}

	public MutableRef(T ref) {
		this.ref = ref;
	}

	public MutableRef<T> set(T value) {
		this.ref = value;
		return this;
	}

	public T get() {
		return ref;
	}
}
