/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v8;

import static ch.ethz.globis.phtree.PhTreeHelper.applyHcPos;
import static ch.ethz.globis.phtree.PhTreeHelper.getMaxConflictingBits;
import static ch.ethz.globis.phtree.PhTreeHelper.posInArray;

import org.zoodb.index.critbit.CritBit64.CBIterator;
import org.zoodb.index.critbit.CritBit64.Entry;

import ch.ethz.globis.phtree.v8.PhTree8.NodeEntry;

/**
 * Contains the tree modification operations: insert, remove and update key.
 *
 *  The put, delete and update operations we decomposed into several methods. These methods
 *  are split into two categories:
 *  - navigation methods: these are only going down the tree and don't perform any modifications
 *  - mutation methods: these methods receive some nodes as arguments and modify them
 *
 *  This split is useful for extending insert, remove and update operations for multi-threading.
 *
 * @param <T> value type
 */
public final class PhOperationsSimple<T> implements PhOperations<T> {

    private static final int NO_INSERT_REQUIRED = Integer.MAX_VALUE;

    private PhTree8<T> tree;

    PhOperationsSimple() {}

    public PhOperationsSimple(PhTree8<T> tree) {
        this.tree = tree;
    }

    @Override
    public Node<T> createNode(Node<T> original, int dim) {
    	return Node.createNode(original, dim);
    }

    @Override
	public Node<T> createNode(PhTree8<T> parent, int infixLen, int postLen, 
			int estimatedPostCount) {
		return Node.createNode(parent, infixLen, postLen, estimatedPostCount);
	}

    @Override
    public T put(long[] key, T value) {
        if (tree.getRoot() == null) {
            tree.insertRoot(key, value);
            return null;
        }
        return insert(key, value, tree.getRoot(), null, -1);
    }

    /*
            Insertion navigation methods.
     */

    protected T insert(long[] key, T value, Node<T> node, Node<T> parent, 
    		long posInParent) {
        //for a leaf node, the existence of a sub just indicates that the value may exist.
        long pos = posInArray(key, node.getPostLen());
        if (node.getPostLen() > 0) {
            if (node.isPostNI()) {
                return insertNI(key, value, node, pos);
            }
            Node<T> sub = node.getSubNode(pos, key.length);
            if (sub == null) {
                return insertNoSub(key, value, node, pos, parent, posInParent);
            } else {
                if (sub.hasInfixes() && conflictingInfix(sub, key)) {
                    //splitting may be required, the node has infixes
                    return insertSplit(key, value, sub, node, pos);
                }
                //splitting not necessary
                return insert(key, value, sub, node, pos);
            }
        } else {
            //is leaf
            return insertLeaf(key, value, node, pos);
        }
    }

    protected T insertNI(long[] key, T value, Node<T> node, long pos) {
        NodeEntry<T> e = node.getChildNI(pos);

        //check if we need to recurse
        if (e != null && e.node != null) {
            Node<T> sub = e.node;
            //sub found
            if (sub.hasInfixes() && conflictingInfix(sub, key)) {
                //splitting may be required, the node has infixes
                return insertSplit(key, value, sub, node, pos);
            }
            //splitting not necessary
            return insert(key, value, sub, node, pos);
        }

        //or apply the changes to the current node
        return performInsertionNI(tree, key, value, node, e, pos);
    }

    /*
        Insertion mutation methods.
     */
    protected final T performInsertionNI(PhTree8<T> tree, long[] key, T value, 
    		Node<T> node, NodeEntry<T> e, long pos) {
        if (e == null) {
            //nothing found at all
            //insert as postfix
            node.addPostPOB(pos, -1, key, value);
            tree.increaseNrEntries();
            return null;
        } else {
            //must be a post
            T prevVal = e.getValue();
            //maybe it's the same value that we want to add?
            if (node.postEquals(e.getKey(), key)) {
                //value exists
                e.setPost(e.getKey(), value);
                return prevVal;
            }

            //existing value
            //Create a new node that contains the existing and the new value.
            Node<T> sub = calcPostfixes(key, value, e.getKey(), prevVal, node.getPostLen());

            //replace value with new leaf
            node.setPostCount(node.getPostCount()-1);
            node.setSubCount(node.getSubCount()+1);
            e.setNode(sub);
            tree.increaseNrEntries();
            return null;
        }
    }


