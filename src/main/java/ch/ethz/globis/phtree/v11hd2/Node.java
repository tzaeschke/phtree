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
import ch.ethz.globis.phtree.util.Refs;
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
	//public static final int NT_THRESHOLD = 150; //TODO 
	public static final int NT_THRESHOLD = 1;

	private int entryCnt = 0;

	/**
	 * Structure of the byte[] and the required bits
	 * AHC:
	 * | kdKey AHC        |
	 * | 2^DIM*(DIM*pLen) |
	 * 
	 * LHC:
	 * | hcPos / kdKeyLHC      |
	 * | pCnt*(DIM + DIM*pLen) |
	 * 
	 * 
	 * pLen = postLen
	 * pCnt = postCount
	 * sCnt = subCount
	 */
	long[] ba = null;

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
        if (original.ba != null) {
        	this.ba = Bits.arrayClone(original.ba);
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
		Bits.arrayReplace(ba, null);
		entryCnt = 0;
		NodePool.offer(this);
		ind = null;
	}
	
	int calcArraySizeTotalBits() {
		int nBits = getBitPosIndex();
		//post-fixes
		return nBits;
	}

	private int calcArraySizeTotalBitsNt() {
		return getBitPosIndex();
	}


	/**
	 * 
	 * @param pin
	 * @param hcPos
	 * @param outVal
	 * @return whether the infix length is > 0
	 */
	boolean getInfixOfSub(int pin, long[] hcPos, long[] outVal) {
		int offs = pinToOffsBitsData(pin, outVal.length);
		if (!hasSubInfix(offs, outVal.length)) {
			return false;
		}
		//To cut of trailing bits
		long mask = (-1L) << postLen;
		for (int i = 0; i < outVal.length; i++) {
			//Replace val with infix (val may be !=0 from traversal)
			outVal[i] = (mask & outVal[i]) | Bits.readArray(ba, offs, postLen);
			offs += postLen;
		}
		return true;
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
		ntPut(hcPos, newKey, value);
		return;
	}

	void replaceEntryWithSub(long[] hcPos, long[] infix, Node newSub) {
		//NT
		ntReplaceEntry(hcPos, infix, newSub);
	}
	
	private void writeSubInfixInfo(long[] ba, int subInfoOffs, int subInfixLen) {
		//-> Should work for AHC and LHC with (offs+postLen-1)
		
		//The last bit of the infix encode whether we have 0 infix length
		//length
		boolean hasInfix = subInfixLen != 0;
		Bits.setBit(ba, subInfoOffs, hasInfix);
	}
	
	private boolean hasSubInfix(int subInfoOffs, int dims) {
		return Bits.getBit(ba, subInfoOffs + dims*postLen - 1);
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

	Object ntGetEntry(long[] hcPos, long[] outKey, long[] valTemplate) {
		//TODO apply hcPos
		//TODO apply valTemplate to outkey
		return NodeTreeV11.getEntry(ind(), hcPos, outKey, null, null);
	}

	Object ntGetEntryIfMatches(long[] hcPos, long[] keyToMatch) {
		//TODO apply hcPos
		//TODO apply valTemplate to outkey
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
		final int dims = key.length;
		final int bufEntryCnt = getEntryCount();
		//decide here whether to use hyper-cube or linear representation
		//--> Initially, the linear version is always smaller, because the cube has at least
		//    two entries, even for a single dimension. (unless DIM > 2*REF=2*32 bit 
		//    For one dimension, both need one additional bit to indicate either
		//    null/not-null (hypercube, actually two bit) or to indicate the index. 

		//TODO remove
//		if (!isNT()) {
//			ntBuild(bufEntryCnt, dims, key);
//		}
		ntPut(hcPos, key, value);
	}

	void postToNI(int startBit, int postLen, long[] outKey, long[] hcPos, long[] prefix, long mask) {
		for (int d = 0; d < outKey.length; d++) {
			outKey[d] = Bits.readArray(ba, startBit, postLen) | (prefix[d] & mask);
			startBit += postLen;
		}
		PhTreeHelperHD.applyHcPosHD(hcPos, postLen, outKey);
	}

	void postFromNI(long[] ia, int startBit, long key[], int postLen) {
		//insert postifx
		for (int d = 0; d < key.length; d++) {
			Bits.writeArray(ia, startBit + postLen * d, postLen, key[d]);
		}
	}

	void infixFromNI(long[] ba, int startBit, long[] key, int subInfixLen) {
		//insert infix:
		for (int i = 0; i < key.length; i++) {
			Bits.writeArray(ba, startBit, postLen, key[i]);
			startBit += postLen;
		}
		int subInfoOffs = startBit-1; 
		writeSubInfixInfo(ba, subInfoOffs, subInfixLen);
	}

	/**
	 * WARNING: This is overloaded in subclasses of Node.
	 * @return Index.
	 */
	NtNode<Object> createNiIndex(int dims) {
		return NtNode.createRoot(dims);
	}
	
	private void ntBuild(int bufEntryCnt, int dims, long[] prefix) {
		//Migrate node to node-index representation
		if (ind != null) {
			throw new IllegalStateException();
		}
		ind = createNiIndex(dims);

//		long prefixMask = (-1L) << postLen;
		
		//read posts 
//		int offsIndex = getBitPosIndex();
//		int dataOffs = pinToOffsBitsLHC(0, offsIndex, dims);
//		int postLenTotal = dims*postLen;
//		final long[] buffer = new long[dims];
//		for (int i = 0; i < bufEntryCnt; i++) {
//			long[] p2 = BitsHD.readArrayHD(ba, dataOffs, IK_WIDTH(dims));
//			dataOffs += IK_WIDTH(dims);
//			Object e = values[i];
//			postToNI(dataOffs, postLen, buffer, p2, prefix, prefixMask);
//			//We use 'null' as parameter to indicate that we want 
//			//to skip checking for splitNode or increment of entryCount
//			NodeTreeV11.addEntry(ind, p2, buffer, e, null);
//			dataOffs += postLenTotal;
//		}

		ba = Bits.arrayTrim(ba, calcArraySizeTotalBitsNt());
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
	private Object ntDeconstruct(int dims, long[] posToRemove) {
		//Migrate node to node-index representation
		if (ind == null) {
			throw new IllegalStateException();
		}

		int entryCountNew = ntGetSize() - 1;
		decEntryCount();

		Object oldValue = null;
		int offsIndex = getBitPosIndex();
		long[] bia2 = Bits.arrayCreate(calcArraySizeTotalBits());
		//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
		Bits.copyBitsLeft(ba, 0, bia2, 0, offsIndex);
		int postLenTotal = dims*postLen;
		//LHC mode
		Object[] v2 = Refs.arrayCreate(entryCountNew);
		int n=0;
		PhIterator64<Object> it = ntIterator(dims);
		int entryPosLHC = offsIndex;
		while (it.hasNext()) {
			NtEntry<Object> e = it.nextEntryReuse();
			long[] pos = e.getKdKey();
			if (BitsHD.isEq(pos, posToRemove)) {
				//skip the item that should be deleted.
				oldValue = e.value();
				continue;
			}
			//write hc-key
			BitsHD.writeArrayHD(bia2, entryPosLHC, IK_WIDTH(dims), pos);
			entryPosLHC += IK_WIDTH(dims);
			v2[n] = e.value();
			if (e.value() instanceof Node) {
				Node node = (Node) e.getValue();
				infixFromNI(bia2, entryPosLHC, e.getKdKey(), node.getInfixLen());
			} else {
				postFromNI(bia2, entryPosLHC, e.getKdKey(), postLen);
			}
			entryPosLHC += postLenTotal;
			n++;
		}
		ba = Bits.arrayReplace(ba, bia2);
//		values = Refs.arrayReplace(values, v2);

		NtNodePool.offer(ind);
		ind = null;
		return oldValue;
	}


	private static int N_GOOD = 0;
	private static int N = 0;
	
	
	boolean checkAndApplyInfixNt(int infixLen, long[] postFix, long[] valTemplate, 
			long[] rangeMin, long[] rangeMax) {
		//first check if node-prefix allows sub-node to contain any useful values

		if (PhTreeHelperHD.DEBUG) {
			N_GOOD++;
			//Ensure that we never enter this method if the node cannot possibly contain a match.
			long maskClean = (-1L) << postLen;
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

		//assign infix
		//Shift in two steps in case they add up to 64.
		long maskClean = (-1L) << postLen;

		//first, clean trailing bits
		//Mask for comparing the tempVal with the ranges, except for bit that have not been
		//extracted yet.
		long compMask = (-1L)<<(postLen - infixLen);
		for (int dim = 0; dim < valTemplate.length; dim++) {
			long in = (valTemplate[dim] & maskClean) | postFix[dim];
			in &= compMask;
			if (in > rangeMax[dim] || in < (rangeMin[dim]&compMask)) {
				return false;
			}
			valTemplate[dim] = in;
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
	<T>  boolean checkAndGetEntryNt(long[] hcPos, Object value, PhEntry<T> result, 
			long[] valTemplate, long[] rangeMin, long[] rangeMax) {
		PhTreeHelperHD.applyHcPosHD(hcPos, postLen, valTemplate);
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
