/*
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
package ch.ethz.globis.phtree.v16.bst;

import ch.ethz.globis.phtree.util.unsynced.LongArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectPool;
import ch.ethz.globis.phtree.v16.Node;
import ch.ethz.globis.phtree.v16.Node.BSTEntry;
import ch.ethz.globis.phtree.v16.PhTree16;

public class BSTPool {

    private final ObjectArrayPool<BSTEntry> POOL_ENTRY = ObjectArrayPool.create();
    private final LongArrayPool POOL_KEY = LongArrayPool.create();
	private final ObjectArrayPool<BSTreePage> POOL_NODES = ObjectArrayPool.create();
    private final ObjectPool<BSTreePage> POOL_NODE = ObjectPool.create(null);

    public static BSTPool create(){
    	return new BSTPool();
	}

    private BSTPool() {
    	// empty
    }
    
    /**
     * Create an array.
     * @param newSize size
     * @return New array.
     */
    public BSTEntry[] arrayCreateEntries(int newSize) {
    	return POOL_ENTRY.getArray(newSize);
	}

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public BSTEntry[] arrayExpand(BSTEntry[] oldA, int newSize) {
    	BSTEntry[] newA = POOL_ENTRY.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL_ENTRY.offer(oldA);
    	return newA;
	}

	
    /**
     * Create a new array.
     * @param newSize size
     * @return New array.
     */
    public long[] arrayCreateLong(int newSize) {
    	return POOL_KEY.getArray(newSize);
	}

	
    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public long[] arrayExpand(long[] oldA, int newSize) {
    	long[] newA = POOL_KEY.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL_KEY.offer(oldA);
    	return newA;
	}

	
	/**
     * Create an array.
     * @param newSize size
     * @return New array.
     */
    public BSTreePage[] arrayCreateNodes(int newSize) {
    	return POOL_NODES.getArray(newSize);
	}

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public BSTreePage[] arrayExpand(BSTreePage[] oldA, int newSize) {
    	BSTreePage[] newA = POOL_NODES.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	POOL_NODES.offer(oldA);
    	return newA;
	}

	
	public void reportFreeNode(BSTreePage p) {
		POOL_KEY.offer(p.getKeys());
		if (p.isLeaf()) {
			p.updateNeighborsRemove();
			POOL_ENTRY.offer(p.getValues());
		} else {
			POOL_NODES.offer(p.getSubPages());
		}
		p.nullify();
		POOL_NODE.offer(p);
	}

	public BSTreePage getNode(Node ind, BSTreePage parent, boolean isLeaf, BSTreePage leftPredecessor,
							  PhTree16<?> tree) {
		BSTreePage p = POOL_NODE.get();
		if (p != null) {
			p.init(ind, parent, isLeaf, leftPredecessor);
			return p;
		}
		return new BSTreePage(ind, parent, isLeaf, leftPredecessor, tree);
	}

}
