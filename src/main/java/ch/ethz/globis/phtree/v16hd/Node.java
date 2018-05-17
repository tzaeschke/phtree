/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v16hd;

import java.util.Arrays;
import java.util.List;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhTreeHelperHD;
import ch.ethz.globis.phtree.util.BitsLong;
import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.v16hd.PhTree16HD.UpdateInfo;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16hd.bst.BSTPool;
import ch.ethz.globis.phtree.v16hd.bst.BSTreePage;


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
		//The idea is to have at most one level of inner pages for d<=12
		//The inner pages are all slightly larger the strictly necessary because the fill rate of leaves is < 100%
		switch (dims) {
		case 1: maxLeafN = 2; maxInnerN = 3; break;
		case 2: maxLeafN = 4; maxInnerN = 3; break;
		case 3: maxLeafN = 8; maxInnerN = 3; break;
		case 4: maxLeafN = 16; maxInnerN = 3; break;
		case 5: maxLeafN = 16; maxInnerN = 4+1; break;
		case 6: maxLeafN = 16; maxInnerN = 6+1; break;
		case 7: maxLeafN = 16; maxInnerN = 10+1; break;
		case 8: maxLeafN = 16; maxInnerN = 20+1; break;
		case 9: maxLeafN = 32; maxInnerN = 20+1; break;
		case 10: maxLeafN = 32; maxInnerN = 35+1; break;
		case 11: maxLeafN = 32; maxInnerN = 70+1; break;
		case 12: maxLeafN = 64; maxInnerN = 70+1; break;
		default: maxLeafN = 100; maxInnerN = 100; break;
		}
		this.root = bstCreateRoot();
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
		BSTPool.reportFreeNode(root);
		root = null;
		NodePool.offer(this);
	}
	

	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param posInNode
	 * @param pos The position of the node when mapped to a vector.
	 * @return The sub node or null.
	 */
	Object doInsertIfMatching(long[] keyToMatch, Object newValueToInsert, PhTree16HD<?> tree, long[] hcBuf) {
		PhTreeHelperHD.posInArrayHD(keyToMatch, getPostLen(), hcBuf);

		//ntPut will also increase the node-entry count
		Object v = addEntry(hcBuf, keyToMatch, newValueToInsert);
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
	Object doIfMatching(long[] keyToMatch, boolean getOnly, Node parent, UpdateInfo insertRequired, 
			PhTree16HD<?> tree, long[] hcBuf) {
		
		PhTreeHelperHD.posInArrayHD(keyToMatch, getPostLen(), hcBuf);
		
		if (getOnly) {
			BSTEntry e = getEntry(hcBuf, keyToMatch);
			return e != null ? e.getValue() : null;
		}			
		Object v = removeEntry(hcBuf, keyToMatch, insertRequired);
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
		//We use a simplified mask, because the prefix is always present
		//long mask = ~((-1L)<<(getPostLen()-subPostLen-1));
		return (-1L) << (subPostLen+1);
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
    public Node createNode(long[] key1, Object val1, long[] key2, Object val2, int mcb) {
        //determine length of infix
        int newLocalInfLen = getPostLen() - mcb;
        int newPostLen = mcb-1;
        Node newNode = createNode(key1.length, newLocalInfLen, newPostLen);

        long[] posSub1 = BitsHD.newArray(key1.length);
        PhTreeHelperHD.posInArrayHD(key1, newPostLen, posSub1);
        long[] posSub2 = BitsHD.newArray(key2.length);
        PhTreeHelperHD.posInArrayHD(key2, newPostLen, posSub2);
        if (BitsHD.isLess(posSub1, posSub2)) {
        	newNode.writeEntry(0, posSub1, key1, val1);
        	newNode.writeEntry(1, posSub2, key2, val2);
        } else {
        	newNode.writeEntry(0, posSub2, key2, val2);
        	newNode.writeEntry(1, posSub1, key1, val1);
        }
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
		
		//okay, at his point we have a post that matches and (since it matches) we need to remove
		//the local node because it contains at most one other entry and it is not the root node.

		//We know that there is only a leaf node with only a single entry, so...
		BSTEntry nte = root.getFirstValue();
		
		//TODO should we pass around BSTEntry instead of Node? Then we would not have 
		//to allocate/recalculate posInParent ...
		long[] posInParent = BitsHD.newArray(key.length);
		PhTreeHelperHD.posInArrayHD(key, parent.getPostLen(), posInParent);
		if (nte.getValue() instanceof Node) {
			long[] newPost = nte.getKdKey();
			//connect sub to parent
			Node sub2 = (Node) nte.getValue();
			int newInfixLen = getInfixLen() + 1 + sub2.getInfixLen();
			sub2.setInfixLen(newInfixLen);

			//update parent, the position is the same
			//we use newPost as Infix
			//Replace sub!
			parent.replaceEntry(posInParent, newPost, sub2);
		} else {
			//this is also a post
			//Replace post!
			parent.replaceEntry(posInParent, nte.getKdKey(), nte.getValue());
		}

		//TODO return old key/BSTEntry to pool
		
		discardNode();
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
	private void writeEntry(int pin, long[] hcPos, long[] newKey, Object value) {
		if (value instanceof Node) {
			Node node = (Node) value;
			int newSubInfixLen = postLenStored() - node.postLenStored() - 1;  
			node.setInfixLen(newSubInfixLen);
		} 
		addEntry(hcPos, newKey, value);
		return;
	}
	

	private static int N_GOOD = 0;
	private static int N = 0;
	
	boolean checkInfix(int infixLen, long[] keyToTest, long[] rangeMin, long[] rangeMax) {
		//first check if node-prefix allows sub-node to contain any useful values

		if (PhTreeHelperHD.DEBUG) {
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
		
		if (infixLen == 0) {
			return true;
		}

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
	<T> boolean checkAndGetEntry(BSTEntry candidate, PhEntry<T> result, long[] rangeMin, long[] rangeMax) {
		Object value = candidate.getValue();
		if (value instanceof Node) {
			Node sub = (Node) value;
			if (!checkInfix(sub.getInfixLen(), candidate.getKdKey(), rangeMin, rangeMax)) {
				return false;
			}
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
    
    
    // ************************************
    // ************************************
    // BSTree
    // ************************************
    // ************************************
	
    public static int statNLeaves = 0;
	public static int statNInner = 0;
	
	private BSTreePage bstCreateRoot() {

		//bootstrap index
		return bstCreatePage(null, true, null);
	}


	public final BSTEntry bstGetOrCreate(long[] key) {
		BSTreePage page = getRoot();
		if (page.isLeaf()) {
			BSTEntry e = page.getOrCreate(key, null, -1, this);
			if (e.getKdKey() == null && e.getValue() instanceof BSTreePage) {
    			BSTreePage newPage = (BSTreePage) e.getValue();
				root = BSTreePage.create(this, null, page, newPage);
				e.setValue(null);
			}
			return e;
		} 
		
		Object o = page;
		while (o instanceof BSTreePage && !((BSTreePage)o).isLeaf()) {
			o = ((BSTreePage)o).getOrCreate(key, this);
		}
		if (o == null) {
			//did not exist
		}
		return (BSTEntry)o;
	}


	public BSTEntry bstRemove(long[] key, long[] kdKey, PhTree16HD.UpdateInfo ui) {
		final BSTreePage rootPage = getRoot();
		if (rootPage.isLeaf()) {
			return rootPage.remove(key, kdKey, this, ui);
		} 
		
		BSTEntry result = rootPage.findAndRemove(key, kdKey, this, ui);
		if (rootPage.getNKeys() == 0) { 
			root = rootPage.getFirstSubPage();
			BSTPool.reportFreeNode(rootPage);
		}
		return result;
	}


	public BSTEntry bstGet(long[] key) {
		BSTreePage page = getRoot();
		while (page != null && !page.isLeaf()) {
			page = page.findSubPage(key);
		}
		if (page == null) {
			return null;
		}
		return page.getValueFromLeaf(key);
	}

	public BSTreePage bstCreatePage(BSTreePage parent, boolean isLeaf, BSTreePage leftPredecessor) {
		return BSTreePage.create(this, parent, isLeaf, leftPredecessor);
	}

	public BSTreePage getRoot() {
		return root;
	}

	public void bstUpdateRoot(BSTreePage newRoot) {
		root = newRoot;
	}

	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		if (root != null) {
			root.toStringTree(sb, "");
		}
		return sb.toString();
	}

	
	public BSTIteratorAll iterator() {
		return new BSTIteratorAll().reset(getRoot());
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

	
	public int maxLeafN() {
		return maxLeafN;
	}

	public int maxInnerN() {
		return maxInnerN;
	}

	
	// *****************************************
	// BST handler
	// *****************************************
	
	/**
	 * General contract:
	 * Returning a value or NULL means: Value was replaced, no change in counters
	 * Returning a Node means: Traversal not finished, no change in counters
	 * Returning null means: Insert successful, please update global entry counter
	 * 
	 * Node entry counters are updated internally by the operation
	 * Node-counting is done by the NodePool.
	 * 
	 * @param hcPos hc pos
	 * @param kdKey key
	 * @param value value
	 * @return
	 */
	Object addEntry(long[] hcPos, long[] kdKey, Object value) {
		//Uses bstGetOrCreate() -> 
		//- get or create entry
		//- if value==null -> new entry, just set key,value
		//- if not null: decide to replacePos (exact match) or replaceWithSub 
		BSTEntry be = bstGetOrCreate(hcPos);
		if (be.getKdKey() == null) {
			//new!
			be.set(hcPos, kdKey, value);
			return null;
		} 
		
		//exists!!
		return handleCollision(be, kdKey, value);
	}

	
	private Object handleCollision(BSTEntry existingE, long[] kdKey, Object value) {
		//We have two entries in the same location (local hcPos).
		//Now we need to compare the kdKeys.
		//If they are identical, we either replace the VALUE or return the SUB-NODE
		// (that's actually the same, simply return the VALUE)
		//If the kdKey differs, we have to split, insert a newSubNode and return null.

		Object localVal = existingE.getValue();
		if (localVal instanceof Node) {
			Node subNode = (Node) localVal;
			if (subNode.getInfixLen() > 0) {
				long mask = calcInfixMask(subNode.getPostLen());
				return insertSplitPH(existingE, kdKey, value, mask);
			}
			//No infix conflict, just traverse subnode
			return localVal;
		} else {
			if (getPostLen() > 0) {
				long mask = calcPostfixMask();
				return insertSplitPH(existingE, kdKey, value, mask);
			}
			//perfect match -> replace value
			existingE.set(existingE.getKey(), kdKey, value);
			return localVal;
		}
	}
	

	public Object insertSplitPH(BSTEntry currentEntry, long[] newKey, Object newValue, long mask) {
		if (mask == 0) {
			//There won't be any split, no need to check.
			return currentEntry.getValue();
		}
		long[] localKdKey = currentEntry.getKdKey();
		Object currentValue = currentEntry.getValue();
		int maxConflictingBits = Node.calcConflictingBits(newKey, localKdKey, mask);
		if (maxConflictingBits == 0) {
			if (!(currentValue instanceof Node)) {
				//replace value
				currentEntry.set(currentEntry.getKey(), newKey, newValue);
			}
			//return previous value
			return currentValue;
		}
		
		Node newNode = createNode(newKey, newValue, localKdKey, currentValue, maxConflictingBits);

		//replace value
		currentEntry.set(currentEntry.getKey(), BitsLong.arrayClone(localKdKey), newNode);
		//entry did not exist
        return null;
	}
	
	private Object replaceEntry(long[] hcPos, long[] kdKey, Object value) {
		BSTEntry be = bstGet(hcPos);
		Object prev = be.getValue();
		be.set(hcPos, kdKey, value);
		return prev;
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
	 * @param hcPos hc pos
	 * @param key key
	 * @param ui UpdateInfo
	 * @return See contract.
	 */
	private Object removeEntry(long[] hcPos, long[] key, UpdateInfo ui) {
		//Only remove value-entries, node-entries are simply returned without removing them
		BSTEntry prev = bstRemove(hcPos, key, ui);
		//return values: 
		// - null -> not found / remove failed
		// - Node -> recurse node
		// - T -> remove success
		//Node: removing a node is never necessary: When values are removed from the PH-Tree, nodes are replaced
		// with vales from sub-nodes, but they are never simply removed.
		//-> The BST.remove() needs to do:
		//  - Key not found: no delete, return null
		//  - No match: no delete, return null
		//  - Match Node: no delete, return Node
		//  - Match Value: delete, return value
		return prev == null ? null : prev.getValue();
	}

	public REMOVE_OP bstInternalRemoveCallback(BSTEntry currentEntry, long[] key, UpdateInfo ui) {
		if (matches(currentEntry, key)) {
			if (currentEntry.getValue() instanceof Node) {
				return REMOVE_OP.KEEP_RETURN;
			}
			if (ui != null) {
				//replace
				int bitPosOfDiff = Node.calcConflictingBits(key, ui.newKey, -1L);
				if (bitPosOfDiff <= getPostLen()) {
					//replace
					//simply replace kdKey!!
					//Replacing the long[] should be correct (and fastest, and avoiding GC)
					currentEntry.set(currentEntry.getKey(), ui.newKey, currentEntry.getValue());
					return REMOVE_OP.KEEP_RETURN;
				} else {
					ui.insertRequired = bitPosOfDiff;
				}
			}
			return REMOVE_OP.REMOVE_RETURN;
		}
		return REMOVE_OP.KEEP_RETURN_NULL;
	}
	
	
	BSTEntry getEntry(long[] hcPos, long[] keyToMatch) {
		BSTEntry be = bstGet(hcPos);
		if (be == null) {
			return null;
		}
		if (keyToMatch != null && !matches(be, keyToMatch)) {
			return null;
		}
		return be; 
	}

	
	private boolean matches(BSTEntry be, long[] keyToMatch) {
		//This is always 0, unless we decide to put several keys into a single array
		if (be.getValue() instanceof Node) {
			Node sub = (Node) be.getValue();
			if (sub.getInfixLen() > 0) {
				final long mask = calcInfixMask(sub.getPostLen());
				if (!readAndCheckKdKey(be.getKdKey(), keyToMatch, mask)) {
					return false;
				}
			}
		} else {
			long[] candidate = be.getKdKey();
			for (int i = 0; i < keyToMatch.length; i++) {
				if (candidate[i] != keyToMatch[i]) {
					return false;
				}
			}
		}
		return true;
	}
	
	private static boolean readAndCheckKdKey(long[] allKeys, long[] keyToMatch, long mask) {
		for (int i = 0; i < keyToMatch.length; i++) {
			if (((allKeys[i] ^ keyToMatch[i]) & mask) != 0) {
				return false;
			}
		}
		return true;
	}


	void getStats(PhTreeStats stats, List<BSTEntry> entries) {
		BSTIteratorAll iter = iterator();
		while (iter.hasNextEntry()) {
			entries.add(iter.nextEntry());
		}
		BSTStats bstStats = getStats();
		//nInner
		stats.nAHC += bstStats.nNodesInner;
		//nLeaf
		stats.nNT += bstStats.nNodesLeaf;
		//Capacity inner
		stats.nNtNodes += bstStats.capacityLeaf;
	}
	
		
	
	public enum REMOVE_OP {
		REMOVE_RETURN,
		KEEP_RETURN,
		KEEP_RETURN_NULL;
	}

	public static class BSTEntry {
		private long[] key;
		private long[] kdKey;
		private Object value;
		public BSTEntry(long[] key, long[] k, Object v) {
			this.key = key;
			kdKey = k;
			value = v;
		}
		public long[] getKey() {
			return key;
		}
		public long[] getKdKey() {
			return kdKey;
		}
		public Object getValue() {
			return value;
		}
		public void set(long[] key, long[] kdKey, Object value) {
			this.key = key;
			this.kdKey = kdKey;
			this.value = value;
		}
		@Override
		public String toString() {
			return (kdKey == null ? null : Arrays.toString(kdKey)) + "->" + value;
		}
		public void setValue(Object value) {
			this.value = value;
		}
	}

}
