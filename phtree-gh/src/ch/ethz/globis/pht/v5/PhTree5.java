/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v5;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;

import org.zoodb.index.critbit.CritBit64COW;
import org.zoodb.index.critbit.CritBit64COW.CBIterator;
import org.zoodb.index.critbit.CritBit64COW.Entry;
import org.zoodb.index.critbit.CritBit64COW.QueryIteratorMask;

import ch.ethz.globis.pht.PhDimFilter;
import ch.ethz.globis.pht.PhDistance;
import ch.ethz.globis.pht.PhEntry;
import ch.ethz.globis.pht.PhPredicate;
import ch.ethz.globis.pht.PhTree;
import ch.ethz.globis.pht.PhTreeHelper.Stats;
import ch.ethz.globis.pht.util.PhMapper;
import ch.ethz.globis.pht.util.PhTreeQStats;
import ch.ethz.globis.pht.util.Refs;
import ch.ethz.globis.pht.util.StringBuilderLn;

import static ch.ethz.globis.pht.PhTreeHelper.*;

/**
 * n-dimensional index (quad-/oct-/n-tree).
 *
 * Version 5: moved postCnt/subCnt into node.
 *
 * Version 4: Using long[] instead of int[]
 *
 * Version 3: This includes values for each key.
 *
 * Storage:
 * - classic: One node per combination of bits. Unused nodes can be cut off.
 * - use prefix-truncation -> a node may contain a series of unique bit combinations
 *
 * - To think about: Use optimised tree storage: 00=branch-down 01=branch-up 02=skip 03=ghost (after delete)
 *   -> 0=skip 10=down 11=up?
 *
 *
 * Hypercube: expanded byte array that contains 2^DIM references to sub-nodes (and posts, depending 
 * on implementation)
 * Linearization: Storing Hypercube as paired array of index<->non_null_reference 
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 */
public class PhTree5<T> implements PhTree<T> {

	//Enable HC incrementer / iteration
	static final boolean HCI_ENABLED = true; 

	/** If the minMask is larger than the threshold, then the first value in a node iterator
	 * is looked up by binary search instead of full search. */
	static final int USE_MINMASK_BINARY_SEARCH_THRESHOLD = 10;

	private static final boolean NI_THRESHOLD(int subCnt, int postCnt) {
		return (subCnt > 500 || postCnt > 50);
	}

	//Dimension. This is the number of attributes of an entity.
	private final int DIM;

	//depth in bits == number of bits that are stored for any
	//attribute.
	private final int DEPTH;

	//size of references in bytes
	private static final int REF_BITS = 4*8;

	private int nEntries = 0;
	private int nNodes = 0;

	private static final int UNKNOWN = -1;

	private static final int NO_INSERT_REQUIRED = Integer.MAX_VALUE;

	static final class NodeEntry<T> extends PhEntry<T> {
		Node<T> node;
		NodeEntry(long[] key, T value) {
			super(key, value);
			this.node = null;
		}
		NodeEntry(Node<T> node) {
			super(null, null);
			this.node = node;
		}

		void setNode(Node<T> node) {
			set(null, null);
			this.node = node;
		}
		void setPost(long[] key, T val) {
			set(key, val);
			this.node = null;
		}
	}

	public static class Node<T> {

		static final int HC_BITS = 0;  //number of bits required for storing current (HC)-representation
		static final int PINN_HC_WIDTH = 1; //width of not-null flag for post-hc
		static final int PIK_WIDTH(int DIM) { return DIM; };//DIM; //post index key width 
		static final int SIK_WIDTH(int DIM) { return DIM; };//DIM; //sub index key width 
		//      private static int LEN_COUNTER = -1;//DIM+1; //Counters require DIM + 1 bits, e.g. [0..8] for DIM=3

		private Node<T>[] subNRef;
		private T[] values;
		
		private int subCnt = 0;
		private int postCnt = 0;

		/**
		 * Structure of the byte[] and the required bits
		 * Post-HC (subIndex can be HC or LHC):
		 * | isHC | isHC | pCnt | sCnt | subIndex HC/LHC | postKeys HC | postValues HC  |
		 * |    1 |    1 |  DIM |  DIM | 0 / sCnt*DIM    | 2^DIM       | 2^DIM*DIM*pLen |
		 * 
		 * Post-LHC (subIndex can be HC or LHC):
		 * | isHC | isHC | pCnt | sCnt | subIndex HC/LHC | post-keys and -values LHC |
		 * |    1 |    1 |  DIM |  DIM | 0 / sCnt*DIM    | pCnt*(DIM + DIM*pLen)     |
		 * 
		 * 
		 * pLen = postLen
		 * pCnt = postCount
		 * sCnt = subCount
		 */
		long[] ba = null;

		// |   1st   |   2nd    |   3rd   |    4th   |
		// | isSubHC | isPostHC | isSubNI | isPostNI |
		private byte isHC = 0;

		private byte postLen = 0;
		byte infixLen = 0; //prefix size

		private CritBit64COW<NodeEntry<T>> ind = null;



		private Node() {
			// For ZooDB only
		}

        @SuppressWarnings("unchecked")
		private Node(Node<T> original) {
            if (original.subNRef != null) {
                int size = original.subNRef.length;
                this.subNRef = new Node[size];
                System.arraycopy(original.subNRef, 0, this.subNRef, 0, size);
            }
            if (original.values != null) {
                this.values = (T[]) original.values.clone();
            }
            if (original.ba != null) {
                this.ba = new long[original.ba.length];
                System.arraycopy(original.ba, 0, this.ba, 0, original.ba.length);
            }
            this.subCnt = original.subCnt;
            this.postCnt = original.postCnt;
            this.infixLen = original.infixLen;
            this.isHC = original.isHC;
            this.postLen = original.postLen;
            this.infixLen = original.infixLen;
            if (original.ind != null) {
                this.ind = original.ind.copy();
            }
        }

		private Node(int infixLen, int postLen, int estimatedPostCount, final int DIM) {
			this.infixLen = (byte) infixLen;
			this.postLen = (byte) postLen;
			if (estimatedPostCount >= 0) {
				int size = calcArraySizeTotalBits(estimatedPostCount, DIM);
				this.ba = Bits.arrayCreate(size);
			}
		}

		public static <T> Node<T> createNode(PhTree5<T> parent, int infixLen, int postLen, 
				int estimatedPostCount, final int DIM) {
			Node<T> n = new Node<T>(infixLen, postLen, estimatedPostCount, DIM);
			parent.nNodes++;
			return n;
		}

		boolean hasInfixes() {
			return infixLen > 0;
		}

		private int calcArraySizeTotalBits(int bufPostCnt, final int DIM) {
			int nBits = getBitPos_PostIndex(DIM);
			//post-fixes
			if (isPostHC()) {
				//hyper-cube
				nBits += (PINN_HC_WIDTH + DIM * postLen) * (1 << DIM);
			} else if (isPostNI()) {
				nBits += 0;
			} else {
				//hc-pos index
				nBits += bufPostCnt * (PIK_WIDTH(DIM) + DIM * postLen);
			}
			return nBits;
		}

		private int calcArraySizeTotalBitsNI(final int DIM) {
			return getBitPos_PostIndex(DIM);
		}

		long getInfix(int dim) {
			return Bits.readArray(this.ba, getBitPos_Infix()
					+ dim*infixLen, infixLen) << (postLen+1);
		}


		void getInfix(long[] val) {
			if (!hasInfixes()) {
				return;
			}
			int maskLen = postLen + 1 + infixLen;
			//To cut of trailing bits
			long mask = (-1L) << maskLen;
			for (int i = 0; i < val.length; i++) {
				//Replace val with infix (val may be !=0 from traversal)
				val[i] &= mask;
				val[i] |= getInfix(i);
			}
		}


		/**
		 * Get the infix without first deleting the incoming val[].
		 * @param val
		 */
		void getInfixNoOverwrite(long[] val) {
			if (!hasInfixes()) {
				return;
			}
			for (int i = 0; i < val.length; i++) {
				val[i] |= getInfix(i);
			}
		}


		private void writeInfix(long[] key) {
			int pos = getBitPos_Infix();
			int shift = postLen+1;
			for (int i = 0; i < key.length; i++) {
				Bits.writeArray(this.ba, pos, infixLen, key[i] >>> shift);
				pos += infixLen;
			}
		}


		private long getInfixBit(int infId, final int infixInternalOffset) {
			int startBitTotal = infId*infixLen + infixInternalOffset;
			return Bits.getBit01(ba, getBitPos_Infix() + startBitTotal);
		}

		/**
		 * 
		 * @param pos The position of the node when mapped to a vector.
		 * @return The sub node or null.
		 */
		NodeEntry<T> getChildNI(long pos) {
			return niGet(pos);
		}

		/**
		 * 
		 * @param pos The position of the node when mapped to a vector.
		 * @return The sub node or null.
		 */
		Node<T> getSubNode(long pos, final int DIM) {
			if (ind != null) {
				NodeEntry<T> e = niGet(pos);
				if (e == null) {
					return null;
				}
				return e.node; 
			}
			if (subNRef == null) {
				return null;
			}
			if (isSubHC()) {
				return subNRef[(int) pos];
			}
			int subOffsBits = getBitPos_SubNodeIndex(DIM);
			int p2 = Bits.binarySearch(ba, subOffsBits, getSubCount(), pos, SIK_WIDTH(DIM), 0);
			if (p2 < 0) {
				return null;
			}
			return subNRef[p2];
		}


		/**
		 * Return sub node at given position.
		 * @param posHC Hyper-cube position for hyper-cube representation
		 * @param posLHC array position for LHC representation
		 * @return The sub node at the given position
		 */
		Node<T> getSubNodeWithPos(long posHC, int posLHC) {
			if (isSubNI()) {
				return niGet(posHC).node;
			}
			Node<T> ret;
			if (isSubHC()) {
				ret = subNRef[(int)posHC];
			} else {
				ret = subNRef[posLHC];
			}
			return ret;
		}


		/**
		 * Add a new sub-node. 
		 * @param pos
		 * @param sub
		 */
		@SuppressWarnings("unchecked")
		void addSubNode(long pos, Node<T> sub, final int DIM) {
			final int bufSubCount = getSubCount();
			final int bufPostCount = getPostCount();

			if (!isSubNI() && NI_THRESHOLD(bufSubCount, bufPostCount)) {
				niBuild(bufSubCount, bufPostCount, DIM);
			}
			if (isSubNI()) {
				niPut(pos, sub);
				setSubCount(bufSubCount+1);
				return;
			}

			if (subNRef == null) {
				subNRef = new Node[2];
			}

			//decide here whether to use hyper-cube or linear representation
			if (isSubHC()) {
				subNRef[(int) pos] = sub;
				setSubCount(bufSubCount+1);
				return;
			}

			int subOffsBits = getBitPos_SubNodeIndex(DIM);

			//switch to normal array (full hyper-cube) if applicable.
			if (DIM<=31 && (REF_BITS+SIK_WIDTH(DIM))*(subNRef.length+1L) >= REF_BITS*(1L<<DIM)) {
				//migrate to full array!
				Node<T>[] na = new Node[1<<DIM];
				for (int i = 0; i < bufSubCount; i++) {
					int posOld = (int) Bits.readArray(ba, subOffsBits + i*SIK_WIDTH(DIM), SIK_WIDTH(DIM));
					na[posOld] = subNRef[i];
				}
				subNRef = na;
				Bits.removeBits(ba, subOffsBits, bufSubCount*SIK_WIDTH(DIM));
				setSubHC(true);
				subNRef[(int) pos] = sub;
				//subCount++;
				setSubCount(bufSubCount+1);
				int reqSize = calcArraySizeTotalBits(bufPostCount, DIM);
				ba = Bits.arrayTrim(ba, reqSize);
				return;
			}

			int p2 = Bits.binarySearch(ba, subOffsBits, bufSubCount, pos, SIK_WIDTH(DIM), 0);

			//subCount++;
			setSubCount(bufSubCount+1);

			int start = -(p2+1);
			int len = bufSubCount+1 - start-1;
			// resize only if necessary (could be multiples of 2 to avoid copying)!
			if (subNRef.length < bufSubCount+1) {
				int newLen = bufSubCount+1;
				newLen = (newLen&1)==0 ? newLen : newLen+1; //ensure multiples of two
				Node<T>[] na2 = new Node[newLen];
				System.arraycopy(subNRef, 0, na2, 0, start);
				System.arraycopy(subNRef, start, na2, start+1, len);
				subNRef = na2;
			} else {
				System.arraycopy(subNRef, start, subNRef, start+1, len);
			}
			subNRef[start] = sub;

			//resize index array?
			ba = Bits.arrayEnsureSize(ba, calcArraySizeTotalBits(bufPostCount, DIM));
			Bits.insertBits(ba, subOffsBits + start*SIK_WIDTH(DIM), SIK_WIDTH(DIM));
			Bits.writeArray(ba, subOffsBits + start*SIK_WIDTH(DIM), SIK_WIDTH(DIM), pos);
		}

		public void replacePost(int pob, long pos, long[] newKey, T value) {
			if (isPostNI()) {
				niPut(pos, newKey, value);
				return;
			}
			long[] ia = ba;
			int offs = pob;
			for (long key: newKey) {
				Bits.writeArray(ia, offs, postLen, key);
				offs += postLen;
			}
		}

