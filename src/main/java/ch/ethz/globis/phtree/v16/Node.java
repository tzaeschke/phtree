/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16;

import static ch.ethz.globis.phtree.PhTreeHelper.posInArray;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Function;

import ch.ethz.globis.pht64kd.MaxKTreeI.NtEntry;
import ch.ethz.globis.pht64kd.MaxKTreeI.PhIterator64;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.BitsLong;
import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.v16.BSTHandler.BSTEntry;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorMask;
import ch.ethz.globis.phtree.v16.bst.BSTIteratorMinMax;
import ch.ethz.globis.phtree.v16.bst.BSTPool;
import ch.ethz.globis.phtree.v16.bst.BSTreePage;
import ch.ethz.globis.phtree.v16.bst.LLEntry;


/**
 * Node of the PH-tree.
 * 
 * @author ztilmann
 */
public class Node {

	public byte maxLeafN;// = 100;//10;//340;
	/** Max number of keys in inner page (there can be max+1 page-refs) */
	public byte maxInnerN;// = 100;//11;//509;

	private int entryCnt = 0;

	/**
	 * postLenStored: Stored bits, including the hc address.
	 * postLenClassic: The number of postFix bits
	 * Rule: postLenClassic + 1 = postLenStored.  
	 */
	private byte postLenStored = 0;
	private byte infixLenStored = 0; //prefix size

	//Nested tree index
	private BSTreePage root;

	
    private Node() {
		// For ZooDB only
	}

	static Node createEmpty() {
		return new Node();
	}

	private void initNode(int infixLenClassic, int postLenClassic, int dims) {
		this.infixLenStored = (byte) (infixLenClassic + 1);
		this.postLenStored = (byte) (postLenClassic + 1);
		this.entryCnt = 0;
		switch (dims) {
		case 1: maxLeafN = 2; maxInnerN = 2; break;
		case 2: maxLeafN = 4; maxInnerN = 2; break;
		case 3: maxLeafN = 8; maxInnerN = 2; break;
		case 4: maxLeafN = 16; maxInnerN = 2; break;
		case 5: maxLeafN = 16; maxInnerN = 2+1; break;
		case 6: maxLeafN = 16; maxInnerN = 4+1; break;
		case 7: maxLeafN = 16; maxInnerN = 8+1; break;
		case 8: maxLeafN = 16; maxInnerN = 16+1; break;
		case 9: maxLeafN = 32; maxInnerN = 16+1; break;
		case 10: maxLeafN = 32; maxInnerN = 32+1; break;
		case 11: maxLeafN = 32; maxInnerN = 64+1; break;
		case 12: maxLeafN = 64; maxInnerN = 64+1; break;
		default: maxLeafN = 100; maxInnerN = 100; break;
		}
		this.root = createRoot();
	}

	public static Node createNode(int dims, int infixLenClassic, int postLenClassic) {
		Node n = NodePool.getNode();
		n.initNode(infixLenClassic, postLenClassic, dims);
		return n;
	}

	<T> PhEntry<T> createNodeEntry(long[] key, T value) {
		return new PhEntry<>(key, value);
	}
	
	void discardNode() {
		entryCnt = 0;
		getRoot().clear();
		nEntries = 0;
		BSTPool.reportFreePage(root);
		root = null;
		NodePool.offer(this);
	}
	

	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param posInNode
	 * @param pos The position of the node when mapped to a vector.
	 * @return The sub node or null.
	 */
	Object doInsertIfMatching(long[] keyToMatch, Object newValueToInsert, PhTree16<?> tree) {
		long hcPos = posInArray(keyToMatch, getPostLen());

		//ntPut will also increase the node-entry count
		Object v = ntPut(hcPos, keyToMatch, newValueToInsert);
		//null means: Did not exist, or we had to do a split...
		if (v == null) {
			tree.increaseNrEntries();
		}
		return v;
	}

	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param keyToMatch The key of the entry
	 * @param getOnly True if we only get the value. False if we want to delete it.
	 * @param parent
	 * @param newKey
	 * @param insertRequired
	 * @param tree
	 * @return The sub node or null.
	 */
	Object doIfMatching(long[] keyToMatch, boolean getOnly, Node parent,
			long[] newKey, int[] insertRequired, PhTree16<?> tree) {
		
		long hcPos = posInArray(keyToMatch, getPostLen());
		
		if (getOnly) {
			return ntGetEntryIfMatches(hcPos, keyToMatch);
		}			
		Object v = ntRemoveEntry(hcPos, keyToMatch, newKey, insertRequired);
		if (v != null && !(v instanceof Node)) {
			//Found and removed entry.
			tree.decreaseNrEntries();
			if (getEntryCount() == 1) {
				mergeIntoParentNt(keyToMatch, parent);
			}
		}
		return v;
	}
	
