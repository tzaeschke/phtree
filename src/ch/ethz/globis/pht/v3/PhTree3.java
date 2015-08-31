/*
 * Copyright 2011-2014 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.v3;

import static ch.ethz.globis.pht.PhTreeHelper.DEBUG;
import static ch.ethz.globis.pht.PhTreeHelper.align8;
import static ch.ethz.globis.pht.PhTreeHelper.debugCheck;
import static ch.ethz.globis.pht.PhTreeHelper.getMaxConflictingBits;
import static ch.ethz.globis.pht.PhTreeHelper.getMaxConflictingBitsWithMask;
import static ch.ethz.globis.pht.PhTreeHelper.posInArray;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;

import org.zoodb.index.critbit.CritBit64;
import org.zoodb.index.critbit.CritBit64.CBIterator;
import org.zoodb.index.critbit.CritBit64.Entry;
import org.zoodb.index.critbit.CritBit64.QueryIteratorMask;

import ch.ethz.globis.pht.PhDimFilter;
import ch.ethz.globis.pht.PhDistance;
import ch.ethz.globis.pht.PhEntry;
import ch.ethz.globis.pht.PhPredicate;
import ch.ethz.globis.pht.PhTree;
import ch.ethz.globis.pht.PhTreeHelper.Stats;
import ch.ethz.globis.pht.util.BitsInt;
import ch.ethz.globis.pht.util.PhMapper;
import ch.ethz.globis.pht.util.PhTreeQStats;
import ch.ethz.globis.pht.util.Refs;
import ch.ethz.globis.pht.util.StringBuilderLn;

/**
 * n-dimensional index (quad-/oct-/n-tree).
 *
 * Version 3 of the PH-Tree. This includes values for each key.
 *
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 */
public class PhTree3<T> implements PhTree<T> {

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
	
	private static final class NodeEntry<T> {
		long[] key;
		T value;
		Node<T> node;
		NodeEntry(long[] key, T value) {
			this.key = key;
			this.value = value;
			this.node = null;
		}
		NodeEntry(Node<T> node) {
			this.key = null;
			this.value = null;
			this.node = node;
		}
		
		long[] getKey() {
			return key;
		}
		
		T getValue() {
			return value;
		}
		void setNode(Node<T> node) {
			this.key = null;
    		this.value = null;
    		this.node = node;
		}
		void setPost(long[] key, T val) {
			this.key = key;
    		this.value = val;
    		this.node = null;
		}
	}
	
    public static class Node<T> {
    	
    	static final int HC_BITS = 0;  //number of bits required for storing current (HC)-representation
        static final int PINN_HC_WIDTH = 1; //width of not-null flag for post-hc
        static final int PIK_WIDTH(int DIM) { return DIM; };//DIM; //post index key width 
        static final int SIK_WIDTH(int DIM) { return DIM; };//DIM; //sub index key width 
        
        private Node<T>[] subNRef;
        private T[] values;

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
        int[] ba = null;
        
        // |   1st   |   2nd    |   3rd   |    4th   |
        // | isSubHC | isPostHC | isSubNI | isPostNI |
        private byte isHC = 0;
        private byte postLen = 0;
        byte infixLen = 0; //prefix size
        private CritBit64<NodeEntry<T>> ind = null; 
        
        
        
        private Node() {
			// For ZooDB only
		}
        
        private Node(int infixLen, int postLen, int estimatedPostCount, final int DIM) {
        	this.infixLen = (byte) infixLen;
        	this.postLen = (byte) postLen;
        	if (estimatedPostCount >= 0) {
        		int size = calcArraySizeTotalBits(estimatedPostCount, 0, false, DIM);
        		this.ba = BitsInt.arrayCreate(size);
        	}
        }

        public static <T> Node<T> createNode(PhTree3<T> parent, int infixLen, int postLen, 
        		int estimatedPostCount, final int DIM) {
        	Node<T> n = new Node<T>(infixLen, postLen, estimatedPostCount, DIM);
            parent.nNodes++;
        	return n;
        }
        
        boolean hasInfixes() {
            return infixLen > 0;
        }

