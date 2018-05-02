/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14;

import java.io.ObjectOutputStream.PutField;
import java.util.List;

import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.util.Refs;
import ch.ethz.globis.phtree.v14.bst.BSTIteratorMinMax;
import ch.ethz.globis.phtree.v14.bst.BSTIteratorMinMax.LLEntry;
import ch.ethz.globis.phtree.v14.bst.BSTree;

public class BSTHandler {

	public static class BSTEntry {
		private final long[] kdKey;
		private Object value;
		public BSTEntry(long[] k, Object v) {
			kdKey = k;
			value = v;
		}
		public long[] getKdKey() {
			return kdKey;
		}
		public Object getValue() {
			return value;
		}
		public void setValue(Object v) {
			this.value = v;
		}
		
	}
	
	static Object addEntry(BSTree<BSTEntry> ind, long hcPos, long[] kdKey, Object value, Node phNode) {
		//TODO for replace, can we reuse the existing key???
		BSTEntry be = ind.put(hcPos, new BSTEntry(kdKey, value), (BSTEntry oldEntry, BSTEntry newEntry) -> {
			//This returns the value to be returned for B) and C)
			
			//A) 
			if (oldEntry == null) {
				//In this case, BSTree need to assign the new Entry
//				System.out.println("CH 1"); //TODO
				if (phNode != null) {
//					System.out.println("CH 1a"); //TODO
					phNode.incEntryCount();
				}
//				System.out.println("CH 1b"); //TODO
				return null;
			}
			
			//B)
			if (phNode == null) {
//				System.out.println("CH B"); //TODO
				oldEntry.setValue(value);
				return null;
			}
			
			//C)
			//We two entries in the same location (local hcPos).
			//Now we need to compare the kdKeys.
			//If they are identical, we either replace the VALUE or return the SUB-NODE
			// (that's actually the same, simply return the VALUE)
			//If the kdKey differs, we have to split, insert a newSubNode and return null.

			//C)
			Object localVal = oldEntry.getValue();
			if (localVal instanceof Node) {
				Node subNode = (Node) localVal;
				long mask = phNode.calcInfixMask(subNode.getPostLen());
//				System.out.println("CH c1"); //TODO
				return insertSplitPH(oldEntry, newEntry, mask, phNode);
			} else {
				if (phNode.getPostLen() > 0) {
					long mask = phNode.calcPostfixMask();
//					System.out.println("CH c2"); //TODO
					return insertSplitPH(oldEntry, newEntry, mask, phNode);
				}
				//perfect match -> replace value
//				System.out.println("CH c2x"); //TODO
				oldEntry.setValue(value);
				newEntry.setValue(localVal);
				return newEntry;
			}
		});
		return be == null ? null : be.getValue();
		
		//Contract:
		//phNode==null -> replace instead of split! 
		//Otherwise:
		// - A) entry does not exist -> add / incCounter(phNode)
		// - B) entry exists, phNode == null -> replace
		// - C) entry exists: -> check infix/postfix
		//   - C.1) value==Node: conflict ? split;insert-new-node;return-null : return Node
		//   - C.2) value==obj:  conflict ? split;insert-new-node;return-null : return prevValue
		//
		//Summary:
		// - A) set T, return null; 
		// - B) set T, (return prev T)
		// - C.1) set newNode return null || set (), return (prev)T
		// - C.2) set newNode return null || set T, return prev T
		// A) is handled by BST.put(), B)/C) is handled by lambda
		
		//A)
//		long localHcPos = NtNode.pos2LocalPos(hcPos, currentNode.getPostLen());
//		int pin = currentNode.getPosition(localHcPos, NtNode.MAX_DIM);
//		if (pin < 0) {
//			//insert
//			currentNode.localAddEntryPIN(pin, localHcPos, hcPos, kdKey, value);
//			incCounter(phNode);
//			return null;
//		}
//
//
//		//external postfix is not checked  
//		if (phNode == null) {
//			//B)
//			return (T) currentNode.localReplaceEntry(pin, kdKey, value);
//		} else {
//			//What do we have to do?
//			//We two entries in the same location (local hcPos).
//			//Now we need to compare the kdKeys.
//			//If they are identical, we either replace the VALUE or return the SUB-NODE
//			// (that's actually the same, simply return the VALUE)
//			//If the kdKey differs, we have to split, insert a newSubNode and return null.
//
//			//C)
//			if (localVal instanceof Node) {
//				Node subNode = (Node) localVal;
//				long mask = phNode.calcInfixMask(subNode.getPostLen());
//				return (T) insertSplitPH(kdKey, value, localVal, pin, 
//						mask, currentNode, phNode);
//			} else {
//				if (phNode.getPostLen() > 0) {
//					long mask = phNode.calcPostfixMask();
//					return (T) insertSplitPH(kdKey, value, localVal, pin, 
//							mask, currentNode, phNode);
//				}
//				//perfect match -> replace value
//				currentNode.localReplaceValue(pin, value);
//				return (T) value;
//			}
//		}
	}