	public long calcPostfixMask() {
		return ~((-1L)<<getPostLen());
	}
	
	public long calcInfixMask(int subPostLen) {
		long mask = ~((-1L)<<(getPostLen()-subPostLen-1));
		return mask << (subPostLen+1);
	}
	

    /**
     * 
     * @param key1 key 1
     * @param val1 value 1
     * @param key2 key 2
     * @param val2 value 2
     * @param mcb most conflicting bit
     * @return A new node or 'null' if there are no conflicting bits
     */
    public Node createNode(long[] key1, Object val1, long[] key2, Object val2,
    		int mcb) {
        //determine length of infix
        int newLocalInfLen = getPostLen() - mcb;
        int newPostLen = mcb-1;
        Node newNode = createNode(key1.length, newLocalInfLen, newPostLen);

        long posSub1 = posInArray(key1, newPostLen);
        long posSub2 = posInArray(key2, newPostLen);
        if (posSub1 < posSub2) {
        	newNode.writeEntry(0, posSub1, key1, val1);
        	newNode.writeEntry(1, posSub2, key2, val2);
        } else {
        	newNode.writeEntry(0, posSub2, key2, val2);
        	newNode.writeEntry(1, posSub1, key1, val1);
        }
//        newNode.incEntryCount();
//        newNode.incEntryCount();
        return newNode;
    }

    /**
     * @param v1 key 1
     * @param v2 key 2
     * @param mask bits to consider (1) and to ignore (0)
     * @return the position of the most significant conflicting bit (starting with 1) or
     * 0 in case of no conflicts.
     */
    public static int calcConflictingBits(long[] v1, long[] v2, long mask) {
		//long mask = (1l<<node.getPostLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
     	//write all differences to diff, we just check diff afterwards
		long diff = 0;
		for (int i = 0; i < v1.length; i++) {
			diff |= (v1[i] ^ v2[i]);
		}
    	return Long.SIZE-Long.numberOfLeadingZeros(diff & mask);
    }
    
    
	private void mergeIntoParentNt(long[] key, Node parent) {
		//check if merging is necessary (check children count || isRootNode)
		if (parent == null || getEntryCount() > 2) {
			//no merging required
			//value exists --> remove it
			return;
		}

		//TODO this is apparently never called (ntIterator() would fail), why???. 
		
		//okay, at his point we have a post that matches and (since it matches) we need to remove
		//the local node because it contains at most one other entry and it is not the root node.
		PhIterator64<Object> iter = ntIterator();
		NtEntry<Object> nte = iter.nextEntryReuse();

		long posInParent = PhTreeHelper.posInArray(key, parent.getPostLen());
		if (nte.getValue() instanceof Node) {
			long[] newPost = nte.getKdKey();
			//connect sub to parent
			Node sub2 = (Node) nte.getValue();
			int newInfixLen = getInfixLen() + 1 + sub2.getInfixLen();
			sub2.setInfixLen(newInfixLen);

			//update parent, the position is the same
			//we use newPost as Infix
			parent.replaceEntryWithSub(posInParent, newPost, sub2);
		} else {
			//this is also a post
			parent.replaceSubWithPost(posInParent, nte.getKdKey(), nte.getValue());
		}

		//TODO return old key/BSTEntry to pool
		
		discardNode();
	}


	/**
	 * @param posInNode
	 * @param pos The position of the node when mapped to a vector.
	 * @return The sub node or null.
	 */
	BSTEntry getEntry(long hcPos) {
		return ntGetEntry(hcPos);
	}


