/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v11hd;

import ch.ethz.globis.phtree.PhTreeHelper;

/**
 * Reference pooling and management for Node instances.
 * 
 * @author ztilmann
 */
public class NodePool {
	
	private static final Node[] POOL = 
			new Node[PhTreeHelper.MAX_OBJECT_POOL_SIZE];
	private static int poolSize;
	/** Nodes currently used outside the pool. */
	private static int activeNodes = 0;

	private NodePool() {
		// empty
	}

	static synchronized Node getNode() {
		activeNodes++;
		if (poolSize == 0) {
			return Node.createEmpty();
		}
		return POOL[--poolSize];
	}

	static synchronized void offer(Node node) {
		activeNodes--;
		if (poolSize < POOL.length) {
			POOL[poolSize++] = node;
		}
	}
	
	public static int getActiveNodes() {
		return activeNodes;
	}
}