    /**
     * Splitting occurs if a node with an infix has to be split, because a new value to be inserted
     * requires a partially different infix.
     * @param key key to insert
     * @param value value to insert
     * @param node current subnode
     * @param parent current parent node
     * @param posInParent hc-pos in parent
     * @return The value
     */
    protected T insertSplit(long[] key, T value, Node<T> node, Node<T> parent,
                  long posInParent) {
        int DIM = key.length;

        //check if splitting is necessary
        long[] infix = new long[DIM];
        node.getInfixNoOverwrite(infix);
        int maxConflictingBits = tree.getConflictingInfixBits(key, infix, node);

        //do the splitting

        //newLocalLen
        int newLocalInfLen = node.getInfixLen() + 1 + node.getPostLen() - maxConflictingBits;
        int newSubInfLen = node.getInfixLen() - newLocalInfLen - 1;

        //What does 'splitting' mean:
        //The current node has an infix that is not (or only partially) compatible with the new key.
        //The new key should end up as post-fix for the current node. All current post-fixes
        //and sub-nodes are moved to a new sub-node. We know that there are more than two children
        //(posts+subs), otherwise the node should have been removed already.

        //How splitting works:
        //We insert a new node between the current and the parent node.
        //The parent is then updated with the new sub-node and the current node gets a shorter
        //infix.

        //We use the infixes as references, because they provide the correct location for the new sub
        long posOfNewSub = tree.posInArrayFromInfixes(node, newLocalInfLen);

        //create new middle node
        int newPostLen = maxConflictingBits-1;
        Node<T> newNode = createNode(tree, newLocalInfLen, newPostLen, 1);
        if (newLocalInfLen > 0) {
            newNode.writeInfix(infix);
        }

        int oldInfLen = node.getInfixLen();
        node.setInfixLen(newSubInfLen);

        //cut off existing prefixes in sub-node
        Bits.removeBits(node.ba, node.getBitPos_Infix(), (oldInfLen-newSubInfLen)*DIM);
        node.writeInfix(infix);
        //ensure that subNode has correct byte[] size
        node.ba = Bits.arrayTrim(node.ba, node.calcArraySizeTotalBits(
                node.getPostCount(), DIM));


        //insert the sub into new node
        newNode.addSubNode(posOfNewSub, node, DIM);

        //insert key into new node
        long pos = posInArray(key, newPostLen);
        newNode.addPost(pos, key, value);

        tree.increaseNrEntries();

        parent.replaceSub(posInParent, newNode, DIM);

        return null;
    }

    protected boolean conflictingInfix(Node<T> node, long[] key) {
        int DIM = tree.getDim();

        long[] infix = new long[DIM];
        node.getInfixNoOverwrite(infix);
        int maxConflictingBits = tree.getConflictingInfixBits(key, infix, node);
        return (maxConflictingBits != 0);
    }

    protected T insertNoSub(long[] key, T value, Node<T> node, 
    		long pos, Node<T> parent, long posInParent) {
        int DIM = key.length;

        //do we have a postfix at that position?
        int pob = node.getPostOffsetBits(pos, DIM);
        if (pob >= 0) {
            Node<T> sub;
            //maybe it's the same value that we want to add?
            if (node.postEqualsPOB(pob, pos, key)) {
                //value exists
                return node.updatePostValuePOB(pob, pos, key, DIM, value);
            }

            //existing value
            long[] prevKey = new long[DIM];
            T prevVal = node.getPostPOB(pob, pos, prevKey);
            //Create a new node that contains the existing and the new value.
            sub = calcPostfixes(key, value, prevKey, prevVal, node.getPostLen());

            node.removePostPOB(pos, pob, DIM);

            //insert a new leaf
            node.addSubNode(pos, sub, DIM);
        } else {
            //insert as postfix
            node.addPostPOB(pos, pob, key, value);
        }
        tree.increaseNrEntries();
        return null;
    }