	/**
	 * Writes a complete entry.
	 * This should only be used for new nodes.
	 * 
	 * @param pin
	 * @param hcPos
	 * @param newKey
	 * @param value
	 * @param newSubInfixLen -infix len for sub-nodes. This is ignored for post-fixes.
	 */
	private void writeEntry(int pin, long hcPos, long[] newKey, Object value) {
		if (value instanceof Node) {
			Node node = (Node) value;
			int newSubInfixLen = postLenStored() - node.postLenStored() - 1;  
			node.setInfixLen(newSubInfixLen);
		} 
		ntPut(hcPos, newKey, value);
		return;
	}

	private void replaceEntryWithSub(long hcPos, long[] infix, Node newSub) {
		ntReplaceEntry(hcPos, infix, newSub);
		return;
	}
	
	boolean hasSubInfixNI(long[] infix) {
		//TODO reenable? But we also need to write it...
		//return (infix[infix.length-1] & 1L) != 0;
		return true;
	}
	
	/**
	 * Replace a sub-node with a postfix, for example if the current sub-node is removed, 
	 * it may have to be replaced with a post-fix.
	 */
	void replaceSubWithPost(long hcPos, long[] key, Object value) {
		ntReplaceEntry(hcPos, key, value);
		return;
	}

	void ntReplaceEntry(long hcPos, long[] kdKey, Object value) {
		//We use 'null' as parameter to indicate that we want replacement, rather than splitting,
		//if the value exists.
		BSTHandler.replaceEntry(this, hcPos, kdKey, value);
	}
	
	/**
	 * General contract:
	 * Returning a value or NULL means: Value was replaced, no change in counters
	 * Returning a Node means: Traversal not finished, no change in counters
	 * Returning null means: Insert successful, please update global entry counter
	 * 
	 * Node entry counters are updated internally by the operation
	 * Node-counting is done by the NodePool.
	 * 
	 * @param hcPos
	 * @param dims
	 * @return
	 */
	Object ntPut(long hcPos, long[] kdKey, Object value) {
		return BSTHandler.addEntry(this, hcPos, kdKey, value, this);
	}
	
	/**
	 * General contract:
	 * Returning a value or NULL means: Value was removed, please update global entry counter
	 * Returning a Node means: Traversal not finished, no change in counters
	 * Returning null means: Entry not found, no change in counters
	 * 
	 * Node entry counters are updated internally by the operation
	 * Node-counting is done by the NodePool.
	 * 
	 * @param hcPos
	 * @param dims
	 * @return
	 */
	Object ntRemoveAnything(long hcPos, int dims) {
    	return BSTHandler.removeEntry(this, hcPos, null, null, null, null);
	}

	Object ntRemoveEntry(long hcPos, long[] key, long[] newKey, int[] insertRequired) {
    	return BSTHandler.removeEntry(this, hcPos, key, newKey, insertRequired, this);
	}

	BSTEntry ntGetEntry(long hcPos) {
		return BSTHandler.getEntry(this, hcPos, null, null);
	}

	Object ntGetEntryIfMatches(long hcPos, long[] keyToMatch) {
		BSTEntry e =  BSTHandler.getEntry(this, hcPos, keyToMatch, this);
		return e != null ? e.getValue() : null;
	}

	int ntGetSize() {
		return getEntryCount();
	}
	

	/**
	 * 
	 * @param hcPos
	 * @param pin position in node: ==hcPos for AHC or pos in array for LHC
	 * @param key
	 */
	void addPostPIN(long hcPos, int pin, long[] key, Object value) {
		ntPut(hcPos, key, value);
		return;
	}


	private static int N_GOOD = 0;
	private static int N = 0;
	