	private static BSTEntry insertSplitPH(BSTEntry currentEntry, BSTEntry newEntry, long mask, Node phNode) {
		if (mask == 0) {
			//There won't be any split, no need to check.
			return currentEntry;
		}
		long[] localKdKey = currentEntry.getKdKey();
		long[] newKey = newEntry.getKdKey();
		Object currentValue = currentEntry.getValue();
		Object newValue = newEntry.getValue();
		int maxConflictingBits = Node.calcConflictingBits(newKey, localKdKey, mask);
		if (maxConflictingBits == 0) {
			if (!(currentValue instanceof Node)) {
				//replace value
//				System.out.println("CHis 1"); //TODO
				currentEntry.setValue(newValue);
				newEntry.setValue(currentValue);
				//newEntry now contains the previous value
				return newEntry;
			}
//			System.out.println("CHis 2"); //TODO
			return currentEntry;
		}
		
		Node newNode = phNode.createNode(newKey, newValue, 
						localKdKey, currentValue, maxConflictingBits);

		//replace value
		currentEntry.setValue(newNode);
		//entry did not exist
//		System.out.println("CHis 3"); //TODO
        return null;
	}
	
	static Object removeEntry(BSTree<BSTEntry> ind, long hcPos, long[] key, 
			long[] newKey, int[] insertRequired, Node phNode) {
		//Only remove value-entries, node-entries are simply returned without removing them
		BSTEntry prev = ind.remove(hcPos, e -> (e != null && !(e.value instanceof Node) && matches(e, key, phNode)) );
		//return values: 
		// - null -> not found / remove failed
		// - Node -> recurse node
		// - T -> remove success
		//Node: removeing a node is never necessary: When values are removed from the PH-Tree, nodes are replaced
		// with vales from sub-nodes, but they are never simply removed.
		if (newKey != null) {
			if (prev != null && prev.getValue() != null && !(prev.getValue() instanceof Node)) {
				//replace
				int bitPosOfDiff = Node.calcConflictingBits(key, newKey, -1L);
				//TODO fixme
				if (bitPosOfDiff <= phNode.getPostLen()) {
					//replace
//					return replacePost(pinToDelete, hcPos, newKey);
					return addEntry(ind, hcPos, newKey, prev.getValue(), phNode);
				} else {
					insertRequired[0] = bitPosOfDiff;
				}
				//throw new UnsupportedOperationException();
			}
		}
		return prev == null ? null : prev.getValue();
	}

	static Object getEntry(BSTree<BSTEntry> ind, long hcPos, long[] outKey, long[] keyToMatch, Node node) {
		LLEntry e = ind.get(hcPos);
		if (e == null) {
			return null;
		}
		BSTEntry be = (BSTEntry) e.getValue();
		if (keyToMatch != null) {
			if (!matches(be, keyToMatch, node)) {
				return null;
			}
		}
		if (outKey != null) {  //TODO de we need outkey?
			System.arraycopy(be.getKdKey(), 0, outKey, 0, outKey.length);
		}
		return be.getValue(); 
	}

