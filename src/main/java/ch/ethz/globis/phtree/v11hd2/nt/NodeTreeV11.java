/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v11hd2.nt;

import static ch.ethz.globis.phtree.PhTreeHelper.align8;

import java.util.List;

import ch.ethz.globis.pht64kd.MaxKTreeHdI;
import ch.ethz.globis.phtree.util.IntVar;
import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.v11hd2.Bits;
import ch.ethz.globis.phtree.v11hd.BitsHD;
import ch.ethz.globis.phtree.v11hd2.Node;

/**
 * NodeTrees are a way to represent Nodes that are to big to be represented as AHC or LHC nodes.
 * 
 * A NodeTree splits a k-dimensional node into a hierarchy of smaller nodes by splitting,
 * for example, the 16-dim key into 2 8-dim keys.
 * 
 * Unlike the normal PH-Tree, NodeTrees do not support infixes. (TODO?)
 * 
 * @author ztilmann
 *
 * @param <T> The value type of the tree 
 */
public class NodeTreeV11<T> implements MaxKTreeHdI {

	//Enable HC incrementer / iteration
	static final boolean HCI_ENABLED = true; 
	//Enable AHC mode in nodes
	static final boolean AHC_ENABLED = true; 
	//This needs to be different from PhTree NULL to avoid confusion when extracting values.
	public static final Object NT_NULL = new Object();

	private static int WARNINGS = 0;

	
	protected final IntVar nEntries = new IntVar(0);
	//Number of bit in the global key: [1..64].
	private final int keyBitWidth;
	
	private NtNode<T> root = null;

	private NodeTreeV11(int keyBitWidth) {
		if (keyBitWidth < 1 || keyBitWidth > 64) {
			throw new UnsupportedOperationException("keyBitWidth=" + keyBitWidth);
		}
		this.keyBitWidth = keyBitWidth;
		this.root = NtNode.createRoot(getKeyBitWidth());
	}

	/**
	 * @param keyBitWidth bit width of keys, for example 64
	 * @return A new NodeTree
     * @param <T> value type
	 */
	public static <T> NodeTreeV11<T> create(int keyBitWidth) {
		return new NodeTreeV11<>(keyBitWidth);
	}
	
