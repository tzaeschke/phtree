/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich
 * and Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v13SynchedPool;

import ch.ethz.globis.pht64kd.MaxKTreeI.NtEntry;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.v13SynchedPool.nt.NtIteratorMask;


/**
 * This NodeIterator reuses existing instances, which may be easier on the Java GC.
 * 
 * 
 * @author ztilmann
 *
 * @param <T> value type
 */
public class NodeIteratorNoGC<T> {
	
	private static final long START = -1; 
	
	private final int dims;
	private boolean isHC;
	private boolean isNI;
	private long next;
	private Node node;
	private int currentOffsetKey;
	private NtIteratorMask<Object> niIterator;
	private int nMaxEntry;
	private int nFound = 0;
	private int postEntryLenLHC;
	private final long[] valTemplate;
	private long maskLower;
	private long maskUpper;
	private long[] rangeMin;
	private long[] rangeMax;
	private boolean useHcIncrementer;
	private boolean useNiHcIncrementer;
	private PhFilter checker;

	/**
	 *
	 * @param dims dimensions
	 * @param valTemplate A null indicates that no values are to be extracted.
	 */
	public NodeIteratorNoGC(int dims, long[] valTemplate) {
		this.dims = dims;
		this.valTemplate = valTemplate;
	}

	/**
	 *
	 * @param node node
	 * @param rangeMin The minimum value that any found value should have. If the found value is
	 *  lower, the search continues.
	 * @param rangeMax see rangeMin
	 * @param checker result verifier, can be null.
	 */
	private void reinit(Node node, long[] rangeMin, long[] rangeMax, PhFilter checker) {
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		next = START;
		currentOffsetKey = 0;
		nFound = 0;
		this.checker = checker;

		this.node = node;
		this.isHC = node.isAHC();
		this.isNI = node.isNT();
		nMaxEntry = node.getEntryCount();
		if (!isHC) {
			//Position of the current entry
			currentOffsetKey = node.getBitPosIndex();
			//length of post-fix WITH key
			postEntryLenLHC = Node.IK_WIDTH(dims) + dims*node.postLenStored();
		}

		useHcIncrementer = false;
		useNiHcIncrementer = false;

		if (dims > 6) {
			initHCI();
		}

		if (isNI && !useNiHcIncrementer) {
			if (niIterator == null) {
				niIterator = new NtIteratorMask<>(dims);
			}
			niIterator.reset(node.ind(), maskLower, maskUpper);
		}
	}

	private void initHCI() {
		//LHC, NI, ...
		long maxHcAddr = ~((-1L)<<dims);
		int nSetFilterBits = Long.bitCount(maskLower | ((~maskUpper) & maxHcAddr));
		//nPossibleMatch = (2^k-x)
		long nPossibleMatch = 1L << (dims - nSetFilterBits);
		if (isNI) {
			int nChild = node.ntGetSize();
			int logNChild = Long.SIZE - Long.numberOfLeadingZeros(nChild);
			//the following will overflow for k=60
			boolean useHcIncrementer = (nChild > nPossibleMatch*(double)logNChild*2);
			//DIM < 60 as safeguard against overflow of (nPossibleMatch*logNChild)
			if (useHcIncrementer && PhTree13SP.HCI_ENABLED && dims < 50) {
				useNiHcIncrementer = true;
			} else {
				useNiHcIncrementer = false;
			}
		} else if (PhTree13SP.HCI_ENABLED){
			if (isHC) {
				//nPossibleMatch < 2^k?
				//PAPER
				useHcIncrementer = nPossibleMatch*2 <= maxHcAddr;
			} else {
				int logNPost = Long.SIZE - Long.numberOfLeadingZeros(nMaxEntry) + 1+1;
				useHcIncrementer = (nMaxEntry >= 2*nPossibleMatch*(double)logNPost);
			}
		}
	}

	/**
	 * Advances the cursor.
	 * @return TRUE iff a matching element was found.
	 */
	boolean increment(PhEntry<T> result) {
		return getNext(result);
	}

	/**
	 *
	 * @return False if the value does not match the range, otherwise true.
	 */
	@SuppressWarnings("unchecked")
	private boolean readValue(int pin, long pos, PhEntry<T> result) {
		Object o = node.checkAndGetEntryPIN(pin, pos, valTemplate, result.getKey(),
				rangeMin, rangeMax);
		if (o == null) {
			return false;
		}

		if (o instanceof Node) {
			Node sub = (Node) o;
			//skip this for postLen>=63
			if (checker != null && sub.getPostLen() < (PhTree13SP.DEPTH_64-1) &&
					!checker.isValid(sub.getPostLen()+1, valTemplate)) {
				return false;
			}
			result.setNodeInternal(sub);
			return true;
		}

		if (checker != null && !checker.isValid(result.getKey())) {
			return false;
		}
		result.setValueInternal((T) o);
		return true;
	}

	private boolean readValue(long pos, Object value, PhEntry<T> result) {
		if (!node.checkAndGetEntryNt(pos, value, result, valTemplate, rangeMin, rangeMax)) {
			return false;
		}

		//subnode ?
		if (value instanceof Node) {
			Node sub = (Node) value;
			//skip this for postLen>=63
			if (checker != null && sub.getPostLen() < (PhTree13SP.DEPTH_64-1) &&
					!checker.isValid(sub.getPostLen()+1, valTemplate)) {
				return false;
			}
			return true;
		}

		return checker == null || checker.isValid(result.getKey());
	}