		/**
		 * Replace a sub-node, for example if the current sub-node is removed, it may have to be
		 * replaced with a sub-sub-node.
		 */
		void replaceSub(long pos, Node<T> newSub, final int DIM) {
			if (isSubNI()) {
				niPut(pos, newSub);
				return;
			}
			if (isSubHC()) {
				subNRef[(int) pos] = newSub;
			} else {
				//linearized cube
				int subOffsBits = getBitPos_SubNodeIndex(DIM);
				int p2 = Bits.binarySearch(ba, subOffsBits, getSubCount(), pos, SIK_WIDTH(DIM), 0);
				subNRef[p2] = newSub;
			}
		}

		@SuppressWarnings("unchecked")
		private void removeSub(long pos, final int DIM) {
			int bufSubCnt = getSubCount();
			if (isSubNI()) {
				final int bufPostCnt = getPostCount();
				if (!NI_THRESHOLD(bufSubCnt, bufPostCnt)) {
					niDeconstruct(DIM, pos, true);
					return;
				}
			}
			if (isSubNI()) {
				niRemove(pos);
				setSubCount(bufSubCnt-1);
				return;
			}
			final int bufPostCnt = getPostCount();

			//switch representation (HC <-> Linear)?
			//+1 bit for null/not-null flag
			long sizeHC = REF_BITS*(1L<<DIM); 
			//+DIM assuming compressed IDs
			long sizeLin = (REF_BITS+SIK_WIDTH(DIM))*(subNRef.length-1L);
			if (isSubHC() && (sizeLin < sizeHC)) {
				//revert to linearized representation, if applicable
				int prePostBits_SubHC = getBitPos_PostIndex(DIM);
				setSubHC( false );
				bufSubCnt--;
				setSubCount(bufSubCnt);
				int prePostBits_SubLHC = getBitPos_PostIndex(DIM);
				int bia2Size = calcArraySizeTotalBits(bufPostCnt, DIM);
				long[] bia2 = Bits.arrayCreate(bia2Size);
				Node<T>[] sa2 = new Node[bufSubCnt];
				int preSubBits = getBitPos_SubNodeIndex(DIM);
				//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
				Bits.copyBitsLeft(ba, 0, bia2, 0, preSubBits);
				int n=0;
				for (int i = 0; i < (1L<<DIM); i++) {
					if (i==pos) {
						//skip the item that should be deleted.
						continue;
					}
					if (subNRef[i] != null) {
						sa2[n]= subNRef[i];
						Bits.writeArray(bia2, preSubBits + n*SIK_WIDTH(DIM), SIK_WIDTH(DIM), i);
						n++;
					}
				}
				//length: we copy as many bits as fit into bia2, which is easiest to calculate
				Bits.copyBitsLeft(
						ba, prePostBits_SubHC, 
						bia2, prePostBits_SubLHC,
						bia2Size-prePostBits_SubLHC);  
				ba = bia2;
				subNRef = sa2;
				return;
			}			


			if (isSubHC()) {
				//hyper-cube
				setSubCount(bufSubCnt-1);
				subNRef[(int) pos] = null;
				//Nothing else to do.
			} else {
				//linearized cube
				int subOffsBits = getBitPos_SubNodeIndex(DIM);
				int p2 = Bits.binarySearch(ba, subOffsBits, bufSubCnt, pos, SIK_WIDTH(DIM), 0);

				//subCount--;
				setSubCount(bufSubCnt-1);

				//remove value
				int len = bufSubCnt - p2-1;
				// resize only if necessary (could be multiples of 2 to avoid copying)!
				// not -1 to allow being one larger than necessary.
				if (subNRef.length > bufSubCnt) {
					int newLen = bufSubCnt-1;
					newLen = (newLen&1)==0 ? newLen : newLen+1; //ensure multiples of two
					if (newLen > 0) {
						Node<T>[] na2 = new Node[newLen];
						System.arraycopy(subNRef, 0, na2, 0, p2);
						System.arraycopy(subNRef, p2+1, na2, p2, len);
						subNRef = na2;
					} else {
						subNRef = null;
					}
				} else {
					if (p2+1 < subNRef.length) {
						System.arraycopy(subNRef, p2+1, subNRef, p2, len);
					}
				}

				//resize index array
				int offsKey = getBitPos_SubNodeIndex(DIM) + SIK_WIDTH(DIM)*p2;
				Bits.removeBits(ba, offsKey, SIK_WIDTH(DIM));

				//shrink array
				ba = Bits.arrayTrim(ba, calcArraySizeTotalBits(bufPostCnt, DIM));
			}
		}

		/**
		 * Compare two post-fixes. Takes as parameter not the position but the post-offset-bits.
		 * @param pob
		 * @param key
		 * @return true, if the post-fixes match
		 */
		boolean postEqualsPOB(int offsPostKey, long hcPos, long[] key) {
			if (isPostNI()) {
				long[] post = niGet(hcPos).getKey();
				long mask = ~((-1L) << postLen);
				for (int i = 0; i < key.length; i++) {
					//post requires a mask because we currently don't adjust it if the node moves up or down
					if (((post[i] ^ key[i]) & mask) != 0) {
						return false;
					}
				}
				return true;
			}

			long[] ia = ba;
			int offs = offsPostKey;
			//Can not be null at this point...
			//Also, length can be expected to be equal
			long mask = ~((-1L) << postLen);
			for (int i = 0; i < key.length; i++) {
				long l = Bits.readArray(ia, offs + i*postLen, postLen);
				if (l != (key[i] & mask)) {
					return false;
				}
			}
			return true;
		}

		boolean niContains(long hcPos) {
			return ind.contains(hcPos);
		}

		NodeEntry<T> niGet(long hcPos) {
			return ind.get(hcPos);
		}

		NodeEntry<T> niPut(long hcPos, long[] key, T value) {
			long[] copy = new long[key.length];
			System.arraycopy(key, 0, copy, 0, key.length);
			return ind.put(hcPos, new NodeEntry<T>(copy, value));
		}

		NodeEntry<T> niPutNoCopy(long hcPos, long[] key, T value) {
			return ind.put(hcPos, new NodeEntry<T>(key, value));
		}

		NodeEntry<T> niPut(long hcPos, Node<T> subNode) {
			return ind.put(hcPos, new NodeEntry<T>(subNode));
		}

		NodeEntry<T> niRemove(long hcPos) {
			return ind.remove(hcPos);
		}

		/**
		 * Compare two post-fixes. Takes as parameter not the position but the post-offset-bits.
		 * @param key1
		 * @param key2
		 * @return true, if the post-fixes match
		 */
		boolean postEquals(long[] key1, long[] key2) {
			//Can not be null at this point...
			//Also, length can be expected to be equal

			long mask = ~((-1L) << postLen);
			for (int i = 0; i < key1.length; i++) {
				if (((key1[i] ^ key2[i]) & mask) != 0) {
					return false;
				}
			}
			return true;
		}

		void addPost(long pos, long[] key, T value) {
			final int DIM = key.length;
			if (isPostNI()) {
				addPostPOB(pos, -1, key, value);
				return;
			}

			int offsKey = getPostOffsetBits(pos, DIM);
			addPostPOB(pos, offsKey, key, value);
		}

		/**
		 * 
		 * @param pos
		 * @param offsPostKey POB: Post offset bits from getPostOffsetBits(...)
		 * @param key
		 */
		void addPostPOB(long pos, int offsPostKey, long[] key, T value) {
			final int DIM = key.length;
			final int bufSubCnt = getSubCount();
			final int bufPostCnt = getPostCount();
			//decide here whether to use hyper-cube or linear representation
			//--> Initially, the linear version is always smaller, because the cube has at least
			//    two entries, even for a single dimension. (unless DIM > 2*REF=2*32 bit 
			//    For one dimension, both need one additional bit to indicate either
			//    null/not-null (hypercube, actually two bit) or to indicate the index. 

			if (!isPostNI() && NI_THRESHOLD(bufSubCnt, bufPostCnt)) {
				niBuild(bufSubCnt, bufPostCnt, DIM);
			}
			if (isPostNI()) {
				niPut(pos, key, value);
				setPostCount(bufPostCnt+1);
				return;
			}

			if (values == null) {
				values = Refs.arrayCreate(1);
			}

			//switch representation (HC <-> Linear)?
			//+1 bit for null/not-null flag
			long sizeHC = (long) ((DIM * postLen + PINN_HC_WIDTH) * (1L << DIM)); 
			//+DIM because every index entry needs DIM bits
			long sizeLin = (DIM * postLen + PIK_WIDTH(DIM)) * (bufPostCnt+1L);
			if (!isPostHC() && (DIM<=31) && (sizeLin >= sizeHC)) {
				int prePostBits = getBitPos_PostIndex(DIM);
				setPostHC( true );
				long[] bia2 = Bits.arrayCreate(calcArraySizeTotalBits(bufPostCnt+1, DIM));
				T [] v2 = Refs.arrayCreate(1<<DIM);
				//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
				Bits.copyBitsLeft(ba, 0, bia2, 0, prePostBits);
				int postLenTotal = DIM*postLen; 
				for (int i = 0; i < bufPostCnt; i++) {
					int entryPosLHC = prePostBits + i*(PIK_WIDTH(DIM)+postLenTotal);
					int p2 = (int)Bits.readArray(ba, entryPosLHC, PIK_WIDTH(DIM));
					Bits.setBit(bia2, prePostBits+PINN_HC_WIDTH*p2, true);
					Bits.copyBitsLeft(ba, entryPosLHC+PIK_WIDTH(DIM),
							bia2, prePostBits + (1<<DIM)*PINN_HC_WIDTH + postLenTotal*p2, 
							postLenTotal);
					v2[p2] = values[i];
				}
				ba = bia2;
				values = v2;
				offsPostKey = getPostOffsetBits(pos, DIM);
			}


			//get position
			offsPostKey = -(offsPostKey+1);

			//subBcnt++;
			setPostCount(bufPostCnt+1);

			if (isPostHC()) {
				//hyper-cube
				for (int i = 0; i < key.length; i++) {
					Bits.writeArray(ba, offsPostKey + postLen * i, postLen, key[i]);
				}
				int offsNN = getBitPos_PostIndex(DIM);
				Bits.setBit(ba, (int) (offsNN+PINN_HC_WIDTH*pos), true);
				values[(int) pos] = value;
			} else {
				long[] ia;
				int offs;
				if (!isPostNI()) {
					//resize array
					ba = Bits.arrayEnsureSize(ba, calcArraySizeTotalBits(bufPostCnt+1, DIM));
					ia = ba;
					offs = offsPostKey;
					Bits.insertBits(ia, offs-PIK_WIDTH(DIM), PIK_WIDTH(DIM) + DIM*postLen);
					//insert key
					Bits.writeArray(ia, offs-PIK_WIDTH(DIM), PIK_WIDTH(DIM), pos);
					//insert value:
					for (int i = 0; i < DIM; i++) {
						Bits.writeArray(ia, offs + postLen * i, postLen, key[i]);
					}
					values = Refs.arrayEnsureSize(values, bufPostCnt+1);
					Refs.insertAtPos(values, offs2ValPos(offs, pos, DIM), value);
				} else {
					throw new IllegalStateException();
				}

			}
		}

		long[] postToNI(int startBit, int postLen, int DIM) {
			long[] key = new long[DIM];
			for (int d = 0; d < key.length; d++) {
				key[d] |= Bits.readArray(ba, startBit, postLen);
				startBit += postLen;
			}
			return key;
		}

		void postFromNI(long[] ia, int startBit, long key[], int postLen) {
			//insert value:
			for (int d = 0; d < key.length; d++) {
				Bits.writeArray(ia, startBit + postLen * d, postLen, key[d]);
			}
		}

		void niBuild(int bufSubCnt, int bufPostCnt, int DIM) {
			//Migrate node to node-index representation
			if (ind != null || isPostNI() || isSubNI()) {
				throw new IllegalStateException();
			}
			ind = CritBit64COW.create();

			//read posts 
			if (isPostHC()) {
				int prePostBitsKey = getBitPos_PostIndex(DIM);
				int prePostBitsVal = prePostBitsKey + (1<<DIM)*PINN_HC_WIDTH;
				int postLenTotal = DIM*postLen;
				for (int i = 0; i < (1L<<DIM); i++) {
					if (Bits.getBit(ba, prePostBitsKey + PINN_HC_WIDTH*i)) {
						int postPosLHC = prePostBitsVal + i*postLenTotal;
						//Bits.writeArray(bia2, entryPosLHC, PIK_WIDTH(DIM), i);
						//Bits.copyBitsLeft(
						//		ba, prePostBits + (1<<DIM)*PINN_HC_WIDTH + postLenTotal*i, 
						//		bia2, entryPosLHC+PIK_WIDTH(DIM),
						//		postLenTotal);
						long[] key = postToNI(postPosLHC, postLen, DIM);
						postPosLHC += DIM*postLen;
						niPutNoCopy(i, key, values[i]);
					}
				}
			} else {
				int prePostBits = getBitPos_PostIndex(DIM);
				int postPosLHC = prePostBits;
				for (int i = 0; i < bufPostCnt; i++) {
					//int entryPosLHC = prePostBits + i*(PIK_WIDTH(DIM)+postLenTotal);
					long p2 = Bits.readArray(ba, postPosLHC, PIK_WIDTH(DIM));
					postPosLHC += PIK_WIDTH(DIM);
					//This reads compressed keys...
					//					Bits.setBit(bia2, prePostBits+PINN_HC_WIDTH*p2, true);
					//					Bits.copyBitsLeft(ba, entryPosLHC+PIK_WIDTH(DIM),
					//							bia2, prePostBits + (1<<DIM)*PINN_HC_WIDTH + postLenTotal*p2, 
					//							postLenTotal);
					long[] key = postToNI(postPosLHC, postLen, DIM);
					postPosLHC += DIM*postLen;

					niPutNoCopy(p2, key, values[i]);
				}
			}

			//sub nodes
			if (isSubHC()) {
				for (int i = 0; i < (1L<<DIM); i++) {
					if (subNRef[i] != null) {
						niPut(i, subNRef[i]);
					}
				}
			} else {
				int subOffsBits = getBitPos_SubNodeIndex(DIM);
				for (int i = 0; i < bufSubCnt; i++) {
					long posOld = Bits.readArray(ba, subOffsBits, SIK_WIDTH(DIM));
					subOffsBits += SIK_WIDTH(DIM);
					niPut(posOld, subNRef[i]);
				}
			}

			setPostHC(false);
			setSubHC(false);
			setPostNI(true);
			setSubNI(true);
			ba = Bits.arrayTrim(ba, calcArraySizeTotalBitsNI(DIM));
			subNRef = null;
			values = null; 
		}