	boolean checkInfixNt(int infixLen, long[] keyToTest, long[] rangeMin, long[] rangeMax) {
		//first check if node-prefix allows sub-node to contain any useful values

		if (PhTreeHelper.DEBUG) {
			N_GOOD++;
			//Ensure that we never enter this method if the node cannot possibly contain a match.
			long maskClean = mask1100(getPostLen());
			for (int dim = 0; dim < keyToTest.length; dim++) {
				if ((keyToTest[dim] & maskClean) > rangeMax[dim] || 
						(keyToTest[dim] | ~maskClean) < rangeMin[dim]) {
					if (getPostLen() < 63) {
						System.out.println("N-CAAI: " + ++N + " / " + N_GOOD);
						throw new IllegalStateException();
					}
					//ignore, this happens with negative values.
					//return false;
				}
			}
		}
		
		if (!hasSubInfixNI(keyToTest) || infixLen == 0) {
			return true;
		}

		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????
		//TODO Why not abort with infixlen == 0? Or use applyHcPos if infixeLenNew == n1 ????

		//first, clean trailing bits
		//Mask for comparing the tempVal with the ranges, except for bit that have not been
		//extracted yet.
		long compMask = mask1100(postLenStored() - infixLen);
		for (int dim = 0; dim < keyToTest.length; dim++) {
			long in = keyToTest[dim] & compMask;
			if (in > rangeMax[dim] || in < (rangeMin[dim]&compMask)) {
				return false;
			}
		}

		return true;
	}

	public static long mask1100(int zeroBits) {
		return zeroBits == 64 ? 0 : ((-1L) << zeroBits);
	}
	
	/**
	 * Get post-fix.
	 * @param hcPos
	 * @param in The entry to check. 
	 * @param range After the method call, this contains the postfix if the postfix matches the
	 * range. Otherwise it contains only part of the postfix.
	 * @return NodeEntry if the postfix matches the range, otherwise null.
	 */
	@SuppressWarnings("unchecked")
	<T> boolean checkAndGetEntryNt(BSTEntry candidate, PhEntry<T> result, long[] rangeMin, long[] rangeMax) {
		Object value = candidate.getValue();
		if (value instanceof Node) {
			Node sub = (Node) value;
			if (!checkInfixNt(sub.getInfixLen(), candidate.getKdKey(), rangeMin, rangeMax)) {
				return false;
			}
			//TODO we only need to set the key..
			//TODO do we need to set anything at all????
			result.setKeyInternal(candidate.getKdKey());
			result.setNodeInternal(sub);
			return true;
		} else if (BitsLong.checkRange(candidate.getKdKey(), rangeMin, rangeMax)) {
			result.setKeyInternal(candidate.getKdKey());
			result.setValueInternal((T) value);
			return true;
		} else {
			return false;
		}
	}


	/**
	 * @return entry counter
	 */
	public int getEntryCount() {
		return entryCnt;
	}


	public void decEntryCount() {
		--entryCnt;
	}


	public void incEntryCount() {
		++entryCnt;
	}


	int getInfixLen() {
		return infixLenStored() - 1;
	}
	
	int infixLenStored() {
		return infixLenStored;
	}

	void setInfixLen(int newInfLen) {
		infixLenStored = (byte) (newInfLen + 1);
	}

	public int getPostLen() {
		return postLenStored - 1;
	}

	public int postLenStored() {
		return postLenStored;
	}

    PhIterator64<Object> ntIterator() {
		//TODO
		//TODO
		//TODO
		//TODO
		throw new UnsupportedOperationException();
//      return new NtIteratorMinMax<>(dims).reset(ind, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    BSTIteratorMask ntIteratorWithMask(long maskLower, long maskUpper) {
    	return new BSTIteratorMask().reset(getRoot(), maskLower, maskUpper);
	}
    
    // ************************************
    // ************************************
    // BSTree
    // ************************************
    // ************************************
    
    @Deprecated
	public
	static final BSTEntry NULL = new BSTEntry(null, null);
	
    public static int statNLeaves = 0;
	public static int statNInner = 0;

	private int nEntries = 0;
	
	private BSTreePage createRoot() {

		//bootstrap index
		return createPage(null, true);
	}

	public final BSTEntry put(long key, BSTEntry value) {
		return put(key, value, null);
	}
	
