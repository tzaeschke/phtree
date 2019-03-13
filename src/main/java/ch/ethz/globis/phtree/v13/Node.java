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

import static ch.ethz.globis.phtree.PhTreeHelper.posInArray;

import ch.ethz.globis.phtree.PhTreeHelper;


/**
 * Node of the PH-tree.
 * 
 * @author ztilmann
 */
public class Node {
	
	//size of references in bytes
	private static final int REF_BITS = 4*8;
	private static final int HC_BITS = 0;  //number of bits required for storing current (HC)-representation
	private static final int INN_HC_WIDTH = 0; //Index-NotNull: width of not-null flag for post/infix-hc
	/** Bias towards using AHC. AHC is used if (sizeLHC*AHC_LHC_BIAS) greater than (sizeAHC)  */
	public static final double AHC_LHC_BIAS = 2.0;

	private Object[] values;
	
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
	private long[] ba = null;

	// |   1st   |   2nd    |   3rd   |    4th   |
	// | isSubHC | isPostHC | isSubNI | isPostNI |
	private boolean isHC = false;

	/**
	 * postLenStored: Stored bits, including the hc address.
	 * postLenClassic: The number of postFix bits
	 * Rule: postLenClassic + 1 = postLenStored.  
	 */
	private byte postLenStored = 0;
	private byte infixLenStored = 0; //prefix size

	
	static final int IK_WIDTH(int dims) { return dims; } //post index key width

    private Node() {
		// For ZooDB only
	}

	protected Node(Node original, PhTree13<?> tree) {
        if (original.values != null) {
            this.values = tree.objPool().arrayClone(original.values);
        }
        this.entryCnt = original.entryCnt;
        this.isHC = original.isHC;
        this.postLenStored = original.postLenStored;
        this.infixLenStored = original.infixLenStored;
        if (original.ba != null) {
        	this.ba = tree.longPool().arrayClone(original.ba);
        }
    }

	static Node createEmpty() {
		return new Node();
	}

	private void initNode(int infixLenClassic, int postLenClassic, int dims, PhTree13<?> tree) {
		this.infixLenStored = (byte) (infixLenClassic + 1);
		this.postLenStored = (byte) (postLenClassic + 1);
		this.entryCnt = 0;
		this.isHC = false;
		int size = calcArraySizeTotalBits(2, dims);
		this.ba = tree.longPool().arrayCreate(size);
		this.values = tree.objPool().arrayCreate(2);
	}

	static Node createNode(int dims, int infixLenClassic, int postLenClassic, PhTree13<?> tree) {
		Node n = tree.nodePool().get();
		n.initNode(infixLenClassic, postLenClassic, dims, tree);
		return n;
	}

	private void discardNode(PhTree13<?> tree) {
		tree.longPool().arrayReplace(ba, null);
		tree.objPool().arrayReplace(values, null);
		entryCnt = 0;
		tree.nodePool().offer(this);
	}
	
	int calcArraySizeTotalBits(int entryCount, final int dims) {
		int nBits = getBitPosIndex();
		//post-fixes
		if (isAHC()) {
			//hyper-cube
			nBits += (INN_HC_WIDTH + dims * postLenStored()) * (1 << dims);
		} else {
			//hc-pos index
			nBits += entryCount * (IK_WIDTH(dims) + dims * postLenStored());
		}
		return nBits;
	}