    protected T insertLeaf(long[] key, T value, Node<T> node, long pos) {
        int DIM = key.length;

        int pob = node.getPostOffsetBits(pos, DIM);
        if (pob < 0) {
            node.addPostPOB(pos, pob, key, value);
            tree.increaseNrEntries();
            return null;
        }
        
        if (node.isPostNI()) {
        	//this can happen because getPostOffsetBits always return positive values for NI
        	if (!node.hasPostFix(pos, DIM)) {
                node.addPostPOB(pos, pob, key, value);
                tree.increaseNrEntries();
                return null;
        	}
        }
        
            //value exists
            return node.updatePostValuePOB(pob, pos, key, DIM, value);
        }

    private Node<T> calcPostfixes(long[] key1, T val1, long[] key2, T val2, int parentPostLen) {
        //determine length of infix
        int mcb = getMaxConflictingBits(key1, key2, parentPostLen);
        int infLen = parentPostLen - mcb;
        int postLen = mcb-1;
        Node<T> node = createNode(tree, infLen, postLen, 2);

        node.writeInfix(key1);
        long posSub1 = posInArray(key1, postLen);
        node.addPost(posSub1, key1, val1);
        long posSub2 = posInArray(key2, postLen);
        node.addPost(posSub2, key2, val2);
        return node;
    }

    @Override
    public T remove(long... key) {
        if (tree.getRoot() == null) {
            return null;
        }
        return delete(key, tree.getRoot(), null, PhTree8.UNKNOWN, null, null);
    }

    /*
        Delete navigation methods.

        Merging occurs if a node is not the root node and has after deletion less than two children.
     */
    protected T delete(long[] key, Node<T> node, Node<T> parent, long posInParent,
                     long[] newKey, int[] insertRequired) {
        //first, check infix!
        //second, check post/sub
        //third, remove post or follow sub.

        //check infix
        if (node.getInfixLen() > 0) {
            if (!checkInfixMatch(node, key)) {
                return null;
            }
        }

        //NI-node?
        if (node.isPostNI()) {
            T ret = deleteNI(key, node, parent, posInParent, newKey, insertRequired);
            if (insertRequired != null && insertRequired[0] < node.getPostLen()) {
                insert(newKey, ret, node, parent, posInParent);
                insertRequired[0] = NO_INSERT_REQUIRED;
            }
            return ret;
        }

        //check for sub
        final long pos = posInArray(key, node.getPostLen());
        Node<T> sub1 = node.getSubNode(pos, key.length);
        if (sub1 != null) {
            T ret = delete(key, sub1, node, pos, newKey, insertRequired);
            if (insertRequired != null && insertRequired[0] < node.getPostLen()) {
                insert(newKey, ret, node, parent, posInParent);
                insertRequired[0] = NO_INSERT_REQUIRED;
            }
            return ret;
        }

        return performDeletion(key, node, parent, posInParent, newKey, insertRequired, pos);
    }