		/**
		 * 
		 * @param bufSubCnt
		 * @param bufPostCnt
		 * @param DIM
		 * @param posToRemove
		 * @param removeSub Remove sub or post?
		 * @return Previous value if post was removed
		 */
		@SuppressWarnings("unchecked")
		T niDeconstruct(int DIM, long posToRemove, boolean removeSub) {
			//Migrate node to node-index representation
			if (ind == null || !isPostNI() || !isSubNI()) {
				throw new IllegalStateException();
			}

			setPostNI(false);
			setSubNI(false);
			final int newSubCnt;
			final int newPostCnt;
			if (removeSub) {
				newSubCnt = getSubCount()-1;
				newPostCnt = getPostCount();
				setSubCount(newSubCnt);
			} else {
				newSubCnt = getSubCount();
				newPostCnt = getPostCount()-1;
				setPostCount(newPostCnt);
			}

			//calc post mode.
			//+1 bit for null/not-null flag
			long sizePostHC = (DIM * postLen + PINN_HC_WIDTH) * (1L << DIM); 
			//+DIM because every index entry needs DIM bits
			long sizePostLin = (DIM * postLen + PIK_WIDTH(DIM)) * newPostCnt;
			boolean isPostHC = (DIM<=31) && (sizePostLin >= sizePostHC);
			setPostHC(isPostHC);



			//sub-nodes:
			//switch to normal array (full hyper-cube) if applicable.
			if (DIM<=31 && (REF_BITS+SIK_WIDTH(DIM))*newSubCnt >= REF_BITS*(1L<<DIM)) {
				//migrate to full HC array
				Node<T>[] na = new Node[1<<DIM];
				CBIterator<NodeEntry<T>> it = ind.iterator();
				while (it.hasNext()) {
					Entry<NodeEntry<T>> e = it.nextEntry();
					if (e.value().node != null && e.key() != posToRemove) {
						na[(int) e.key()] = e.value().node;
					}
				}
				subNRef = na;
				setSubHC(true);
			} else {
				//migrate to LHC
				setSubHC( false );
				int bia2Size = calcArraySizeTotalBits(newPostCnt, DIM);
				long[] bia2 = Bits.arrayCreate(bia2Size);
				Node<T>[] sa2 = new Node[newSubCnt];
				int preSubBits = getBitPos_SubNodeIndex(DIM);
				//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
				Bits.copyBitsLeft(ba, 0, bia2, 0, preSubBits);
				int n=0;
				CBIterator<NodeEntry<T>> it = ind.iterator();
				while (it.hasNext()) {
					Entry<NodeEntry<T>> e = it.nextEntry();
					if (e.value().node != null) {
						long pos = e.key();
						if (pos == posToRemove) {
							//skip the item that should be deleted.
							continue;
						}
						sa2[n] = e.value().node;
						Bits.writeArray(bia2, preSubBits + n*SIK_WIDTH(DIM), SIK_WIDTH(DIM), pos);
						n++;
					}
				}
				ba = bia2;
				subNRef = sa2;
			}

			//post-data:
			T oldValue = null;
			int prePostBits = getBitPos_PostIndex(DIM);
			long[] bia2 = Bits.arrayCreate(calcArraySizeTotalBits(newPostCnt, DIM));
			//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
			Bits.copyBitsLeft(ba, 0, bia2, 0, prePostBits);
			int postLenTotal = DIM*postLen;
			if (isPostHC) {
				//HC mode
				T [] v2 = Refs.arrayCreate(1<<DIM);
				int startBitBase = prePostBits + (1<<DIM)*PINN_HC_WIDTH;
				CBIterator<NodeEntry<T>> it = ind.iterator();
				while (it.hasNext()) {
					Entry<NodeEntry<T>> e = it.nextEntry();
					if (e.value().getKey() != null) {
						if (e.key() == posToRemove) {
							oldValue = e.value().getValue();
							continue;
						}
						int p2 = (int) e.key();
						Bits.setBit(bia2, prePostBits+PINN_HC_WIDTH*p2, true);
						int startBit = startBitBase + postLenTotal*p2;
						postFromNI(bia2, startBit, e.value().getKey(), postLen);
						v2[p2] = e.value().getValue();
					}
				}
				ba = bia2;
				values = v2;
			} else {
				//LHC mode
				T[] v2 = Refs.arrayCreate(newPostCnt);
				int n=0;
				CBIterator<NodeEntry<T>> it = ind.iterator();
				int entryPosLHC = prePostBits;
				while (it.hasNext()) {
					Entry<NodeEntry<T>> e = it.nextEntry();
					long pos = e.key();
					if (e.value().getKey() != null) {
						if (pos == posToRemove) {
							//skip the item that should be deleted.
							oldValue = e.value().getValue();
							continue;
						}
						v2[n] = e.value().getValue();
						Bits.writeArray(bia2, entryPosLHC, PIK_WIDTH(DIM), pos);
						entryPosLHC += PIK_WIDTH(DIM);
						postFromNI(bia2, entryPosLHC, e.value().getKey(), postLen);
						entryPosLHC += postLenTotal;
						n++;
					}
				}
				ba = bia2;
				values = v2;
			}			

			if (newPostCnt == 0) {
				values = null;
			}
			ind = null;
			return oldValue;
		}


		T getPostPOB(int offsPostKey, long pos, long[] key) {
			if (isPostNI()) {
				final long mask = (~0L)<<postLen;
				NodeEntry<T> e = niGet(pos);
				long[] eKey = e.getKey();
				for (int i = 0; i < key.length; i++) {
					key[i] &= mask;
					key[i] |= eKey[i];
				}
				//System.arraycopy(e.getKey(), 0, key, 0, key.length);
				return e.getValue();
			}

			long[] ia = ba;
			int offs;
			offs = offsPostKey;
			int valPos = offs2ValPos(offs, pos, key.length);
			final long mask = (~0L)<<postLen;
			for (int i = 0; i < key.length; i++) {
				key[i] &= mask;
				key[i] |= Bits.readArray(ia, offs, postLen);
				offs += postLen;
			}
			return values[valPos];   
		}


		/**
		 * Get post-fix.
		 * @param offsPostKey
		 * @param key
		 * @param range After the method call, this contains the postfix if the postfix matches the
		 * range. Otherwise it contains only part of the postfix.
		 * @return true, if the postfix matches the range.
		 */
		NodeEntry<T> getPostPOB(int offsPostKey, long hcPos, long[] key, 
				long[] rangeMin, long[] rangeMax) {
			long[] ia = ba;
			int offs = offsPostKey;
			final long mask = (~0L)<<postLen;
			for (int i = 0; i < key.length; i++) {
				key[i] &= mask;
				key[i] |= Bits.readArray(ia, offs, postLen);
				if (key[i] < rangeMin[i] || key[i] > rangeMax[i]) {
					return null;
				}
				offs += postLen;
			}
			int valPos = offs2ValPos(offsPostKey, hcPos, key.length);
			return new NodeEntry<T>(key, values[valPos]);
		}


		/**
		 * Get post-fix.
		 * @param offsPostKey
		 * @param key
		 * @param range After the method call, this contains the postfix if the postfix matches the
		 * range. Otherwise it contains only part of the postfix.
		 * @return true, if the postfix matches the range.
		 */
		NodeEntry<T> getPostPOB(int offsPostKey, long hcPos, long[] key, 
				long[] rangeMin, long[] rangeMax, int[] minToCheck, int[] maxToCheck) {
			long[] ia = ba;
			final long mask = (~0L)<<postLen;
			
			for (int i: minToCheck) {
				key[i] &= mask;
				key[i] |= Bits.readArray(ia, offsPostKey+i*postLen, postLen);
				if (key[i] < rangeMin[i]) {
					return null;
				}
			}
			for (int i: maxToCheck) {
				key[i] &= mask;
				key[i] |= Bits.readArray(ia, offsPostKey+i*postLen, postLen);
				if (key[i] > rangeMax[i]) {
					return null;
				}
			}
			
			
			int offs = offsPostKey;
			for (int i = 0; i < key.length; i++) {
				key[i] &= mask;
				key[i] |= Bits.readArray(ia, offs, postLen);
				offs += postLen;
			}
			int valPos = offs2ValPos(offsPostKey, hcPos, key.length);
			return new NodeEntry<T>(key, values[valPos]);
		}

		/**
		 * Same as above, but without checks.
		 */
		NodeEntry<T> getPostPOBNoCheck(int offsPostKey, long hcPos, long[] key) {
			long[] ia = ba;
			int offs = offsPostKey;
			final long mask = (~0L)<<postLen;
			for (int i = 0; i < key.length; i++) {
				key[i] &= mask;
				key[i] |= Bits.readArray(ia, offs, postLen);
				offs += postLen;
			}
			int valPos = offs2ValPos(offsPostKey, hcPos, key.length);
			return new NodeEntry<T>(key, values[valPos]);
		}

		T getPostValuePOB(int offs, long pos, int DIM) {
			if (!isPostNI()) {
				int valPos = offs2ValPos(offs, pos, DIM);
				return values[valPos];
			} 

			return niGet(pos).getValue(); 
		}


		T updatePostValuePOB(int offs, long pos, long[] key, int DIM, T value) {
			if (!isPostNI()) {
				int valPos = offs2ValPos(offs, pos, DIM);
				T old = values[valPos];
				values[valPos] = value;
				return old;
			} 

			return niPutNoCopy(pos, key, value).getValue(); 
		}


		T getPostValue(long pos, int DIM) {
			if (isPostHC()) {
				return getPostValuePOB(UNKNOWN, pos, UNKNOWN); 
			}
			int offs = getPostOffsetBits(pos, DIM);

			return getPostValuePOB(offs, pos, DIM);
		}


		T getPost(long pos, long[] key) {
			if (isPostNI()) {
				return getPostPOB(-1, pos, key);
			}
			final int DIM = key.length;
			int offs = getPostOffsetBits(pos, DIM);

			return getPostPOB(offs, pos, key);
		}


		T removePostPOB(long pos, int offsPostKey, final int DIM) {
			final int bufPostCnt = getPostCount();
			final int bufSubCnt = getSubCount();

			if (isPostNI()) {
				if (!NI_THRESHOLD(bufSubCnt, bufPostCnt)) {
					T v = niDeconstruct(DIM, pos, false);
					return v;
				}
			}
			if (isPostNI()) {
				setPostCount(bufPostCnt-1);
				return niRemove(pos).getValue();
			}

			T oldVal = null;

			//switch representation (HC <-> Linear)?
			//+1 bit for null/not-null flag
			long sizeHC = (DIM * postLen + PINN_HC_WIDTH) * (1L << DIM); 
			//+DIM assuming compressed IDs
			long sizeLin = (DIM * postLen + PIK_WIDTH(DIM)) * (bufPostCnt-1L);
			if (isPostHC() && (sizeLin < sizeHC)) {
				//revert to linearized representation, if applicable
				setPostHC( false );
				long[] bia2 = Bits.arrayCreate(calcArraySizeTotalBits(bufPostCnt-1, DIM));
				T[] v2 = Refs.arrayCreate(bufPostCnt);
				int prePostBits = getBitPos_PostIndex(DIM);
				int prePostBitsVal = prePostBits + (1<<DIM)*PINN_HC_WIDTH;
				//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
				Bits.copyBitsLeft(ba, 0, bia2, 0, prePostBits);
				int postLenTotal = DIM*postLen;
				int n=0;
				for (int i = 0; i < (1L<<DIM); i++) {
					if (i==pos) {
						//skip the item that should be deleted.
						oldVal = values[i];
						continue;
					}
					if (Bits.getBit(ba, prePostBits + PINN_HC_WIDTH*i)) {
						int entryPosLHC = prePostBits + n*(PIK_WIDTH(DIM)+postLenTotal);
						Bits.writeArray(bia2, entryPosLHC, PIK_WIDTH(DIM), i);
						Bits.copyBitsLeft(
								ba, prePostBitsVal + postLenTotal*i, 
								bia2, entryPosLHC+PIK_WIDTH(DIM),
								postLenTotal);
						v2[n] = values[i];
						n++;
					}
				}
				ba = bia2;
				values = v2;
				//subBcnt--;
				setPostCount(bufPostCnt-1);
				if (bufPostCnt-1 == 0) {
					values = null;
				}
				return oldVal;
			}			

			//subBcnt--;
			setPostCount(bufPostCnt-1);

			if (isPostHC()) {
				//hyper-cube
				int offsNN = getBitPos_PostIndex(DIM);
				Bits.setBit(ba, (int) (offsNN+PINN_HC_WIDTH*pos), false);
				oldVal = values[(int) pos]; 
				values[(int) pos] = null;
				//Nothing else to do, values can just stay where they are
			} else {
				if (!isPostNI()) {
					//linearized cube:
					//remove key and value
					Bits.removeBits(ba, offsPostKey-PIK_WIDTH(DIM), PIK_WIDTH(DIM) + DIM*postLen);
					//shrink array
					ba = Bits.arrayTrim(ba, calcArraySizeTotalBits(bufPostCnt-1, DIM));
					//values:
					int valPos = offs2ValPos(offsPostKey, pos, DIM);
					oldVal = values[valPos]; 
					Refs.removeAtPos(values, valPos);
					values = Refs.arrayTrim(values, bufPostCnt-1);
				} else {
					throw new IllegalStateException();
				}
			}
			if (bufPostCnt-1 == 0) {
				values = null;
			}
			return oldVal;
		}