	/**
	 * 
	 * @param pin pos in node
	 * @param hcPos HC-pos
	 * @param outVal output
	 * @return whether the infix length is > 0
	 */
	private boolean getInfixOfSub(int pin, long hcPos, long[] outVal) {
		int offs = pinToOffsBitsData(pin, hcPos, outVal.length);
		if (!hasSubInfix(offs, outVal.length)) {
			applyHcPos(hcPos, outVal);
			return false;
		}
		//To cut of trailing bits
		long mask = mask1100(postLenStored());
		for (int i = 0; i < outVal.length; i++) {
			//Replace val with infix (val may be !=0 from traversal)
			outVal[i] = (mask & outVal[i]) | Bits.readArray(ba, offs, postLenStored());
			offs += postLenStored();
		}
		return true;
	}

	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param keyToMatch search key
	 * @param newValueToInsert new value
	 * @param tree tree
	 * @return The sub node or null.
	 */
	Object doInsertIfMatching(long[] keyToMatch, Object newValueToInsert, PhTree13<?> tree) {
		long hcPos = posInArray(keyToMatch, getPostLen());
		int pin = getPosition(hcPos, keyToMatch.length);
		//check whether hcPos is valid
		if (pin < 0) {
			tree.increaseNrEntries();
			addPostPIN(hcPos, pin, keyToMatch, newValueToInsert, tree);
			return null;
		}
		
		Object v;
		int offs;
		int dims = keyToMatch.length;
		if (isAHC()) {
			v = values[(int) hcPos];
			offs = posToOffsBitsDataAHC(hcPos, getBitPosIndex(), dims);
		} else {
			v = values[pin];
			offs = pinToOffsBitsDataLHC(pin, getBitPosIndex(), dims);
		}
//		Object v = isHC() ? values[(int) hcPos] : values[pin];
//		int offs = pinToOffsBitsData(pin, hcPos, keyToMatch.length);
		if (v instanceof Node) {
			Node sub = (Node) v;
			if (hasSubInfix(offs, dims)) {
				long mask = calcInfixMask(sub.getPostLen());
				return insertSplit(keyToMatch, newValueToInsert, v, pin, hcPos, tree, offs, mask);
			}
			return v;
		} else {
			if (getPostLen() > 0) {
				long mask = calcPostfixMask();
				return insertSplit(keyToMatch, newValueToInsert, v, pin, hcPos, tree, offs, mask);
			}
			//perfect match -> replace value
			values[pin] = newValueToInsert;
			return v;
		}
	}

	/**
	 * Returns the value (T or Node) if the entry exists and matches the key.
	 * @param keyToMatch The key of the entry
	 * @param getOnly True if we only get the value. False if we want to delete it.
	 * @param parent parent node
	 * @param newKey new key
	 * @param insertRequired insert info
	 * @param tree tree
	 * @return The sub node or null.
	 */
	Object doIfMatching(long[] keyToMatch, boolean getOnly, Node parent,
			long[] newKey, int[] insertRequired, PhTree13<?> tree) {
		
		long hcPos = posInArray(keyToMatch, getPostLen());
		
		int pin;
		Object v;
		int offs;
		int dims = keyToMatch.length;
		if (isAHC()) {
			v = values[(int) hcPos];
			if (v == null) {
				//not found
				return null;
			}
			pin = (int) hcPos;
			offs = posToOffsBitsDataAHC(hcPos, getBitPosIndex(), dims);
		} else {
			pin = getPosition(hcPos, keyToMatch.length);
			if (pin < 0) {
				//not found
				return null;
			}
			v = values[pin];
			offs = pinToOffsBitsDataLHC(pin, getBitPosIndex(), dims);
		}
		if (v instanceof Node) {
			Node sub = (Node) v;
			if (hasSubInfix(offs, dims)) {
				final long mask = calcInfixMask(sub.getPostLen());
				if (!readAndCheckKdKey(offs, keyToMatch, mask)) {
					return null;
				}
			}
			return v;
		} else {
			final long mask = calcPostfixMask();
			if (!readAndCheckKdKey(offs, keyToMatch, mask)) {
				return null;
			}
			if (getOnly) {
				return v;
			} else {
				return deleteAndMergeIntoParent(pin, hcPos, keyToMatch, 
							parent, newKey, insertRequired, v, tree);
			}			
		}
	}
	
	private boolean readAndCheckKdKey(int offs, long[] keyToMatch, long mask) {
		for (int i = 0; i < keyToMatch.length; i++) {
			long k = Bits.readArray(ba, offs, postLenStored());
			if (((k ^ keyToMatch[i]) & mask) != 0) {
				return false;
			}
			offs += postLenStored();
		}
		return true;
	}

	private long calcPostfixMask() {
		return ~((-1L)<<getPostLen());
	}
	
	private long calcInfixMask(int subPostLen) {
		long mask = ~((-1L)<<(getPostLen()-subPostLen-1));
		return mask << (subPostLen+1);
	}
	

