/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v15;

import static ch.ethz.globis.phtree.PhTreeHelper.posInArray;

import ch.ethz.globis.pht64kd.MaxKTreeI.NtEntry;
import ch.ethz.globis.pht64kd.MaxKTreeI.PhIterator64;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.Refs;
import ch.ethz.globis.phtree.util.RefsLong;
import ch.ethz.globis.phtree.v15.BSTHandler.BSTEntry;
import ch.ethz.globis.phtree.v15.bst.BSTIteratorMask;
import ch.ethz.globis.phtree.v15.bst.BSTree;


/**
 * Node of the PH-tree.
 * 
 * @author ztilmann
 */
public class Node {

	private int entryCnt = 0;

	/**
	 * postLenStored: Stored bits, including the hc address.
	 * postLenClassic: The number of postFix bits
	 * Rule: postLenClassic + 1 = postLenStored.  
	 */
	private byte postLenStored = 0;
	private byte infixLenStored = 0; //prefix size

	//Hierarchical tree index: a hierarchy of long[]
	//Nested tree index
	private BSTree<BSTEntry> ind = null;

	
    private Node() {
		// For ZooDB only
	}

	protected Node(Node original) {
        this.entryCnt = original.entryCnt;
        this.infixLenStored = original.infixLenStored;
        this.postLenStored = original.postLenStored;
        this.infixLenStored = original.infixLenStored;
        if (original.ind != null) {
        	//copy NT tree
        	throw new UnsupportedOperationException();
        }
    }

	static Node createEmpty() {
		return new Node();
	}

	private void initNode(int infixLenClassic, int postLenClassic, int dims) {
		this.infixLenStored = (byte) (infixLenClassic + 1);
		this.postLenStored = (byte) (postLenClassic + 1);
		this.entryCnt = 0;
		this.ind = createNiIndex(dims);
	}

	static Node createNode(int dims, int infixLenClassic, int postLenClassic) {
		Node n = NodePool.getNode();
		n.initNode(infixLenClassic, postLenClassic, dims);
		return n;
	}

	static Node createNode(Node original) {
		return new Node(original);
	}

	<T> PhEntry<T> createNodeEntry(long[] key, T value) {
		return new PhEntry<>(key, value);
	}
	
	void discardNode() {
		entryCnt = 0;
		NodePool.offer(this);
		ind = null;
	}
	
	int calcArraySizeTotalBits(int entryCount, final int dims) {
		//TODO 
		return 64;
	}


	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param posInNode
	 * @param pos The position of the node when mapped to a vector.
	 * @return The sub node or null.
	 */
	Object doInsertIfMatching(long[] keyToMatch, Object newValueToInsert, PhTree15<?> tree) {
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
			long[] newKey, int[] insertRequired, PhTree15<?> tree) {
		
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
        newNode.incEntryCount();
        newNode.incEntryCount();
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
		int dims = key.length;

		//check if merging is necessary (check children count || isRootNode)
		if (parent == null || getEntryCount() > 2) {
			//no merging required
			//value exists --> remove it
			return;
		}

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

		discardNode();
	}

	/**
	 * @param posInNode
	 * @param pos The position of the node when mapped to a vector.
	 * @return The sub node or null.
	 */
	Object getEntryByPIN(long hcPos, long[] postBuf) {
		//For example for knnSearches!!!!!
		return ntGetEntry(hcPos, postBuf);
	}


	/**
	 * @param posInNode
	 * @param pos The position of the node when mapped to a vector.
	 * @return The sub node or null.
	 */
	Object getEntry(long hcPos, long[] postBuf) {
		return getEntryByPIN(hcPos, postBuf);
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
		ntPut(hcPos, newKey, value);
		return;
	}

	void replaceEntryWithSub(long hcPos, long[] infix, Node newSub) {
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
		BSTHandler.replaceEntry(ind, hcPos, kdKey, value);
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
		return BSTHandler.addEntry(ind, hcPos, kdKey, value, this);
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
    	return BSTHandler.removeEntry(ind, hcPos, null, null, null, null);
	}

	Object ntRemoveEntry(long hcPos, long[] key, long[] newKey, int[] insertRequired) {
    	return BSTHandler.removeEntry(ind, hcPos, key, newKey, insertRequired, this);
	}

	Object ntGetEntry(long hcPos, long[] outKey) {
		return BSTHandler.getEntry(ind(), hcPos, outKey, null, null);
	}

