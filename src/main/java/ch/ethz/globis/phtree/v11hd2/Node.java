/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v11hd2;

import static ch.ethz.globis.phtree.PhTreeHelperHD.posInArrayHD;

import ch.ethz.globis.pht64kd.MaxKTreeHdI.NtEntry;
import ch.ethz.globis.pht64kd.MaxKTreeHdI.PhIterator64;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhTreeHelperHD;
import ch.ethz.globis.phtree.v11hd2.nt.NodeTreeV11;
import ch.ethz.globis.phtree.v11hd2.nt.NtIteratorFull;
import ch.ethz.globis.phtree.v11hd2.nt.NtIteratorMask;
import ch.ethz.globis.phtree.v11hd2.nt.NtNode;
import ch.ethz.globis.phtree.v11hd2.nt.NtNodePool;


/**
 * Node of the PH-tree.
 * 
 * @author ztilmann
 */
public class Node {
	
	//size of references in bytes
	private static final int HC_BITS = 0;  //number of bits required for storing current (HC)-representation
	/** Bias towards using AHC. AHC is used if (sizeLHC*AHC_LHC_BIAS) greater than (sizeAHC)  */
	public static final double AHC_LHC_BIAS = 2.0; 

	private int entryCnt = 0;
	private byte postLen = 0;
	private byte infixLen = 0; //prefix size

	//Nested tree index
	private NtNode<Object> ind = null;

	
	static final int IK_WIDTH(int dims) { return dims; }; //post index key width 

    private Node() {
		// For ZooDB only
	}

	protected Node(Node original) {
        this.entryCnt = original.entryCnt;
        this.infixLen = original.infixLen;
        this.postLen = original.postLen;
        this.infixLen = original.infixLen;
        if (original.ind != null) {
        	//copy NT tree
        	throw new UnsupportedOperationException();
        }
    }

	static Node createEmpty() {
		return new Node();
	}

	private void initNode(int infixLen, int postLen, int dims) {
		this.infixLen = (byte) infixLen;
		this.postLen = (byte) postLen;
		this.entryCnt = 0;
		//this.ind = null;
		//int size = calcArraySizeTotalBits();
		//this.ba = Bits.arrayCreate(size);
		//NT
		ind = createNiIndex(dims);
//		ba = Bits.arrayTrim(ba, calcArraySizeTotalBitsNt());
	}

	static Node createNode(int dims, int infixLen, int postLen) {
		Node n = NodePool.getNode();
		n.initNode(infixLen, postLen, dims);
		return n;
	}

	static Node createNode(Node original) {
		return new Node(original);
	}

	<T> PhEntry<T> createNodeEntry(long[] key, T value) {
		return new PhEntry<>(key, value);
	}
	
	void discardNode() {
		NtNodePool.offer(ind);
		entryCnt = 0;
		NodePool.offer(this);
		ind = null;
	}
	
	int calcArraySizeTotalBits() {
		int nBits = getBitPosIndex();
		//post-fixes
		return nBits;
	}

	void getInfixOfSubNt(long[] infix, long[] outKey) {
		if (!hasSubInfixNI(infix)) {
			return;
		}
		//To cut of trailing bits
		long mask = (-1L) << postLen;
		for (int i = 0; i < outKey.length; i++) {
			//Replace val with infix (val may be !=0 from traversal)
			outKey[i] = (mask & outKey[i]) | infix[i];
		}
	}

	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param posInNode
	 * @param pos The position of the node when mapped to a vector.
	 * @return The sub node or null.
	 */
	Object doInsertIfMatching(long[] keyToMatch, Object newValueToInsert, PhTreeHD11b<?> tree) {
		long[] hcPos = posInArrayHD(keyToMatch, getPostLen());

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
			long[] newKey, int[] insertRequired, PhTreeHD11b<?> tree) {
		
		long[] hcPos = posInArrayHD(keyToMatch, getPostLen());

		//NT
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
		return ~((-1L)<<postLen);
	}
	