	/**
	 * 
	 * @param root The root of this internal tree.
	 * @param hcPos The 'key' in this node tree
	 * @param kdKey The key of the key-value that is stored under the hcPos
	 * @param value The value of the key-value that is stored under the hcPos
	 * @return The previous value at the position, if any.
	 */
	private static <T> T addEntry(NtNode<T> root, long[] hcPos, 
			long[] kdKey, Object value, IntVar entryCount) {
		T t = addEntry(root, hcPos, kdKey, value, (Node)null);
		if (t == null) {
			entryCount.inc();
		}
		return t;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T addEntry(NtNode<T> root, long[] hcPos, 
			long[] kdKey, Object value, Node phNode) {
		NtNode<T> currentNode = root;
		final long[] postInFix = new long[hcPos.length];
		while (true) {
			BitsHD.set0(postInFix);
			long localHcPos = NtNode.pos2LocalPos(hcPos, currentNode.getPostLen());
			int pin = currentNode.getPosition(localHcPos, NtNode.MAX_DIM);
			if (pin < 0) {
				//insert
				currentNode.localAddEntryPIN(pin, localHcPos, hcPos, kdKey, value);
				incCounter(phNode);
				return null;
			}

			Object localVal = currentNode.getValueByPIN(pin);
			boolean isSubNode = localVal instanceof NtNode;
			int conflictingLevels;
			NtNode<T> sub = null;
			if (isSubNode) {
				sub = (NtNode<T>) localVal;
				//check infix if infixLen > 0
				if (currentNode.getPostLen() - sub.getPostLen() > 1) {
					currentNode.localReadInfix(pin, localHcPos, postInFix);
					conflictingLevels = NtNode.getConflictingLevels(hcPos, postInFix, 
							currentNode.getPostLen(), sub.getPostLen());
				} else {
					conflictingLevels = 0;
				}
			} else {
				currentNode.localReadPostfix(pin, localHcPos, postInFix);
				conflictingLevels = NtNode.getConflictingLevels(hcPos, postInFix, currentNode.getPostLen());
			}				

			if (conflictingLevels != 0) {
				int newPostLen =  conflictingLevels - 1;
				NtNode<T> newNode = NtNode.createNode(newPostLen, kdKey.length);
				currentNode.localReplaceEntryWithSub(pin, localHcPos, hcPos, newNode);
				long localHcInSubOfNewEntry = NtNode.pos2LocalPos(hcPos, newPostLen);
				long localHcInSubOfPrevEntry = NtNode.pos2LocalPos(postInFix, newPostLen);
				//TODO assume pin=0 for first entry?
				newNode.localAddEntry(localHcInSubOfNewEntry, hcPos, kdKey, value);
				long[] localKdKey = new long[kdKey.length];
				currentNode.readKdKeyPIN(pin, localKdKey);
				newNode.localAddEntry(localHcInSubOfPrevEntry, postInFix, localKdKey, localVal);
				incCounter(phNode);
				return null;
			}
			
			if (isSubNode) {
				//traverse subNode
				currentNode = sub;
			} else {
				//identical internal postFixes.
				
				//external postfix is not checked  
				if (phNode == null) {
					return (T) currentNode.localReplaceEntry(pin, kdKey, value);
				} else {
					//What do we have to do?
					//We two entries in the same location (local hcPos).
					//Now we need to compare the kdKeys.
					//If they are identical, we either replace the VALUE or return the SUB-NODE
					// (that's actually the same, simply return the VALUE)
					//If the kdKey differs, we have to split, insert a newSubNode and return null.
					
					if (localVal instanceof Node) {
						Node subNode = (Node) localVal;
						//TODO
						//TODO
						//TODO
//						if (hasSubInfix(offs, dims)) {
							long mask = phNode.calcInfixMask(subNode.getPostLen());
							return (T) insertSplitPH(kdKey, value, localVal, pin, 
									mask, currentNode, phNode);
//						}
//						return v;
					} else {
						if (phNode.getPostLen() > 0) {
							long mask = phNode.calcPostfixMask();
							return (T) insertSplitPH(kdKey, value, localVal, pin, 
									mask, currentNode, phNode);
						}
						//perfect match -> replace value
						currentNode.localReplaceValue(pin, value);
						return (T) value;
					}
				}
			}
		}
	}
	
	/**
	 * Increases the entry count of the NtTree. For PhTree nodes,
	 * this means increasing the entry count of the node.
	 */
	private static void incCounter(Node node) {
		if (node != null) {
			node.incEntryCount();
		}
	}
	
	private static Object insertSplitPH(long[] newKey, Object newValue, Object currentValue,
			int pin, long mask, NtNode<?> currentNode, Node phNode) {
		long[] localKdKey = new long[newKey.length];
		currentNode.readKdKeyPIN(pin, localKdKey);
		int maxConflictingBits = Node.calcConflictingBits(newKey, localKdKey, mask);
		if (maxConflictingBits == 0) {
			if (!(currentValue instanceof Node)) {
				currentNode.localReplaceValue(pin, newValue);
			}
			return currentValue;
		}
		
		Node newNode = phNode.createNode(newKey, newValue, 
						localKdKey, currentValue, maxConflictingBits);

		currentNode.localReplaceEntry(pin, newKey, newNode);
		//entry did not exist
        return null;
	}
	
	/**
	 * Remove an entry from the tree.
	 * @param root root node 
	 * @param hcPos HC-pos
	 * @param outerDims dimensions of main tree
	 * @param entryCount entry counter object
	 * @return The value of the removed key or null
     * @param <T> value type
	 */
	public static <T> Object removeEntry(
			NtNode<T> root, long[] hcPos, int outerDims, IntVar entryCount) {
		Object t = removeEntry(root, hcPos, outerDims, null, null, null, (Node)null);
		if (t != null) {
			entryCount.dec();
		}
		return t;
	}
	
	/**
	 * Removes an entry from the tree.
	 * @param root parent node
	 * @param hcPos HC-pos
	 * @param outerDims dimensions in main tree
	 * @param keyToMatch key
	 * @param newKey new key (for updates)
	 * @param insertRequired insert required?
	 * @param phNode parent node
	 * @return The value of the removed key or null
     * @param <T> value type
	 */
	@SuppressWarnings("unchecked")
	public static <T> Object removeEntry(NtNode<T> root, long[] hcPos, int outerDims, 
			long[] keyToMatch, long[] newKey, int[] insertRequired, Node phNode) {
    	NtNode<T> parentNode = null;
    	int parentPin = -1;
    	long parentHcPos = -1;
    	NtNode<T> currentNode = root;
		final long[] postInFix = new long[hcPos.length];
		while (true) {
			BitsHD.set0(postInFix);
			long localHcPos = NtNode.pos2LocalPos(hcPos, currentNode.getPostLen());
			int pin = currentNode.getPosition(localHcPos, NtNode.MAX_DIM);
			if (pin < 0) {
				//Not found
				return null;
			}

			Object localVal = currentNode.getValueByPIN(pin);
			boolean isLocalSubNode = localVal instanceof NtNode;
			boolean conflictingLevels;
			NtNode<T> sub = null;
			if (isLocalSubNode) {
				sub = (NtNode<T>) localVal;
				//check infix if infixLen > 0
				if (currentNode.getPostLen() - sub.getPostLen() > 1) {
					currentNode.localReadInfix(pin, localHcPos, postInFix);
					conflictingLevels = NtNode.hasConflictingLevels(hcPos, postInFix, 
							currentNode.getPostLen(), sub.getPostLen());
				} else {
					conflictingLevels = false;
				}
			} else {
				currentNode.localReadPostfix(pin, localHcPos, postInFix);
				conflictingLevels = NtNode.hasConflictingLevels(hcPos, postInFix, currentNode.getPostLen());
			}				

			if (conflictingLevels) {
				//no match
				return null;
			}
			
			if (isLocalSubNode) {
				//traverse local subNode
				parentNode = currentNode;
				parentPin = pin;
				parentHcPos = localHcPos;
				currentNode = sub;
			} else {
				//perfect match, now we should remove the value (which can be a normal sub-node!)
				
				if (phNode != null) {
					Object o = phGetIfKdMatches(keyToMatch, currentNode, pin, localVal, phNode); 
					//compare kdKey!
					if (o instanceof Node) {
						//This is a node, simply return it for further tarversal
						return o;
					}
					if (o == null) {
						//no match
						return null;
					}
					
					//Check for update()
					if (newKey != null) {
						//replace
						int bitPosOfDiff = Node.calcConflictingBits(keyToMatch, newKey, -1L);
						if (bitPosOfDiff <= phNode.getPostLen()) {
							//replace
							return currentNode.replaceEntry(pin, newKey, localVal);
						} else {
							insertRequired[0] = bitPosOfDiff;
						}
					}
					
					//okay, we have a matching postfix, continue...
					phNode.decEntryCount();
				}
				
				//TODO why read T again?
				T ret = (T) currentNode.removeValue(localHcPos, pin, outerDims, NtNode.MAX_DIM);
				//Ignore for n>2 or n==0 (empty root node)
				if (parentNode != null && currentNode.getEntryCount() == 1) {
					//insert remaining entry into parent.
					int pin2 = currentNode.findFirstEntry(NtNode.MAX_DIM);
					long localHcPos2 = currentNode.localReadKey(pin2);
					Object val2 = currentNode.getValueByPIN(pin2);
					
					long[] postInfix2 = new long[hcPos.length];  //TODO pool?
					//get prefix
					BitsHD.set(postInfix2, hcPos);
					//read infix, postfix, ...
					currentNode.localReadAndApplyReadPostfixAndHc(pin2, localHcPos2, postInfix2);

					parentNode.replacePost(parentPin, parentHcPos, postInfix2, NtNode.MAX_DIM);
					long[] kdKey2 = new long[outerDims];
					currentNode.readKdKeyPIN(pin2, kdKey2);
					parentNode.localReplaceEntry(parentPin, kdKey2, val2);
					currentNode.discardNode();
				}
				return ret;
			}
		}
		
	}
	
	private static Object phGetIfKdMatches(long[] keyToMatch,
			NtNode<?> currentNodeNt, int pinNt, Object currentVal, Node phNode) {
		if (currentVal instanceof Node) {
			Node sub = (Node) currentVal;
			//if (hasSubInfix(offs, dims)) {
				final long mask = phNode.calcInfixMask(sub.getPostLen());
				if (!currentNodeNt.readKdKeyAndCheck(pinNt, keyToMatch, mask)) {
					//no match
					return null;
				}
			//}
			return currentVal;
		} else {
			final long mask = phNode.calcPostfixMask();
			if (!currentNodeNt.readKdKeyAndCheck(pinNt, keyToMatch, mask)) {
				//no match
				return null;
			}
			
			//So we have a match and an entry to remove
			//We simply remove it an l;ett Node handle the merging, if required.
			return currentVal;
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <T> Object getEntry(NtNode<T> root, long[] hcPos, long[] outKey, 
			long[] kdKeyToMatch, Node phNode) {
		NtNode<T> currentNode = root;
		final long[] postInFix = new long[hcPos.length];
		while (true) {
			BitsHD.set0(postInFix);
			long localHcPos = NtNode.pos2LocalPos(hcPos, currentNode.getPostLen());
			int pin = currentNode.getPosition(localHcPos, NtNode.MAX_DIM);
			if (pin < 0) {
				//Not found
				return null;
			}
			
			Object localVal;
			if (outKey != null) {
				localVal = currentNode.getEntryByPIN(pin, outKey);
			} else {
				localVal = currentNode.getValueByPIN(pin);
			}
			boolean isLocalSubNode = localVal instanceof NtNode;
			boolean conflictingLevels;
			NtNode<T> sub = null;
			if (isLocalSubNode) {
				sub = (NtNode<T>) localVal;
				//check infix if infixLen > 0
				if (currentNode.getPostLen() - sub.getPostLen() > 1) {
					currentNode.localReadInfix(pin, localHcPos, postInFix);
					conflictingLevels = NtNode.hasConflictingLevels(hcPos, postInFix, 
							currentNode.getPostLen(), sub.getPostLen());
				} else {
					conflictingLevels = false;
				}
			} else {
				currentNode.localReadPostfix(pin, localHcPos, postInFix);
				conflictingLevels = NtNode.hasConflictingLevels(hcPos, postInFix, currentNode.getPostLen());
			}				

			if (conflictingLevels) {
				//no match
				return null;
			}
			
			if (isLocalSubNode) {
				//traverse local subNode
				currentNode = sub;
			} else {
				//identical postFixes, so we return the value (which can be a normal sub-node!)
				//compare kdKey, null indicates 'no match'.
				if (kdKeyToMatch != null && phGetIfKdMatches(
								kdKeyToMatch, currentNode, pin, localVal, phNode) == null) {
					//no match
					return null;
			}
				return localVal;
			}
		}
	}

	/**
	 * Replace only the value of an entry.
	 * @param root The root of this internal tree.
	 * @param hcPos The 'key' in this node tree
	 * @param value The value of the key-value that is stored under the hcPos
	 * @return The previous value at the position, if any.
     * @param <T> value type
	 */
	@SuppressWarnings("unchecked")
	public static <T> T replaceValue(NtNode<T> root, long[] hcPos, Object value) {
		NtNode<T> currentNode = root;
		final long[] postInFix = new long[hcPos.length]; //TODO pool?
		while (true) {
			BitsHD.set0(postInFix);
			long localHcPos = NtNode.pos2LocalPos(hcPos, currentNode.getPostLen());
			int pin = currentNode.getPosition(localHcPos, NtNode.MAX_DIM);
			if (pin < 0) {
				//not found
				throw new IllegalArgumentException();
			}

			Object localVal = currentNode.getValueByPIN(pin);
			boolean isSubNode = localVal instanceof NtNode;
			boolean conflictingLevels;
			NtNode<T> sub = null;
			if (isSubNode) {
				sub = (NtNode<T>) localVal;
				//check infix if infixLen > 0
				if (currentNode.getPostLen() - sub.getPostLen() > 1) {
					currentNode.localReadInfix(pin, localHcPos, postInFix);
					conflictingLevels = NtNode.hasConflictingLevels(hcPos, postInFix, 
							currentNode.getPostLen(), sub.getPostLen());
				} else {
					conflictingLevels = false;
				}
			} else {
				currentNode.localReadPostfix(pin, localHcPos, postInFix);
				conflictingLevels = NtNode.hasConflictingLevels(hcPos, postInFix, currentNode.getPostLen());
			}				

			if (conflictingLevels) {
				//not found
				throw new IllegalArgumentException();
			}
			
			if (isSubNode) {
				//traverse subNode
				currentNode = sub;
			} else {
				//identical internal postFixes.
				//external postfix is not checked, this method should only be called  
				return (T)currentNode.localReplaceValue(pin, value);
			}
		}
	}
	
	/**
	 * Best HC incrementer ever. 
	 * @param v
	 * @param min
	 * @param max
	 * @return next valid value or min.
	 */
	static long inc(long v, long min, long max) {
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~max);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r++;
		//remove invalid bits.
		return (r & max) | min;

		//return -1 if we exceed 'max' and cause an overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		//return (r <= v) ? -1 : r;
	}

	@Override
	public int size() {
		return nEntries.get();
	}

	void increaseNrEntries() {
		nEntries.inc();
	}
	
	void decreaseNrEntries() {
		nEntries.dec();
	}

	@Override
	public int getKeyBitWidth() {
		return keyBitWidth;
	}

	@Override
	public NtNode<T> getRoot() {
		return root;
	}
	
	public T put(long[] key, long[] kdKey, T value) {
		return NodeTreeV11.addEntry(
				root, key, kdKey, value == null ? NT_NULL : value, nEntries);
	}
	
	public boolean putB(long[] key, long[] kdKey) {
		return NodeTreeV11.addEntry(
				root, key, kdKey, NT_NULL, nEntries) != null;
	}
	
	public boolean contains(long[] key, long[] outKdKey) {
		return NodeTreeV11.getEntry(root, key, outKdKey, null, null) != null;
	}
	
	@SuppressWarnings("unchecked")
	public T get(long[] key, long[] outKdKey) {
		Object ret = NodeTreeV11.getEntry(root, key, outKdKey, null, null);
		return ret == NT_NULL ? null : (T)ret;
	}
	
	@SuppressWarnings("unchecked")
	public T remove(long[] key) {
		Object ret = NodeTreeV11.removeEntry(root, key, getKeyBitWidth(), nEntries);
		return ret == NT_NULL ? null : (T)ret;
	}
	
	public boolean removeB(long[] key) {
		Object ret = NodeTreeV11.removeEntry(root, key, getKeyBitWidth(), nEntries);
		return ret != null;
	}
	
	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		printTree(sb, root);
		return sb.toString();
	}
	
	@SuppressWarnings("unchecked")
	private void printTree(StringBuilderLn str, NtNode<T> node) {
		int indent = NtNode.calcTreeHeight(getKeyBitWidth()) - node.getPostLen();
		String pre = "";
		for (int i = 0; i < indent; i++) {
			pre += "-";
		}
		str.append(pre + "pl=" + node.getPostLen());
		str.append(";ec=" + node.getEntryCount());
		str.appendLn("; ID=" + node);
		
		long[] kdKey = new long[getKeyBitWidth()];
		for (int i = 0; i < (1<<NtNode.MAX_DIM); i++) {
			int pin = node.getPosition(i, NtNode.MAX_DIM);
			if (pin >= 0) {
				Object v = node.getEntryByPIN(pin, kdKey);
				if (v instanceof NtNode) {
					str.append(pre + i + " ");
					printTree(str, (NtNode<T>) v);
				} else {
					str.appendLn(pre + i + " " + Bits.toBinary(kdKey) + " v=" + v);
				}
			}
		}
	}
	
	public NtIteratorMask<T> queryWithMask(long[] minMask, long[] maxMask) {
		NtIteratorMask<T> it = new NtIteratorMask<>(getKeyBitWidth(), minMask, maxMask);
		it.reset(root);
		return it;
	}
	
	@SuppressWarnings("unused")
	public PhIterator64<T> query(long min, long max) {
		throw new UnsupportedOperationException();
//		NtIteratorMinMax<T> it = new NtIteratorMinMax<>(getKeyBitWidth());
// 		it.reset(root, min, max);
//		return it;
	}
	
	public PhIterator64<T> iterator() {
		throw new UnsupportedOperationException();
//		NtIteratorMinMax<T> it = new NtIteratorMinMax<>(getKeyBitWidth());
//		it.reset(root, Long.MIN_VALUE, Long.MAX_VALUE);
//		return it;
	}
	
	public boolean checkTree() {
		System.err.println("Not implemented: checkTree()");
		return true;
	}

	/**
	 * Collect tree statistics.
	 * @param node node to look at
	 * @param stats statistics object
	 * @param dims dimensions
	 * @param entryBuffer entry list
	 */
	public static void getStats(NtNode<?> node, PhTreeStats stats, int dims, 
			List<Object> entryBuffer) {
		final int REF = 4;

		//Counter for NtNodes
		stats.nNtNodes++;

		// ba[] + values[] + kdKey[] + postLen + isAHC + entryCount
		stats.size += align8(12 + REF + REF + REF + 1 + 1 + 2);

		int nNodeEntriesFound = 0;
		for (Object o: node.values()) {
			if (o == null) {
				continue;
			}
			nNodeEntriesFound++;
			if (o instanceof NtNode) {
				getStats((NtNode<?>) o, stats, dims, entryBuffer);
			} else {
				//subnode entry or postfix entry
				entryBuffer.add(o);
			}
		}
		if (nNodeEntriesFound != node.getEntryCount()) {
			System.err.println("WARNING: entry count mismatch: found/ntec=" + 
					nNodeEntriesFound + "/" + node.getEntryCount());
		}
		//count children
		//nChildren += node.getEntryCount();
		stats.size += 16 + align8(node.ba.length * Long.BYTES);
		stats.size += 16 + align8(node.values().length * REF);
		stats.size += 16 + align8(node.kdKeys().length * Long.BYTES);
		
		if (dims<=31 && node.getEntryCount() > (1L<<dims)) {
			System.err.println("WARNING: Over-populated node found: ntec=" + node.getEntryCount());
		}
		
		//check space
		int baS = node.calcArraySizeTotalBits(node.getEntryCount(), dims);
		baS = Bits.calcArraySize(baS);
		if (baS < node.ba.length) {
			System.err.println("Array too large in NT(" + ++WARNINGS + "): " + 
					node.ba.length + " - " + baS + " = " + (node.ba.length - baS));
		}
	}
	
}