	private static boolean matches(BSTEntry be, long[] keyToMatch, Node node) {
		//This is always 0, unless we decide to put several keys into a single array
		final int offs = 0;
		if (be.getValue() instanceof Node) {
			Node sub = (Node) be.getValue();
			//TODO we are currently nmot setting this, so we can't read it...
//TODO				if (node.hasSubInfix(offs, dims)) {
				final long mask = node.calcInfixMask(sub.getPostLen());
				if (!readAndCheckKdKey(be.getKdKey(), offs, keyToMatch, mask, node.postLenStored())) {
					return false;
				}
//			}
		} else {
			final long mask = node.calcPostfixMask();
			if (!readAndCheckKdKey(be.getKdKey(), offs, keyToMatch, mask, node.postLenStored())) {
				return false;
			}
		}
		return true;
	}
	
	private static boolean readAndCheckKdKey(long[] allKeys, int offs, long[] keyToMatch, long mask, int postLen) {
		for (int i = 0; i < keyToMatch.length; i++) {
//			long k = Bits.readArray(allKeys, offs, postLen);
//			if (((k ^ keyToMatch[i]) & mask) != 0) {
//				return false;
//			}
//			offs += postLen;
			if (((allKeys[i] ^ keyToMatch[i]) & mask) != 0) {
				return false;
			}
		}
		return true;
	}

	void addPostPIN(long hcPos, int pin, long[] key, Object value, Node node) {
		final int dims = key.length;
		final int bufEntryCnt = node.getEntryCount();
		//decide here whether to use hyper-cube or linear representation
		//--> Initially, the linear version is always smaller, because the cube has at least
		//    two entries, even for a single dimension. (unless DIM > 2*REF=2*32 bit 
		//    For one dimension, both need one additional bit to indicate either
		//    null/not-null (hypercube, actually two bit) or to indicate the index. 

		node.incEntryCount();

		//get position
		pin = -(pin+1);

		//resize array
		node.setBa( Bits.arrayEnsureSize(node.ba(), calcArraySizeTotalBits(bufEntryCnt+1, dims)) );
		long[] ia = node.ba();
		int offs = pinToOffsBitsLHC(pin, dims);
		Bits.insertBits(ia, offs, ENTRY_WIDTH(dims));
		//insert key
		Bits.writeArray(ia, offs, IK_WIDTH(dims), hcPos);
		//insert value:
		offs += IK_WIDTH(dims); 
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
		//TODO
/*		lsdflsadjlf;jsl;djfl;sajdl;jsf;l
		for (int i = 0; i < key.length; i++) {
			Bits.writeArray(ia, offs, postLenStored(), key[i]);
			offs += postLenStored();
		}
		*/
		node.setValues( Refs.insertSpaceAtPos(node.values(), pin, bufEntryCnt+1) );
		node.values()[pin] = value;
	}

	@Deprecated
	private int pinToOffsBitsLHC(int pin, int offsIndex, int dims) {
		return pinToOffsBitsLHC(pin, dims);
	}
	
	private int pinToOffsBitsLHC(int pin, int dims) {
		return pin*ENTRY_WIDTH(dims);
	}

	@Deprecated
	private int getBitPosIndex() {
		return 0;
	}

	private int calcArraySizeTotalBits(int nEntries, int dims) {
		return nEntries*ENTRY_WIDTH(dims);
	}

	private static int IK_WIDTH(int dims) {
		return dims;
	}

	private static int HT_WIDTH() {
		//32 bits for HT-key
		return 31;
	}
	
	private static int ENTRY_WIDTH(int dims) {
		return IK_WIDTH(dims) + HT_WIDTH();
	}

	static void getStats(BSTree<BSTEntry> ind, PhTreeStats stats, int dims, List<Object> entries) {
		BSTIteratorMinMax<BSTEntry> iter = ind.iterator();
		while (iter.hasNextULL()) {
			entries.add(iter.nextULL().getValue());
		}
	}
	
}