        private int calcArraySizeTotalBits(int bufPostCnt, int bufSubCnt, boolean bufIsPostHC, 
        		final int DIM) {
        	int nBits = getBitPos_PostIndex(bufSubCnt, DIM);
        	//post-fixes
        	if (bufIsPostHC) {
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
        
        private int calcArraySizeTotalBitsNI(int bufSubCnt, final int DIM) {
        	return getBitPos_PostIndex(bufSubCnt, DIM);
        }
       
        long getInfix(int dim, final int DIM) {
        	return BitsInt.readArray(this.ba, getBitPos_Infix(DIM)
        			+ dim*infixLen, infixLen) << (postLen+1);
        }
        
        
        void getInfix(long[] val) {
        	if (!hasInfixes()) {
        		return;
        	}
        	int maskLen = postLen + 1 + infixLen;
        	//To cut of trailing bits
            long mask = (-1L) << maskLen;
            final int DIM = val.length;
            for (int i = 0; i < val.length; i++) {
                //Replace val with infix (val may be !=0 from traversal)
                val[i] &= mask;
                val[i] |= getInfix(i, DIM);
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
            final int DIM = val.length;
            for (int i = 0; i < val.length; i++) {
                val[i] |= getInfix(i, DIM);
        	}
        }
        
        
        /**
         * 
         * @param infId
         * @param inf
         * @param DIM
         * @param infixOffs The number of bits AFTER the infix.
         */
        private void setInfix(int infId, long inf, final int DIM) {
        	BitsInt.writeArray(this.ba, getBitPos_Infix(DIM)
        			+ infId*infixLen, infixLen, inf >>> (postLen+1));
        }
        

        private boolean getInfixBit(int infId, final int DIM, final int infixInternalOffset) {
            int startBitTotal = infId*infixLen + infixInternalOffset;
            return BitsInt.getBit(ba, getBitPos_Infix(DIM) + startBitTotal);
        }

        /**
         * 
         * @param pos The position of the node when mapped to a vector.
         * @return The sub node or null.
         */
        NodeEntry<T> getChild(long pos, final int DIM) {
        	if (ind != null) {
        		return niGet(pos);
        	}
			if (subNRef == null) {
				return null;
			}
			if (isSubHC()) {
				return new NodeEntry<>(subNRef[(int) pos]);
			}
			int subOffsBits = getBitPos_SubNodeIndex(DIM);
			int p2 = BitsInt.binarySearch(ba, subOffsBits, getSubCount(DIM), pos, SIK_WIDTH(DIM), 0);
			if (p2 < 0) {
				return null;
			}
			return new NodeEntry<>(subNRef[p2]);
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
//        		if (DEBUG && e.key != null) {
//        			throw new IllegalStateException();
//        		}
    			return e.node; 
        	}
			if (subNRef == null) {
				return null;
			}
			if (isSubHC()) {
				return subNRef[(int) pos];
			}
			int subOffsBits = getBitPos_SubNodeIndex(DIM);
			int p2 = BitsInt.binarySearch(ba, subOffsBits, getSubCount(DIM), pos, SIK_WIDTH(DIM), 0);
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
	    	final int bufSubCount = getSubCount(DIM);
			final int bufPostCount = getPostCount(DIM);
			
			if (!isSubNI() && NI_THRESHOLD(bufSubCount, bufPostCount)) {
				niBuild(bufSubCount, bufPostCount, DIM);
			}
			if (isSubNI()) {
				niPut(pos, sub);
				setSubCount(bufSubCount+1, DIM);
				return;
			}

			if (subNRef == null) {
				subNRef = new Node[2];
			}
			
			//decide here whether to use hyper-cube or linear representation
			if (isSubHC()) {
				subNRef[(int) pos] = sub;
				setSubCount(bufSubCount+1, DIM);
				return;
			}

			int subOffsBits = getBitPos_SubNodeIndex(DIM);

			//switch to normal array (full hyper-cube) if applicable.
			if (DIM<=31 && (REF_BITS+SIK_WIDTH(DIM))*(subNRef.length+1L) >= REF_BITS*(1L<<DIM)) {
				//migrate to full array!
				Node<T>[] na = new Node[1<<DIM];
				for (int i = 0; i < bufSubCount; i++) {
					int posOld = (int) BitsInt.readArray(ba, subOffsBits + i*SIK_WIDTH(DIM), SIK_WIDTH(DIM));
					na[posOld] = subNRef[i];
				}
				subNRef = na;
				BitsInt.removeBits(ba, subOffsBits, bufSubCount*SIK_WIDTH(DIM));
				setSubHC(true);
				subNRef[(int) pos] = sub;
				//subCount++;
				setSubCount(bufSubCount+1, DIM);
				int reqSize = calcArraySizeTotalBits(bufPostCount, bufSubCount, isPostHC(), DIM);
				ba = BitsInt.arrayTrim(ba, reqSize);
				return;
			}
			
			int p2 = BitsInt.binarySearch(ba, subOffsBits, bufSubCount, pos, SIK_WIDTH(DIM), 0);
			if (DEBUG &&  p2 >= 0) {
				throw new IllegalStateException("pos=" + pos);
			}
				
			//subCount++;
			setSubCount(bufSubCount+1, DIM);
			
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
			ba = BitsInt.arrayEnsureSize(ba, calcArraySizeTotalBits(bufPostCount, bufSubCount+1, 
					false, DIM));
			BitsInt.insertBits(ba, subOffsBits + start*SIK_WIDTH(DIM), SIK_WIDTH(DIM));
			BitsInt.writeArray(ba, subOffsBits + start*SIK_WIDTH(DIM), SIK_WIDTH(DIM), pos);
		}

		/**
		 * Replace a sub-node, for example if the current sub-node is removed, it may have to be
		 * replaced with a sub-sub-node.
		 */
		private void replaceSub(long pos, Node<T> newSub, final int DIM) {
			if (isSubNI()) {
				niPut(pos, newSub);
				return;
			}
			if (isSubHC()) {
				subNRef[(int) pos] = newSub;
			} else {
				//linearized cube
				int subOffsBits = getBitPos_SubNodeIndex(DIM);
				int p2 = 
					BitsInt.binarySearch(ba, subOffsBits, getSubCount(DIM), pos, SIK_WIDTH(DIM), 0);
				if (DEBUG &&  p2 < 0) {
					throw new IllegalStateException("pos=" + pos);
				}
				subNRef[p2] = newSub;
			}
		}
		
		@SuppressWarnings("unchecked")
		private void removeSub(long pos, final int DIM) {
			final int bufSubCnt = getSubCount(DIM);
			if (isSubNI()) {
				final int bufPostCnt = getPostCount(DIM);
				if (!NI_THRESHOLD(bufSubCnt, bufPostCnt)) {
					niDeconstruct(bufSubCnt, bufPostCnt, DIM, pos, true);
					setSubCount(bufSubCnt-1, DIM);
					return;
				}
			}
			if (isSubNI()) {
				niRemove(pos);
				setSubCount(bufSubCnt-1, DIM);
				return;
			}
			final int bufPostCnt = getPostCount(DIM);
			
			//switch representation (HC <-> Linear)?
			//+1 bit for null/not-null flag
			long sizeHC = REF_BITS*(1L<<DIM); 
			//+DIM assuming compressed IDs
			long sizeLin = (REF_BITS+SIK_WIDTH(DIM))*(subNRef.length-1L);
			if (isSubHC() && (sizeLin < sizeHC)) {
				//revert to linearized representation, if applicable
				int prePostBits_SubHC = getBitPos_PostIndex(bufSubCnt, DIM);
				setSubHC( false );
				int prePostBits_SubLHC = getBitPos_PostIndex(bufSubCnt-1, DIM);
				int bia2Size = calcArraySizeTotalBits(bufPostCnt, bufSubCnt-1, isPostHC(), DIM);
				int[] bia2 = BitsInt.arrayCreate(bia2Size);
				Node<T>[] sa2 = new Node[bufSubCnt-1];
				int preSubBits = getBitPos_SubNodeIndex(DIM);
				//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
				BitsInt.copyBitsLeft(ba, 0, bia2, 0, preSubBits);
				int n=0;
				for (int i = 0; i < (1L<<DIM); i++) {
					if (i==pos) {
						//skip the item that should be deleted.
						continue;
					}
					if (subNRef[i] != null) {
						sa2[n]= subNRef[i];
						BitsInt.writeArray(bia2, preSubBits + n*SIK_WIDTH(DIM), SIK_WIDTH(DIM), i);
						n++;
					}
				}
				//length: we copy as many bits as fit into bia2, which is easiest to calculate
				BitsInt.copyBitsLeft(
						ba, prePostBits_SubHC, 
						bia2, prePostBits_SubLHC,
						bia2Size-prePostBits_SubLHC);  
				ba = bia2;
				subNRef = sa2;
				//subBcnt--;
				setSubCount(bufSubCnt-1, DIM);
				return;
			}			
			
			
			if (isSubHC()) {
				//hyper-cube
				setSubCount(bufSubCnt-1, DIM);
				subNRef[(int) pos] = null;
				//Nothing else to do.
			} else {
				//linearized cube
				int subOffsBits = getBitPos_SubNodeIndex(DIM);
				int p2 = BitsInt.binarySearch(ba, subOffsBits, bufSubCnt, pos, SIK_WIDTH(DIM), 0);
				if (DEBUG &&  p2 < 0) {
					throw new IllegalStateException("pos=" + pos + "  p2=" + p2);
				}
					
				//subCount--;
				setSubCount(bufSubCnt-1, DIM);

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
				if (DEBUG && offsKey < 0) {
					throw new IllegalStateException("Element does not exist.");
				}
				BitsInt.removeBits(ba, offsKey, DIM);

				//shrink array
				ba = BitsInt.arrayTrim(ba, calcArraySizeTotalBits(bufPostCnt, bufSubCnt-1, isPostHC(), DIM));
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
			
            int[] ia = ba;
			int offs = offsPostKey;
			//Can not be null at this point...
			//Also, length can be expected to be equal
			long mask = ~((-1L) << postLen);
			for (int i = 0; i < key.length; i++) {
				long l = BitsInt.readArray(ia, offs + i*postLen, postLen);
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
			final int bufSubCnt = getSubCount(DIM);
			final int bufPostCnt = getPostCount(DIM);
			if (isPostNI()) {
				addPostPOB(pos, -1, key, value, bufSubCnt, bufPostCnt);
				return;
			}
			
			int offsKey = getPostOffsetBits(pos, bufPostCnt, bufSubCnt, isPostHC(), DIM);
			if (DEBUG && offsKey >= 0) {
				throw new IllegalStateException("Element already exists: " + offsKey);
			}
			addPostPOB(pos, offsKey, key, value, bufSubCnt, bufPostCnt);
		}
		
		/**
		 * 
		 * @param pos
		 * @param offsPostKey POB: Post offset bits from getPostOffsetBits(...)
		 * @param key
		 */
		void addPostPOB(long pos, int offsPostKey, long[] key, T value, 
				int bufSubCnt, int bufPostCnt) {
			final int DIM = key.length;
			if (bufSubCnt == UNKNOWN) bufSubCnt = getSubCount(DIM); 
			if (bufPostCnt == UNKNOWN) bufPostCnt = getPostCount(DIM);
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
				setPostCount(bufPostCnt+1, DIM);
				return;
			}
			
			if (values == null) {
				values = Refs.arrayCreate(1);
			}
			
			//switch representation (HC <-> Linear)?
			//+1 bit for null/not-null flag
			long sizeHC = (DIM * postLen + 1L) * (1L << DIM); 
			//+DIM because every index entry needs DIM bits
			long sizeLin = (DIM * postLen + DIM) * (bufPostCnt+1L);
			if (!isPostHC() && (DIM<=31) && (sizeLin >= sizeHC)) {
				int prePostBits = getBitPos_PostIndex(bufSubCnt, DIM);
				setPostHC( true );
				int[] bia2 = BitsInt.arrayCreate(calcArraySizeTotalBits(bufPostCnt+1, bufSubCnt, true, DIM));
				T [] v2 = Refs.arrayCreate(1<<DIM);
				//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
				BitsInt.copyBitsLeft(ba, 0, bia2, 0, prePostBits);
				int postLenTotal = DIM*postLen; 
				for (int i = 0; i < bufPostCnt; i++) {
					int entryPosLHC = prePostBits + i*(PIK_WIDTH(DIM)+postLenTotal);
					int p2 = (int)BitsInt.readArray(ba, entryPosLHC, PIK_WIDTH(DIM));
					BitsInt.setBit(bia2, prePostBits+PINN_HC_WIDTH*p2, true);
					BitsInt.copyBitsLeft(ba, entryPosLHC+PIK_WIDTH(DIM),
							bia2, prePostBits + (1<<DIM)*PINN_HC_WIDTH + postLenTotal*p2, 
							postLenTotal);
					v2[p2] = values[i];
				}
				ba = bia2;
				values = v2;
				offsPostKey = getPostOffsetBits(pos, bufPostCnt, bufSubCnt, true, DIM);
			}
			
			
			//get position
			offsPostKey = -(offsPostKey+1);

			//subBcnt++;
			setPostCount(bufPostCnt+1, DIM);
			
			if (isPostHC()) {
				//hyper-cube
				for (int i = 0; i < key.length; i++) {
					BitsInt.writeArray(ba, offsPostKey + postLen * i, postLen, key[i]);
				}
				int offsNN = getBitPos_PostIndex(bufSubCnt, DIM);
				BitsInt.setBit(ba, (int) (offsNN+PINN_HC_WIDTH*pos), true);
				values[(int) pos] = value;
			} else {
				int[] ia;
				int offs;
				if (!isPostNI()) {
					//resize array
					ba = BitsInt.arrayEnsureSize(ba, calcArraySizeTotalBits(bufPostCnt+1, bufSubCnt, false, DIM));
					ia = ba;
					offs = offsPostKey;
					BitsInt.insertBits(ia, offs-PIK_WIDTH(DIM), PIK_WIDTH(DIM) + DIM*postLen);
					//insert key
					BitsInt.writeArray(ia, offs-PIK_WIDTH(DIM), PIK_WIDTH(DIM), pos);
					//insert value:
					for (int i = 0; i < DIM; i++) {
						BitsInt.writeArray(ia, offs + postLen * i, postLen, key[i]);
					}
					values = Refs.arrayEnsureSize(values, bufPostCnt+1);
					Refs.insertAtPos(values, offs2ValPos(offs, pos, DIM, bufSubCnt), value);
				} else {
					throw new IllegalStateException();
				}

			}
		}
		
		long[] postToNI(int startBit, int postLen, int DIM) {
			long[] key = new long[DIM];
			for (int d = 0; d < key.length; d++) {
				key[d] |= BitsInt.readArray(ba, startBit, postLen);
				startBit += postLen;
			}
			return key;
		}
		
		void postFromNI(int[] ia, int startBit, long key[], int postLen) {
			//insert value:
			for (int d = 0; d < key.length; d++) {
				BitsInt.writeArray(ia, startBit + postLen * d, postLen, key[d]);
			}
		}
		
		void niBuild(int bufSubCnt, int bufPostCnt, int DIM) {
			//Migrate node to node-index representation
			if (ind != null || isPostNI() || isSubNI()) {
				throw new IllegalStateException();
			}
			ind = CritBit64.create();
			
			//read posts 
			if (isPostHC()) {
				int prePostBitsKey = getBitPos_PostIndex(bufSubCnt, DIM);
				int prePostBitsVal = prePostBitsKey + (1<<DIM)*PINN_HC_WIDTH;
				int postLenTotal = DIM*postLen;
				for (int i = 0; i < (1L<<DIM); i++) {
					if (BitsInt.getBit(ba, prePostBitsKey + PINN_HC_WIDTH*i)) {
						int postPosLHC = prePostBitsVal + i*postLenTotal;
						//BitsInt.writeArray(bia2, entryPosLHC, PIK_WIDTH(DIM), i);
						//BitsInt.copyBitsLeft(
						//		ba, prePostBits + (1<<DIM)*PINN_HC_WIDTH + postLenTotal*i, 
						//		bia2, entryPosLHC+PIK_WIDTH(DIM),
						//		postLenTotal);
						long[] key = postToNI(postPosLHC, postLen, DIM);
						postPosLHC += DIM*postLen;
						System.out.println("n-b-1: " + BitsInt.toBinary(key, 8));
						niPutNoCopy(i, key, values[i]);
					}
				}
			} else {
				int prePostBits = getBitPos_PostIndex(bufSubCnt, DIM);
				int postPosLHC = prePostBits;
				for (int i = 0; i < bufPostCnt; i++) {
					//int entryPosLHC = prePostBits + i*(PIK_WIDTH(DIM)+postLenTotal);
					long p2 = BitsInt.readArray(ba, postPosLHC, PIK_WIDTH(DIM));
					postPosLHC += PIK_WIDTH(DIM);
					//This reads compressed keys...
//					BitsInt.setBit(bia2, prePostBits+PINN_HC_WIDTH*p2, true);
//					BitsInt.copyBitsLeft(ba, entryPosLHC+PIK_WIDTH(DIM),
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
					long posOld = BitsInt.readArray(ba, subOffsBits, SIK_WIDTH(DIM));
					subOffsBits += SIK_WIDTH(DIM);
					niPut(posOld, subNRef[i]);
				}
			}
			
			setPostHC(false);
			setSubHC(false);
			setPostNI(true);
			setSubNI(true);
			ba = BitsInt.arrayTrim(ba, calcArraySizeTotalBitsNI(bufSubCnt, DIM));
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
		T niDeconstruct(int bufSubCnt, int bufPostCnt, int DIM, long posToRemove, boolean removeSub) {
			//Migrate node to node-index representation
			if (ind == null || !isPostNI() || !isSubNI()) {
				throw new IllegalStateException();
			}
			
			setPostNI(false);
			setSubNI(false);
			final int newSubCnt = removeSub ? bufSubCnt-1 : bufSubCnt;
			final int newPostCnt = removeSub ? bufPostCnt : bufPostCnt-1;
			
			//calc post mode.
			//+1 bit for null/not-null flag
			long sizePostHC = (DIM * postLen + 1L) * (1L << DIM); 
			//+DIM because every index entry needs DIM bits
			long sizePostLin = (DIM * postLen + DIM) * newPostCnt;
			boolean isPostHC = (DIM<=31) && (sizePostLin >= sizePostHC);

			
			
			//sub-nodes:
			//switch to normal array (full hyper-cube) if applicable.
			if (DIM<=31 && (REF_BITS+SIK_WIDTH(DIM))*newSubCnt >= REF_BITS*(1L<<DIM)) {
				//migrate to full HC array
				Node<T>[] na = new Node[1<<DIM];
				CBIterator<NodeEntry<T>> it = ind.iterator();
				int i = 0;
				while (it.hasNext()) {
					Entry<NodeEntry<T>> e = it.nextEntry();
					if (e.value().node != null && e.key() != posToRemove) {
						na[(int) e.key()] = e.value().node;
						i++;
					}
				}
				subNRef = na;
				setSubHC(true);
				if (DEBUG && i != newSubCnt) {
					throw new IllegalStateException();
				}
			} else {
				//migrate to LHC
				setSubHC( false );
				int bia2Size = calcArraySizeTotalBits(newPostCnt, newSubCnt, isPostHC, DIM);
				int[] bia2 = BitsInt.arrayCreate(bia2Size);
				Node<T>[] sa2 = new Node[newSubCnt];
				int preSubBits = getBitPos_SubNodeIndex(DIM);
				//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
				BitsInt.copyBitsLeft(ba, 0, bia2, 0, preSubBits);
				int n=0;
				CBIterator<NodeEntry<T>> it = ind.iterator();
				while (it.hasNext()) {
					Entry<NodeEntry<T>> e = it.nextEntry();
					if (e.value().node != null) {
						long pos = e.key();
						if (pos == posToRemove) {
							//skip the item that should be deleted.
							if (DEBUG && !removeSub) {
								throw new IllegalStateException();
							}
							continue;
						}
						sa2[n] = e.value().node;
						BitsInt.writeArray(bia2, preSubBits + n*SIK_WIDTH(DIM), SIK_WIDTH(DIM), pos);
						n++;
					}
				}
				ba = bia2;
				subNRef = sa2;
			}

			//post-data:
			T oldValue = null;
			setPostHC(isPostHC);
			int prePostBits = getBitPos_PostIndex(newSubCnt, DIM);
			int[] bia2 = BitsInt.arrayCreate(calcArraySizeTotalBits(newPostCnt, newSubCnt, isPostHC, DIM));
			//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
			BitsInt.copyBitsLeft(ba, 0, bia2, 0, prePostBits);
			int postLenTotal = DIM*postLen;
			if (isPostHC) {
				//HC mode
				T [] v2 = Refs.arrayCreate(1<<DIM);
				int startBitBase = prePostBits + (1<<DIM)*PINN_HC_WIDTH;
				CBIterator<NodeEntry<T>> it = ind.iterator();
				while (it.hasNext()) {
					Entry<NodeEntry<T>> e = it.nextEntry();
					if (e.value().key != null) {
						if (e.key() == posToRemove) {
							oldValue = e.value().value;
							if (DEBUG && removeSub) {
								throw new IllegalStateException();
							}
							continue;
						}
						int p2 = (int) e.key();
						BitsInt.setBit(bia2, prePostBits+PINN_HC_WIDTH*p2, true);
						int startBit = startBitBase + postLenTotal*p2;
						postFromNI(bia2, startBit, e.value().key, postLen);
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
					if (e.value().key != null) {
						if (pos == posToRemove) {
							if (DEBUG && removeSub) {
								throw new IllegalStateException();
							}
							//skip the item that should be deleted.
							oldValue = e.value().value;
							continue;
						}
						v2[n] = e.value().value;
						BitsInt.writeArray(bia2, entryPosLHC, PIK_WIDTH(DIM), pos);
						entryPosLHC += PIK_WIDTH(DIM);
						postFromNI(bia2, entryPosLHC, e.value().key, postLen);
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
				for (int i = 0; i < key.length; i++) {
					key[i] &= mask;
					key[i] |= e.key[i];
				}
				//System.arraycopy(e.getKey(), 0, key, 0, key.length);
				return e.getValue();
			}

			int[] ia = ba;
			int offs;
			offs = offsPostKey;
			int valPos = offs2ValPos(offs, pos, key.length);
			final long mask = (~0L)<<postLen;
			for (int i = 0; i < key.length; i++) {
				key[i] &= mask;
				key[i] |= BitsInt.readArray(ia, offs, postLen);
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
				long[] rangeMin, long[] rangeMax, boolean isPostBLHC) {
			if (isPostBLHC) {
				//must be BLHC
				NodeEntry<T> e = niGet(hcPos);
				long[] post = e.getKey();
				final long mask = (~0L)<<postLen;
				for (int i = 0; i < key.length; i++) {
					key[i] &= mask;
					key[i] |= post[i];
					if (key[i] < rangeMin[i] || key[i] > rangeMax[i]) {
						return null;
					}
				}
				return e;
			}

			int[] ia = ba;
			int offs = offsPostKey;
			final long mask = (~0L)<<postLen;
			for (int i = 0; i < key.length; i++) {
				key[i] &= mask;
				key[i] |= BitsInt.readArray(ia, offs, postLen);
				if (key[i] < rangeMin[i] || key[i] > rangeMax[i]) {
					return null;
				}
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
			
			return niGet(pos).value; 
		}

		
		T updatePostValuePOB(int offs, long pos, long[] key, int DIM, T value) {
			if (!isPostNI()) {
				int valPos = offs2ValPos(offs, pos, DIM);
				T old = values[valPos];
				values[valPos] = value;
				return old;
			} 
			
			return niPutNoCopy(pos, key, value).value;
		}
		

		T getPostValue(long pos, int DIM) {
			if (isPostHC()) {
				return getPostValuePOB(UNKNOWN, pos, UNKNOWN); 
			}
			final int bufSubCnt = getSubCount(DIM);
			final int bufPostCnt = getPostCount(DIM);
			final boolean bufIsPostHC = isPostHC();
			int offs = getPostOffsetBits(pos, bufPostCnt, bufSubCnt, bufIsPostHC, DIM);
			if (DEBUG && offs < 0) {
				throw new IllegalStateException("Element does not exist.");
			}
			
			return getPostValuePOB(offs, pos, DIM);
		}
		

		T getPost(long pos, long[] key) {
			if (isPostNI()) {
				return getPostPOB(-1, pos, key);
			}
            final int DIM = key.length;
			final int bufSubCnt = getSubCount(DIM);
			final int bufPostCnt = getPostCount(DIM);
			final boolean bufIsPostHC = isPostHC();
			int offs = getPostOffsetBits(pos, bufPostCnt, bufSubCnt, bufIsPostHC, DIM);
			if (DEBUG && offs < 0) {
				throw new IllegalStateException("Element does not exist.");
			}
			
			return getPostPOB(offs, pos, key);
		}

		
		T removePostPOB(long pos, int offsPostKey, final int DIM) {
			final int bufPostCnt = getPostCount(DIM);
			final int bufSubCnt = getSubCount(DIM);

			if (isPostNI()) {
				if (!NI_THRESHOLD(bufSubCnt, bufPostCnt)) {
					T v = niDeconstruct(bufSubCnt, bufPostCnt, DIM, pos, false);
					setPostCount(bufPostCnt-1, DIM);
					return v;
				}
			}
			if (isPostNI()) {
				setPostCount(bufPostCnt-1, DIM);
				return niRemove(pos).value;
			}

			T oldVal = null;
			
			//switch representation (HC <-> Linear)?
			//+1 bit for null/not-null flag
			long sizeHC = (DIM * postLen + 1L) * (1L << DIM); 
			//+DIM assuming compressed IDs
			long sizeLin = (DIM * postLen + DIM) * (bufPostCnt-1L);
			if (isPostHC() && (sizeLin < sizeHC)) {
				//revert to linearized representation, if applicable
				setPostHC( false );
				int[] bia2 = BitsInt.arrayCreate(calcArraySizeTotalBits(bufPostCnt-1, bufSubCnt, false, DIM));
				T[] v2 = Refs.arrayCreate(bufPostCnt);
				int prePostBits = getBitPos_PostIndex(bufSubCnt, DIM);
				int prePostBitsVal = prePostBits + (1<<DIM)*PINN_HC_WIDTH;
				//Copy only bits that are relevant. Otherwise we might mess up the not-null table!
				BitsInt.copyBitsLeft(ba, 0, bia2, 0, prePostBits);
				int postLenTotal = DIM*postLen;
				int n=0;
				for (int i = 0; i < (1L<<DIM); i++) {
					if (i==pos) {
						//skip the item that should be deleted.
						oldVal = values[i];
						continue;
					}
					if (BitsInt.getBit(ba, prePostBits + PINN_HC_WIDTH*i)) {
						int entryPosLHC = prePostBits + n*(PIK_WIDTH(DIM)+postLenTotal);
						BitsInt.writeArray(bia2, entryPosLHC, PIK_WIDTH(DIM), i);
						BitsInt.copyBitsLeft(
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
				setPostCount(bufPostCnt-1, DIM);
				if (bufPostCnt-1 == 0) {
					values = null;
				}
				return oldVal;
			}			
			
			//subBcnt--;
			setPostCount(bufPostCnt-1, DIM);
			
			if (isPostHC()) {
				//hyper-cube
				int offsNN = getBitPos_PostIndex(bufSubCnt, DIM);
				BitsInt.setBit(ba, (int) (offsNN+PINN_HC_WIDTH*pos), false);
				oldVal = values[(int) pos]; 
				values[(int) pos] = null;
				//Nothing else to do, values can just stay where they are
			} else {
				if (!isPostNI()) {
					//linearized cube:
					//remove key and value
					BitsInt.removeBits(ba, offsPostKey-PIK_WIDTH(DIM), PIK_WIDTH(DIM) + DIM*postLen);
					//shrink array
					ba = BitsInt.arrayTrim(ba, calcArraySizeTotalBits(bufPostCnt-1, bufSubCnt, false, DIM));
					//values:
					int valPos = offs2ValPos(offsPostKey, pos, DIM, bufSubCnt);
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
			//return BitsInt.getBit(ba, 0);
		}
		
		
		/**
		 * Set whether the post-fixes are stored as hyper-cube.
		 */
		void setPostHC(boolean b) {
			isHC = (byte) (b ? (isHC | 0b10) : (isHC & (~0b10)));
			//BitsInt.setBit(ba, 0, b);
		}
		
		
		/**
		 * @return True if the sub-nodes are stored as hyper-cube
		 */
		boolean isSubHC() {
			return (isHC & 0b01) != 0;
			//return BitsInt.getBit(ba, 1);
		}
		
		
		/**
		 * Set whether the sub-nodes are stored as hyper-cube.
		 */
		void setSubHC(boolean b) {
			isHC = (byte) (b ? (isHC | 0b01) : (isHC & (~0b01)));
			//BitsInt.setBit(ba, 1, b);
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
		int getPostCount(final int DIM) {
	        //DIM+1; //Counters require DIM + 1 bits, e.g. [0..8] for DIM=3
			return (int) BitsInt.readArray(ba, HC_BITS, DIM+1);
		}
		
		
		/**
		 * Set post-fix counter.
		 */
		private void setPostCount(long cnt, final int DIM) {
	        //DIM+1; //Counters require DIM + 1 bits, e.g. [0..8] for DIM=3
			BitsInt.writeArray(ba, HC_BITS, DIM+1, cnt);
		}
		
		
		/**
		 * @return Sub-node counter
		 */
		int getSubCount(final int DIM) {
			//DIM+3: two bits for HC/LHC + (DIM+1) for postCount
	        //DIM+1; //Counters require DIM + 1 bits, e.g. [0..8] for DIM=3
			return (int) BitsInt.readArray(ba, HC_BITS+1+DIM, DIM+1);
		}
		
		
		/**
		 * Set sub-node counter.
		 */
		private void setSubCount(long cnt, final int DIM) {
			BitsInt.writeArray(ba, HC_BITS+1+DIM, DIM+1, cnt);
		}
		
		
		/**
		 * Posts start after sub-index.
		 * Sub-index is empty in case of sub-hypercube.
		 * @return Position of first bit of post index or not-null table.
		 */
		private int getBitPos_PostIndex(int bufSubCnt, final int DIM) {
			int offsOfSubs = 0;
			//subHC and subNI require no space
			if (isSubLHC()) {
				//linearized cube
				offsOfSubs = bufSubCnt * SIK_WIDTH(DIM); 
			}
			return getBitPos_SubNodeIndex(DIM) + offsOfSubs; 
		}
		
		private int getBitPos_SubNodeIndex(final int DIM) {
			return getBitPos_Infix(DIM) + (infixLen*DIM);
		}
		
		private int getBitPos_Infix(final int DIM) {
			// isPostHC / isSubHC / postCount / subCount
			return HC_BITS   +   DIM+1   +   DIM+1;
		}
		
	
		/**
		 * 
		 * @param pos
		 * @return Offset (in bits) in according array. In case the entry does not
		 * exist, a negative number is returned that represents the insertion position.
		 * 
		 */
		int getPostOffsetBits(long pos, final int DIM) {
			boolean isPostHC = isPostHC();
			int bufPostCnt = getPostCount(DIM);
			int bufSubCnt = getSubCount(DIM);
			return getPostOffsetBits(pos, bufPostCnt, bufSubCnt, isPostHC, DIM);
		}

		/**
		 * 
		 * @param offs
		 * @param pos
		 * @param DIM
		 * @param bufSubCnt use -1 to have it calculated by this method
		 * @return
		 */
		private int offs2ValPos(int offs, long pos, int DIM, int bufSubCnt) {
			if (isPostHC()) {
				return (int) pos;
			} else {
				if (bufSubCnt == UNKNOWN) {
					bufSubCnt = getSubCount(DIM);
				}
				int offsInd = getBitPos_PostIndex(bufSubCnt, DIM);
				//get p2 of:
				//return p2 * (PIK_WIDTH(DIM) + postLen * DIM) + offsInd + PIK_WIDTH(DIM);
				int valPos = (offs - PIK_WIDTH(DIM) - offsInd) / (postLen*DIM+PIK_WIDTH(DIM)); 
				if (DEBUG && (valPos*(postLen*DIM+PIK_WIDTH(DIM)) + PIK_WIDTH(DIM) + offsInd != offs)) {
					System.out.println("offs=" + offs + "  vp=" + valPos + " DIM=" + DIM);
					System.out.println("bsc=" + bufSubCnt + " PW=" + PIK_WIDTH(DIM) + "  offsInd=" + offsInd);
					System.out.println("pl=" + postLen + "  offsInd=" + offsInd);
					throw new IllegalStateException();
				}
				return valPos;
			}
		}
		
		
		private int offs2ValPos(int offs, long pos, int DIM) {
			if (isPostHC()) {
				return (int) pos;
			}
			return offs2ValPos(offs, pos, DIM, getSubCount(DIM));
		}
		
		
		/**
		 * 
		 * @param pos
		 * @param bufPostCnt
		 * @param bufSubCnt
		 * @param bufIsPostHC
		 * @param DIM
		 * @return 		The position (in bits) of the postfix VALUE. For LHC, the key is stored 
		 * 				directly before the value.
		 */
		private int getPostOffsetBits(long pos, int bufPostCnt, int bufSubCnt, boolean bufIsPostHC,
				final int DIM) {
			int offsInd = getBitPos_PostIndex(bufSubCnt, DIM);
			if (bufIsPostHC) {
				//hyper-cube
				int posInt = (int) pos;  //Hypercube can not be larger than 2^31
				boolean notNull = BitsInt.getBit(ba, offsInd+PINN_HC_WIDTH*posInt);
				offsInd += PINN_HC_WIDTH*(1<<DIM);
				if (!notNull) {
					return -(posInt * postLen * DIM + offsInd)-1;
				}
				return posInt * postLen * DIM + offsInd;
			} else {
				if (!isPostNI()) {
					//linearized cube
					int p2 = BitsInt.binarySearch(ba, offsInd, bufPostCnt, pos, PIK_WIDTH(DIM), 
							DIM * postLen);
					if (p2 < 0) {
						p2 = -(p2+1);
						p2 *= (PIK_WIDTH(DIM) + postLen * DIM);
						p2 += PIK_WIDTH(DIM);
						return -(p2 + offsInd) -1;
					}
					return p2 * (PIK_WIDTH(DIM) + postLen * DIM) + offsInd + PIK_WIDTH(DIM);
				} else {
					if (DEBUG && pos > Integer.MAX_VALUE) {
						throw new UnsupportedOperationException();
					}
					NodeEntry<T> e = niGet(pos);
					if (e != null && e.key != null) {
						return (int)pos;
					}
					return (int) (-pos -1);
				}
			}
		}
		
		boolean hasPostFix(long pos, final int DIM) {
			if (!isPostNI()) {
				return getPostOffsetBits(pos, getPostCount(DIM), getSubCount(DIM), 
						isPostHC(), DIM) >= 0;
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
    		nMaxPost = node.getPostCount(DIM);
    		nMaxSub = node.getSubCount(DIM);
    		isPostFinished = (nMaxPost <= 0);
    		isSubFinished = (nMaxSub <= 0);
    		this.maskLower = lower;
    		this.maskUpper = upper;
    		//Position of the current entry
    		currentOffsetSub = node.getBitPos_SubNodeIndex(DIM);
    		currentOffsetSub -= (isSubHC) ? 0 : Node.SIK_WIDTH(DIM);
    		if (isPostNI) {
    			postEntryLen = -1; //not used
    			niIterator = node.ind.queryWithMask(maskLower, maskUpper);
    		} else {
        		currentOffsetPostKey = node.getBitPos_PostIndex(nMaxSub, DIM);
        		// -set key offset to position before first element
        		// -set value offset to first element
        		if (isPostHC) {
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
    		NodeEntry<T> e = node.getPostPOB(offsPostKey, pos, key, rangeMin, rangeMax, isPostNI);
    		if (e == null) {
    			return false;
    		}
    		nextPostKey = key;
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
			for (int i = 0; i < buf.length; i++) {
				buf[i] &= mask;  
				buf[i] |= e.key[i];
				if (buf[i] < rangeMin[i] || buf[i] > rangeMax[i]) {
					return false;
				}
			}
			nextPostKey = buf;
			nextPostVal = e.value;
			nextSubNode = null;
			return true;
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
    			if (isPostHC) {
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
    					currentOffsetPostKey += Node.PINN_HC_WIDTH;  //pos with bit-offset
    					if (BitsInt.getBit(node.ba, currentOffsetPostKey)) {
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
    			} else {
    				if (!isPostNI) {
    					nextPost = -1;
    					while (!isPostFinished) {
							currentOffsetPostKey += postEntryLen;
							if (++nPostsFound > nMaxPost) {
    							isPostFinished = true;
    							break;
    						}
	    					long currentPos = BitsInt.readArray(node.ba, currentOffsetPostKey, Node.PIK_WIDTH(DIM));
	    	    			//check HC-pos
		    				if (!checkHcPos(currentPos)) {
	    	    				if (currentPos > maskUpper) {
	    	    					isPostFinished = true;
	    	    					break;
	    	    				}
	    	    				continue;
	    	    			}
	    	    			//check post-fix
	    	    			if (!readValue(currentPos, currentOffsetPostKey+Node.PIK_WIDTH(DIM))) {
	    	    				continue;
	    	    			}
	    					nextPost = currentPos;
	    	    			break;
    					}
    				} else {
    					throw new UnsupportedOperationException();
    				}
    			}
    		}
    		if (!isSubFinished && nextSub == next) {
    			if (isSubHC) {
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
    			} else {
    				nextSub = -1;
    				while (!isSubFinished) {
    					if (posSubLHC + 1  >= nMaxSub) {
    						isSubFinished = true;
    						break;
    					}
	    				currentOffsetSub += Node.SIK_WIDTH(DIM);
	    				long currentPos = BitsInt.readArray(node.ba, currentOffsetSub, Node.SIK_WIDTH(DIM));
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

		
    	private boolean checkHcPos(long pos) {
			if ((pos & maskUpper) != pos) {
				return false;
			}
			if ((pos | maskLower) != pos) {
				return false;
			}
			return true;
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
	    	if (!checkAndApplyRange(node, valTemplate, rangeMin, rangeMax, DIM)) {
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
    		nMaxPost = node.getPostCount(DIM);
    		nMaxSub = node.getSubCount(DIM);
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
        		currentOffsetPostKey = node.getBitPos_PostIndex(nMaxSub, DIM);
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
				for (int i = 0; i < buf.length; i++) {
					buf[i] &= mask;  
					buf[i] |= e.key[i];
				}
				nextPostKey = buf;
			}
			nextPostVal = e.value;
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
    					if (BitsInt.getBit(node.ba, currentOffsetPostKey)) {
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
    					long currentPos = BitsInt.readArray(node.ba, currentOffsetPostKey, Node.PIK_WIDTH(DIM));
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
	    				long currentPos = BitsInt.readArray(node.ba, currentOffsetSub, Node.SIK_WIDTH(DIM));
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
    
    public PhTree3(int dim, int depth) {
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
        
        currentDepth += node.getInfixLen();
        stats.q_totalDepth += currentDepth;
        
        if (node.subNRef != null) {
        	for (Node<T> sub: node.subNRef) {
        		if (sub != null) {
        			getQuality(currentDepth + 1, sub, stats);
        		}
        	}
        }
        
        //count post-fixes
       	stats.q_nPostFixN[currentDepth] += node.getPostCount(DIM);

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
        	if (node.getSubCount(DIM) == 0) {
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
        	        stats.size += 16 + e.key.length*8;
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
       		nChildren += node.getPostCount(DIM);
            //count post-fixes
            stats.size += 16 + align8(BitsInt.arraySizeInByte(node.ba));
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
        sizeBA = node.calcArraySizeTotalBits(node.getPostCount(DIM), node.getSubCount(DIM), 
        		node.isPostHC(), DIM);
        sizeBA = BitsInt.calcArraySize(sizeBA);
        sizeBA = BitsInt.arraySizeInByte(sizeBA);
        stats.size += align8(sizeBA);
        
        currentDepth += node.getInfixLen();
        int nChildren = 0;

        if (node.isPostNI()) {
        	nChildren = node.ind.size();
        	stats.size += (nChildren-1) * 48 + 40;
        	if (node.getSubCount(DIM) == 0) {
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
        	        stats.size += 16 + e.key.length*8;
        		}
        	}
        } else {
	        if (node.isSubHC()) {
	        	stats.nHCS++;
	        }
	        if (node.subNRef != null) {
	        	//+ REF for the byte[]
	        	stats.size += align8(node.getSubCount(DIM) * REF + REF);
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
	        nChildren += node.getPostCount(DIM);
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
        if (DIM<=31 && node.getPostCount(DIM) + node.getSubCount(DIM) > (1L<<DIM)) {
        	System.err.println("WARNING: Over-populated node found: pc=" + node.getPostCount(DIM) + 
        			"  sc=" + node.getSubCount(DIM));
        }
        //check space
        int baS = node.calcArraySizeTotalBits(node.getPostCount(DIM), node.getSubCount(DIM), 
        		node.isPostHC(), DIM);
        baS = BitsInt.calcArraySize(baS);
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
                if (((key[i] ^ node.getInfix(i, DIM)) & mask) != 0) {
                    //infix does not match
                    return false;
                }
            }
            currentDepth += node.getInfixLen();
        }

        long pos = posInArray(key, currentDepth, DEPTH);
    	
        //NI-node?
        if (node.isPostNI()) {
        	NodeEntry<T> e = node.getChild(pos, DIM);
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
                if (((key[i] ^ node.getInfix(i, DIM)) & mask) != 0) {
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
        return delete(key, getRoot(), 0, null, UNKNOWN);
    }

    /**
     * Merging occurs if a node is not the root node and has after deletion less than two children.
     * @param key new value to be deleted
     */
    private T delete(long[] key, Node<T> node, int currentDepth, Node<T> parent, long posInParent) {
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
                if (((key[i] ^ node.getInfix(i, DIM)) & mask) != 0) {
                    //infix does not match
                    return null;
                }
            }
            currentDepth += node.getInfixLen();
        }

        //NI-node?
        if (node.isPostNI()) {
        	return deleteNI(key, node, currentDepth, parent, posInParent);
        }
 
        //check for sub
        final long pos = posInArray(key, currentDepth, DEPTH);
    	Node<T> sub1 = node.getSubNode(pos, DIM);
        if (sub1 != null) {
        	return delete(key, sub1, currentDepth+1, node, pos);
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
 
    	//okay we have something to delete 
    	nEntries--;
    	
        //check if merging is necessary (check children count || isRootNode)
    	int nP = node.getPostCount(DIM);
    	int nS = node.getSubCount(DIM);
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
		int infOffs = node.getBitPos_Infix(DIM);
		int newInfixLen = node.getInfixLen() + 1 + sub2.getInfixLen();
       	sub2.infixLen = (byte)newInfixLen;
		sub2.ba = BitsInt.arrayEnsureSize(sub2.ba, sub2.calcArraySizeTotalBits(
				sub2.getPostCount(DIM), sub2.getSubCount(DIM), sub2.isPostHC(), DIM));
		BitsInt.insertBits(sub2.ba, infOffs, DIM*(node.getInfixLen()+1));
		
		// update infix
		for (int i = 0; i < DIM; i++) {
			sub2.setInfix(i, infix[i], DIM);
		}
		
		//update parent, the position is the same
		parent.replaceSub(posInParent, sub2, DIM);
		
        return oldValue;
    }

    private T deleteNI(long[] key, Node<T> node, int currentDepth,
			Node<T> parent, long posInParent) {
        final long pos = posInArray(key, currentDepth, DEPTH);
        NodeEntry<T> e = node.getChild(pos, DIM);
        if (e == null) {
        	return null;
        }
        if (e.node != null) {
        	return delete(key, e.node, currentDepth+1, node, pos);
        }
    	
    	if (!node.postEquals(e.getKey(), key)) {
    		//value does not exist
    		return null;
    	}
 
    	//okay we have something to delete 
    	nEntries--;
    	
        //check if merging is necessary (check children count || isRootNode)
    	int nP = node.getPostCount(DIM);
    	int nS = node.getSubCount(DIM);
        if (parent == null || nP + nS > 2) {
            //no merging required
       		//value exists --> remove it
        	node.removePostPOB(pos, -1, DIM);  //do not call-NI directly, we may have to deconstruct 
       		return e.value;
        }

        //okay, at his point we have a post that matches and (since it matches) we need to remove 
        //the local node because it contains at most one other entry and it is not the root node.
        nNodes--;
        

		T oldValue = e.value;

        //locate the other entry
		CBIterator<NodeEntry<T>> iter = node.ind.iterator();
		Entry<NodeEntry<T>> ie = iter.nextEntry();
		if (ie.key() == pos) {
			//pos2 is the entry to be deleted, find the other entry for pos2
			ie = iter.nextEntry();
		}
		
		e = ie.value(); 
    	if (e.key != null) {
       		//this is also a post
    		long[] newPost = e.getKey();
    		node.getInfixNoOverwrite(newPost);
    		T val = e.value;
    		applyArrayPosToValue(ie.key(), node.getPostLen(), newPost, currentDepth==0);
    		e.setPost(newPost, val);
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
		int infOffs = node.getBitPos_Infix(DIM);
		int newInfixLen = node.getInfixLen() + 1 + sub2.getInfixLen();
       	sub2.infixLen = (byte)newInfixLen;
		sub2.ba = BitsInt.arrayEnsureSize(sub2.ba, sub2.calcArraySizeTotalBits(
				sub2.getPostCount(DIM), sub2.getSubCount(DIM), sub2.isPostHC(), DIM));
		BitsInt.insertBits(sub2.ba, infOffs, DIM*(node.getInfixLen()+1));
		
		// update infix
		for (int i = 0; i < DIM; i++) {
			sub2.setInfix(i, infix[i], DIM);
		}
		
		//update parent, the position is the same
		parent.replaceSub(posInParent, sub2, DIM);
		
        return oldValue;
	}

	private T insertNI(long[] key, T value, int currentDepth, Node<T> node, long pos) {
    	NodeEntry<T> e = node.getChild(pos, DIM);
    	if (e == null) {
    		//nothing found at all
    		//insert as postfix
    		node.addPostPOB(pos, -1, key, value, UNKNOWN, UNKNOWN);
    		nEntries++;
    		return null;
    	} else if (e.node != null) {
    		Node<T> sub = e.node;
    		//sub found
    		if (sub.hasInfixes()) {
    			//splitting may be required, the node has infixes
    			return insertSplit(key, value, sub, currentDepth+1);
    		}
    		//splitting not necessary
    		return insert(key, value, currentDepth+1, sub);
    	} else {
    		//must be a post
    		T prevVal = e.value; 
    		//maybe it's the same value that we want to add?
    		if (node.postEquals(e.key, key)) {
    			//value exists
    			e.value = value;
    			return prevVal;
    		}

    		//existing value
    		//Create a new node that contains the existing and the new value.
    		Node<T> sub = calcPostfixes(key, value, e.getKey(), prevVal, currentDepth+1);

    		//replace value with new leaf
    		node.setPostCount(node.getPostCount(DIM)-1, DIM);
    		node.setSubCount(node.getSubCount(DIM)+1, DIM);
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
                	node.addPostPOB(pos, pob, key, value, UNKNOWN, UNKNOWN);
                }
                nEntries++;
                return null;
            } else {
        		if (sub.hasInfixes()) {
                    //splitting may be required, the node has infixes
        			return insertSplit(key, value, sub, currentDepth+1);
        		}
        		//splitting not necessary
        		return insert(key, value, currentDepth+1, sub);
            }
        } else {
            //is leaf
        	int pob = node.getPostOffsetBits(pos, DIM);
            if (pob < 0) {
            	node.addPostPOB(pos, pob, key, value, UNKNOWN, UNKNOWN);
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
    	
        long mask = (1l<<infLen) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
        mask <<= mcb; //last bits is stored in postfix
        for (int i = 0; i < key1.length; i++) {
            long v = key1[i];
            node.setInfix(i, v & mask, DIM);
        }
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
    private T insertSplit(long[] key, T value, Node<T> node, int currentDepth) {
        //check if splitting is necessary
     	long[] infix = new long[DIM];
     	node.getInfixNoOverwrite(infix);
     	int maxConflictingBits = getConflictingInfixBits(key, infix, node);
     	if (maxConflictingBits == 0) {
     		//no conflicts detected, no splitting required.
     		return insert(key, value, currentDepth, node);
     	}


        //do the splitting
        if (DEBUG && DEPTH-maxConflictingBits < 0) {
            throw new IllegalStateException("cb=" + maxConflictingBits);
        }
        //nelLocalLen: -1 for the bit stored in the map
        byte newLocalLen = (byte) (DEPTH-currentDepth-maxConflictingBits);
        if (DEBUG && newLocalLen < 0) {
            throw new IllegalStateException("cd=" + currentDepth + " nll=" + newLocalLen +
                    " mcb=" + maxConflictingBits);
        }
        int newSubInfLen = node.getInfixLen() - newLocalLen - 1;
        if (DEBUG && newSubInfLen < 0) {
            throw new IllegalStateException("" + node.getInfixLen() + " - " + newLocalLen + 
            		" mcb=" + maxConflictingBits + "  cd=" + currentDepth);
        }

        //What does 'splitting' mean:
        //The current node has an infix that is not (or only partially compatible with 
        //the new value. 
        //The new value should end up as post-fix for the current node. All current post-fixes
        //and sub-nodes are moved to a new sub-node. We know that there are more than one children
        //(posts+subs), otherwise the tree would be deteriorated.
        //If there would be only one, we wouldn't need a new sub-node, but this should never happen.
        
        //How splitting works:
        //We insert a new nodes below the current node.
        //In any case we create two new sub-nodes. One with the new value and one with the shortened
        //infix (replaces the existing single sub).

        //before everything else: calculate pos, because we lose node attributes below
        //We use the infixes as references, because they provide the correct location for the new sub
        long posOfNewSub = posInArrayFromInfixes(node, newLocalLen);

        //First: create sub-node to contain all current sub-nodes, but using a shorter infix.
        //This (current node) could be a leave-node
        
        //create new sub-node
        Node<T> newSub = Node.createNode(this, newSubInfLen, node.getPostLen(), UNKNOWN, DIM); 
        
        //copy values from current node. Post-fixes and sub-nodes do not need to be updated.
        //copy state byte...
        newSub.isHC = node.isHC;
//        newSub.setPostHC(node.isPostHC());
//        newSub.setSubHC(node.isSubHC());
//        newSub.setPostNI(node.isPostNI());
//        newSub.setSubNI(node.isSubNI());
        newSub.subNRef = node.subNRef;
        newSub.ba = node.ba;
        newSub.ind = node.ind;
        newSub.values = node.values;
        node.setPostHC(false);
        node.setSubHC(false); 
   		node.setPostNI(false);
   		node.setSubNI(false);
        node.postLen = (byte)(DEPTH-currentDepth-newLocalLen-1);
        node.subNRef = null;
        node.ind = null;
        node.values = null;
        int oldInfLen = node.getInfixLen();
        node.infixLen = newLocalLen;
        //this sets all values to 0/false:
        node.ba = BitsInt.arrayCreate(node.calcArraySizeTotalBits(1, 1, false, DIM));
        
        //cut off existing prefixes in sub-node
    	int baseS = newSub.getBitPos_Infix(DIM);
        int toSkip = oldInfLen - newSubInfLen;
        for (int i = 0; i < DIM; i++) {
        	BitsInt.copyBitsLeft(newSub.ba, baseS+oldInfLen*i+toSkip, 
        			newSub.ba, baseS+newSubInfLen*i, newSubInfLen);
        }
    	BitsInt.removeBits(newSub.ba, baseS+newSubInfLen*DIM, (oldInfLen-newSubInfLen)*DIM);
    	//ensure that subNode has correct byte[] size
    	newSub.ba = BitsInt.arrayTrim(newSub.ba, newSub.calcArraySizeTotalBits(
    			newSub.getPostCount(DIM), newSub.getSubCount(DIM), newSub.isPostHC(), DIM));
        
        //update local node
        if (newLocalLen > 0) {
            long maskLocal = ~((-1L) << maxConflictingBits);
            maskLocal = ~maskLocal;
            //cut off existing prefixes
            //as done for the subNode? 
            for (int i = 0; i < DIM; i++) {
                node.setInfix(i, infix[i] & maskLocal, DIM);
            }
        }

        //insert the new sub
        node.addSubNode(posOfNewSub, newSub, DIM);

        //Second: new sub for new value:
        //insert a new leaf
        long pos = posInArray(key, currentDepth+newLocalLen, DEPTH);
    	node.addPost(pos, key, value);

    	nEntries++;
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
            if (node.getInfixBit(i, DIM, infixInternalOffset)) {
                pos |= 1L;
            }
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
        		sb.append(BitsInt.toBinary(key, DEPTH));
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
        		" sc=" + node.getSubCount(DIM) + " pc=" + node.getPostCount(DIM) + " inf=[");

        //for a leaf node, the existence of a sub just indicates that the value exists.
        node.getInfix(key);
        if (node.getInfixLen() > 0) {
        	long[] inf = new long[DIM];
        	node.getInfix(inf);
        	sb.append(BitsInt.toBinary(inf, DEPTH));
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
                sb.append(ind + BitsInt.toBinary(key, DEPTH));
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
	public PhQuery<T> query(long[] min, long[] max) {
		if (min.length != DIM || max.length != DIM) {
			throw new IllegalArgumentException("Invalid number of arguments: " + min.length +  
					" / " + max.length + "  DIM=" + DIM);
		}
		return new PhIterator3<T>(getRoot(), min, max, DIM, DEPTH);
	}

    private static final <T> boolean checkAndApplyRange(Node<T> node, long[] valTemplate, 
    		long[] rangeMin, long[] rangeMax, final int DIM) {
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
    			long in = node.getInfix(dim, DIM);
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
	 * Performs a spherical range query with a maximum distance {@code maxDistance} from point
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
		return new PhIterator3<T>(getRoot(), min, max, DIM, DEPTH);
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

	@Override
	public T update(long[] oldKey, long[] newKey) {
		T old = remove(oldKey); 
		//TODO this is wrong, cold be null .... 
		if (old == null) {
			return null;
		}
		T dummy = put(newKey, old);
		//TODO this is wrong, cold be null anyway.... 
		if (dummy != null) {
			return null;
		}
		return old;
	}
	
	@Override
	public List<PhEntry<T>> queryAll(long[] min, long[] max) {
		ArrayList<PhEntry<T>> ret = new ArrayList<>();
		PhIterator<T> i = query(min, max);
		while (i.hasNext()) {
			ret.add(i.nextEntry());
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <R> List<R> queryAll(long[] min, long[] max, int maxResults,
			PhPredicate filter, PhMapper<T, R> mapper) {
		if (filter != null || mapper != null || maxResults < Integer.MAX_VALUE) {
			throw new UnsupportedOperationException();
		}
		return (List<R>) queryAll(min, max);
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

