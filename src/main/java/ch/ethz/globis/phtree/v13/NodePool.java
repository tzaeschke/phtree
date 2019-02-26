/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable. All rights reserved.
 *
 * This file is part of the PH-Tree project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.ethz.globis.phtree.v13;

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
