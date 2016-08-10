/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v11.nt;

/**
 * Manipulation methods and pool for long[].
 * 
 * @author ztilmann
 */
public class NtNodePool {
	
	private static final int MAX_POOL_SIZE = 100;
	private static final NtNode<?>[] POOL = new NtNode[MAX_POOL_SIZE];
	private static int poolSize;
	/** Nodes currently used outside the pool. */
	private static int activeNodes = 0;

	private NtNodePool() {
		// empty
	}

	static synchronized NtNode<?> getNode() {
		activeNodes++;
		if (poolSize == 0) {
			return NtNode.createEmptyNode();
		}
		return POOL[--poolSize];
	}

	public static synchronized void offer(NtNode<?> node) {
		activeNodes--;
		if (poolSize < POOL.length) {
			POOL[poolSize++] = node;
		}
	}
	
	public static int getActiveNodes() {
		return activeNodes;
	}
}