		/**
		 * @return True if the post-fixes are stored as hyper-cube
		 */
		boolean isPostHC() {
			return (isHC & 0b10) != 0;
			//return Bits.getBit(ba, 0);
		}


		/**
		 * Set whether the post-fixes are stored as hyper-cube.
		 */
		void setPostHC(boolean b) {
			isHC = (byte) (b ? (isHC | 0b10) : (isHC & (~0b10)));
			//Bits.setBit(ba, 0, b);
		}


		/**
		 * @return True if the sub-nodes are stored as hyper-cube
		 */
		boolean isSubHC() {
			return (isHC & 0b01) != 0;
			//return Bits.getBit(ba, 1);
		}


		/**
		 * Set whether the sub-nodes are stored as hyper-cube.
		 */
		void setSubHC(boolean b) {
			isHC = (byte) (b ? (isHC | 0b01) : (isHC & (~0b01)));
			//Bits.setBit(ba, 1, b);
		}


		/**
		 * @return True if the sub-nodes are stored as hyper-cube
		 */
		boolean isSubLHC() {
			//bit 0 and 2 = 1+4
			return (isHC & 0b101) == 0;
		}

		boolean isPostLHC() {
			//bit 1 and 3 = 2+8
			return (isHC & 0b110) == 0;
		}


		boolean isPostNI() {
			return (isHC & 0b100) != 0;
		}


		void setPostNI(boolean b) {
			isHC = (byte) (b ? (isHC | 0b100) : (isHC & (~0b100)));
		}


		boolean isSubNI() {
			return isPostNI();
		}


		void setSubNI(boolean b) {
			setPostNI(b);
		}


		/**
		 * @return Post-fix counter
		 */
		int getPostCount() {
			return postCnt;
		}


		/**
		 * Set post-fix counter.
		 */
		private void setPostCount(int cnt) {
			postCnt = cnt;
		}


		/**
		 * @return Sub-node counter
		 */
		int getSubCount() {
			return subCnt;
		}


		/**
		 * Set sub-node counter.
		 */
		private void setSubCount(int cnt) {
			subCnt = cnt;
		}


		/**
		 * Posts start after sub-index.
		 * Sub-index is empty in case of sub-hypercube.
		 * @return Position of first bit of post index or not-null table.
		 */
		int getBitPos_PostIndex(final int DIM) {
			int offsOfSubs = 0;
			//subHC and subNI require no space
			if (isSubLHC()) {
				//linearized cube
				offsOfSubs = getSubCount() * SIK_WIDTH(DIM); 
			}
			return getBitPos_SubNodeIndex(DIM) + offsOfSubs; 
		}

		int getBitPos_SubNodeIndex(final int DIM) {
			return getBitPos_Infix() + (infixLen*DIM);
		}

		private int getBitPos_Infix() {
			// isPostHC / isSubHC / postCount / subCount
			return HC_BITS;//   +   DIM+1   +   DIM+1;
		}

		/**
		 * 
		 * @param offs
		 * @param pos
		 * @param DIM
		 * @param bufSubCnt use -1 to have it calculated by this method
		 * @return
		 */
		private int offs2ValPos(int offs, long pos, int DIM) {
			if (isPostHC()) {
				return (int) pos;
			} else {
				int offsInd = getBitPos_PostIndex(DIM);
				//get p2 of:
				//return p2 * (PIK_WIDTH(DIM) + postLen * DIM) + offsInd + PIK_WIDTH(DIM);
				int valPos = (offs - PIK_WIDTH(DIM) - offsInd) / (postLen*DIM+PIK_WIDTH(DIM)); 
				return valPos;
			}
		}


		/**
		 * 
		 * @param pos
		 * @return Offset (in bits) in according array. In case the entry does not
		 * exist, a negative number is returned that represents the insertion position.
		 * 
		 */
		/**
		 * 
		 * @param pos
		 * @param DIM
		 * @return 		The position (in bits) of the postfix VALUE. For LHC, the key is stored 
		 * 				directly before the value.
		 */
		int getPostOffsetBits(long pos, final int DIM) {
			int offsInd = getBitPos_PostIndex(DIM);
			if (isPostHC()) {
				//hyper-cube
				int posInt = (int) pos;  //Hypercube can not be larger than 2^31
				boolean notNull = Bits.getBit(ba, offsInd+PINN_HC_WIDTH*posInt);
				offsInd += PINN_HC_WIDTH*(1<<DIM);
				if (!notNull) {
					return -(posInt * postLen * DIM + offsInd)-1;
				}
				return posInt * postLen * DIM + offsInd;
			} else {
				if (!isPostNI()) {
					//linearized cube
					int p2 = Bits.binarySearch(ba, offsInd, getPostCount(), pos, PIK_WIDTH(DIM), 
							DIM * postLen);
					if (p2 < 0) {
						p2 = -(p2+1);
						p2 *= (PIK_WIDTH(DIM) + postLen * DIM);
						p2 += PIK_WIDTH(DIM);
						return -(p2 + offsInd) -1;
					}
					return p2 * (PIK_WIDTH(DIM) + postLen * DIM) + offsInd + PIK_WIDTH(DIM);
				} else {
					NodeEntry<T> e = niGet(pos);
					if (e != null && e.getKey() != null) {
						return (int)pos;
					}
					return (int) (-pos -1);
				}
			}
		}

		boolean hasPostFix(long pos, final int DIM) {
			if (!isPostNI()) {
				return getPostOffsetBits(pos, DIM) >= 0;
			}
			NodeEntry<T> e = niGet(pos);
			return (e != null) && (e.getKey() != null);
		}

		int getInfixLen() {
			return infixLen;
		}

		int getPostLen() {
			return postLen;
		}
		public Node<T> subNRef(int pos) {
			return subNRef[pos];
		}
		public CritBit64COW<NodeEntry<T>> ind() {
			return ind;
		}

        public Node<T> copy() {
            return new Node<>(this);
        }
    }

	static class NodeIterator<T> {
		private final int DIM;
		private final boolean isPostHC;
		private final boolean isPostNI;
		private final boolean isSubHC;
		private final int postLen;
		private final boolean isDepth0;
		private long next = -1;
		private long nextPost = -1;
		private long nextSub = -1;
		private long[] nextPostKey;
		private T nextPostVal;
		private Node<T> nextSubNode;
		private final Node<T> node;
		private int currentOffsetPostKey;
		private int currentOffsetPostVal;
		private int currentOffsetSub;
		private QueryIteratorMask<NodeEntry<T>> niIterator;
		private final int nMaxPost;
		private final int nMaxSub;
		private int nPostsFound = 0;
		private int posSubLHC = -1; //position in sub-node LHC array
		private final int postEntryLen;
		private final long[] valTemplate;
		private final long maskLower;
		private final long maskUpper;
		private final long[] rangeMin;
		private final long[] rangeMax;
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
		 * @param rangeMin The minimum value that any found value should have. If the found value is
		 *  lower, the search continues.
		 * @param rangeMax
		 */
		public NodeIterator(Node<T> node, int DIM, long[] valTemplate, long lower, long upper, 
				long[] rangeMin, long[] rangeMax, boolean isDepth0) {
			this.DIM = DIM;
			this.node = node;
			this.valTemplate = valTemplate;
			this.rangeMin = rangeMin;
			this.rangeMax = rangeMax;
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
					int nChild = node.ind.size();
					int logNChild = Long.SIZE - Long.numberOfLeadingZeros(nChild);
					//the following will overflow for k=60
					boolean useHcIncrementer = (nChild > nPossibleMatch*(double)logNChild*2);
					//DIM < 60 as safeguard against overflow of (nPossibleMatch*logNChild)
					if (useHcIncrementer && HCI_ENABLED && DIM < 50) {
						niIterator = null;
					} else {
						niIterator = node.ind.queryWithMask(maskLower, maskUpper);
					}
				} else if (HCI_ENABLED){
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
		}

		boolean hasNext() {
			return next != -1;
		}

		void increment() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			next = getNext();
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
			return isPostNI ? (nextSubNode != null) : (next == nextSub);
		}

		/**
		 * 
		 * @return False if the value does not match the range, otherwise true.
		 */
		private boolean readValue(long pos, int offsPostKey) {
			long[] key = new long[DIM];
			System.arraycopy(valTemplate, 0, key, 0, DIM);
			applyArrayPosToValue(pos, postLen, key, isDepth0);
			if (DEBUG_FULL) {
				//verify that we don't have keys here that can't possibly match...
				final long mask = (~0L)<<postLen;
				for (int i = 0; i < key.length; i++) {
					key[i] &= mask;
					if (key[i] < (rangeMin[i]&mask) || key[i] > (rangeMax[i]&mask)) {
						throw new IllegalStateException("k=" + key[i] + " m/m=" + rangeMin[i] +
								"/" + rangeMax[i]);
					}
				}
			}
			NodeEntry<T> e = node.getPostPOB(offsPostKey, pos, key, rangeMin, rangeMax);
			if (e == null) {
				return false;
			}
			nextPostKey = e.getKey();
			nextPostVal = e.getValue();
			//Don't set to 'null' here, that interferes with parallel iteration over post/sub 
			//nextSubNode = null;
			return true;
		}

