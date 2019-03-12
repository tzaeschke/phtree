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

    private final ObjectArrayPool<BSTEntry> entryArrayPool = ObjectArrayPool.create(n -> new BSTEntry[n]);
    private final LongArrayPool longArrayPool = LongArrayPool.create();
	private final ObjectArrayPool<BSTreePage> pageArrayPool = ObjectArrayPool.create(n -> new BSTreePage[n]);
	private final ObjectPool<BSTreePage> pagePool = ObjectPool.create(null);
	private final ObjectPool<BSTEntry> poolEntries = ObjectPool.create(BSTEntry::new);

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
    	return entryArrayPool.getArray(newSize);
	}

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public BSTEntry[] arrayExpand(BSTEntry[] oldA, int newSize) {
    	BSTEntry[] newA = entryArrayPool.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	entryArrayPool.offer(oldA);
    	return newA;
	}


    /**
     * Create a new array.
     * @param newSize size
     * @return New array.
     */
    public long[] arrayCreateLong(int newSize) {
    	return longArrayPool.getArray(newSize);
	}

	
    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public long[] arrayExpand(long[] oldA, int newSize) {
    	long[] newA = longArrayPool.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	longArrayPool.offer(oldA);
    	return newA;
	}

	
	/**
     * Create an array.
     * @param newSize size
     * @return New array.
     */
    public BSTreePage[] arrayCreateNodes(int newSize) {
    	return pageArrayPool.getArray(newSize);
	}

    /**
     * Resize an array.
     * @param oldA old array
     * @param newSize size
     * @return New array larger array.
     */
    public BSTreePage[] arrayExpand(BSTreePage[] oldA, int newSize) {
    	BSTreePage[] newA = pageArrayPool.getArray(newSize);
    	System.arraycopy(oldA, 0, newA, 0, oldA.length);
    	pageArrayPool.offer(oldA);
    	return newA;
	}

	
	public void reportFreeNode(BSTreePage p) {
		longArrayPool.offer(p.getKeys());
		if (p.isLeaf()) {
			p.updateNeighborsRemove();
			entryArrayPool.offer(p.getValues());
		} else {
			pageArrayPool.offer(p.getSubPages());
		}
		p.nullify();
		pagePool.offer(p);
	}

	public BSTreePage getNode(Node ind, BSTreePage parent, boolean isLeaf, BSTreePage leftPredecessor,
							  PhTree16<?> tree) {
		BSTreePage p = pagePool.get();
		if (p != null) {
			p.init(ind, parent, isLeaf, leftPredecessor);
			return p;
		}
		return new BSTreePage(ind, parent, isLeaf, leftPredecessor, tree);
	}

	public BSTEntry getEntry() {
    	return poolEntries.get();
	}

	public void offerEntry(BSTEntry entry) {
    	entry.set(0, null, null);
    	poolEntries.offer(entry);
	}
}
