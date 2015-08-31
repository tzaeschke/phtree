/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v5;

import java.util.ArrayList;

import org.zoodb.index.critbit.CritBit64COW.Entry;
import org.zoodb.index.critbit.CritBit64COW.QueryIteratorMask;

import ch.ethz.globis.pht.PhPredicate;
import ch.ethz.globis.pht.util.PhMapper;
import ch.ethz.globis.pht.v5.PhTree5.Node;
import ch.ethz.globis.pht.v5.PhTree5.NodeEntry;

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
 * @param <T>
 * @param <R>
 */
public class NodeIteratorListReuse<T, R> {
	
	public class PhIteratorStack {
		//TODO this should (but doesn't) compile if we remove <T,R> from NodeIterator (line 68)
		private final NodeIterator[] stack = new NodeIterator[64];
		private int size = 0;


		public NodeIterator prepare(Node<T> node) {
			if (!PhTree5.checkAndApplyRange(node, valTemplate, rangeMin, rangeMax)) {
				return null;
			}
			NodeIterator ni = stack[size++];
			if (ni == null)  {
				ni = new NodeIterator();
				stack[size-1] = ni;
			}
			return ni;
		}

		public NodeIterator pop() {
			NodeIterator ret = stack[--size];
			stack[size] = null;
			return ret;
		}
	}

	private final int DIM;
	private final ArrayList<R> results;
	private final int maxResults;
	private final PhPredicate filter;
	private final PhMapper<T, R> mapper;
	private final long[] valTemplate;
	private final long[] rangeMin;
	private final long[] rangeMax;

	private final PhIteratorStack pool;
	
	private final class NodeIterator<T2,R2> {
	
		private boolean isPostHC;
		private boolean isPostNI;
		private boolean isSubHC;
		private int postLen;
		private boolean isDepth0;
		private long next = -1;
		private long nextPost = -1;
		private long nextSub = -1;
		private Node<T> node;
		private int currentOffsetPostKey;
		private int currentOffsetPostVal;
		private int currentOffsetSub;
		private QueryIteratorMask<NodeEntry<T>> niIterator;
		private int nMaxPost;
		private int nMaxSub;
		private int nPostsFound = 0;
		private int posSubLHC = -1; //position in sub-node LHC array
		private int postEntryLen;
		private long maskLower;
		private long maskUpper;
		private boolean usePostHcIncrementer;
		private boolean useSubHcIncrementer;
		private boolean isPostFinished;
		private boolean isSubFinished;

