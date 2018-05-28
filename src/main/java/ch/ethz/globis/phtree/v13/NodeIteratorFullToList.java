/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v13;

import ch.ethz.globis.pht64kd.MaxKTreeI.NtEntry;
import ch.ethz.globis.phtree.PhEntryDist;
import ch.ethz.globis.phtree.v13.PhQueryKnnHS.KnnResultList;
import ch.ethz.globis.phtree.v13.nt.NtIteratorMinMax;



/**
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public class NodeIteratorFullToList<T> {
	
	private final int dims;
	private Node node;
	private NtIteratorMinMax<Object> ntIterator;
	private long[] prefix;
	private final long maxPos;


	/**
	 * 
	 * @param dims dimensions
	 */
	public NodeIteratorFullToList(int dims) {
		this.dims = dims;
		this.maxPos = (1L << dims) -1;
	}
	
	/**
	 * 
	 * @param node
	 * @param resultList
	 * @param prefix 
	 */
	private void reinit(Node node, KnnResultList<T> resultList, long[] prefix) {
		this.node = node;
		this.prefix = prefix;
		
		getAll(resultList);
	}

	
	@SuppressWarnings("unchecked")
	private void readValue(int posInNode, long hcPos, KnnResultList<T> resultList) {
		if (node.values()[posInNode] == null) {
			return;
		}
		PhEntryDist<T> result = resultList.phGetTempEntry(); 
		long[] key = result.getKey();
		System.arraycopy(prefix, 0, key, 0, prefix.length);
		//The key is the current (and future) prefix as well as key of key-value
		Object v = node.getEntryPIN(posInNode, hcPos, key, key);
		if (v == null) {
			throw new IllegalStateException();
		}
		
		if (v instanceof Node) {
			result.setNodeInternal(v);
		} else {
			//ensure that 'node' is set to null
			result.setValueInternal((T) v );
		}
		resultList.phOffer(result);
	}

	
	@SuppressWarnings("unchecked")
	private void readValue(long[] kdKey, Object value, KnnResultList<T> resultList) {
		PhEntryDist<T> result = resultList.phGetTempEntry(); 
		if (value instanceof Node) {
			Node sub = (Node) value;
			System.arraycopy(kdKey, 0, result.getKey(), 0, kdKey.length);
			result.setNodeInternal(sub);
		} else {
			System.arraycopy(kdKey, 0, result.getKey(), 0, kdKey.length);
			//ensure that 'node' is set to null
			result.setValueInternal((T) value);
		}
		resultList.phOffer(result);
	}


	private void getAll(KnnResultList<T> resultList) {
		if (node.isNT()) {
			niFindNext(resultList);
		} else if (node.isAHC()) {
			getAllAHC(resultList);
		} else {
			getNextLHC(resultList);
		}
	}
	
	private void getAllAHC(KnnResultList<T> resultList) {
		//while loop until 1 is found.
		long currentPos = 0; 
		do {
			readValue((int) currentPos, currentPos, resultList);
		} while (++currentPos <= maxPos);
	}
	
	private void getNextLHC(KnnResultList<T> resultList) {
		int currentOffsetKey = node.getBitPosIndex();
		int postEntryLenLHC = Node.IK_WIDTH(dims)+dims*node.postLenStored();
		long currentPos;
		int nEntriesFound = 0;
		int nMaxEntries = node.getEntryCount();
		while (nEntriesFound < nMaxEntries) {
			nEntriesFound++;
			currentPos = Bits.readArray(node.ba(), currentOffsetKey, Node.IK_WIDTH(dims));
			currentOffsetKey += postEntryLenLHC;
			readValue(nEntriesFound-1, currentPos, resultList);
		}
	}
	
	private void niFindNext(KnnResultList<T> resultList) {
		if (ntIterator == null) {
			ntIterator = new NtIteratorMinMax<>(dims);
		}
		ntIterator.reset(node.ind(), 0, Long.MAX_VALUE);
		while (ntIterator.hasNext()) {
			NtEntry<Object> e = ntIterator.nextEntryReuse();
			readValue(e.getKdKey(), e.value(), resultList);
		}
	}

	void init(Node node, KnnResultList<T> resultList, long[] prefix) {
		reinit(node, resultList, prefix);
	}

}