    protected boolean checkInfixMatch(Node<T> node, long[] key) {
        long mask = (1l<<node.getInfixLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
        int shiftMask = (node.getPostLen()+1);
        //mask <<= shiftMask; //last bit is stored in bool-array
        mask = shiftMask==64 ? 0 : mask<<shiftMask;
        for (int i = 0; i < key.length; i++) {
            if (((key[i] ^ node.getInfix(i)) & mask) != 0) {
                //infix does not match
                return false;
            }
        }
        return true;
    }

    protected T deleteNI(long[] key, Node<T> node, Node<T> parent, long posInParent,
                         long[] newKey, int[] insertRequired) {
        final long pos = posInArray(key, node.getPostLen());
        NodeEntry<T> e = node.getChildNI(pos);
        if (e == null) {
            return null;
        }
        if (e.node != null) {
            T ret = delete(key, e.node, node, pos, newKey, insertRequired);
            if (insertRequired != null && insertRequired[0] < node.getPostLen()) {
                insert(newKey, ret, node, parent, posInParent);
                insertRequired[0] = NO_INSERT_REQUIRED;
            }
            return ret;
        }

        return performDeletionNI(tree, key, e, node, parent, posInParent, newKey, 
        		insertRequired, pos);
    }

    protected T performDeletionNI(PhTree8<T> tree, long[] key, NodeEntry<T> e, Node<T> node,
    		Node<T> parent, long posInParent, long[] newKey, int[] insertRequired, long pos) {
        int DIM = key.length;

        if (!node.postEquals(e.getKey(), key)) {
            //value does not exist
            return null;
        }

        //Check for update()
        if (newKey != null) {
            //long mask = (1l<<node.getPostLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
            long diff = 0;
            for (int i = 0; i < key.length; i++) {
                //write all differences to x, we just check x afterwards
                diff |= (key[i] ^ newKey[i]);
            }
            //replace
            int bitPosOfDiff = Long.SIZE-Long.numberOfLeadingZeros(diff);
            if (bitPosOfDiff <= node.getPostLen()) {
                //replace
                NodeEntry<T> ne = node.niGet(pos);
                T oldValue = ne.getValue();
                ne.setPost(newKey.clone(), oldValue);
                return oldValue;
            } else {
                insertRequired[0] = bitPosOfDiff;
            }
        }

        //okay we have something to delete
        tree.decreaseNrEntries();

        //check if merging is necessary (check children count || isRootNode)
        int nP = node.getPostCount();
        int nS = node.getSubCount();
        if (parent == null || nP + nS > 2) {
            //no merging required
            //value exists --> remove it
            node.removePostPOB(pos, -1, DIM);  //do not call-NI directly, we may have to deconstruct
            return e.getValue();
        }

        //The following code is never used because NI implies nP+nS > 50

        //okay, at his point we have a post that matches and (since it matches) we need to remove
        //the local node because it contains at most one other entry and it is not the root node.
        tree.decreaseNrNodes();

        T oldValue = e.getValue();

        //locate the other entry
        CBIterator<NodeEntry<T>> iter = node.niIterator();
        Entry<NodeEntry<T>> ie = iter.nextEntry();
        if (ie.key() == pos) {
            //pos2 is the entry to be deleted, find the other entry for pos2
            ie = iter.nextEntry();
        }

        e = ie.value();
        if (e.getKey() != null) {
            //this is also a post
            long[] newPost = e.getKey();
            node.getInfixNoOverwrite(newPost);
            T val = e.getValue();
            applyHcPos(ie.key(), node.getPostLen(), newPost);
            parent.removeSub(posInParent, DIM);
            node.setRemoved(true);
            parent.addPost(posInParent, newPost, val);
            return oldValue;
        }

        //connect sub to parent
        Node<T> sub2 = e.node;

        performDeletionWithSub(node, parent, posInParent, sub2, ie.key(), DIM);

        return oldValue;
    }

    T performDeletion(long[] key, Node<T> node, Node<T> parent, long posInParent,
                      long[] newKey, int[] insertRequired, long pos) {

        int DIM = key.length;

        //check matching post
        int pob = node.getPostOffsetBits(pos, DIM);
        if (pob < 0) {
            return null;
        }

        if (!node.postEqualsPOB(pob, pos, key)) {
            //value does not exist
            return null;
        }

        //Check for update()
        if (newKey != null) {
            //long mask = (1l<<node.getPostLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
            long diff = 0;
            for (int i = 0; i < key.length; i++) {
                //write all differences to x, we just check x afterwards
                diff |= (key[i] ^ newKey[i]);
            }
            //replace
            int bitPosOfDiff = Long.SIZE-Long.numberOfLeadingZeros(diff);
            if (bitPosOfDiff <= node.getPostLen()) {
                //replace
                T oldValue = node.getPostValuePOB(pob, pos, DIM);
                node.replacePost(pob, pos, newKey, oldValue);
                return oldValue;
            } else {
                insertRequired[0] = bitPosOfDiff;
            }
        }

        //okay we have something to delete
        tree.decreaseNrEntries();

        //check if merging is necessary (check children count || isRootNode)
        int nP = node.getPostCount();
        int nS = node.getSubCount();
        if (parent == null || nP + nS > 2) {
            //no merging required
            //value exists --> remove it
            return node.removePostPOB(pos, pob, DIM);
        }

        //okay, at his point we have a post that matches and (since it matches) we need to remove
        //the local node because it contains at most one other entry and it is not the root node.
        tree.decreaseNrNodes();

        T oldValue = node.getPostValue(pos, DIM);

        //locate the other entry
        NodeIteratorFull<T> iter = new NodeIteratorFull<T>(node, DIM, null);
        long pos2 = iter.getCurrentPos();
        if (pos2 == pos) {
            //pos2 is the entry to be deleted, find the other entry for pos2
            iter.increment();
            pos2 = iter.getCurrentPos();
        }
        boolean isSubNode = iter.isNextSub();
        int posSubLHC = iter.getPosSubLHC();

        if (!isSubNode) {
            //this is also a post
            long[] newPost = new long[DIM];
            node.getInfixNoOverwrite(newPost);
            T val = node.getPost(pos2, newPost);
            applyHcPos(pos2, node.getPostLen(), newPost);
            parent.removeSub(posInParent, DIM);
            parent.addPost(posInParent, newPost, val);
            node.setRemoved(true);
            return oldValue;
        }

        //connect sub to parent
        Node<T> sub2 = getSubNode(node, pos2, posSubLHC);

        performDeletionWithSub(node, parent, posInParent, sub2, pos2, DIM);

        return oldValue;
    }

    protected void performDeletionWithSub(Node<T> node, Node<T> parent, 
    		long posInParent, Node<T> sub2, long posSub, int DIM) {
        // build new infix
        long[] infix = new long[DIM];
        node.getInfixNoOverwrite(infix);

        //update infix
        sub2.adjustInfix(infix, node.getInfixLen(), node.getPostLen(), posSub);
        
        //update parent, the position is the same
        parent.replaceSub(posInParent, sub2, DIM);
        node.setRemoved(true);
    }

    protected Node<T> getSubNode(Node<T> node, long pos2, int posSubLHC) {
        return node.getSubNodeWithPos(pos2, posSubLHC);
    }

    @Override
    public T update(long[] oldKey, long[] newKey) {
        if (tree.getRoot() == null) {
            return null;
        }
        final int[] insertRequired = new int[]{NO_INSERT_REQUIRED};
        T v = delete(oldKey, tree.getRoot(), null, PhTree8.UNKNOWN, newKey, insertRequired);
        if (insertRequired[0] != NO_INSERT_REQUIRED) {
            //this is only 'true' if the value existed AND if oldKey was not replaced with newKey,
            //because they wouldn't be in the same location.
            put(newKey, v);
        }
        return v;
    }

    protected Node<T> copyNodeAndReplaceInParent(Node<T> node, Node<T> parent, long posInParent) {
        if (parent != null) {
            node = createNode(node, tree.getDim());
            parent.replaceSub(posInParent, node, tree.getDim());
        }
        return node;
    }

    protected Node<T> copyNodeAndReplaceInParentNI(Node<T> node, long childPos) {
        NodeEntry<T> e = node.getChildNI(childPos);
        if (e != null) {
            if (e.node != null) {
                node.niPut(childPos, createNode(e.node, tree.getDim()));
            } else {
                node.niPut(childPos, e.getKey(), e.getValue());
            }
        }
        return node;
    }

    public void setTree(PhTree8<T> tree) {
        this.tree = tree;
    }
}
