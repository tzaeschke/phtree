/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v11hd2.nt;

import ch.ethz.globis.pht64kd.MaxKTreeHdI.NtEntry;
import ch.ethz.globis.phtree.v11hd.BitsHD;

/**
 * Iterator over a NodeTree.
 * 
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public class NtNodeIteratorFull<T> {
	
	private static final long FINISHED = Long.MAX_VALUE; 
	private static final long START = -1; 
	
	private boolean isHC;
	private long next;
	private NtNode<T> nextSubNode;
	private NtNode<T> node;
	private int currentOffsetKey;
	private int nMaxEntry;
	private int nFound = 0;
	private int postEntryLenLHC;
	//The valTemplate contains the known prefix
	private final long[] prefix;
	private final long maxPos;

	/**
	 */
	public NtNodeIteratorFull(int keyBitWidth) {
		//TODO?!?!? Reuse valTemplate???
		this.prefix = BitsHD.newArray(keyBitWidth);
		this.maxPos = ~((-1L) << NtNode.MAX_DIM);
	}
	
	/**
	 * 
	 * @param node
	 * @param globalMinMask The minimum value that any found value should have. If the found value is
	 *  lower, the search continues.
	 * @param globalMaxMask
	 * @param prefix
	 */
	private void reinit(NtNode<T> node, long[] valTemplate) {
		BitsHD.set(prefix, valTemplate); //TODO
		next = START;
		nextSubNode = null;
		currentOffsetKey = 0;
		nFound = 0;
	
		this.node = node;
		this.isHC = node.isAHC();
		nMaxEntry = node.getEntryCount();
		//Position of the current entry
		currentOffsetKey = node.getBitPosIndex();
		if (!isHC) {
			//length of post-fix WITH key
			postEntryLenLHC = NtNode.IK_WIDTH(NtNode.MAX_DIM) + NtNode.MAX_DIM*node.getPostLen();
		}
	}

	/**
	 * Advances the cursor. 
	 * @return TRUE iff a matching element was found.
	 */
	boolean increment(NtEntry<T> result) {
		getNext(result);
		return next != FINISHED;
	}

	long getCurrentPos() {
		return next;
	}

	/**
	 * Return whether the next value returned by next() is a sub-node or not.
	 * 
	 * @return True if the current value (returned by next()) is a sub-node, 
	 * otherwise false
	 */
	boolean isNextSub() {
		return nextSubNode != null;
	}

	/**
	 * 
	 * @return False if the value does not match the range, otherwise true.
	 */
	@SuppressWarnings("unchecked")
	private boolean readValue(int pin, long pos, NtEntry<T> result) {
		Object v = node.getValueByPIN(pin);
		
		if (v == null) {
			return false;
		}
		
		node.localReadAndApplyReadPostfixAndHc(pin, pos, prefix);
		
		if (v instanceof NtNode) {
			NtNode<T> sub = (NtNode<T>) v;
			nextSubNode = sub;
		} else {
			nextSubNode = null;
			node.getKdKeyByPIN(pin, result.getKdKey());
			result.setValue(v == NodeTreeV11.NT_NULL ? null : (T)v);
		}
		result.setKey(prefix);
		return true;
	}

	private void getNext(NtEntry<T> result) {
		if (isHC) {
			getNextAHC(result);
		} else {
			getNextLHC(result);
		}
	}
	
	private void getNextAHC(NtEntry<T> result) {
		long currentPos = next == START ? 0 : next+1; 
		while (currentPos <= maxPos) {
			if (readValue((int)currentPos, currentPos, result)) {
				next = currentPos;
				return;
			}
			currentPos++;  //pos w/o bit-offset
		}
		next = FINISHED;
	}
	
	private void getNextLHC(NtEntry<T> result) {
		while (++nFound <= nMaxEntry) {
			long currentPos = 
					Bits.readArray(node.ba, currentOffsetKey, NtNode.IK_WIDTH(NtNode.MAX_DIM));
			currentOffsetKey += postEntryLenLHC;
			//check post-fix
			if (readValue(nFound-1, currentPos, result)) {
				next = currentPos;
				return;
			}
		}
		next = FINISHED;
	}
	

	public NtNode<T> getCurrentSubNode() {
		return nextSubNode;
	}

	public NtNode<T> node() {
		return node;
	}

	void init(long[] valTemplate, NtNode<T> node, boolean isNegativeRoot) {
		this.node = node; //for calcLimits
		reinit(node, valTemplate);
	}

	public long[] getPrefix() {
		//TODO returning this is dangerous?!?!?
		return prefix;
	}
}
