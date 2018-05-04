/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14;

@Deprecated
public class HTable<T> {
	
	private static final int BITS_INNER = 3;
	private static final int MAX_N_INNER = 1 << BITS_INNER;
	
	private int nEntries = 0;
	private Object ht;
	private int htDepth = 0;
	
	
	//TODO use pool for Object[]
	
	void create(int dims) {
		ht = new Object[MAX_N_INNER];
		htDepth = 1;
		
	}
	
	@SuppressWarnings("unchecked")
	public T get(int slotId) {
		if (nEntries <= 1) {
			return (T) ht;
		}
		Object[] oa = (Object[]) ht;
		int mask = getIdMask();
		int shift = getIdShift(htDepth);
		for (int i = htDepth-1; i > 0; i--) {
			//inner node
			oa = (Object[]) oa[(slotId >>> shift) & mask];
			shift -= BITS_INNER;
		} 
		
		return (T) oa[(slotId >>> shift) & mask];
	}
	
	public int append(T data) {
		if (needsResizeForAdd(nEntries)) {
			Object[] oa = new Object[MAX_N_INNER];
			oa[0] = ht;
			ht = oa;
			htDepth++;
		}
		set(nEntries, data, false);
		return nEntries++;
	}
	
	@SuppressWarnings("unchecked")
	private T set(int slotId, T data, boolean shrink) {
		//How do we handle unbalanced trees? Depth may be lower in the rightmost branch
		//Append:
		// - If child == Object[]: Traverse
		// - If child == null: Abort loop, set shift=0, insert element
		// - If child == T: 
		//   - if child has room (Insert new Object[] into parent,
		
		//New plan: look at nEntries!
		//- if bits in a certain depth are '0' then we can skip the level:
		//  There is no (need for an) array.
		
		if (nEntries <= 1 && slotId == 0) {
			Object ret = ht;
			ht = data;
			//TODO return [] to pool if shrink (do in calling method?)
			return (T) ret;
		}
		Object[] oa = (Object[]) ht;
		int mask = getIdMask();
		int shift = getIdShift(htDepth);
		for (int i = htDepth-1; i > 0; i--) {
			//inner node
			Object[] sub = (Object[]) oa[(slotId >>> shift) & mask];
			if (sub == null) {
				sub = new Object[MAX_N_INNER];
				oa[(slotId >>> shift) & mask] = sub;
			}
			shift -= BITS_INNER;
			oa = sub;
		} 
		
		T ret = (T) oa[(slotId >>> shift) & mask];
		//TODO resize array if required?
		oa[(slotId >>> shift) & mask] = data;
		
		if (shrink) {
			
		}
		
		return ret;
	}
	
	
	public T replaceWithLast(int slotId) {
		T last = set(nEntries-1, null, true);
		nEntries--;
		set(slotId, last, false);
		return last;
	}
	
	private static int getIdMask() {
		return ~((-1) << BITS_INNER);
	}
	
	private static int getIdShift(int htDepth) {
		return (htDepth-1)*BITS_INNER;
	}
	
	private boolean needsResizeForAdd(int nEntries) {
		int nEntries2 = nEntries+1;
		
//		int nBits = Integer.numberOfLeadingZeros(nEntries);
//		int nBits2 = Integer.numberOfLeadingZeros(nEntries2);
//		if (nBits2 == nBits) {
//			return false;
//		}
		return nEntries2 > 1 << (htDepth*BITS_INNER);
		//return (32-nBits2)>>>BITS_INNER > (32-nBits)>>>BITS_INNER;
	}
	
	private static int getHtDepth(int nEntries) {
		if (nEntries <= 1) {
			return 0;
		}
		//TODO correct?
		return (32 - Integer.numberOfLeadingZeros(nEntries));
	}
}