		private boolean readValue(long pos, NodeEntry<T> e) {
			long[] buf = new long[DIM];
			System.arraycopy(valTemplate, 0, buf, 0, valTemplate.length);
			applyArrayPosToValue(pos, postLen, buf, isDepth0);

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
			nextPostKey = e.getKey();
			nextPostVal = e.getValue();
			nextSubNode = null;
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
					currentPos = inc(currentPos, maskLower, maskUpper);
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
					currentPos = inc(currentPos, maskLower, maskUpper);
					if (currentPos <= maskLower) {
						isSubFinished = true;
						return -1;
					}
				}
				if (isSubHC) {
					if (node.subNRef[(int)currentPos] == null) {
						//this can happen because above method returns negative values only for LHC.
						continue; //not found --> continue
					}
					posSubLHC = (int) currentPos;
					nextSubNode = node.subNRef[posSubLHC];
					//found --> abort
					return currentPos;
				} else {
					int subOffsBits = currentOffsetSub;//node.getBitPos_SubNodeIndex(DIM);
					int subNodePos = Bits.binarySearch(node.ba, subOffsBits, nMaxSub, (int)currentPos, Node.SIK_WIDTH(DIM), 0);
					if (subNodePos >= 0) {
						posSubLHC = subNodePos;
						nextSubNode = node.subNRef[posSubLHC];
						//found --> abort
						return currentPos;
					}
				}
			} while (true);//currentPos >= 0);
		}

		private void initPostCursor() {
			if (HCI_ENABLED && !isPostNI && !isPostHC && next < 0 && 
					usePostHcIncrementer && 
					maskLower >= USE_MINMASK_BINARY_SEARCH_THRESHOLD) {
				//optimisation to find better initial value
				long start = maskLower;
				int posBit = node.getPostOffsetBits(start, DIM);
				//if the value does not exist, we search for the first valid one.
				while (posBit < 0) {
					start = inc(start, maskLower, maskUpper);
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
			if (HCI_ENABLED && !isPostNI && !isSubHC && next < 0 && 
					useSubHcIncrementer && 
					maskLower >= USE_MINMASK_BINARY_SEARCH_THRESHOLD) {
				//optimisation to find better initial value
				long start = maskLower;
				int subOffsBits = currentOffsetSub;//node.getBitPos_SubNodeIndex(DIM);
				int subNodePos = Bits.binarySearch(
						node.ba, subOffsBits, nMaxSub, (int)start, Node.SIK_WIDTH(DIM), 0);
				//if the value does not exist, we search for the first valid one.
				while (subNodePos < 0) {
					start = inc(start, maskLower, maskUpper);
					if (start <= maskLower) {
						break;
					}
					int max = nMaxSub;
					subNodePos = Bits.binarySearch(node.ba, subOffsBits, max, (int)start, Node.SIK_WIDTH(DIM), 0);
				}
				if (subNodePos >= 0) {
					posSubLHC = subNodePos;
					nextSubNode = node.subNRef[posSubLHC];
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
				if (node.subNRef[currentPos] != null) {
					//check HC-pos
					if (!checkHcPos(currentPos)) {
						if (currentPos > maskUpper) {
							isSubFinished = true;
							break;
						}
						continue;
					}
					nextSub = currentPos;
					nextSubNode = node.getSubNodeWithPos(currentPos, -1);
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
				nextSubNode = node.getSubNodeWithPos(-1, posSubLHC);
				break;
			}
		}

		private void niFindNext() {
			//iterator?
			if (niIterator != null) {
				while (niIterator.hasNext()) {
					Entry<NodeEntry<T>> e = niIterator.nextEntry();
					next = e.key();
					nextSubNode = e.value().node;
					if (nextSubNode == null) {
						if (!readValue(e.key(), e.value())) {
							continue;
						}
					} else {
						nextPostVal = null;
						nextPostKey = null;
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
					currentPos = inc(currentPos, maskLower, maskUpper);
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
				nextSubNode = e.node;
				if (e.node != null) {
					nextPostVal = null;
					nextPostKey = null;
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
			//			if (DEBUG) {
			//				TestPerf.STAT_X5++;
			//				if ((pos & maskUpper) != pos) {
			//					if ((pos | maskLower) != pos) {
			//						TestPerf.STAT_X5ab++;
			//						return false;
			//					}
			//					TestPerf.STAT_X5b++;
			//					return false;
			//				}
			//				if ((pos | maskLower) != pos) {
			//					TestPerf.STAT_X5a++;
			//					return false;
			//				}
			//				return true;
			//			}
			return ((pos | maskLower) & maskUpper) == pos;
		}

		/**
		 * Return the count of currently found sub-nodes minus one. For LHC, this is equal to 
		 * the position in the sub-node array.
		 * @return subCount - 1
		 */
		public int getPosSubLHC() {
			return posSubLHC;
		}

		/**
		 * Return the key at the current position of the POST-ITERATOR. This may be a higher
		 * value than the current pos in case current pos indicates a sub-ref.
		 * @return the current value at the current post-position.
		 */
		public long[] getCurrentPost() {
			return nextPostKey;
		}

		/**
		 * Return the value at the current position of the POST-ITERATOR. This may be a higher
		 * value than the current pos in case current pos indicates a sub-ref.
		 * @return the current value at the current post-position.
		 */
		public T getCurrentPostVal() {
			return nextPostVal;
		}

		public Node<T> getCurrentSubNode() {
			return nextSubNode;
		}

		public Node<T> node() {
			return node;
		}

		public boolean isDepth0() {
			return isDepth0;
		}

		static <T> NodeIterator<T> create(Node<T> node, long[] valTemplate, 
				long[] rangeMin, long[] rangeMax, final int DIM, final boolean isDepth0) {
			if (!checkAndApplyRange(node, valTemplate, rangeMin, rangeMax)) {
				return null;
			}
			NodeIterator<T> iter = init(rangeMin, rangeMax, valTemplate, DIM, node, isDepth0);
			return iter;
		}
	}




	private static class NodeIteratorFull<T> {
		private final int DIM;
		private final int postLen;
		private final boolean isDepth0;
		private long next = -1;
		private long nextPost = -1;
		private long nextSub = -1;
		private long[] nextPostKey;
		private T nextPostVal;
		private Node<T> nextSubNode;
		private final Node<T> node;
		private int currentOffsetPostKey;
		private int currentOffsetPostVal;
		private int currentOffsetSub;
		private CBIterator<NodeEntry<T>> niIterator;
		private final int nMaxPost;
		private final int nMaxSub;
		private int postsFound = 0;
		private int posSubLHC = -1; //position in sub-node LHC array
		private final int postEntryLen;
		private final long[] valTemplate;
		private boolean isPostFinished;
		private boolean isSubFinished;

		/**
		 * 
		 * @param node
		 * @param DIM
		 * @param valTemplate A null indicates that no values are to be extracted.
		 */
		public NodeIteratorFull(Node<T> node, int DIM, long[] valTemplate, boolean isDepth0) {
			this.DIM = DIM;
			this.node = node;
			this.valTemplate = valTemplate;
			this.postLen = node.getPostLen();
			this.isDepth0 = isDepth0;
			nMaxPost = node.getPostCount();
			nMaxSub = node.getSubCount();
			isPostFinished = (nMaxPost <= 0);
			isSubFinished = (nMaxSub <= 0);
			//Position of the current entry
			currentOffsetSub = node.getBitPos_SubNodeIndex(DIM);
			currentOffsetSub -= (node.isSubHC()) ? 0 : Node.SIK_WIDTH(DIM);
			if (node.isPostNI()) {
				niIterator = node.ind.iterator();
				//not needed
				postEntryLen = -1;
			} else {
				currentOffsetPostKey = node.getBitPos_PostIndex(DIM);
				// -set key offset to position before first element
				// -set value offset to first element
				if (node.isPostHC()) {
					//length of post-fix WITHOUT key
					postEntryLen = DIM*postLen;
					currentOffsetPostVal = currentOffsetPostKey + (1<<DIM)*Node.PINN_HC_WIDTH;  
					currentOffsetPostKey -= Node.PINN_HC_WIDTH;
				} else {
					//length of post-fix WITH key
					postEntryLen = Node.PIK_WIDTH(DIM)+DIM*postLen;
					currentOffsetPostVal = currentOffsetPostKey + Node.PIK_WIDTH(DIM);  
					currentOffsetPostKey -= postEntryLen;
				}
			}

			//get infix
			if (valTemplate != null) {
				node.getInfix(valTemplate);
			}

			next = getNext();
		}

		boolean hasNext() {
			return next != -1;
		}

		void increment() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			next = getNext();
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
			return node.isPostNI() ? (nextSubNode != null) : (next == nextSub);
		}

		private void readValue(long pos, int offsPostKey) {
			if (valTemplate != null) {
				long[] key = new long[DIM];
				System.arraycopy(valTemplate, 0, key, 0, DIM);
				applyArrayPosToValue(pos, postLen, key, isDepth0);
				if (DEBUG_FULL) {
					//verify that we don't have keys here that can't possibly match...
					final long mask = (~0L)<<postLen;
					for (int i = 0; i < key.length; i++) {
						key[i] &= mask;
					}
				}
				nextPostVal = node.getPostPOB(offsPostKey, pos, key);
				nextPostKey = key;
			}
			//Don't set to 'null' here, that interferes with parallel iteration over post/sub 
			//nextSubNode = null;
		}

		private void readValue(long pos, NodeEntry<T> e) {
			//(valTemplate== null) always matches, special case for iterator in delete()
			if (valTemplate != null) {
				long[] buf = new long[DIM];
				System.arraycopy(valTemplate, 0, buf, 0, valTemplate.length);
				applyArrayPosToValue(pos, postLen, buf, isDepth0);

				//extract postfix
				final long mask = (~0L)<<postLen;
				long[] eKey = e.getKey();
				for (int i = 0; i < buf.length; i++) {
					buf[i] &= mask;  
					buf[i] |= eKey[i];
				}
				nextPostKey = e.getKey();
			}
			nextPostVal = e.getValue();
			nextSubNode = null;
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
				if (node.isPostHC()) {
					//while loop until 1 is found.
					long currentPos = next; 
					nextPost = -1;
					while (!isPostFinished) {
						if (currentPos >= 0) {
							currentPos++;  //pos w/o bit-offset
						} else {
							currentPos = 0; //initial value
						}
						if (currentPos >= (1<<DIM)) {
							isPostFinished = true;
							break;
						}
						currentOffsetPostKey += Node.PINN_HC_WIDTH;  //pos with bit-offset
						if (Bits.getBit(node.ba, currentOffsetPostKey)) {
							//read post-fix
							int offs = (int) (currentOffsetPostVal+currentPos*postEntryLen);
							readValue(currentPos, offs);
							nextPost = currentPos;
							break;
						}
					}
				} else {
					nextPost = -1;
					while (!isPostFinished) {
						if (postsFound >= nMaxPost) {
							isPostFinished = true;
							break;
						}
						currentOffsetPostKey += postEntryLen;
						long currentPos = Bits.readArray(node.ba, currentOffsetPostKey, Node.PIK_WIDTH(DIM));
						//read post-fix
						readValue(currentPos, currentOffsetPostKey+Node.PIK_WIDTH(DIM));
						nextPost = currentPos;
						postsFound++;
						break;
					}
				}
			}
			if (!isSubFinished && nextSub == next) {
				if (node.isSubHC()) {
					int currentPos = (int) next;  //We use (int) because arrays are always (int).
					int maxPos = 1<<DIM; 
					nextSub = -1;
					while (!isSubFinished) {
						currentPos++;
						if (currentPos >= maxPos) {
							isSubFinished = true;
							break;
						}
						if (node.subNRef[currentPos] != null) {
							nextSub = currentPos;
							nextSubNode = node.getSubNodeWithPos(currentPos, -1);
							break;
						}
					}
				} else {
					nextSub = -1;
					while (!isSubFinished) {
						if (posSubLHC + 1 >= nMaxSub) {
							isSubFinished = true;
							break;
						}
						currentOffsetSub += Node.SIK_WIDTH(DIM);
						long currentPos = Bits.readArray(node.ba, currentOffsetSub, Node.SIK_WIDTH(DIM));
						posSubLHC++;
						nextSub = currentPos;
						nextSubNode = node.getSubNodeWithPos(-1, posSubLHC);
						break;
					}
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

		private void niFindNext() {
			while (niIterator.hasNext()) {
				Entry<NodeEntry<T>> e = niIterator.nextEntry();
				long pos = e.key();
				next = pos;
				nextSubNode = e.value().node;
				if (nextSubNode == null) {
					readValue(e.key(), e.value());
				} else {
					nextPostVal = null;
					nextPostKey = null;
				}
				return;
			}

			next = -1;
			return;
		}


		/**
		 * Return the count of currently found sub-nodes minus one. For LHC, this is equal to 
		 * the position in the sub-node array.
		 * @return subCount - 1
		 */
		public int getPosSubLHC() {
			return posSubLHC;
		}

		/**
		 * Return the value at the current position of the POST-ITERATOR. This may be a higher
		 * value than the current pos in case current pos indicates a sub-ref.
		 * @return the current value at the current post-position.
		 */
		public long[] getCurrentPostKey() {
			return nextPostKey;
		}

		/**
		 * Return the value at the current position of the POST-ITERATOR. This may be a higher
		 * value than the current pos in case current pos indicates a sub-ref.
		 * @return the current value at the current post-position.
		 */
		public T getCurrentPostVal() {
			return nextPostVal;
		}

		public Node<T> getCurrentSubNode() {
			return nextSubNode;
		}
	}




	private Node<T> root = null;

	private Node<T> getRoot() {
		return root;
	}

	public PhTree5(int dim, int depth) {
		DIM = dim;
		DEPTH = depth;
		debugCheck();
	}

	@Override
	public int size() {
		return nEntries;
	}

	@Override
	public int getNodeCount() {
		return nNodes;
	}

	@Override
	public PhTreeQStats getQuality() {
		return getQuality(0, getRoot(), new PhTreeQStats(DEPTH));
	}

	private PhTreeQStats getQuality(int currentDepth, Node<T> node, PhTreeQStats stats) {
		stats.nNodes++;
		if (node.isPostHC()) {
			stats.nHCP++;
		}
		if (node.isSubHC()) {
			stats.nHCS++;
		}
		if (node.isPostNI()) {
			stats.nNI++;
		}
		stats.infixHist[node.getInfixLen()]++;
		stats.nodeDepthHist[currentDepth]++;
		int size = node.getPostCount() + node.getSubCount();
		stats.nodeSizeLogHist[32-Integer.numberOfLeadingZeros(size)]++;
		
		currentDepth += node.getInfixLen();
		stats.q_totalDepth += currentDepth;

		if (node.subNRef != null) {
			for (Node<T> sub: node.subNRef) {
				if (sub != null) {
					getQuality(currentDepth + 1, sub, stats);
				}
			}
		} else {
			if (node.ind != null) {
				for (NodeEntry<T> n: node.ind) {
					if (n.node != null) {
						getQuality(currentDepth + 1, n.node, stats);
					}
				}
			}
		}

		//count post-fixes
		stats.q_nPostFixN[currentDepth] += node.getPostCount();

		return stats;
	}


	@Override
	public Stats getStats() {
		return getStats(0, getRoot(), new Stats());
	}

	private Stats getStats(int currentDepth, Node<T> node, Stats stats) {
		final int REF = 4;//bytes for a reference
		stats.nNodes++;
		// this +  ref-SubNRef[] + ref-subB[] + refInd + refVal[] + infLen + infOffs
		stats.size += align8(12 + REF + REF + REF +  REF + 1 + 1 + 1 + 1);

		currentDepth += node.getInfixLen();
		int nChildren = 0;
		if (node.isPostNI()) {
			nChildren += node.ind.size();
			stats.size += (node.ind.size()-1) * 48 + 40;
			if (node.getSubCount() == 0) {
				stats.nLeafNodes++;
			} else {
				stats.nInnerNodes++;
			}
			for (NodeEntry<T> e: node.ind) {
				stats.size += 24; //e
				if (e.node != null) {
					getStats(currentDepth + 1, e.node, stats);
				} else {
					//count post-fixes
					stats.size += 16 + e.getKey().length*8;
				}
			}
		} else {
			if (node.subNRef != null) {
				stats.size += 16 + align8(node.subNRef.length * REF);
				stats.nInnerNodes++;
				for (Node<T> sub: node.subNRef) {
					if (sub != null) {
						nChildren++;
						getStats(currentDepth + 1, sub, stats);
					}
				}
				stats.nSubOnly += nChildren;
			} else {
				stats.nLeafNodes++;
			}
			nChildren += node.getPostCount();
			//count post-fixes
			stats.size += 16 + align8(Bits.arraySizeInByte(node.ba));
		}


		if (nChildren == 1) {
			//This should not happen! Except for a root node if the tree has <2 entries.
			System.err.println("WARNING: found lonely node..." + (node == getRoot()));
			stats.nLonely++;
		}
		if (nChildren == 0) {
			//This should not happen! Except for a root node if the tree has <2 entries.
			System.err.println("WARNING: found ZOMBIE node..." + (node == getRoot()));
			stats.nLonely++;
		}
		stats.nChildren += nChildren;
		return stats;
	}

	@Override
	public Stats getStatsIdealNoNode() {
		return getStatsIdealNoNode(0, getRoot(), new Stats());
	}

	private Stats getStatsIdealNoNode(int currentDepth, Node<T> node, Stats stats) {
		final int REF = 4;//bytes for a reference
		stats.nNodes++;

		// 16=object[] +  16=byte[] + value[]
		stats.size += 16 + 16 + 16;

		//  infixLen + isHC + + postlen 
		stats.size += 1 + 1 + 1 + 4 * REF;

		int sizeBA = 0;
		sizeBA = node.calcArraySizeTotalBits(node.getPostCount(), DIM);
		sizeBA = Bits.calcArraySize(sizeBA);
		sizeBA = Bits.arraySizeInByte(sizeBA);
		stats.size += align8(sizeBA);

		currentDepth += node.getInfixLen();
		int nChildren = 0;

		if (node.isPostNI()) {
			nChildren = node.ind.size();
			stats.size += (nChildren-1) * 48 + 40;
			if (node.getSubCount() == 0) {
				stats.nLeafNodes++;
			} else {
				stats.nInnerNodes++;
			}
			for (NodeEntry<T> e: node.ind) {
				stats.size += 24; //e
				if (e.node != null) {
					getStatsIdealNoNode(currentDepth + 1, e.node, stats);
				} else {
					//count post-fixes
					stats.size += 16 + e.getKey().length*8;
				}
			}
		} else {
			if (node.isSubHC()) {
				stats.nHCS++;
			}
			if (node.subNRef != null) {
				//+ REF for the byte[]
				stats.size += align8(node.getSubCount() * REF + REF);
				stats.nInnerNodes++;
				for (Node<T> sub: node.subNRef) {
					if (sub != null) {
						nChildren++;
						getStatsIdealNoNode(currentDepth + 1, sub, stats);
					}
				}
				stats.nSubOnly += nChildren;
			} else {
				//byte[] ref
				stats.size += align8(1 * REF);
				stats.nLeafNodes++;
			}

			//count post-fixes
			nChildren += node.getPostCount();
			if (node.isPostHC()) {
				stats.nHCP++;
			}
		}


		stats.nChildren += nChildren;

		//sanity checks
		if (nChildren == 1) {
			//This should not happen! Except for a root node if the tree has <2 entries.
			System.err.println("WARNING: found lonely node..." + (node == getRoot()));
			stats.nLonely++;
		}
		if (nChildren == 0) {
			//This should not happen! Except for a root node if the tree has <2 entries.
			System.err.println("WARNING: found ZOMBIE node..." + (node == getRoot()));
			stats.nLonely++;
		}
		if (node.isPostHC() && node.isSubHC()) {
			System.err.println("WARNING: Double HC found");
		}
		if (DIM<=31 && node.getPostCount() + node.getSubCount() > (1L<<DIM)) {
			System.err.println("WARNING: Over-populated node found: pc=" + node.getPostCount() + 
					"  sc=" + node.getSubCount());
		}
		//check space
		int baS = node.calcArraySizeTotalBits(node.getPostCount(), DIM);
		baS = Bits.calcArraySize(baS);
		if (baS < node.ba.length) {
			stats.nTooLarge++;
			if ((node.ba.length - baS)==2) {
				stats.nTooLarge2++;
			} else if ((node.ba.length - baS)==4) {
				stats.nTooLarge4++;
			} else {
				System.err.println("Array too large: " + node.ba.length + " - " + baS + " = " + 
						(node.ba.length - baS));
			}
		}
		return stats;
	}

	@Override
	public T put(long[] key, T value) {
		if (getRoot() == null) {
			root = Node.createNode(this, 0, DEPTH-1, 1, DIM);
			//calcPostfixes(valueSet, root, 0);
			long pos = posInArray(key, 0, DEPTH);
			root.addPost(pos, key, value);
			nEntries++;
			return null;
		}
        return insert(key, value, 0, getRoot());
    }

	@Override
	public boolean contains(long... key) {
		if (getRoot() == null) {
			return false;
		}
		return contains(key, 0, getRoot());
	}


	private boolean contains(long[] key, int currentDepth, Node<T> node) {
		if (node.getInfixLen() > 0) {
			long mask = ~((-1l)<<node.getInfixLen()); // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
			int shiftMask = node.getPostLen()+1;
			//mask <<= shiftMask; //last bit is stored in bool-array
			mask = shiftMask==64 ? 0 : mask<<shiftMask;
			for (int i = 0; i < key.length; i++) {
				if (((key[i] ^ node.getInfix(i)) & mask) != 0) {
					//infix does not match
					return false;
				}
			}
			currentDepth += node.getInfixLen();
		}

		long pos = posInArray(key, currentDepth, DEPTH);

		//NI-node?
		if (node.isPostNI()) {
			NodeEntry<T> e = node.getChildNI(pos);
			if (e == null) {
				return false;
			} else if (e.node != null) {
				return contains(key, currentDepth + 1, e.node);
			}
			return node.postEquals(e.getKey(), key);
		}

		//check sub-node (more likely than postfix, because there can be more than one value)
		Node<T> sub = node.getSubNode(pos, DIM);
		if (sub != null) {
			return contains(key, currentDepth + 1, sub);
		}

		//check postfix
		int pob = node.getPostOffsetBits(pos, DIM);
		if (pob >= 0) {
			return node.postEqualsPOB(pob, pos, key);
		}

		return false;
	}

	@Override
	public T get(long... key) {
		if (getRoot() == null) {
			return null;
		}
		return get(key, 0, getRoot());
	}


	private T get(long[] key, int currentDepth, Node<T> node) {
		if (node.getInfixLen() > 0) {
			long mask = (1l<<node.getInfixLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
			int shiftMask = node.getPostLen()+1;
			//mask <<= shiftMask; //last bit is stored in bool-array
			mask = shiftMask==64 ? 0 : mask<<shiftMask;
			for (int i = 0; i < key.length; i++) {
				if (((key[i] ^ node.getInfix(i)) & mask) != 0) {
					//infix does not match
					return null;
				}
			}
			currentDepth += node.getInfixLen();
		}

		long pos = posInArray(key, currentDepth, DEPTH);

		//check sub-node (more likely than postfix, because there can be more than one value)
		Node<T> sub = node.getSubNode(pos, DIM);
		if (sub != null) {
			return get(key, currentDepth + 1, sub);
		}

		//check postfix
		int pob = node.getPostOffsetBits(pos, DIM);
		if (pob >= 0) {
			if (node.postEqualsPOB(pob, pos, key)) {
				return node.getPostValuePOB(pob, pos, DIM);
			}
		}

		return null;
	}

	/**
	 * A value-set is an object with n=DIM values.
	 * @param key
	 * @return true if the value was found
	 */
	@Override
	public T remove(long... key) {
		if (getRoot() == null) {
			return null;
		}
        return delete(key, getRoot(), 0, null, UNKNOWN, null, null);
	}

	/**
	 * Merging occurs if a node is not the root node and has after deletion less than two children.
	 * @param key new value to be deleted
	 */
	private T delete(long[] key, Node<T> node, int currentDepth, Node<T> parent, long posInParent, 
			long[] newKey, int[] insertRequired) {
		//first, check infix!
		//second, check post/sub
		//third, remove post or follow sub.

		//check infix
		if (node.getInfixLen() > 0) {
			long mask = (1l<<node.getInfixLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
			int shiftMask = (node.getPostLen()+1);
			//mask <<= shiftMask; //last bit is stored in bool-array
			mask = shiftMask==64 ? 0 : mask<<shiftMask;
			for (int i = 0; i < DIM; i++) {
				if (((key[i] ^ node.getInfix(i)) & mask) != 0) {
					//infix does not match
					return null;
				}
			}
			currentDepth += node.getInfixLen();
		}

		//NI-node?
		if (node.isPostNI()) {
			T ret = deleteNI(key, node, currentDepth, parent, posInParent, newKey, insertRequired);
			if (insertRequired != null && insertRequired[0] < node.getPostLen()) {
				insert(newKey, ret, currentDepth-node.getInfixLen(), node);
				insertRequired[0] = NO_INSERT_REQUIRED;
			}
			return ret;
		}

		//check for sub
		final long pos = posInArray(key, currentDepth, DEPTH);
		Node<T> sub1 = node.getSubNode(pos, DIM);
		if (sub1 != null) {
			T ret = delete(key, sub1, currentDepth+1, node, pos, newKey, insertRequired);
			if (insertRequired != null && insertRequired[0] < node.getPostLen()) {
				insert(newKey, ret, currentDepth-node.getInfixLen(), node);
				insertRequired[0] = NO_INSERT_REQUIRED;
			}
			return ret;
		}

		//check matching post
		int pob = node.getPostOffsetBits(pos, DIM);
		if (pob < 0) {
			return null;
		}

		if (!node.postEqualsPOB(pob, pos, key)) {
			//value does not exist
			return null;
		}

		//Check for update()
		if (newKey != null) {
			long diff = 0;
			for (int i = 0; i < key.length; i++) {
				//write all differences to DIFF, we just check x afterwards
				diff |= (key[i] ^ newKey[i]);
			}
			int bitPosOfDiff = Long.SIZE-Long.numberOfLeadingZeros(diff);
			if (bitPosOfDiff <= node.getPostLen()) {
				//replace
				T oldValue = node.getPostValuePOB(pob, pos, DIM);
				node.replacePost(pob, pos, newKey, oldValue);
				return oldValue;
			} else {
				insertRequired[0] = bitPosOfDiff;
			}
		}
		
		//okay we have something to delete 
		nEntries--;

		//check if merging is necessary (check children count || isRootNode)
		int nP = node.getPostCount();
		int nS = node.getSubCount();
		if (parent == null || nP + nS > 2) {
			//no merging required
			//value exists --> remove it
			T ret = node.getPostValuePOB(pob, pos, DIM);
			node.removePostPOB(pos, pob, DIM);
			return ret;
		}

		//okay, at his point we have a post that matches and (since it matches) we need to remove 
		//the local node because it contains at most one other entry and it is not the root node.
		nNodes--;


		T oldValue = node.getPostValue(pos, DIM);

		//locate the other entry
		NodeIteratorFull<T> iter = new NodeIteratorFull<T>(node, DIM, null, currentDepth==0);
		long pos2 = iter.getCurrentPos();
		if (pos2 == pos) {
			//pos2 is the entry to be deleted, find the other entry for pos2
			iter.increment();
			pos2 = iter.getCurrentPos();
		}
		boolean isSubNode = iter.isNextSub(); 
		int posSubLHC = iter.getPosSubLHC();

		if (!isSubNode) {
			//this is also a post
			long[] newPost = new long[DIM];
			node.getInfixNoOverwrite(newPost);
			T val = node.getPost(pos2, newPost);
			applyArrayPosToValue(pos2, node.getPostLen(), newPost, currentDepth==0);
			parent.removeSub(posInParent, DIM);
			parent.addPost(posInParent, newPost, val);
			return oldValue;
		}

		//connect sub to parent
		Node<T> sub2 = node.getSubNodeWithPos(pos2, posSubLHC);

		// build new infix
		long[] infix = new long[DIM];
		node.getInfixNoOverwrite(infix);
		sub2.getInfixNoOverwrite(infix);
		applyArrayPosToValue(pos2, node.getPostLen(), infix, currentDepth==0);

		// update infix-len and resize array
		int infOffs = node.getBitPos_Infix();
		int newInfixLen = node.getInfixLen() + 1 + sub2.getInfixLen();
		sub2.infixLen = (byte)newInfixLen;
		sub2.ba = Bits.arrayEnsureSize(sub2.ba, sub2.calcArraySizeTotalBits(
				sub2.getPostCount(), DIM));
		Bits.insertBits(sub2.ba, infOffs, DIM*(node.getInfixLen()+1));

		// update infix
		sub2.writeInfix(infix);

		//update parent, the position is the same
		parent.replaceSub(posInParent, sub2, DIM);

		return oldValue;
	}

	private T deleteNI(long[] key, Node<T> node, int currentDepth,
			Node<T> parent, long posInParent, long[] newKey, int[] insertRequired) {
		final long pos = posInArray(key, currentDepth, DEPTH);
		NodeEntry<T> e = node.getChildNI(pos);
		if (e == null) {
			return null;
		}
		if (e.node != null) {
			T ret = delete(key, e.node, currentDepth+1, node, pos, newKey, insertRequired);
			if (insertRequired != null && insertRequired[0] < node.getPostLen()) {
				insert(newKey, ret, currentDepth, node);
				insertRequired[0] = NO_INSERT_REQUIRED;
			}
			return ret;
		}

		if (!node.postEquals(e.getKey(), key)) {
			//value does not exist
			return null;
		}

		//Check for update()
		if (newKey != null) {
			long diff = 0;
			for (int i = 0; i < key.length; i++) {
				//write all differences to DIFF, we just check x afterwards
				diff |= (key[i] ^ newKey[i]);
			}
			int bitPosOfDiff = Long.SIZE-Long.numberOfLeadingZeros(diff);
			if (bitPosOfDiff <= node.getPostLen()) {
				//replace
				NodeEntry<T> ne = node.niGet(pos);
				T oldValue = ne.getValue();
				ne.setPost(newKey.clone(), oldValue);
				return oldValue;
			} else {
				insertRequired[0] = bitPosOfDiff;
			}
		}

		
		//okay we have something to delete 
		nEntries--;

		//check if merging is necessary (check children count || isRootNode)
		int nP = node.getPostCount();
		int nS = node.getSubCount();
		if (parent == null || nP + nS > 2) {
			//no merging required
			//value exists --> remove it
			node.removePostPOB(pos, -1, DIM);  //do not call-NI directly, we may have to deconstruct 
			return e.getValue();
		}

		//The following code is never used because NI implies nP+nS > 50
		
		//okay, at his point we have a post that matches and (since it matches) we need to remove 
		//the local node because it contains at most one other entry and it is not the root node.
		nNodes--;


		T oldValue = e.getValue();

		//locate the other entry
		CBIterator<NodeEntry<T>> iter = node.ind.iterator();
		Entry<NodeEntry<T>> ie = iter.nextEntry();
		if (ie.key() == pos) {
			//pos2 is the entry to be deleted, find the other entry for pos2
			ie = iter.nextEntry();
		}

		e = ie.value(); 
		if (e.getKey() != null) {
			//this is also a post
			long[] newPost = e.getKey();
			node.getInfixNoOverwrite(newPost);
			T val = e.getValue();
			applyArrayPosToValue(ie.key(), node.getPostLen(), newPost, currentDepth==0);
			parent.removeSub(posInParent, DIM);
			parent.addPost(posInParent, newPost, val);
			return oldValue;
		}

		//connect sub to parent
		Node<T> sub2 = e.node;

		// build new infix
		long[] infix = new long[DIM];
		node.getInfixNoOverwrite(infix);
		sub2.getInfixNoOverwrite(infix);
		applyArrayPosToValue(ie.key(), node.getPostLen(), infix, currentDepth==0);

		// update infix-len and resize array
		int infOffs = node.getBitPos_Infix();
		int newInfixLen = node.getInfixLen() + 1 + sub2.getInfixLen();
		sub2.infixLen = (byte)newInfixLen;
		sub2.ba = Bits.arrayEnsureSize(sub2.ba, sub2.calcArraySizeTotalBits(
				sub2.getPostCount(), DIM));
		Bits.insertBits(sub2.ba, infOffs, DIM*(node.getInfixLen()+1));

		// update infix
		sub2.writeInfix(infix);

		//update parent, the position is the same
		parent.replaceSub(posInParent, sub2, DIM);

		return oldValue;
	}

	private T insertNI(long[] key, T value, int currentDepth, Node<T> node, long pos) {
		NodeEntry<T> e = node.getChildNI(pos);
		if (e == null) {
			//nothing found at all
			//insert as postfix
			node.addPostPOB(pos, -1, key, value);
			nEntries++;
			return null;
		} else if (e.node != null) {
			Node<T> sub = e.node;
			//sub found
			if (sub.hasInfixes()) {
				//splitting may be required, the node has infixes
				return insertSplit(key, value, sub, currentDepth+1, node, pos);
			}
			//splitting not necessary
			return insert(key, value, currentDepth+1, sub);
		} else {
			//must be a post
			T prevVal = e.getValue(); 
			//maybe it's the same value that we want to add?
			if (node.postEquals(e.getKey(), key)) {
				//value exists
				e.setPost(e.getKey(), value);
				return prevVal;
			}

			//existing value
			//Create a new node that contains the existing and the new value.
			Node<T> sub = calcPostfixes(key, value, e.getKey(), prevVal, currentDepth+1);

			//replace value with new leaf
			node.setPostCount(node.getPostCount()-1);
			node.setSubCount(node.getSubCount()+1);
			e.setNode(sub);
			nEntries++;
			return null;
		}
	}

	private T insert(long[] key, T value, int currentDepth, Node<T> node) {
		currentDepth += node.getInfixLen();
		//for a leaf node, the existence of a sub just indicates that the value may exist.
		long pos = posInArray(key, currentDepth, DEPTH);
		if (currentDepth+1 < DEPTH) {
			if (node.isPostNI()) {
				return insertNI(key, value, currentDepth, node, pos);
			}
			Node<T> sub = node.getSubNode(pos, DIM);
			if (sub == null) {
				//do we have a postfix at that position?
				int pob = node.getPostOffsetBits(pos, DIM);
				if (pob >= 0) {

					//maybe it's the same value that we want to add?
					if (node.postEqualsPOB(pob, pos, key)) {
						//value exists
						return node.updatePostValuePOB(pob, pos, key, DIM, value);
					}

					//existing value
					long[] prevKey = new long[DIM];
					T prevVal = node.getPostPOB(pob, pos, prevKey);
					//Create a new node that contains the existing and the new value.
					sub = calcPostfixes(key, value, prevKey, prevVal, currentDepth+1);

					node.removePostPOB(pos, pob, DIM);

					//insert a new leaf
					node.addSubNode(pos, sub, DIM);
				} else {
					//insert as postfix
					node.addPostPOB(pos, pob, key, value);
				}
				nEntries++;
				return null;
			} else {
				if (sub.hasInfixes()) {
					//splitting may be required, the node has infixes
					return insertSplit(key, value, sub, currentDepth+1, node, pos);
				}
				//splitting not necessary
				return insert(key, value, currentDepth+1, sub);
			}
		} else {
			//is leaf
			int pob = node.getPostOffsetBits(pos, DIM);
			if (pob < 0) {
				node.addPostPOB(pos, pob, key, value);
				nEntries++;
				return null;
			}
			if (node.postEqualsPOB(pob, pos, key)) {
				//value exists
				return node.updatePostValuePOB(pob, pos, key, DIM, value);
				//return node.getPost(pos, key);
			}
			throw new IllegalStateException("cd="+currentDepth + " il=" + node.getInfixLen());
		}
	}

	/**
	 * Post-fixes are useful when inserting new elements.
	 * @param key1
	 * @param node
	 * @param currentDepth
	 * @return true if the value already existed
	 */
	private Node<T> calcPostfixes(long[] key1, T val1, long[] key2, T val2, int currentDepth) {
		//determine length of infix
		int mcb = getMaxConflictingBits(key1, key2, DEPTH-currentDepth);
		int infLen = DEPTH - currentDepth - mcb;
		int postLen = mcb-1;
		Node<T> node = Node.createNode(this, infLen, postLen, 2, DIM); 

		node.writeInfix(key1);
		long posSub1 = posInArray(key1, postLen);
		node.addPost(posSub1, key1, val1);
		long posSub2 = posInArray(key2, postLen);
		node.addPost(posSub2, key2, val2);
		return node;
	}

	private int getConflictingInfixBits(long[] key, long[] infix, Node<T> node) {
		if (node.getInfixLen() == 0) {
			return 0;
		}
		long mask = (1l<<node.getInfixLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
		int maskOffset = node.getPostLen()+1;
		mask = maskOffset==64 ? 0 : mask<< maskOffset; //last bit is stored in bool-array
		return getMaxConflictingBitsWithMask(key, infix, mask);
	}


	/**
	 * Splitting occurs if a node with an infix has to be split, because a new value to be inserted
	 * requires a partially different infix.
	 * @param key new value to be inserted
	 */
	private T insertSplit(long[] key, T value, Node<T> node, int currentDepth, Node<T> parent,
			long posInParent) {
		//check if splitting is necessary
		long[] infix = new long[DIM];
		node.getInfixNoOverwrite(infix);
		int maxConflictingBits = getConflictingInfixBits(key, infix, node);
		if (maxConflictingBits == 0) {
			//no conflicts detected, no splitting required.
			return insert(key, value, currentDepth, node);
		}


		//do the splitting
		//newLocalLen: -1 for the bit stored in the map
		byte newLocalLen = (byte) (DEPTH-currentDepth-maxConflictingBits);
		int newSubInfLen = node.getInfixLen() - newLocalLen - 1;

		//What does 'splitting' mean:
		//The current node has an infix that is not (or only partially) compatible with the new key. 
		//The new key should end up as post-fix for the current node. All current post-fixes
		//and sub-nodes are moved to a new sub-node. We know that there are more than two children
		//(posts+subs), otherwise the node wshould have been removed already.

		//How splitting works:
		//We insert a new node between the current and the parent node.
		//The parent is then updated with the new sub-node and the current node gets a shorter
		//infix.

		//We use the infixes as references, because they provide the correct location for the new sub
		long posOfNewSub = posInArrayFromInfixes(node, newLocalLen);

		//create new middle node
		int newPostLen = (DEPTH-currentDepth-newLocalLen-1);
		Node<T> newNode = Node.createNode(this, newLocalLen, newPostLen, 1, DIM); 
		if (newLocalLen > 0) {
			newNode.writeInfix(infix);
		}

		int oldInfLen = node.getInfixLen();
		node.infixLen = (byte) newSubInfLen;
		
		//cut off existing prefixes in sub-node
		Bits.removeBits(node.ba, node.getBitPos_Infix(), (oldInfLen-newSubInfLen)*DIM);
		node.writeInfix(infix);
		//ensure that subNode has correct byte[] size
		node.ba = Bits.arrayTrim(node.ba, node.calcArraySizeTotalBits(
				node.getPostCount(), DIM));

		
		//insert the sub into new node
		newNode.addSubNode(posOfNewSub, node, DIM);

		//insert key into new node
		long pos = posInArray(key, currentDepth+newLocalLen, DEPTH);
		newNode.addPost(pos, key, value);

		nEntries++;
		
		parent.replaceSub(posInParent, newNode, DIM);
		
		return null;
	}


	private long posInArrayFromInfixes(Node<T> node, int infixInternalOffset) {
		//n=DIM,  i={0..n-1}
		// i = 0 :  |   0   |   1   |
		// i = 1 :  | 0 | 1 | 0 | 1 |
		// i = 2 :  |0|1|0|1|0|1|0|1|
		//len = 2^n

		long pos = 0;
		for (int i = 0; i < DIM; i++) {
			pos <<= 1;
//			if (node.getInfixBit(i, infixInternalOffset)) {
//				pos |= 1L;
//			}
			pos |= node.getInfixBit(i, infixInternalOffset);
		}
		return pos;
	}

	/**
	 * Apply a HC-position to a value. This means setting one bit for each dimension.
	 * @param pos
	 * @param val
	 */
	static void applyArrayPosToValue(long pos, int currentDepth, long[] val, final int DEPTH) {
		int currentPostLen = DEPTH-1-currentDepth;
		applyArrayPosToValue(pos, currentPostLen, val, currentDepth == 0);
	}

	/**
	 * Apply a HC-position to a value. This means setting one bit for each dimension.
	 * @param pos
	 * @param currentPostLen
	 * @param val
	 * @param isDepth0 Set this to true if currentDepth==0.
	 */
	static void applyArrayPosToValue(long pos, int currentPostLen, long[] val, boolean isDepth0) {
		final int DIM = val.length;
		//for depth=0 we need to set leading zeroes, for example if a valTemplate had previously
		//been assigned leading '1's for a negative value.
		long mask0 = isDepth0 ? 0L : ~(1l << currentPostLen);
		//leading '1'-digits for negative values on depth=0
		//mask1 = !isDepth0 ?   (1l << currentPostLen)  :  ((-1L) << currentPostLen); 
		long mask1 = (isDepth0 ? -1L : 1L) << currentPostLen;
		long posMask = 1L<<DIM;
		for (int d = 0; d < DIM; d++) {
			posMask >>>= 1;
			long x = pos & posMask;
			if (x!=0) {
				val[d] |= mask1;
			} else {
				val[d] &= mask0;
			}
		}
	}

	@Override
	public String toString() {
		return toStringPlain();
	}

	@Override
	public String toStringPlain() {
		StringBuilderLn sb = new StringBuilderLn();
		if (getRoot() != null) {
			toStringPlain(sb, 0, getRoot(), new long[DIM]);
		}
		return sb.toString();
	}

	private void toStringPlain(StringBuilderLn sb, int currentDepth, Node<T> node, long[] key) {
		//for a leaf node, the existence of a sub just indicates that the value exists.
		node.getInfix(key);
		currentDepth += node.getInfixLen();

		for (int i = 0; i < 1L << DIM; i++) {
			applyArrayPosToValue(i, currentDepth, key, DEPTH);
			//inner node?
			Node<T> sub = node.getSubNode(i, DIM);
			if (sub != null) {
				toStringPlain(sb, currentDepth + 1, sub, key);
			}

			//post-fix?
			if (node.hasPostFix(i, DIM)) {
				node.getPost(i, key);
				sb.append(Bits.toBinary(key, DEPTH));
				sb.appendLn("  v=" + node.getPostValue(i, DIM));
			}
		}
	}


	@Override
	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		if (getRoot() != null) {
			toStringTree(sb, 0, getRoot(), new long[DIM], true);
		}
		return sb.toString();
	}

	private void toStringTree(StringBuilderLn sb, int currentDepth, Node<T> node, long[] key, 
			boolean printValue) {
		String ind = "*";
		for (int i = 0; i < currentDepth; i++) ind += "-";
		sb.append( ind + "il=" + node.getInfixLen() + " io=" + (node.getPostLen()+1) + 
				" sc=" + node.getSubCount() + " pc=" + node.getPostCount() + " inf=[");

		//for a leaf node, the existence of a sub just indicates that the value exists.
		node.getInfix(key);
		if (node.getInfixLen() > 0) {
			long[] inf = new long[DIM];
			node.getInfix(inf);
			sb.append(Bits.toBinary(inf, DEPTH));
			currentDepth += node.getInfixLen();
		}
		sb.appendLn("]");

		//To clean previous postfixes.
		for (int i = 0; i < 1L << DIM; i++) {
			applyArrayPosToValue(i, currentDepth, key, DEPTH);
			Node<T> sub = node.getSubNode(i, DIM);
			if (sub != null) {
				sb.appendLn(ind + "# " + i + "  +");
				toStringTree(sb, currentDepth + 1, sub, key, printValue);
			}

			//post-fix?
			if (node.hasPostFix(i, DIM)) {
				T v = node.getPost(i, key);
				sb.append(ind + Bits.toBinary(key, DEPTH));
				if (printValue) {
					sb.append("  v=" + v);
				}
				sb.appendLn("");
			}
		}
	}


	@Override
	public PhIterator<T> queryExtent() {
		if (DIM < 10) {
			return new NDFullIterator<T>(getRoot(), DIM);
		} else {
			return new NDFullIterator2<T>(getRoot(), DIM);
		}
	}

	private static class NDFullIterator<T> implements PhIterator<T> {
		private final int DIM;
		private final Stack<Pos<T>> stack = new Stack<Pos<T>>();
		private static class Pos<T> {
			Pos(Node<T> node, boolean isDepth0) {
				this.node = node;
				this.pos = -1;
				this.isDepth0 = isDepth0;
			}
			Node<T> node;
			int pos;
			//The depth of the HC
			boolean isDepth0;
		}
		private final long[] valTemplate;// = new long[DIM];
		private long[] nextKey = null;
		private T nextVal = null;

		public NDFullIterator(Node<T> root, final int DIM) {
			this.DIM = DIM;
			valTemplate = new long[DIM];
			if (root == null) {
				//empty index
				return;
			}
			stack.push(new Pos<T>(root, true));
			findNextElement();
		}

		private void findNextElement() {
			while (true) {
				Pos<T> p = stack.peek();
				while (p.pos+1 < (1L<<DIM)) {
					p.pos++;
					p.node.getInfix(valTemplate);
					Node<T> sub = p.node.getSubNode(p.pos, DIM); 
					if (sub != null) {
						applyArrayPosToValue(p.pos, p.node.getPostLen(), valTemplate, p.isDepth0);
						stack.push(new Pos<T>(sub, false));
						findNextElement();
						return;
					}
					int pob = p.node.getPostOffsetBits(p.pos, DIM);
					if (pob >= 0) {
						//get value
						long[] key = new long[DIM];
						System.arraycopy(valTemplate, 0, key, 0, DIM);
						applyArrayPosToValue(p.pos, p.node.getPostLen(), key, p.isDepth0);
						nextVal = p.node.getPostPOB(pob, p.pos, key);
						nextKey = key;
						return;
					}
				}
				stack.pop();
				if (stack.isEmpty()) {
					//finished
					nextKey = null;
					nextVal = null;
					return;
				}
			}
		}

		@Override
		public boolean hasNext() {
			return nextKey != null;
		}

		@Override
		public long[] nextKey() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			long[] res = nextKey;
			findNextElement();
			return res;
		}

		@Override
		public T nextValue() {
			T ret = nextVal;
			nextKey();
			return ret;
		}

		@Override
		public PhEntry<T> nextEntry() {
			PhEntry<T> ret = new PhEntry<>(nextKey, nextVal);
			nextKey();
			return ret;
		}

		@Override
		public T next() {
			return nextValue();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Not implemented yet.");
		}

	}


	private static class NDFullIterator2<T> implements PhIterator<T> {
		private final int DIM;
		private final Stack<NodeIteratorFull<T>> stack = new Stack<>();
		private final long[] valTemplate;
		private long[] nextKey = null;
		private T nextVal = null;

		public NDFullIterator2(Node<T> root, final int DIM) {
			this.DIM = DIM;
			valTemplate = new long[DIM];
			if (root == null) {
				//empty index
				return;
			}
			stack.push(new NodeIteratorFull<T>(root, DIM, valTemplate, true));
			findNextElement();
		}

		private void findNextElement() {
			while (true) {
				NodeIteratorFull<T> p = stack.peek();
				while (p.hasNext()) {
					long pos = p.getCurrentPos();

					if (p.isNextSub()) {
						applyArrayPosToValue(pos, p.node.getPostLen(), valTemplate, p.isDepth0);
						stack.push(new NodeIteratorFull<T>(p.getCurrentSubNode(), DIM, valTemplate, false));
						findNextElement();
					} else {
						nextVal = p.getCurrentPostVal();
						nextKey = p.getCurrentPostKey();
					}
					p.increment();
					return;
				}
				stack.pop();
				if (stack.isEmpty()) {
					//finished
					nextKey = null;
					nextVal = null;
					return;
				}
			}
		}

		@Override
		public boolean hasNext() {
			return nextKey != null;
		}

		@Override
		public long[] nextKey() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			long[] res = nextKey;
			findNextElement();
			return res;
		}

		@Override
		public T nextValue() {
			T ret = nextVal;
			nextKey();
			return ret;
		}

		@Override
		public PhEntry<T> nextEntry() {
			PhEntry<T> ret = new PhEntry<>(nextKey, nextVal);
			nextKey();
			return ret;
		}

		@Override
		public T next() {
			return nextValue();
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException("Not implemented yet.");
		}
	}


	/**
	 * Performes a range query. The parameters are the min and max values.
	 * @param min
	 * @param max
	 * @return Result iterator.
	 */
	@Override
	public PhIterator<T> query(long[] min, long[] max) {
		if (min.length != DIM || max.length != DIM) {
			throw new IllegalArgumentException("Invalid number of arguments: " + min.length +  
					" / " + max.length + "  DIM=" + DIM);
		}
		//return new PhIterator<T>(getRoot(), min, max, DIM, DEPTH);
		//return new PhIteratorHighK<T>(getRoot(), min, max, DIM, DEPTH);
		return new PhIteratorReuse<>(root, min, max, DIM, DEPTH);
	}

	/**
	 * Performes a range query. The parameters are the min and max values.
	 * @param min
	 * @param max
	 * @return Result list.
	 */
	@Override
	public List<PhEntry<T>> queryAll(long[] min, long[] max) {
		return queryAll(min, max, Integer.MAX_VALUE, PhPredicate.ACCEPT_ALL, 
				PhMapper.PVENTRY());
	}
	
	/**
	 * Performes a range query. The parameters are the min and max values.
	 * @param min
	 * @param max
	 * @return Result list.
	 */
	@Override
	public <R> List<R> queryAll(long[] min, long[] max, int maxResults, 
			PhPredicate filter, PhMapper<T, R> mapper) {
		if (min.length != DIM || max.length != DIM) {
			throw new IllegalArgumentException("Invalid number of arguments: " + min.length +  
					" / " + max.length + "  DIM=" + DIM);
		}
		
//		if (filter == null) {
//			filter = PhPredicate.ACCEPT_ALL;
//		}
//		if (mapper == null) {
//			mapper = (PhMapper<T, R>) PhMapper.PVENTRY();
//		}
		
		long[] valTemplate = new long[DIM];
		ArrayList<R> list = NodeIteratorList.query(root, valTemplate, min, max, 
				DIM, true, maxResults, filter, mapper);
//		ArrayList<R> list = NodeIteratorListReuse.query(root, valTemplate, min, max, 
//				DIM, true, maxResults, filter, mapper);
		return list;
	}

	/**
	 * Locate all entries where the given attribute lies between the given values.
	 * @param attrID 	Attribute ID, counting 0..n-1
	 * @param min	 	Lower bound
	 * @param max 		Upper bound
	 * @return 			Result list
	 */
	public Iterator<long[]> querySingle(int attrID, long min, long max) {
		return new PhIteratorSingle<T>(getRoot(), attrID, min, max, DIM, DEPTH);
	}


	static final <T> boolean checkAndApplyRange(Node<T> node, long[] valTemplate, 
			long[] rangeMin, long[] rangeMax) {
		//first check if node-prefix allows sub-node to contain any useful values
		int infixLen = node.getInfixLen();
		int postLen = node.getPostLen();

		//assign infix
		int postHcInfixLen = postLen + 1 + infixLen;
		long maskClean = postHcInfixLen==64 ? //currentDepth == 0 && DEPTH == 64 
				0 : ((0xFFFFFFFFFFFFFFFFL<<postHcInfixLen));
		if (infixLen > 0) {
			//first, clean trailing bits
			//Mask for comparing the tempVal with the ranges, except for bit that have not been
			//extracted yet.
			long compMask = (-1L)<<postLen + 1;
			for (int dim = 0; dim < valTemplate.length; dim++) {
				long in = node.getInfix(dim);
				valTemplate[dim] = (valTemplate[dim] & maskClean) | in;
				if (valTemplate[dim] > rangeMax[dim] || 
						valTemplate[dim] < (rangeMin[dim]&compMask)) {
					return false;
				}
			}
		} else {
			for (int dim = 0; dim < valTemplate.length; dim++) {
				valTemplate[dim] = valTemplate[dim] & maskClean;
			}
		}

		return true;
	}

	private static <T> NodeIterator<T> init(long[] rangeMin, long[] rangeMax, long[] valTemplate, 
			int DIM, Node<T> node, boolean isDepth0) {
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
		long maskHcBit = 1L << node.postLen;
		long lowerLimit = 0;
		long upperLimit = 0;
		//to prevent problems with signed long when using 64 bit
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
		return new NodeIterator<T>(node, DIM, valTemplate, lowerLimit, upperLimit, 
				rangeMin, rangeMax, isDepth0);
	}


	@Override
	public int getDIM() {
		return DIM;
	}

	@Override
	public int getDEPTH() {
		return DEPTH;
	}

	/**
	 * Performes a spherical range query with a maximum distance {@code maxDistance} from point
	 * {@code center}.
	 * @param center
	 * @param maxDistance
	 * @return Result iterator.
	 */
	public PhIterator<T> query(long[] center, double maxDistance) {
		if (center.length != DIM || maxDistance < 0) {
			throw new IllegalArgumentException("Invalid arguments: " + center.length +  
					" / " + maxDistance + "  DIM=" + DIM);
		}
		long[] min = new long[DIM];
		long[] max = new long[DIM];
		for (int i = 0; i < DIM; i++) {
			min[i] = (long) (center[i] - maxDistance);
			max[i] = (long) (center[i] + maxDistance);
		}
		return new PhIteratorReuse<T>(getRoot(), min, max, DIM, DEPTH);
	}

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of values to be returned. More values may be returned with several have
	 * 				the same distance.
	 * @param v
	 * @return List of neighbours.
	 */
	@Override
	public ArrayList<long[]> nearestNeighbour(int nMin, long... v) {
		throw new UnsupportedOperationException("Currently not supported in public release.");
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
	public T update(long[] oldKey, long[] newKey) {
		if (getRoot() == null) {
			return null;
		}
		final int[] insertRequired = new int[]{NO_INSERT_REQUIRED};
		T v = delete(oldKey, getRoot(), 0, null, UNKNOWN, newKey, insertRequired);
		if (insertRequired[0] != NO_INSERT_REQUIRED) {
			//this is only 'true' if the value existed AND if oldKey was not replaced with newKey,
			//because they wouldn't be in the same location.
			put(newKey, v);
		}
		return v;
	}

	@Override
	public List<long[]> nearestNeighbour(int nMin, PhDistance dist, PhDimFilter dims, long... key) {
		throw new UnsupportedOperationException();
	}

	/**
	 * Remove all entries from the tree.
	 */
	@Override
	public void clear() {
		root = null;
		nEntries = 0;
		nNodes = 0;
	}
}

