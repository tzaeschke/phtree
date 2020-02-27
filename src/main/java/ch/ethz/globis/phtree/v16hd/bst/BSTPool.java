/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
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
package ch.ethz.globis.phtree.v16hd.bst;

import ch.ethz.globis.phtree.util.unsynced.LongArrayArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectPool;
import ch.ethz.globis.phtree.v16hd.Node;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.PhTree16HD;

public class BSTPool {

    private final ObjectArrayPool<BSTEntry> entryPool = ObjectArrayPool.create(n -> new BSTEntry[n]);
    private final LongArrayArrayPool longArrayPool = LongArrayArrayPool.create();
    private final ObjectArrayPool<BSTreePage> pageArrayPool = ObjectArrayPool.create(n -> new BSTreePage[n]);
    private final ObjectPool<BSTreePage> pagePool = ObjectPool.create(null);

    public static BSTPool create() {
        return new BSTPool();
    }

    private BSTPool() {
        // empty
    }

    /**
     * Create an array.
     *
     * @param newSize size
     * @return New array.
     */
    public BSTEntry[] arrayCreateEntries(int newSize) {
        return entryPool.arrayCreate(newSize);
    }

    /**
     * Resize an array.
     *
     * @param oldA    old array
     * @param newSize size
     * @return New array larger array.
     */
    public BSTEntry[] arrayExpand(BSTEntry[] oldA, int newSize) {
        return entryPool.arrayExpand(oldA, newSize);
    }


    /**
     * Create a new array.
     *
     * @param newSize size
     * @return New array.
     */
    public long[][] arrayCreateLong(int newSize) {
        return longArrayPool.arrayCreate(newSize);
    }


    /**
     * Resize an array.
     *
     * @param oldA    old array
     * @param newSize size
     * @return New array larger array.
     */
    public long[][] arrayExpand(long[][] oldA, int newSize) {
        return longArrayPool.arrayExpand(oldA, newSize);
    }


    /**
     * Create an array.
     *
     * @param newSize size
     * @return New array.
     */
    public BSTreePage[] arrayCreateNodes(int newSize) {
        return pageArrayPool.arrayCreate(newSize);
    }

    /**
     * Resize an array.
     *
     * @param oldA    old array
     * @param newSize size
     * @return New array larger array.
     */
    public BSTreePage[] arrayExpand(BSTreePage[] oldA, int newSize) {
        return pageArrayPool.arrayExpand(oldA, newSize);
    }

    public void reportFreeNode(BSTreePage p) {
        //TODO should be discards all the individual keys??
        longArrayPool.arrayDiscard(p.getKeys());
        if (p.isLeaf()) {
            p.updateNeighborsRemove();
            entryPool.arrayDiscard(p.getValues());
        } else {
            pageArrayPool.arrayDiscard(p.getSubPages());
        }
        p.nullify();
        pagePool.offer(p);
    }

    public BSTreePage getNode(Node ind, BSTreePage parent, boolean isLeaf, BSTreePage leftPredecessor,
                              PhTree16HD<?> tree) {
        BSTreePage p = pagePool.get();
        if (p != null) {
            p.init(ind, parent, isLeaf, leftPredecessor);
            return p;
        }
        return new BSTreePage(ind, parent, isLeaf, leftPredecessor, tree);
    }

}