		/**
		 * 
		 * @param node
		 * @param DIM
		 * @param valTemplate A null indicates that no values are to be extracted.
		 * @param lower The minimum HC-Pos that a value should have.
		 * @param upper
		 * @param minValue The minimum value that any found value should have. If the found value is
		 *  lower, the search continues.
		 * @param maxValue
		 */
		private void reinitAndRun(Node<T> node, long lower, long upper, boolean isDepth0) {
			this.node = node;
			this.isPostHC = node.isPostHC();
			this.isPostNI = node.isPostNI();
			this.isSubHC = node.isSubHC();
			this.postLen = node.getPostLen();
			this.isDepth0 = isDepth0;
			nMaxPost = node.getPostCount();
			nMaxSub = node.getSubCount();
			isPostFinished = (nMaxPost <= 0);
			isSubFinished = (nMaxSub <= 0);
			this.maskLower = lower;
			this.maskUpper = upper;
			//Position of the current entry
			currentOffsetSub = node.getBitPos_SubNodeIndex(DIM);
			if (isPostNI) {
				postEntryLen = -1; //not used
			} else {
				currentOffsetPostKey = node.getBitPos_PostIndex(DIM);
				// -set key offset to position before first element
				// -set value offset to first element
				if (isPostHC) {
					//length of post-fix WITHOUT key
					postEntryLen = DIM*postLen;
					currentOffsetPostVal = currentOffsetPostKey + (1<<DIM)*Node.PINN_HC_WIDTH;  
				} else {
					//length of post-fix WITH key
					postEntryLen = Node.PIK_WIDTH(DIM)+DIM*postLen;
					currentOffsetPostVal = currentOffsetPostKey + Node.PIK_WIDTH(DIM);  
				}
			}


			useSubHcIncrementer = false;
			usePostHcIncrementer = false;

			if (DIM > 3) {
				//LHC, NI, ...
				long maxHcAddr = ~((-1L)<<DIM);
				int nSetFilterBits = Long.bitCount(maskLower | ((~maskUpper) & maxHcAddr));
				//nPossibleMatch = (2^k-x)
				long nPossibleMatch = 1L << (DIM - nSetFilterBits);
				if (isPostNI) {
					int nChild = node.ind().size();
					int logNChild = Long.SIZE - Long.numberOfLeadingZeros(nChild);
					//the following will overflow for k=60
					boolean useHcIncrementer = (nChild > nPossibleMatch*(double)logNChild*2);
					//DIM < 60 as safeguard against overflow of (nPossibleMatch*logNChild)
					if (useHcIncrementer && PhTree5.HCI_ENABLED && DIM < 50) {
						niIterator = null;
					} else {
						niIterator = node.ind().queryWithMask(maskLower, maskUpper);
					}
				} else if (PhTree5.HCI_ENABLED){
					if (isPostHC) {
						//nPossibleMatch < 2^k?
						usePostHcIncrementer = nPossibleMatch < maxHcAddr;
					} else {
						int logNPost = Long.SIZE - Long.numberOfLeadingZeros(nMaxPost) + 1;
						usePostHcIncrementer = (nMaxPost > nPossibleMatch*(double)logNPost); 
					}
					if (isSubHC) {
						useSubHcIncrementer = nPossibleMatch < maxHcAddr;
					} else {
						int logNSub = Long.SIZE - Long.numberOfLeadingZeros(nMaxSub) + 1;
						useSubHcIncrementer = (nMaxSub > nPossibleMatch*(double)logNSub); 
					}
				}
			}

			initPostCursor();

			next = getNext();

			while (next != -1 && results.size() < maxResults) {
				next = getNext();
			}
		}

		private void addResult(NodeEntry<T> e) {
			if (filter == null) {
				results.add((R)e);
			} else if (filter.test(e.getKey())) {
				//results.add((R)e);
				results.add(mapper.map(e));
			}
		}

		/**
		 * 
		 * @return False if the value does not match the range, otherwise true.
		 */
		private boolean readValue(long pos, int offsPostKey) {
			long[] key = new long[DIM];
			System.arraycopy(valTemplate, 0, key, 0, DIM);
			PhTree5.applyArrayPosToValue(pos, postLen, key, isDepth0);
			NodeEntry<T> e = node.getPostPOB(offsPostKey, pos, key, rangeMin, rangeMax);
			if (e == null) {
				return false;
			}
			addResult(e);
			return true;
		}

		private boolean readValue(long pos, NodeEntry<T> e) {
			long[] buf = new long[DIM];
			System.arraycopy(valTemplate, 0, buf, 0, valTemplate.length);
			PhTree5.applyArrayPosToValue(pos, postLen, buf, isDepth0);

			//extract postfix
			final long mask = (~0L)<<postLen;
			long[] eKey = e.getKey();
			for (int i = 0; i < buf.length; i++) {
				buf[i] &= mask;  
				buf[i] |= eKey[i];
				if (buf[i] < rangeMin[i] || buf[i] > rangeMax[i]) {
					return false;
				}
			}
			addResult(e);
			return true;
		}

