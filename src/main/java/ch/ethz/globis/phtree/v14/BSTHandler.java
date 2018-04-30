/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14;

import ch.ethz.globis.phtree.util.Refs;
import ch.ethz.globis.phtree.v14.bst.BSTree;
import ch.ethz.globis.phtree.v14.bst.BSTIteratorMinMax.LLEntry;

public class BSTHandler {

	public static class BSTEntry {
		private final long[] kdKey;
		private final Object value;
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
		
	}
	
	public static Object addEntry(long[] ba, BSTree<BSTEntry> ind, long hcPos, long[] kdKey, Object value, Node node) {
		//TODO for replace, can we reuse the existing key???
		BSTEntry be = ind.put(hcPos, new BSTEntry(kdKey, value), e -> {
			if (e != null && e.getValue()) {
				//TODO pass in Predicate from outside, or at least a flag to indicate which predicate to use, 
				//such as: overwrite (move point with same hcPos), split, or merge. 
			}
		});
		return be == null ? null : be.getValue();
	}

	public static Object removeEntry(long[] ba, BSTree<BSTEntry> ind, long hcPos, int dims, long[] key, 
			long[] newKey, int[] insertRequired, Node node) {
		//Only remove value-entries, node-entries are simply returned without removing them
		BSTEntry prev = ind.remove(hcPos, e -> (e != null && !(e.value instanceof Node) && matches(e, key, node)) );
		//return values: 
		// - null -> not found / remove failed
		// - Node -> recurse node
		// - T -> remove success
		//Node: removeing a node is never necessary: When values are removed from the PH-Tree, nodes are replaced
		// with vales from sub-nodes, but they are never simply removed.
		if (newKey != null) {
			throw new UnsupportedOperationException();
		}
		return prev == null ? null : prev.getValue();
	}

	public static Object getEntry(long[] ba, BSTree<BSTEntry> ind, long hcPos, long[] outKey, long[] keyToMatch,
			Node node) {
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
	
}
