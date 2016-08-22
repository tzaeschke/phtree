/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht64kd;

import ch.ethz.globis.phtree.v11.nt.NodeTreeV11;

public class MaxKTree<T> implements MaxKTreeI {
	
	private final long[] dummy;
	private final NodeTreeV11<T> tree;
	private final int depth;
	
	private MaxKTree(int dims, int depth) {
		this.tree = NodeTreeV11.create(dims*depth);
		this.dummy = new long[dims*depth];
		if (getKeyBitWidth() > 62) {
			System.err.println("Warning: dims=" + getKeyBitWidth());
		}
		this.depth = depth;
	}
	
	public static <T> MaxKTree<T> create(int dims, int depth) {
		return new MaxKTree<>(dims, depth);
	}
	
	private long key2hcPos(long[] key) {
		long ret = 0;
		for (int i = 0; i < key.length; i++) {
			ret <<= depth;
			ret |= key[i];
		}
		return ret;
	}
	
	public T put(long[] key, T value) {
		return tree.put(key2hcPos(key), dummy, value);
	}
	
	public boolean insert(long ... key) {
		return tree.putB(key2hcPos(key), dummy);
	}
	
	public boolean contains(long ... key) {
		return tree.contains(key2hcPos(key), dummy);
	}
	
	public T get(long ... key) {
		return tree.get(key2hcPos(key), dummy);
	}
	
	public T remove(long ... key) {
		return tree.remove(key2hcPos(key));
	}
	
	public boolean delete(long[] key) {
		return tree.removeB(key2hcPos(key));
	}
	
	public String toStringTree() {
		return tree.toStringTree();
	}
	
	@Override
	public int size() {
		return tree.size();
	}
	
	@Override
	public int getKeyBitWidth() {
		return tree.getKeyBitWidth();
	}

	@Override
	public Object getRoot() {
		return tree.getRoot();
	}
}