		private long getNextPostHCI(long currentPos) {
			//Ideally we would switch between b-serch-HCI and incr-search depending on the expected
			//distance to the next value.
			do {
				if (currentPos == -1) {
					//starting position;
					currentPos = maskLower;
				} else {
					currentPos = PhTree5.inc(currentPos, maskLower, maskUpper);
					if (currentPos <= maskLower) {
						isPostFinished = true;
						return -1;
					}
				}
				if (!isPostNI) {
					int pob = node.getPostOffsetBits(currentPos, DIM);
					if (pob >= 0) {
						if (!readValue(currentPos, pob)) {
							continue;
						}
						return currentPos;
					}
				}
			} while (true);//currentPos >= 0);
		}

		private long getNextSubHCI(long currentPos) {
			do {
				if (currentPos == -1) {
					//starting position;
					currentPos = maskLower;
				} else {
					currentPos = PhTree5.inc(currentPos, maskLower, maskUpper);
					if (currentPos <= maskLower) {
						isSubFinished = true;
						return -1;
					}
				}
				if (isSubHC) {
					if (node.subNRef((int)currentPos) == null) {
						//this can happen because above method returns negative values only for LHC.
						continue; //not found --> continue
					}
					posSubLHC = (int) currentPos;
					traverseNode( node.subNRef(posSubLHC), currentPos );
					//found --> abort
					return currentPos;
				} else {
					int subOffsBits = currentOffsetSub;//node.getBitPos_SubNodeIndex(DIM);
					int subNodePos = Bits.binarySearch(node.ba, subOffsBits, nMaxSub, (int)currentPos, Node.SIK_WIDTH(DIM), 0);
					if (subNodePos >= 0) {
						posSubLHC = subNodePos;
						traverseNode( node.subNRef(posSubLHC), subNodePos );
						//found --> abort
						return currentPos;
					}
				}
			} while (true);//currentPos >= 0);
		}

		private void initPostCursor() {
			if (PhTree5.HCI_ENABLED && !isPostNI && !isPostHC && next < 0 && 
					usePostHcIncrementer && 
					maskLower >= PhTree5.USE_MINMASK_BINARY_SEARCH_THRESHOLD) {
				//optimisation to find better initial value
				long start = maskLower;
				int posBit = node.getPostOffsetBits(start, DIM);
				//if the value does not exist, we search for the first valid one.
				while (posBit < 0) {
					start = PhTree5.inc(start, maskLower, maskUpper);
					if (start <= maskLower) {
						break;
					}
					posBit = node.getPostOffsetBits(start, DIM);
				}
				if (posBit >= 0) {
					//substract key-width and one entry width
					currentOffsetPostKey = posBit - Node.PIK_WIDTH(DIM);
				}
			}

			//sub-nodes
			if (PhTree5.HCI_ENABLED && !isPostNI && !isSubHC && next < 0 && 
					useSubHcIncrementer && 
					maskLower >= PhTree5.USE_MINMASK_BINARY_SEARCH_THRESHOLD) {
				//optimisation to find better initial value
				long start = maskLower;
				int subOffsBits = currentOffsetSub;//node.getBitPos_SubNodeIndex(DIM);
				int subNodePos = Bits.binarySearch(
						node.ba, subOffsBits, nMaxSub, (int)start, Node.SIK_WIDTH(DIM), 0);
				//if the value does not exist, we search for the first valid one.
				while (subNodePos < 0) {
					start = PhTree5.inc(start, maskLower, maskUpper);
					if (start <= maskLower) {
						break;
					}
					int max = nMaxSub;
					subNodePos = Bits.binarySearch(node.ba, subOffsBits, max, (int)start, Node.SIK_WIDTH(DIM), 0);
				}
				if (subNodePos >= 0) {
					posSubLHC = subNodePos;
					traverseNode( node.subNRef(posSubLHC), subNodePos );
					currentOffsetSub = subOffsBits;
				}
			}
		}