	private boolean getNextHCI(PhEntry<T> result) {
		//Ideally we would switch between b-serch-HCI and incr-search depending on the expected
		//distance to the next value.
		long currentPos = next;
		do {
			if (currentPos == START) {
				//starting position
				currentPos = maskLower;
			} else {
				currentPos = PhTree13SP.inc(currentPos, maskLower, maskUpper);
				if (currentPos <= maskLower) {
					return false;
				}
			}

			int pin = node.getPosition(currentPos, dims);
			if (pin >= 0 && readValue(pin, currentPos, result)) {
				next = currentPos;
				return true;
			}
		} while (true);
	}

	private boolean getNext(PhEntry<T> result) {
		if (isNI) {
			return niFindNext(result);
		}

		if (useHcIncrementer) {
			return getNextHCI(result);
		} else if (isHC) {
			return getNextAHC(result);
		} else {
			return getNextLHC(result);
		}
	}

	private boolean getNextAHC(PhEntry<T> result) {
		//while loop until 1 is found.
		long currentPos = next == START ? maskLower-1 : next;
		while (true) {
			currentPos++;  //pos w/o bit-offset
			if (currentPos > maskUpper) {
				return false;
			}
			//check HC-pos
			if (checkHcPos(currentPos) && readValue((int)currentPos, currentPos, result)) {
				next = currentPos;
				return true;
			}
		}
	}

	private boolean getNextLHC(PhEntry<T> result) {
		while (true) {
			if (++nFound > nMaxEntry) {
				return false;
			}
			long currentPos = Bits.readArray(node.ba(), currentOffsetKey, Node.IK_WIDTH(dims));
			currentOffsetKey += postEntryLenLHC;
			//check HC-pos
			if (checkHcPos(currentPos)) {
				//check post-fix
				if (readValue(nFound-1, currentPos, result)) {
					next = currentPos; //This is required for kNN-adjusting of iterators
					return true;
				}
			} else if (currentPos > maskUpper) {
				return false;
			}
		}
	}


	private boolean niFindNext(PhEntry<T> result) {
		return useNiHcIncrementer ? niFindNextHCI(result) : niFindNextIter(result);
	}

	private boolean niFindNextIter(PhEntry<T> result) {
		while (niIterator.hasNext()) {
			NtEntry<Object> e = niIterator.nextEntryReuse();
			System.arraycopy(e.getKdKey(), 0, result.getKey(), 0, dims);
			if (readValue(e.key(), e.value(), result)) {
				next = e.getKey(); //This is required for kNN-adjusting of iterators
				return true;
			}
		}
		return false;
	}

	private boolean niFindNextHCI(PhEntry<T> result) {
		//HCI
		//repeat until we found a value inside the given range
		long currentPos = next;
		do {
			if (currentPos != START && currentPos >= maskUpper) {
				break;
			}

			if (currentPos == START) {
				//starting position
				currentPos = maskLower;
			} else {
				currentPos = PhTree13SP.inc(currentPos, maskLower, maskUpper);
				if (currentPos <= maskLower) {
					break;
				}
			}

			Object v = node.ntGetEntry(currentPos, result.getKey(), valTemplate);
			if (v == null) {
				continue;
			}

			next = currentPos;

			//read and check post-fix
			if (readValue(currentPos, v, result)) {
				return true;
			}
		} while (true);
		return false;
	}


	private boolean checkHcPos(long pos) {
		return ((pos | maskLower) & maskUpper) == pos;
	}

	public Node node() {
		return node;
	}

	private void calcLimits(long[] rangeMin, long[] rangeMax) {
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
		int postLen = node.getPostLen();
		long maskHcBit = 1L << postLen;
		long maskVT = (-1L) << postLen;
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

		this.maskLower = lowerLimit;
		this.maskUpper = upperLimit;
	}
	
	boolean adjustMinMax(long[] rangeMin, long[] rangeMax) {
		calcLimits(rangeMin, rangeMax);

		if (next >= this.maskUpper) {
			//we already fully traversed this node
			return false;
		}

		if (next < this.maskLower) {
			if (isHC) {
				currentOffsetKey = node.getBitPosIndex();
				next = START;
			} else if (isNI) {
				if (!useNiHcIncrementer) {
					niIterator.adjustMinMax(maskLower, maskUpper);
				} else {
					next = START;
				}
			} else {
				//LHC
				if (this.next + PhTree13SP.LHC_BINARY_SEARCH_THRESHOLD < this.maskLower) {
					int pin = node.getPosition(maskLower, dims);
					//If we don't find it we use the next following entry, i.e. -(pin+1)
					pin = pin >= 0 ? pin : -(pin+1); 
					currentOffsetKey = node.pinToOffsBitsLHC(pin, node.getBitPosIndex(), dims);
					nFound = pin;
				} 
				//just set it to START, it is not used and will be set during the next iteration
				next = START;
			}
			return true;
		}
			
		if ((useHcIncrementer || useNiHcIncrementer) && !checkHcPos(next)) {
			//Adjust pos in HCI mode such that it works for the next inc()
			//At this point, next is >= maskLower
			long pos = next-1;
			//After the following, pos==START or pos==(a valid entry such that inc(pos) is
			//the next valid entry after the original `next`)
			while (!checkHcPos(pos) && pos > START) {
				//normal iteration to ensure we to get a valid POS for HCI-inc()
				pos--;
			}
			next = pos;
		}
		return true;
	}

	void init(long[] rangeMin, long[] rangeMax, Node node, PhFilter checker) {
		this.node = node; //for calcLimits
		calcLimits(rangeMin, rangeMax);
		reinit(node, rangeMin, rangeMax, checker);
	}

	boolean verifyMinMax() {
		long mask = (-1L) << node.getPostLen()+1;
		for (int i = 0; i < valTemplate.length; i++) {
			if ((valTemplate[i] | ~mask) < rangeMin[i] ||
					(valTemplate[i] & mask) > rangeMax[i]) {
				return false;
			}
		}
		return true;
	}
}