	public final BSTEntry put(long key, BSTEntry value, BiFunction<BSTEntry, BSTEntry, Object> collisionHandler) {
		BSTEntry val = value == null ? NULL : value;
		//Depth as log(nEntries) 
		BSTreePage page = getRoot();
		Object o = page;
		if (page.isLeaf()) {
			o = page.put(key, value, null, -1, collisionHandler, this);
			if (o instanceof BSTreePage) {
				root = createPage(null, false);
    			BSTreePage newPage = (BSTreePage) o;
				root = new BSTreePage(this, null, page, newPage);
				o = null;
			}
		} else {
			while (o instanceof BSTreePage && !((BSTreePage)o).isLeaf()) {
				o = ((BSTreePage)o).put(key, val, collisionHandler, this);
			}
		}
		if (o == null) {
			//did not exist
			nEntries++;
		}
		return (BSTEntry)o;
	}

	/**
	 * @param key The key to remove
	 * @return the previous value
	 * @throws NoSuchElementException if key is not found
	 */
	public BSTEntry remove(long key) {
		return remove(key, null);
	}
	
	/**
	 * @param key The key to remove
	 * @param predicateRemove Whether to remove the entry. This methods will always return T, even if
	 * the 'predicate' returns false. 
	 * @return the previous value
	 * @throws NoSuchElementException if key is not found
	 */
	public BSTEntry remove(long key, Function<BSTEntry, REMOVE_OP> predicateRemove) {
		BSTreePage page = getRoot();
		BSTEntry result = null;
		if (page != null) {
	        if (page.isLeaf()) {
	        	result = page.remove(key, predicateRemove, this);
	        } else {
	        	result = page.findAndRemove(key, predicateRemove, this);
	        }
		}

		return result == NULL ? null : result;
	}

	public LLEntry get(long key) {
		BSTreePage page = getRoot();
		while (page != null && !page.isLeaf()) {
			page = page.findSubPage(key);
		}
		if (page == null) {
			return null;
		}
		return page.getValueFromLeaf(key);
	}

	public BSTreePage createPage(BSTreePage parent, boolean isLeaf) {
		return new BSTreePage(this, parent, isLeaf);
	}

	BSTreePage getRoot() {
		return root;
	}

	public BSTIteratorMinMax iterator(long min, long max) {
		return new BSTIteratorMinMax().reset(root, min, max);
	}

	public BSTIteratorMask iteratorMask(long minMask, long maxMask) {
		return new BSTIteratorMask().reset(root, minMask, maxMask);
	}

	public void updateRoot(BSTreePage newRoot) {
		root = newRoot;
	}
	
	public void print() {
		root.print("");
	}

	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		if (root != null) {
			root.toStringTree(sb, "");
		}
		return sb.toString();
	}

//	public long getMaxKey() {
//		return root.getMax();
//	}
//
//	public long getMinKey() {
//		return root.getMinKey();
//	}

	
	public BSTIteratorMinMax iterator() {
		return iterator(Long.MIN_VALUE, Long.MAX_VALUE);
	}

	
	public static class BSTStats {
		public int nNodesInner = 0;
		public int nNodesLeaf = 0;
		public int capacityInner = 0;
		public int capacityLeaf = 0;
		public int nEntriesInner = 0;
		public int nEntriesLeaf = 0;
		
		@Override
		public String toString() {
			return "nNodesI=" + nNodesInner
					+ ";nNodesL=" + nNodesLeaf
					+ ";capacityI=" + capacityInner
					+ ";capacityL=" + capacityLeaf
					+ ";nEntriesI=" + nEntriesInner
					+ ";nEntriesL=" + nEntriesLeaf
					+ ";fillRatioI=" + round(nEntriesInner/(double)capacityInner)
					+ ";fillRatioL=" + round(nEntriesLeaf/(double)capacityLeaf)
					+ ";fillRatio=" + round((nEntriesInner+nEntriesLeaf)/(double)(capacityInner+capacityLeaf));
		}
		private static double round(double d) {
			return ((int)(d*100+0.5))/100.;
		}
	}
	
	public BSTStats getStats() {
		BSTStats stats = new BSTStats();
		if (root != null) {
			root.getStats(stats);
		}
		return stats;
	}
	
	public int size() {
		return nEntries;
	}

	public void decreaseEntries() {
		nEntries--;
	}
	
	public enum REMOVE_OP {
		REMOVE_RETURN,
		KEEP_RETURN,
		KEEP_RETURN_NULL;
	}

	public int maxLeafN() {
		return maxLeafN;
	}

	public int maxInnerN() {
		return maxInnerN;
	}


}