		private long getNext() {

			if (node.isPostNI()) {
				niFindNext();
				return next;
			}

			//Search for next entry if there are more entries and if current
			//entry has already been returned (or is -1).
			// (nextPost == next) is true when the previously returned entry (=next) was a postfix.
			if (!isPostFinished && nextPost == next) {
				if (usePostHcIncrementer) {
					nextPost = getNextPostHCI(nextPost);
				} else if (isPostHC) {
					getNextPostAHC();
				} else {
					getNextPostLHC();
				}
			}
			if (!isSubFinished && nextSub == next) {
				if (useSubHcIncrementer) {
					nextSub = getNextSubHCI(nextSub);
				} else if (isSubHC) {
					getNextSubAHC();
				} else {
					getNextSubLHC();
				}
			}

			if (isPostFinished && isSubFinished) {
				return -1;
			} 
			if (!isPostFinished && !isSubFinished) {
				if (nextSub < nextPost) {
					return nextSub;
				} else {
					return nextPost;
				}
			}
			if (isPostFinished) {
				return nextSub;
			} else {
				return nextPost;
			}
		}

		private void getNextPostAHC() {
			//while loop until 1 is found.
			long currentPos = next; 
			nextPost = -1;
			while (!isPostFinished) {
				if (currentPos >= 0) {
					currentPos++;  //pos w/o bit-offset
				} else {
					currentPos = maskLower; //initial value
					currentOffsetPostKey += maskLower*Node.PINN_HC_WIDTH;  //pos with bit-offset
				}
				if (currentPos >= (1<<DIM)) {
					isPostFinished = true;
					break;
				}
				boolean bit = Bits.getBit(node.ba, currentOffsetPostKey);
				currentOffsetPostKey += Node.PINN_HC_WIDTH;  //pos with bit-offset
				if (bit) {
					//check HC-pos
					if (!checkHcPos(currentPos)) {
						if (currentPos > maskUpper) {
							isPostFinished = true;
							break;
						}
						continue;
					}
					//check post-fix
					int offs = (int) (currentOffsetPostVal+currentPos*postEntryLen);
					if (!readValue(currentPos, offs)) {
						continue;
					}
					nextPost = currentPos;
					break;
				}
			}
		}

		private void getNextPostLHC() {
			nextPost = -1;
			while (!isPostFinished) {
				if (++nPostsFound > nMaxPost) {
					isPostFinished = true;
					break;
				}
				int offs = currentOffsetPostKey;
				long currentPos = Bits.readArray(node.ba, offs, Node.PIK_WIDTH(DIM));
				currentOffsetPostKey += postEntryLen;
				//check HC-pos
				if (!checkHcPos(currentPos)) {
					if (currentPos > maskUpper) {
						isPostFinished = true;
						break;
					}
					continue;
				}
				//check post-fix
				if (!readValue(currentPos, offs + Node.PIK_WIDTH(DIM))) {
					continue;
				}
				nextPost = currentPos;
				break;
			}
		}

		private void getNextSubAHC() {
			int currentPos = (int) next;  //We use (int) because arrays are always (int).
			int maxPos = 1<<DIM; 
			nextSub = -1;
			while (!isSubFinished) {
				if (currentPos < 0) {
					currentPos = (int) maskLower;
				} else {
					currentPos++;
				}
				if (currentPos >= maxPos) {
					isSubFinished = true;
					break;
				}
				if (node.subNRef(currentPos) != null) {
					//check HC-pos
					if (!checkHcPos(currentPos)) {
						if (currentPos > maskUpper) {
							isSubFinished = true;
							break;
						}
						continue;
					}
					nextSub = currentPos;
					traverseNode( node.getSubNodeWithPos(currentPos, -1), currentPos );
					break;
				}
			}
		} 

		private void getNextSubLHC() {
			nextSub = -1;
			while (!isSubFinished) {
				if (posSubLHC + 1  >= nMaxSub) {
					isSubFinished = true;
					break;
				}
				long currentPos = Bits.readArray(node.ba, currentOffsetSub, Node.SIK_WIDTH(DIM));
				currentOffsetSub += Node.SIK_WIDTH(DIM);
				posSubLHC++;
				//check HC-pos
				if (!checkHcPos(currentPos)) {
					if (currentPos > maskUpper) {
						isSubFinished = true;
						break;
					}
					continue;
				}
				nextSub = currentPos;
				traverseNode(node.getSubNodeWithPos(-1, posSubLHC), currentPos);
				break;
			}
		}

