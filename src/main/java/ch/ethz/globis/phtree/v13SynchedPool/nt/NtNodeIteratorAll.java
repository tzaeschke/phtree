/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v13SynchedPool.nt;

import ch.ethz.globis.pht64kd.MaxKTreeI.NtEntry;

/**
 * Iterator over a NodeTree.
 * 
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public class NtNodeIteratorAll<T> {
	
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
	private long prefix;
	//TODO do we need this?
	private final long localMax;

	/**
	 */
	public NtNodeIteratorAll() {
		this.localMax = ~((-1L) << NtNode.MAX_DIM);
	}

	/**
	 *
	 * @param node
	 * @param prefix
	 */
	private void reinit(NtNode<T> node, long prefix) {
		this.node = node;
		this.prefix = prefix;
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

		prefix = node.localReadAndApplyReadPostfixAndHc(pin, pos, prefix);

		if (v instanceof NtNode) {
			NtNode<T> sub = (NtNode<T>) v;
			nextSubNode = sub;
		} else {
			nextSubNode = null;
			node.getKdKeyByPIN(pin, result.getKdKey());
			result.setValue(v == NodeTreeV13.NT_NULL ? null : (T)v);
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
		while (currentPos <= localMax) {
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
			//check HC-pos
			if (currentPos <= localMax) {
				//check post-fix
				if (readValue(nFound-1, currentPos, result)) {
					next = currentPos;
					return;
				}
			} else {
				break;
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

	void init(long valTemplate, NtNode<T> node) {
		reinit(node, valTemplate);
	}

	public long getPrefix() {
		return prefix;
	}
}