	/**
	 * Splitting occurs if a node with an infix has to be split, because a new value to be inserted
	 * requires a partially different infix.
	 * @param newKey new key
	 * @param newValue new value
	 * @param currentValue current value
	 * @param pin pos in node
	 * @param hcPos HC pos
	 * @param tree tree
	 * @param offs bit offset
	 * @param mask mit bask
	 * @return The value
	 */
	private Object insertSplit(long[] newKey, Object newValue, Object currentValue,
							   int pin, long hcPos, PhTree13<?> tree, int offs, long mask) {
        //do the splitting

        //What does 'splitting' mean (we assume there is currently a sub-node, in case of a postfix
        // work similar):
        //The current sub-node has an infix that is not (or only partially) compatible with 
        //the new key.
        //We create a new intermediary sub-node between the parent node and the current sub-node.
        //The new key/value (which we want to add) should end up as post-fix for the new-sub node. 
        //All other current post-fixes and sub-nodes stay in the current sub-node. 

        //How splitting works:
        //We insert a new node between the current and the parent node:
        //  parent -> newNode -> node
        //The parent is then updated with the new sub-node and the current node gets a shorter
        //infix.

		long[] buffer = new long[newKey.length];
		int maxConflictingBits = calcConflictingBits(newKey, offs, buffer, mask);
		if (maxConflictingBits == 0) {
			if (!(currentValue instanceof Node)) {
				values[pin] = newValue;
			}
			return currentValue;
		}
		
		Node newNode =
                createNode(newKey, newValue, buffer, currentValue, maxConflictingBits, tree);

        replaceEntryWithSub(pin, hcPos, newKey, newNode, tree);
        tree.increaseNrEntries();
		//entry did not exist
        return null;
    }