		private void niFindNext() {
			//iterator?
			if (niIterator != null) {
				while (niIterator.hasNext()) {
					Entry<NodeEntry<T>> e = niIterator.nextEntry();
					next = e.key();
					Node<T> nextSubNode = e.value().node;
					if (nextSubNode == null) {
						if (!readValue(e.key(), e.value())) {
							continue;
						}
					} else {
						traverseNode(nextSubNode, e.key());
						//					nextPostVal = null;
						//					nextPostKey = null;
					}
					return;
				}

				next = -1;
				return;
			}


			//HCI
			//repeat until we found a value inside the given range
			long currentPos = next; 
			do {
				if (currentPos != -1 && currentPos >= maskUpper) {
					break;
				}

				if (currentPos == -1) {
					//starting position;
					currentPos = maskLower;
				} else {
					currentPos = PhTree5.inc(currentPos, maskLower, maskUpper);
					if (currentPos <= maskLower) {
						break;
					}
				}

				NodeEntry<T> e = node.niGet(currentPos);
				if (e == null) {
					continue;
				}

				next = currentPos;

				//sub-node?
				if (e.node != null) {
					traverseNode(e.node, currentPos);
					return;
				}

				//read and check post-fix
				if (readValue(currentPos, e)) {
					return;
				}
			} while (true);
			next = -1;
		}


		private boolean checkHcPos(long pos) {
			return ((pos | maskLower) & maskUpper) == pos;
		}

		private void traverseNode(Node<T> sub, long pos) {
			PhTree5.applyArrayPosToValue(pos, node.getPostLen(), valTemplate, isDepth0);
			if (!PhTree5.checkAndApplyRange(sub, valTemplate, rangeMin, rangeMax)) {
				return;
			}
			run(sub, false);
		}
	}
	
	private NodeIteratorListReuse(int DIM, long[] valTemplate, long[] rangeMin,
			long[] rangeMax, ArrayList<R> results, 
			int maxResults, PhPredicate filter, PhMapper<T, R> mapper) {
		this.DIM = DIM;
		this.valTemplate = valTemplate;
		this.rangeMin = rangeMin;
		this.rangeMax = rangeMax;
		this.results = results;
		this.maxResults = maxResults;
		this.filter = filter;
		this.mapper = mapper;
		this.pool = new PhIteratorStack();
	}

	static <T, R> ArrayList<R> query(Node<T> node, long[] valTemplate, 
			long[] rangeMin, long[] rangeMax, final int DIM, final boolean isDepth0,
			int maxResults, PhPredicate filter, PhMapper<T, R> mapper) {
		ArrayList<R> results = new ArrayList<>();
		if (!PhTree5.checkAndApplyRange(node, valTemplate, rangeMin, rangeMax)) {
			return results;
		}
		
		NodeIteratorListReuse<T, R> i = new NodeIteratorListReuse<>(DIM, valTemplate, rangeMin, rangeMax, 
				results, maxResults, filter, mapper);
		i.run(node, isDepth0);
		return results;
	}
	
	private void run(Node<T> node, final boolean isDepth0) {
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
		long lowerLimit = 0;
		long upperLimit = 0;
		if (!isDepth0) {
			for (int i = 0; i < valTemplate.length; i++) {
				lowerLimit <<= 1;
				upperLimit <<= 1;
				long nodeBisection = valTemplate[i] | maskHcBit; 
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
			//currentDepth==0

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
		NodeIterator nIt = pool.prepare(node);
		if (nIt != null) {
			nIt.reinitAndRun(node, lowerLimit, upperLimit, isDepth0);
		}
		pool.pop();
	}

}
