/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
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

import java.util.List;

import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.util.Refs;

/**
 * A NodeIterator that returns a list instead of an Iterator AND reuses the NodeIterator.
 * 
 * This seems to be slower than the simple NodeIteratorList. Why? 
 * The reason could be that the simple NIL creates many objects, but they end up in the stack
 * memory because they can not escape.
 * The NILReuse creates much less objects, but they end up in an array for reuse, which means
 * they may not be on the stack.... 
 * 
 * @author ztilmann
 *
 * @param <T> value type
 * @param <R> result type
 */
public class NodeIteratorListReuse<T, R> {
	
	private class PhIteratorStack {
		@SuppressWarnings("unchecked")
		private final NodeIterator[] stack =
				Refs.newArray(NodeIteratorListReuse.NodeIterator.class,64);
		private int size = 0;


		NodeIterator prepare() {
			NodeIterator ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIterator();
				stack[size-1] = ni;
			}
			return ni;
		}

		NodeIterator pop() {
			return stack[--size];
		}
	}

	private final int dims;
	private final PhResultList<T,R> results;
	private int maxResults;
	private final long[] valTemplate;
	private long[] rangeMin;
	private long[] rangeMax;

	private final PhIteratorStack pool;
	
	private final class NodeIterator {
	
		private Node node;
		private int nMaxEntry;
		private int nEntryFound = 0;
		private long maskLower;
		private long maskUpper;
		private boolean useHcIncrementer;

		/**
		 * 
		 * @param node node
		 * @param lower The minimum HC-Pos that a value should have.
		 * @param upper see 'lower'
		 */
		void reinitAndRun(Node node, long lower, long upper) {
			this.node = node;
			nMaxEntry = node.getEntryCount();
			this.nEntryFound = 0;
			this.maskLower = lower;
			this.maskUpper = upper;

			useHcIncrementer = false;
			if (dims > 6) {
				initHCI();
			}

			getAll();
		}

		private void initHCI() {
			//LHC, NI, ...
			long maxHcAddr = ~((-1L)<<dims);
			int nSetFilterBits = Long.bitCount(maskLower | ((~maskUpper) & maxHcAddr));
			//nPossibleMatch = (2^k-x)
			long nPossibleMatch = 1L << (dims - nSetFilterBits);
			if (PhTree13.HCI_ENABLED){
				if (node.isAHC()) {
					//nPossibleMatch < 2^k?
					useHcIncrementer = nPossibleMatch < maxHcAddr;
				} else {
					int logNPost = Long.SIZE - Long.numberOfLeadingZeros(nMaxEntry) + 1;
					useHcIncrementer = (nMaxEntry > nPossibleMatch*(double)logNPost); 
				}
			}
		}
		
		private void checkAndAddResult(PhEntry<T> e) {
			results.phOffer(e);
		}

		private void checkAndRunSubnode(Node sub, PhEntry<T> e) {
			if (e != null) {
				results.phReturnTemp(e);
			}
			if (results.phIsPrefixValid(valTemplate, sub.getPostLen()+1)) {
				run(sub);
			}
		}

		@SuppressWarnings("unchecked")
		private void readValue(int pin, long pos) {
			PhEntry<T> resultBuffer = results.phGetTempEntry();
			long[] key = resultBuffer.getKey();
			Object o = node.checkAndGetEntryPIN(pin, pos, valTemplate, key, rangeMin, rangeMax);
 			if (o == null) {
				results.phReturnTemp(resultBuffer);
				return;
			}
			if (o instanceof Node) {
				checkAndRunSubnode( (Node) o, resultBuffer );
				return;
			}
			resultBuffer.setValue((T) o);
			checkAndAddResult(resultBuffer);
		}

		private void getAllHCI() {
			//Ideally we would switch between b-serch-HCI and incr-search depending on the expected
			//distance to the next value.
			long currentPos = maskLower;
			do {
				int pin = node.getPosition(currentPos, dims);
				if (pin >= 0) {
					readValue(pin, currentPos);
				}
				
				currentPos = PhTree13.inc(currentPos, maskLower, maskUpper);
				if (currentPos <= maskLower) {
					break;
				}
			} while (results.size() < maxResults);
		}


		private void getAll() {
			if (useHcIncrementer) {
				getAllHCI();
			} else if (node.isAHC()) {
				getAllAHC();
			} else {
				getAllLHC();
			}
		}

		private void getAllAHC() {
			//Position of the current entry
			long currentPos = maskLower; 
			while (results.size() < maxResults) {
				//check HC-pos
				if (checkHcPos(currentPos)) {
					//check post-fix
					readValue((int) currentPos, currentPos);
				}

				currentPos++;  //pos w/o bit-offset
				if (currentPos > maskUpper) {
					break;
				}
			}
		}

		private void getAllLHC() {
			//Position of the current entry
			int currentOffsetKey = node.getBitPosIndex();
			//length of post-fix WITH key
			int postEntryLenLHC = Node.IK_WIDTH(dims) + dims*node.postLenStored();
			while (results.size() < maxResults) {
				if (++nEntryFound > nMaxEntry) {
					break;
				}
				long currentPos = Bits.readArray(node.ba(), currentOffsetKey, Node.IK_WIDTH(dims));
				currentOffsetKey += postEntryLenLHC;
				//check HC-pos
				if (!checkHcPos(currentPos)) {
					if (currentPos > maskUpper) {
						break;
					}
				} else {
					//check post-fix
					readValue(nEntryFound - 1, currentPos);
				}
			}
		}

		private boolean checkHcPos(long pos) {
			return ((pos | maskLower) & maskUpper) == pos;
		}
	}
	
	NodeIteratorListReuse(int dims, PhResultList<T, R> results) {
		this.dims = dims;
		this.valTemplate = new long[dims];
		this.results = results;
		this.pool = new PhIteratorStack();
	}

	List<R> resetAndRun(Node node, long[] rangeMin, long[] rangeMax, int maxResults) {
		results.clear();
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.maxResults = maxResults;
		run(node);
		return results;
	}
	
	private void run(Node node) {
		//create limits for the local node. there is a lower and an upper limit. Each limit
		//consists of a series of DIM bit, one for each dimension.
		//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
		//not need to be queried.
		//For the upper limit, a '0' indicates that the 'higher' half does not need to be 
		//queried.
		//
		//              ||  lowerLimit=0 || lowerLimit=1 || upperLimit = 0 || upperLimit = 1
		// =============||===================================================================
		// query lower  ||     YES             NO
		// ============ || ==================================================================
		// query higher ||                                     NO               YES
		//
		long maskHcBit = 1L << node.getPostLen();
		long maskVT = (-1L) << node.getPostLen();
		long lowerLimit = 0;
		long upperLimit = 0;
		//to prevent problems with signed long when using 64 bit
		if (maskHcBit >= 0) { //i.e. postLen < 63
			for (int i = 0; i < valTemplate.length; i++) {
				lowerLimit <<= 1;
				upperLimit <<= 1;
				long nodeBisection = (valTemplate[i] | maskHcBit) & maskVT; 
				if (rangeMin[i] >= nodeBisection) {
					//==> set to 1 if lower value should not be queried 
					lowerLimit |= 1L;
				}
				if (rangeMax[i] >= nodeBisection) {
					//Leave 0 if higher value should not be queried.
					upperLimit |= 1L;
				}
			}
		} else {
			//special treatment for signed longs
			//The problem (difference) here is that a '1' at the leading bit does indicate a
			//LOWER value, opposed to indicating a HIGHER value as in the remaining 63 bits.
			//The hypercube assumes that a leading '0' indicates a lower value.
			//Solution: We leave HC as it is.

			for (int i = 0; i < valTemplate.length; i++) {
				lowerLimit <<= 1;
				upperLimit <<= 1;
				if (rangeMin[i] < 0) {
					//If minimum is positive, we don't need the search negative values 
					//==> set upperLimit to 0, prevent searching values starting with '1'.
					upperLimit |= 1L;
				}
				if (rangeMax[i] < 0) {
					//Leave 0 if higher value should not be queried
					//If maximum is negative, we do not need to search positive values 
					//(starting with '0').
					//--> lowerLimit = '1'
					lowerLimit |= 1L;
				}
			}
		}
		NodeIterator nIt = pool.prepare();
		nIt.reinitAndRun(node, lowerLimit, upperLimit);
		pool.pop();
	}

}