    /**
     * 
     * @param key1 key 1
     * @param val1 value 1
     * @param key2 key 2
     * @param val2 value 2
     * @param mcb most conflicting bit
	 * @param tree tree
     * @return A new node or 'null' if there are no conflicting bits
     */
    public Node createNode(long[] key1, Object val1, long[] key2, Object val2, int mcb,
                           PhTree13<?> tree) {
        //determine length of infix
        int newLocalInfLen = getPostLen() - mcb;
        int newPostLen = mcb-1;
        Node newNode = createNode(key1.length, newLocalInfLen, newPostLen, tree);

        long posSub1 = posInArray(key1, newPostLen);
        long posSub2 = posInArray(key2, newPostLen);
        if (posSub1 < posSub2) {
        	newNode.writeEntry(0, posSub1, key1, val1, tree);
        	newNode.writeEntry(1, posSub2, key2, val2, tree);
        } else {
        	newNode.writeEntry(0, posSub2, key2, val2, tree);
        	newNode.writeEntry(1, posSub1, key1, val1, tree);
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
    private static int calcConflictingBits(long[] v1, long[] v2, long mask) {
		//long mask = (1l<<node.getPostLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
     	//write all differences to diff, we just check diff afterwards
		long diff = 0;
		for (int i = 0; i < v1.length; i++) {
			diff |= (v1[i] ^ v2[i]);
		}
    	return Long.SIZE-Long.numberOfLeadingZeros(diff & mask);
    }
    
    /**
     * @param v1 vector
     * @param outV The 2nd kd-key is read into outV
     * @return the position of the most significant conflicting bit (starting with 1) or
     * 0 in case of no conflicts.
     */
    private int calcConflictingBits(long[] v1, int bitOffs, long[] outV, long mask) {
		//long mask = (1l<<node.getPostLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
     	//write all differences to diff, we just check diff afterwards
		long diff = 0;
		long[] ia = ba;
		int offs = bitOffs;
		for (int i = 0; i < v1.length; i++) {
			long k = Bits.readArray(ia, offs, postLenStored());
			diff |= (v1[i] ^ k);
			outV[i] = k;
			offs += postLenStored();
		}
    	return Long.SIZE-Long.numberOfLeadingZeros(diff & mask);
    }
    
	private Object deleteAndMergeIntoParent(int pinToDelete, long hcPos, long[] key, 
			Node parent, long[] newKey, int[] insertRequired, Object valueToDelete, 
			PhTree13<?> tree) {
		
		int dims = key.length;

		//Check for update()
		if (newKey != null) {
			//replace
			int bitPosOfDiff = calcConflictingBits(key, newKey, -1L);
			if (bitPosOfDiff <= getPostLen()) {
				//replace
				return replacePost(pinToDelete, hcPos, newKey);
			} else {
				insertRequired[0] = bitPosOfDiff;
			}
		}

		//okay we have something to delete
		tree.decreaseNrEntries();

		//check if merging is necessary (check children count || isRootNode)
		if (parent == null || getEntryCount() > 2) {
			//no merging required
			//value exists --> remove it
			return removeEntry(hcPos, pinToDelete, dims, tree);
		}

		//okay, at his point we have a post that matches and (since it matches) we need to remove
		//the local node because it contains at most one other entry and it is not the root node.
		
		//The pin of the entry that we want to keep
		int pin2 = -1;
		long pos2 = -1;
		Object val2 = null;
		if (isAHC()) {
			for (int i = 0; i < (1<<key.length); i++) {
				if (values[i] != null && i != pinToDelete) {
					pin2 = i;
					pos2 = i;
					val2 = values[i];
					break;
				}
			}
		} else {
			//LHC: we have only pos=0 and pos=1
			pin2 = (pinToDelete == 0) ? 1 : 0;
			int offs = pinToOffsBitsLHC(pin2, getBitPosIndex(), dims);
			pos2 = Bits.readArray(ba, offs, IK_WIDTH(dims));
			val2 = values[pin2];
		}

		long[] newPost = new long[dims];
		Bits.arraycopy(key, 0, newPost, 0, key.length);

		long posInParent = PhTreeHelper.posInArray(key, parent.getPostLen());
		int pinInParent = parent.getPosition(posInParent, dims);
		if (val2 instanceof Node) {
			getInfixOfSub(pin2, pos2, newPost);
	
			Node sub2 = (Node) val2;
			int newInfixLen = getInfixLen() + 1 + sub2.getInfixLen();
			sub2.setInfixLen(newInfixLen);

			//update parent, the position is the same
			//we use newPost as Infix
			parent.replaceEntryWithSub(pinInParent, posInParent, newPost, sub2, tree);
		} else {
			//this is also a post
			getEntryByPIN(pin2, pos2, newPost);
			parent.replaceSubWithPost(pinInParent, posInParent, newPost, val2, tree);
		}

		discardNode(tree);
		return valueToDelete;
	}

	/**
	 * @param posInNode position in node
	 * @param hcPos HC pos
	 * @return The sub node or null.
	 */
	private Object getEntryByPIN(int posInNode, long hcPos, long[] postBuf) {
		Object o = values[posInNode];
		if (o instanceof Node) {
			getInfixOfSub(posInNode, hcPos, postBuf);
		} else {
			int offsetBit = pinToOffsBitsData(posInNode, hcPos, postBuf.length);
			final long mask = mask1100(postLenStored());
			for (int i = 0; i < postBuf.length; i++) {
				postBuf[i] &= mask;
				postBuf[i] |= Bits.readArray(ba, offsetBit, postLenStored());
				offsetBit += postLenStored();
			}
		}
		return o;
	}


	/**
	 * @param hcPos HC pos
	 * @param postBuf postfix return value
	 * @return The sub node or null.
	 */
	Object getEntry(long hcPos, long[] postBuf) {
		int posInNode = getPosition(hcPos, postBuf.length);
		if (posInNode < 0) {
			return null;
		}
		return getEntryByPIN(posInNode, hcPos, postBuf);
	}


	/**
	 * @param posInNode position in node
	 * @param hcPos HC pos
	 * @param subNodePrefix prefix for subnodes
	 * @param outKey return value
	 * @return The sub node or null.
	 */
	Object getEntryPIN(int posInNode, long hcPos, long[] subNodePrefix, long[] outKey) {
		Object o = values[posInNode];
		if (o == null) {
			return null;
		}
		if (o instanceof Node) {
			getInfixOfSub(posInNode, hcPos, subNodePrefix);
		} else {
			int offsetBit = pinToOffsBitsData(posInNode, hcPos, subNodePrefix.length);
			final long mask = mask1100(postLenStored());
			for (int i = 0; i < subNodePrefix.length; i++) {
				outKey[i] = (subNodePrefix[i] & mask) | Bits.readArray(ba, offsetBit, postLenStored());
				offsetBit += postLenStored();
			}
		}
		return o;
	}

	private boolean shouldSwitchToAHC(int entryCount, int dims) {
		return useAHC(entryCount, dims);
	}
	
	private boolean shouldSwitchToLHC(int entryCount, int dims) {
		return !useAHC(entryCount+2, dims);
	}
	
	private boolean useAHC(int entryCount, int dims) {
		//calc post mode.
		//+1 bit for null/not-null flag
		long sizeAHC = (dims * postLenStored() + INN_HC_WIDTH + REF_BITS) * (1L << dims); 
		//+DIM because every index entry needs DIM bits
		long sizeLHC = (dims * postLenStored() + IK_WIDTH(dims) + REF_BITS) * (long)entryCount;
		//Already 1.1 i.o. 1.0 has significant bad impact on perf.
		return PhTree13.AHC_ENABLED && (dims<=31) && (sizeLHC*AHC_LHC_BIAS >= sizeAHC);
	}

	/**
	 * Writes a complete entry.
	 * This should only be used for new nodes.
	 * 
	 * @param pin position in node
	 * @param hcPos HC pos
	 * @param newKey new key
	 * @param value new value
	 */
	private void writeEntry(int pin, long hcPos, long[] newKey, Object value, PhTree13<?> tree) {
		int dims = newKey.length;
		int offsIndex = getBitPosIndex();
		int offsKey;
		if (isAHC()) {
			values[(int) hcPos] = value;
			offsKey = posToOffsBitsDataAHC(hcPos, offsIndex, dims);
		} else {
			values[pin] = value;
			offsKey = pinToOffsBitsLHC(pin, offsIndex, dims);
			Bits.writeArray(ba, offsKey, IK_WIDTH(dims), hcPos);
			offsKey += IK_WIDTH(dims);
		}
		if (value instanceof Node) {
			Node node = (Node) value;
			int newSubInfixLen = postLenStored() - node.postLenStored() - 1;  
			node.setInfixLen(newSubInfixLen);
			writeSubInfix(pin, hcPos, newKey, node.requiresInfix());
		} else if (postLenStored() > 0) {
			for (int i = 0; i < newKey.length; i++) {
				Bits.writeArray(ba, offsKey, postLenStored(), newKey[i]);
				offsKey += postLenStored();
			}
		}
	}

	private Object replacePost(int pin, long hcPos, long[] newKey) {
		int offs = pinToOffsBitsData(pin, hcPos, newKey.length);
		for (int i = 0; i < newKey.length; i++) {
			Bits.writeArray(ba, offs, postLenStored(), newKey[i]);
			offs += postLenStored();
		}
		return values[pin];
	}

	private void replaceEntryWithSub(int posInNode, long hcPos, long[] infix, Node newSub, PhTree13<?> tree) {
		//TODO during insert we wounldn't need to rewrite the infix, only the infix-flag
		//     would need to be set...
		writeSubInfix(posInNode, hcPos, infix, newSub.requiresInfix());
		values[posInNode] = newSub;
	}
	
	private void writeSubInfix(int pin, long hcPos, long[] infix, boolean subRequiresInfix) {
		//We should write the 1-bit hcPos here, even with infix-len==0.
		replacePost(pin, hcPos, infix);
		int dims = infix.length;
		int subInfoOffs = pinToOffsBitsData(pin, hcPos, dims) + dims*postLenStored() - 1;
		writeSubInfixInfo(ba, subInfoOffs, subRequiresInfix);
	}

	private void writeSubInfixInfo(long[] ba, int subInfoOffs, boolean subRequiresInfix) {
		//-> Should work for AHC and LHC with (offs+postLen-1)
		
		//The last bit of the infix encode whether we have 0 infix length
		//length
		Bits.setBit(ba, subInfoOffs, subRequiresInfix);
	}
	
	private boolean hasSubInfix(int subInfoOffs, int dims) {
		return Bits.getBit(ba, subInfoOffs + dims*postLenStored() - 1);
	}

	/**
	 * Replace a sub-node with a postfix, for example if the current sub-node is removed, 
	 * it may have to be replaced with a post-fix.
	 */
	private void replaceSubWithPost(int pin, long hcPos, long[] key, Object value, PhTree13<?> tree) {
		values[pin] = value;
		replacePost(pin, hcPos, key);
	}


	private void switchLhcToAhcAndGrow(int oldEntryCount, int dims, PhTree13<?> tree) {
		int posOfIndex = getBitPosIndex();
		int posOfData = posToOffsBitsDataAHC(0, posOfIndex, dims);
		setAHC( true );
		long[] bia2 = tree.longPool().arrayCreate(calcArraySizeTotalBits(oldEntryCount+1, dims));
		Object [] v2 = tree.objPool().arrayCreate(1<<dims);
		//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
		Bits.copyBitsLeft(ba, 0, bia2, 0, posOfIndex);
		int postLenTotal = dims*postLenStored(); 
		for (int i = 0; i < oldEntryCount; i++) {
			int entryPosLHC = posOfIndex + i*(IK_WIDTH(dims)+postLenTotal);
			int p2 = (int)Bits.readArray(ba, entryPosLHC, IK_WIDTH(dims));
			Bits.copyBitsLeft(ba, entryPosLHC+IK_WIDTH(dims),
					bia2, posOfData + postLenTotal*p2, 
					postLenTotal);
			v2[p2] = values[i];
		}
		ba = tree.longPool().arrayReplace(ba, bia2);
		values = tree.objPool().arrayReplace(values, v2);
	}
	
	
	private Object switchAhcToLhcAndShrink(int oldEntryCount, int dims, long hcPosToRemove, PhTree13<?> tree) {
		Object oldEntry = null;
		setAHC( false );
		long[] bia2 = tree.longPool().arrayCreate(calcArraySizeTotalBits(oldEntryCount-1, dims));
		Object[] v2 = tree.objPool().arrayCreate(oldEntryCount-1);
		int oldOffsIndex = getBitPosIndex();
		int oldOffsData = oldOffsIndex + (1<<dims)*INN_HC_WIDTH;
		//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
		Bits.copyBitsLeft(ba, 0, bia2, 0, oldOffsIndex);
		int postLenTotal = dims*postLenStored();
		int n=0;
		for (int i = 0; i < (1L<<dims); i++) {
			if (i == hcPosToRemove) {
				//skip the item that should be deleted.
				oldEntry = values[i];
				continue;
			}
			if (values[i] != null) {
				v2[n] = values[i];
				int entryPosLHC = oldOffsIndex + n*(IK_WIDTH(dims)+postLenTotal);
				Bits.writeArray(bia2, entryPosLHC, IK_WIDTH(dims), i);
				Bits.copyBitsLeft(
						ba, oldOffsData + postLenTotal*i, 
						bia2, entryPosLHC + IK_WIDTH(dims),
						postLenTotal);
				n++;
			}
		}
		ba = tree.longPool().arrayReplace(ba, bia2);
		values = tree.objPool().arrayReplace(values, v2);
		return oldEntry;
	}
	
	
	/**
	 * 
	 * @param hcPos HC pos
	 * @param pin position in node: ==hcPos for AHC or pos in array for LHC
	 * @param key new key
	 * @param value new value
	 */
	void addPostPIN(long hcPos, int pin, long[] key, Object value, PhTree13<?> tree) {
		final int dims = key.length;
		final int bufEntryCnt = getEntryCount();
		//decide here whether to use hyper-cube or linear representation
		//--> Initially, the linear version is always smaller, because the cube has at least
		//    two entries, even for a single dimension. (unless DIM > 2*REF=2*32 bit 
		//    For one dimension, both need one additional bit to indicate either
		//    null/not-null (hypercube, actually two bit) or to indicate the index. 

		//switch representation (HC <-> Linear)?
		if (!isAHC() && shouldSwitchToAHC(bufEntryCnt + 1, dims)) {
			switchLhcToAhcAndGrow(bufEntryCnt, dims, tree);
			//no need to update pin now, we are in HC now.
		}

		incEntryCount();

		int offsIndex = getBitPosIndex();
		if (isAHC()) {
			//hyper-cube
			int offsPostKey = posToOffsBitsDataAHC(hcPos, offsIndex, dims);
			for (int i = 0; i < key.length; i++) {
				Bits.writeArray(ba, offsPostKey + postLenStored() * i, postLenStored(), key[i]);
			}
			values[(int) hcPos] = value;
		} else {
			//get position
			pin = -(pin+1);

			//resize array
			ba = tree.longPool().arrayEnsureSize(ba, calcArraySizeTotalBits(bufEntryCnt+1, dims));
			long[] ia = ba;
			int offs = pinToOffsBitsLHC(pin, offsIndex, dims);
			Bits.insertBits(ia, offs, IK_WIDTH(dims) + dims*postLenStored);
			//insert key
			Bits.writeArray(ia, offs, IK_WIDTH(dims), hcPos);
			//insert value:
			offs += IK_WIDTH(dims);
			for (int i = 0; i < key.length; i++) {
				Bits.writeArray(ia, offs, postLenStored(), key[i]);
				offs += postLenStored();
			}
			values = tree.objPool().insertSpaceAtPos(values, pin, bufEntryCnt+1);
			values[pin] = value;
		}
	}

	/**
	 * Get post-fix.
	 * @param pin pos in node
	 * @param hcPos HC pos
	 * @param inOutPrefix Input key with prefix. This may be modified in this method!
	 *              After the method call, this contains the postfix if the postfix matches the
	 * range. Otherwise it contains only part of the postfix.
	 * @param outKey Postfix output if the entry is a postfix
	 * @param rangeMin minimum
	 * @param rangeMax maximum
	 * @return Subnode or value if the postfix matches the range, otherwise NOT_FOUND.
	 */
	Object checkAndGetEntryPIN(int pin, long hcPos, long[] inOutPrefix, long[] outKey,
			long[] rangeMin, long[] rangeMax) {
		Object o = values[pin]; 
		if (o == null) {
			return null;
		}
		
		if (o instanceof Node) {
			return checkAndApplyInfix(((Node)o).getInfixLen(), pin, hcPos, 
					inOutPrefix, rangeMin, rangeMax) ? o : null;
		}
		
		if (checkAndGetPost(pin, hcPos, inOutPrefix, outKey, rangeMin, rangeMax)) {
			return o;
		}
		return null;
	}

	private static int N_GOOD = 0;
	private static int N = 0;
	
	private boolean checkAndApplyInfix(int infixLen, int pin, long hcPos, long[] valTemplate, 
			long[] rangeMin, long[] rangeMax) {
		int dims = valTemplate.length;
		//first check if node-prefix allows sub-node to contain any useful values
		int subOffs = pinToOffsBitsData(pin, hcPos, dims);
		
		if (PhTreeHelper.DEBUG) {
			N_GOOD++;
			//Ensure that we never enter this method if the node cannot possibly contain a match.
			long maskClean = mask1100(getPostLen());
			for (int dim = 0; dim < valTemplate.length; dim++) {
				if ((valTemplate[dim]&maskClean) > rangeMax[dim] || 
						(valTemplate[dim] | ~maskClean) < rangeMin[dim]) {
					if (getPostLen() < 63) {
						//							System.out.println("N-CAAI-min=" + Bits.toBinary(rangeMin[dim]));
						//							System.out.println("N-CAAI-val=" + Bits.toBinary(valTemplate[dim]));
						//							System.out.println("N-CAAI-max=" + Bits.toBinary(rangeMax[dim]));
						//							System.out.println("N-CAAI-msk=" + Bits.toBinary(maskClean));
						//							System.out.println("pl=" + getPostLen() + "  dim=" + dim);
						System.out.println("N-CAAI: " + ++N + " / " + N_GOOD);
						//THis happen for kNN when rangeMin/max are adjusted.
						throw new IllegalStateException("pl=" + getPostLen());
					}
					//ignore, this happens with negative values.
					//return false;
				}
			}
		}
		//	return true;
		//}
		
		if (!hasSubInfix(subOffs, dims)) {
			applyHcPos(hcPos, valTemplate);
			return true;
		}

		//assign infix
		//Shift in two steps in case they add up to 64.
		long maskClean = mask1100(postLenStored());

		//first, clean trailing bits
		//Mask for comparing the tempVal with the ranges, except for bit that have not been
		//extracted yet.
		long compMask = mask1100(postLenStored() - infixLen);
		for (int dim = 0; dim < valTemplate.length; dim++) {
			long inFull = (valTemplate[dim] & maskClean) | Bits.readArray(ba, subOffs, postLenStored());
			long in = inFull & compMask;
			if (in > rangeMax[dim] || (in | ~compMask) < rangeMin[dim]) {
				return false;
			}
			valTemplate[dim] = inFull;
			subOffs += postLenStored();
		}

		return true;
	}

	private static long mask1100(int zeroBits) {
		return zeroBits == 64 ? 0 : ((-1L) << zeroBits);
	}

	private void applyHcPos(long hcPos, long[] valTemplate) {
		PhTreeHelper.applyHcPos(hcPos, getPostLen(), valTemplate);
	}
	
	
	private boolean checkAndGetPost(int pin, long hcPos, long[] inPrefix, long[] outKey, 
			long[] rangeMin, long[] rangeMax) {
		long[] ia = ba;
		int offs = pinToOffsBitsData(pin, hcPos, rangeMin.length);
		final long mask = mask1100(postLenStored());
		for (int i = 0; i < outKey.length; i++) {
			long k = (inPrefix[i] & mask) | Bits.readArray(ia, offs, postLenStored());
			if (k < rangeMin[i] || k > rangeMax[i]) {
				return false;
			}
			outKey[i] = k;
			offs += postLenStored();
		}
		return true;
	}


	private Object removeEntry(long hcPos, int posInNode, final int dims, PhTree13<?> tree) {
		final int bufEntryCnt = getEntryCount();
		//switch representation (HC <-> Linear)?
		if (isAHC() && shouldSwitchToLHC(bufEntryCnt, dims)) {
			//revert to linearized representation, if applicable
			Object oldVal = switchAhcToLhcAndShrink(bufEntryCnt, dims, hcPos, tree);
			decEntryCount();
			return oldVal;
		}			

		int offsIndex = getBitPosIndex();
		Object oldVal;
		if (isAHC()) {
			//hyper-cube
			oldVal = values[(int) hcPos]; 
			values[(int) hcPos] = null;
			//Nothing else to do, values can just stay where they are
		} else {
			//linearized cube:
			//remove key and value
			int posBit = pinToOffsBitsLHC(posInNode, offsIndex, dims);
			Bits.removeBits(ba, posBit, IK_WIDTH(dims) + dims*postLenStored());
			//shrink array
			ba = tree.longPool().arrayTrim(ba, calcArraySizeTotalBits(bufEntryCnt-1, dims));
			//values:
			oldVal = values[posInNode]; 
			values = tree.objPool().removeSpaceAtPos(values, posInNode, bufEntryCnt-1);
		}

		decEntryCount();
		return oldVal;
	}


	/**
	 * @return True if the post-fixes are stored as hyper-cube
	 */
	boolean isAHC() {
		return isHC;
	}


	/**
	 * Set whether the post-fixes are stored as hyper-cube.
	 */
	void setAHC(boolean b) {
		isHC = b;
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


	private int posToOffsBitsDataAHC(long hcPos, int offsIndex, int dims) {
		return offsIndex + INN_HC_WIDTH * (1<<dims) + postLenStored() * dims * (int)hcPos;
	}
	
	private int pinToOffsBitsDataLHC(int pin, int offsIndex, int dims) {
		return offsIndex + (IK_WIDTH(dims) + postLenStored() * dims) * pin + IK_WIDTH(dims);
	}
	
	int pinToOffsBitsLHC(int pin, int offsIndex, int dims) {
		return offsIndex + (IK_WIDTH(dims) + postLenStored() * dims) * pin;
	}
	
	private int pinToOffsBitsData(int pin, long hcPos, int dims) {
		int offsIndex = getBitPosIndex();
		if (isAHC()) {
			return posToOffsBitsDataAHC(hcPos, offsIndex, dims);
		} else {
			return pinToOffsBitsLHC(pin, offsIndex, dims) + IK_WIDTH(dims);
		}
	}
	
	/**
	 * 
	 * @param hcPos HC pos
	 * @param dims dimensions
	 * @return The position of the entry, for example as in the value[]. 
	 */
	int getPosition(long hcPos, final int dims) {
		if (isAHC()) {
			//hyper-cube
			int posInt = (int) hcPos;  //Hypercube can not be larger than 2^31
			return (values[posInt] != null) ? posInt : -(posInt)-1;
		} else {
			//linearized cube
			int offsInd = getBitPosIndex();
			return Bits.binarySearch(ba, offsInd, getEntryCount(), hcPos, IK_WIDTH(dims),
					dims * postLenStored());
		}
	}

	int getInfixLen() {
		return infixLenStored() - 1;
	}
	
	private boolean requiresInfix() {
		return getInfixLen() > 0;
	}

	private int infixLenStored() {
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

	Object[] values() {
		return values;
	}
	
	long[] ba() {
		return ba;
	}

}
