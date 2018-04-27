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
	
	public static Object addEntry(long[] ba, BSTree<Object> ind, long hcPos, long[] kdKey, Object value, Node node) {
		return ind.put(hcPos, new BSTEntry(kdKey, value));
	}

	public static Object removeEntry(long[] ba, BSTree<Object> ind, long hcPos, int dims, long[] key, 
			long[] newKey, int[] insertRequired, Node node) {
		Object prev = ind.remove(hcPos);
		if (newKey != null) {
			throw new UnsupportedOperationException();
		}
		return prev;
	}

	public static Object getEntry(long[] ba, BSTree<Object> ind, long hcPos, long[] outKey, long[] keyToMatch,
			Node object2) {
		LLEntry e = ind.get(hcPos);
		BSTEntry be = (BSTEntry) e.getValue();
		System.arraycopy(be.getKdKey(), 0, outKey, 0, outKey.length);
		//TODO check keyToMatch?!??!
		return e.getValue(); 
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