	Object ntGetEntryIfMatches(long hcPos, long[] keyToMatch) {
		return BSTHandler.getEntry(ind(), hcPos, null, keyToMatch, this);
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

	/**
	 * WARNING: This is overloaded in subclasses of Node.
	 * @return Index.
	 */
	BSTree<BSTEntry> createNiIndex(int dims) {
		return new BSTree<>(dims);
	}
	
	private void ntBuild(int bufEntryCnt, int dims, long[] prefix) {
		//Migrate node to node-index representation
		if (ind != null || isNT()) {
			throw new IllegalStateException();
		}
		ind = createNiIndex(dims);

		long prefixMask = mask1100(postLenStored());
		
		//read posts 
		if (isAHC()) {
			int oldOffsIndex = getBitPosIndex();
			int oldPostBitsVal = posToOffsBitsDataAHC(0, oldOffsIndex, dims);
			int postLenTotal = dims*postLenStored();
			for (int i = 0; i < (1L<<dims); i++) {
				Object o = values[i];
				if (o == null) {
					continue;
				} 
				int dataOffs = oldPostBitsVal + i*postLenTotal;
				final long[] buffer = new long[dims];
				postToNI(dataOffs, buffer, prefix, prefixMask);
				//We use 'null' as parameter to indicate that we want 
				//to skip checking for splitNode or increment of entryCount
				BSTHandler.addEntry(ind, i, buffer, o, null);
			}
		} else {
			int offsIndex = getBitPosIndex();
			int dataOffs = pinToOffsBitsLHC(0, offsIndex, dims);
			int postLenTotal = dims*postLenStored();
			for (int i = 0; i < bufEntryCnt; i++) {
				long p2 = Bits.readArray(ba, dataOffs, IK_WIDTH(dims));
				dataOffs += IK_WIDTH(dims);
				Object e = values[i];
				final long[] buffer = new long[dims];
				postToNI(dataOffs, buffer, prefix, prefixMask);
				//We use 'null' as parameter to indicate that we want 
				//to skip checking for splitNode or increment of entryCount
				BSTHandler.addEntry(ind, p2, buffer, e, null);
				dataOffs += postLenTotal;
			}
		}

		setAHC(false);
		ba = Bits.arrayTrim(ba, calcArraySizeTotalBitsNt());
		values = Refs.arrayReplace(values, null); 
	}

	/**
	 * 
	 * @param bufSubCnt
	 * @param bufPostCnt
	 * @param dims
	 * @param posToRemove
	 * @param removeSub Remove sub or post?
	 * @return Previous value if post was removed
	 */
	private Object ntDeconstruct(int dims, long posToRemove) {
		//Migrate node to node-index representation
		if (ind == null || !isNT()) {
			throw new IllegalStateException();
		}

		int entryCountNew = ntGetSize() - 1;
		decEntryCount();

		//calc node mode.
		boolean shouldBeAHC = useAHC(entryCountNew, dims);
		setAHC(shouldBeAHC);


		Object oldValue = null;
		int offsIndex = getBitPosIndex();
		long[] bia2 = Bits.arrayCreate(calcArraySizeTotalBits(entryCountNew, dims));
		//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
		Bits.copyBitsLeft(ba, 0, bia2, 0, offsIndex);
		int postLenTotal = dims*postLenStored();
		if (shouldBeAHC) {
			//HC mode
			Object[] v2 = Refs.arrayCreate(1<<dims);
			int startBitData = posToOffsBitsDataAHC(0, offsIndex, dims);
			PhIterator64<Object> it = ntIterator();
			while (it.hasNext()) {
				NtEntry<Object> e = it.nextEntryReuse();
				long pos = e.key();
				if (pos == posToRemove) {
					//skip the item that should be deleted.
					oldValue = e.value();
					v2[(int) pos] = null;
					continue;
				}
				int p2 = (int) pos;
				int offsBitData = startBitData + postLenStored() * dims * p2;
				if (e.value() instanceof Node) {
					//subnode
					Node node = (Node) e.value();
					infixFromNI(bia2, offsBitData, e.getKdKey(), node.requiresInfix());
				} else {
					postFromNI(bia2, offsBitData, e.getKdKey());
				}
				v2[p2] = e.value();
			}
			ba = Bits.arrayReplace(ba, bia2);
			values = Refs.arrayReplace(values, v2);
		} else {
			//LHC mode
			Object[] v2 = Refs.arrayCreate(entryCountNew);
			int n=0;
			PhIterator64<Object> it = ntIterator();
			int entryPosLHC = offsIndex;
			while (it.hasNext()) {
				NtEntry<Object> e = it.nextEntryReuse();
				long pos = e.key();
				if (pos == posToRemove) {
					//skip the item that should be deleted.
					oldValue = e.value();
					continue;
				}
				//write hc-key
				Bits.writeArray(bia2, entryPosLHC, IK_WIDTH(dims), pos);
				entryPosLHC += IK_WIDTH(dims);
				v2[n] = e.value();
				if (e.value() instanceof Node) {
					Node node = (Node) e.getValue();
					infixFromNI(bia2, entryPosLHC, e.getKdKey(), node.requiresInfix());
				} else {
					postFromNI(bia2, entryPosLHC, e.getKdKey());
				}
				entryPosLHC += postLenTotal;
				n++;
			}
			ba = Bits.arrayReplace(ba, bia2);
			values = Refs.arrayReplace(values, v2);
		}			

		//TODO
		//TODO
		//TODO
		//TODO
		throw new UnsupportedOperationException();
//		NtNodePool.offer(ind);
//		ind = null;
//		return oldValue;
	}


	private static int N_GOOD = 0;
	private static int N = 0;
	
	boolean checkAndApplyInfixNt(int infixLen, long[] postFix, long[] valTemplate, 
			long[] rangeMin, long[] rangeMax) {
		//first check if node-prefix allows sub-node to contain any useful values

		if (PhTreeHelper.DEBUG) {
			N_GOOD++;
			//Ensure that we never enter this method if the node cannot possibly contain a match.
			long maskClean = mask1100(getPostLen());
			for (int dim = 0; dim < valTemplate.length; dim++) {
				if ((valTemplate[dim] & maskClean) > rangeMax[dim] || 
						(valTemplate[dim] | ~maskClean) < rangeMin[dim]) {
					if (getPostLen() < 63) {
						System.out.println("N-CAAI: " + ++N + " / " + N_GOOD);
						throw new IllegalStateException();
					}
					//ignore, this happens with negative values.
					//return false;
				}
			}
		}
		
		if (!hasSubInfixNI(postFix)) {
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
		
		//assign infix
		//Shift in two steps in case they add up to 64.
		long maskClean = mask1100(postLenStored());

		//first, clean trailing bits
		//Mask for comparing the tempVal with the ranges, except for bit that have not been
		//extracted yet.
		long compMask = mask1100(postLenStored() - infixLen);
		for (int dim = 0; dim < valTemplate.length; dim++) {
			long inFull = (valTemplate[dim] & maskClean) | postFix[dim];
			long in = inFull & compMask;
			if (in > rangeMax[dim] || in < (rangeMin[dim]&compMask)) {
				return false;
			}
			valTemplate[dim] = inFull;
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
	<T>  boolean checkAndGetEntryNt(Object value, PhEntry<T> result, 
			long[] valTemplate, long[] rangeMin, long[] rangeMax) {
		if (value instanceof Node) {
			Node sub = (Node) value;
			if (!checkAndApplyInfixNt(sub.getInfixLen(), result.getKey(), valTemplate, 
					rangeMin, rangeMax)) {
				return false;
			}
			result.setNodeInternal(sub);
		} else {
			long[] inKey = result.getKey();
			for (int i = 0; i < inKey.length; i++) {
				long k = inKey[i];
				if (k < rangeMin[i] || k > rangeMax[i]) {
					return false;
				}
			}
			result.setValueInternal((T) value);
		}
		return true;
	}

	private void applyHcPos(long hcPos, long[] valTemplate) {
		PhTreeHelper.applyHcPos(hcPos, getPostLen(), valTemplate);
	}
	
	
	Object removeEntry(long hcPos, int posInNode, final int dims) {
		Object o = ntRemoveAnything(hcPos, dims);
		decEntryCount();
		return o;
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
	
	private boolean requiresInfix() {
		return getInfixLen() > 0;
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
	
	BSTree<BSTEntry> ind() {
		return ind;
	}

    PhIterator64<Object> ntIterator() {
		//TODO
		//TODO
		//TODO
		//TODO
		throw new UnsupportedOperationException();
//      return new NtIteratorMinMax<>(dims).reset(ind, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    BSTIteratorMask<BSTEntry> ntIteratorWithMask(long maskLower, long maskUpper) {
    	return new BSTIteratorMask<BSTEntry>().reset(ind, maskLower, maskUpper);
		//TODO reuse iterator???
		//TODO
		//TODO
		//TODO
//	return new NtIteratorMask<>(dims).reset(ind, maskLower, maskUpper);
	}

}