	public long calcInfixMask(int subPostLen) {
		long mask = ~((-1L)<<(postLen-subPostLen-1));
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
        int newLocalInfLen = postLen - mcb;
        int newPostLen = mcb-1;
        Node newNode = createNode(key1.length, newLocalInfLen, newPostLen);

        long[] posSub1 = posInArrayHD(key1, newPostLen);
        long[] posSub2 = posInArrayHD(key2, newPostLen);
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
		int dims = key.length;

		//check if merging is necessary (check children count || isRootNode)
		if (parent == null || getEntryCount() > 2) {
			//no merging required
			//value exists --> remove it
			return;
		}

		//okay, at his point we have a post that matches and (since it matches) we need to remove
		//the local node because it contains at most one other entry and it is not the root node.
		PhIterator64<Object> iter = ntIterator(dims);
		NtEntry<Object> nte = iter.nextEntryReuse();

		long[] posInParent = PhTreeHelperHD.posInArrayHD(key, parent.getPostLen());
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
			int newSubInfixLen = postLen - ((Node)value).getPostLen() - 1;  
			((Node)value).setInfixLen(newSubInfixLen);
		}
		ntPut(hcPos, newKey, value);
		return;
	}

	void replaceEntryWithSub(long[] hcPos, long[] infix, Node newSub) {
		//NT
		ntReplaceEntry(hcPos, infix, newSub);
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
	void replaceSubWithPost(long[] hcPos, long[] key, Object value) {
		//NT
		ntReplaceEntry(hcPos, key, value);
		return;
	}

	Object ntReplaceEntry(long[] hcPos, long[] kdKey, Object value) {
		//We use 'null' as parameter to indicate that we want replacement, rather than splitting,
		//if the value exists.
		return NodeTreeV11.addEntry(ind, hcPos, kdKey, value, null);
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
	Object ntPut(long[] hcPos, long[] kdKey, Object value) {
		return NodeTreeV11.addEntry(ind, hcPos, kdKey, value, this);
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
	Object ntRemoveAnything(long[] hcPos, int dims) {
    	return NodeTreeV11.removeEntry(ind, hcPos, dims, null, null, null, null);
	}

	Object ntRemoveEntry(long[] hcPos, long[] key, long[] newKey, int[] insertRequired) {
    	return NodeTreeV11.removeEntry(ind, hcPos, key.length, key, newKey, insertRequired, this);
	}

	Object ntGetEntry(long[] hcPos, long[] outKey) {
		return NodeTreeV11.getEntry(ind(), hcPos, outKey, null, null);
	}

	Object ntGetEntryIfMatches(long[] hcPos, long[] keyToMatch) {
		return NodeTreeV11.getEntry(ind(), hcPos, null, keyToMatch, this);
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
	void addPostPIN(long[] hcPos, int pin, long[] key, Object value) {
		ntPut(hcPos, key, value);
	}


	/**
	 * WARNING: This is overloaded in subclasses of Node.
	 * @return Index.
	 */
	NtNode<Object> createNiIndex(int dims) {
		return NtNode.createRoot(dims);
	}
	

	private static int N_GOOD = 0;
	private static int N = 0;
	
	
	//TODO remove 'apply' from name
	boolean checkAndApplyInfixNt(int infixLen, long[] prefix, 
			long[] rangeMin, long[] rangeMax) {
		//first check if node-prefix allows sub-node to contain any useful values

		if (PhTreeHelperHD.DEBUG) {
			N_GOOD++;
			//Ensure that we never enter this method if the node cannot possibly contain a match.
			long maskClean = (-1L) << postLen;
			for (int dim = 0; dim < prefix.length; dim++) {
				if ((prefix[dim] & maskClean) > rangeMax[dim] || 
						(prefix[dim] | ~maskClean) < rangeMin[dim]) {
					if (getPostLen() < 63) {
						System.out.println("N-CAAI: " + ++N + " / " + N_GOOD);
						throw new IllegalStateException();
					}
					//ignore, this happens with negative values.
					//return false;
				}
			}
		}
		
		//assign infix
		//Shift in two steps in case they add up to 64.
		long maskClean = (-1L) << postLen;

		//first, clean trailing bits
		//Mask for comparing the tempVal with the ranges, except for bit that have not been
		//extracted yet.
		long compMask = (-1L)<<(postLen - infixLen);
		for (int dim = 0; dim < prefix.length; dim++) {
			long in = prefix[dim];// TODO remove this? (valTemplate[dim] & maskClean) | postFix[dim];
			in &= compMask;
			if (in > rangeMax[dim] || in < (rangeMin[dim] & compMask)) {
				return false;
			}
		}

		return true;
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
	<T>  boolean checkAndGetEntryNt(long[] hcPos, Object value, PhEntry<T> result, long[] rangeMin, long[] rangeMax) {
		if (value instanceof Node) {
			Node sub = (Node) value;
			if (!checkAndApplyInfixNt(sub.getInfixLen(), result.getKey(), rangeMin, rangeMax)) {
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


	int getBitPosIndex() {
		return getBitPosInfix();
	}

	private int getBitPosInfix() {
		// isPostHC / isSubHC / postCount / subCount
		return HC_BITS;
	}


	int pinToOffsBitsLHC(int pin, int offsIndex, int dims) {
		return offsIndex + (IK_WIDTH(dims) + postLen * dims) * pin;
	}
	
	int pinToOffsBitsData(int pin, int dims) {
		int offsIndex = getBitPosIndex();
		return pinToOffsBitsLHC(pin, offsIndex, dims) + IK_WIDTH(dims);
	}

	int getInfixLen() {
		return infixLen;
	}

	void setInfixLen(int newInfLen) {
		infixLen = (byte) newInfLen;
	}

	public int getPostLen() {
		return postLen;
	}
	
	NtNode<Object> ind() {
		return ind;
	}

    PhIterator64<Object> ntIterator(int dims) {
        return new NtIteratorFull<>(dims).reset(ind);
    }

    NtIteratorMask<Object> ntIteratorWithMask(int dims, long[] maskLower, long[] maskUpper) {
		return new NtIteratorMask<>(dims, maskLower, maskUpper).reset(ind);
	}

}
