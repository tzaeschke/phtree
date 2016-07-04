/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 * 
 * Author: Tilmann Zaeschke
 */
package ch.ethz.globis.pht.bits;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;
import org.zoodb.index.critbit.BitTools;

public class TestIncSuccessor {

	@Test
	public void testNoFilter() {
		for (int i = 0; i < 1000; i++) {
			assertEquals(i+1, inc(i, 0, 0xFFFF));
			assertTrue(checkHcPos(i, 0, 0xFFFF));
		}
	}
	
	@Test
	public void testMIN() {
		long MAX = 0b11111111;
		long MIN = 0b00001100;
		long prev = 0;
		long[] seq = getSequence(MIN, MAX);
		int seqPos = 0;
		for (int i = 0; i < MAX; i++) {
			long r = succ(i, MIN, MAX);
			//System.out.println("i=" + i + "  -->  rS=" + r);
			assertTrue(checkHcPos(r, MIN, MAX));
			seqPos = checkSequence(seq, seqPos, r, i);
			//System.out.println("min= " + MIN + "   max=" + MAX);
			//System.out.println("r= " + r + "   prev=" + prev);
			assertTrue(r >= prev);
			prev = r;
		}
		long r1 = succ(MAX, MIN, MAX);
		//assertEquals(-1, r1);
		assertTrue(r1 <= MIN);
	}
	
	
	@Test
	public void testMAX() {
		long MIN =   0b000000;
		long MAX =   0b111001;
		long prev = 0;
		long[] seq = getSequence(MIN, MAX);
		int seqPos = 0;
		//System.out.println("min= " + MIN + "   max=" + MAX);
		for (int i = 0; i < MAX; i++) {
			long r = succ(i, MIN, MAX);
			//System.out.println("i=" + i + "  -->  rS=" + r);
			assertTrue(checkHcPos(r, MIN, MAX));
			seqPos = checkSequence(seq, seqPos, r, i);
			assertTrue(r >= prev);
			assertTrue(r > i);
			prev = r;
		}
		long r1 = succ(MAX, MIN, MAX);
		//assertEquals(-1, r1);
		assertTrue(r1 <= MIN);
	}
	
	
	@Test
	public void testMIN_MAX() {
		long MIN =   0b001100;
		//long MAX = 0b010010;
		long MAX =   0b101101;
		long prev = 0;
		long[] seq = getSequence(MIN, MAX);
		int seqPos = 0;
		//System.out.println("min= " + MIN + "   max=" + MAX);
		for (int i = 0; i < MAX; i++) {
			long r = succ(i, MIN, MAX);
			//System.out.println("i=" + i + "  -->  rS=" + r);
			assertTrue(checkHcPos(r, MIN, MAX));
			seqPos = checkSequence(seq, seqPos, r, i);
			assertTrue(r >= prev);
			assertTrue(r > i);
			prev = r;
		}
	}
	
	@Test
	public void testMIN_MAXmany() {
		Random R = new Random(0);
		for (int k = 2; k < 10; k++) {
			for ( int i = 0; i < 200000; i++) {
				test(R, k);
			}
		}
		//System.out.println("A=" + A);
		//System.out.println("B=" + B);
		//System.out.println("C=" + C);
	}
	
	private void test(Random R, int K) {
		long MIN = 0;
		long MAX = 0;
		for (int k = 0; k < K; k++) {
			int r = R.nextInt(2);
			MAX = BitTools.setBit(MAX, 63-k, true);
			if (r == 0) {
				MIN = BitTools.setBit(MIN, 63-k, true);
			} else if (r == 1) {
				MAX = BitTools.setBit(MAX, 63-k, false);
			}
		}
		
		long prev = 0;
		long[] seq = getSequence(MIN, MAX);
		int seqPos = 0;
		//System.out.println("min= " + MIN + "   max=" + MAX);
		for (int i = 0; i < MAX; i++) {
			long r = succ(i, MIN, MAX);
			//System.out.println("i=" + i + "  -->  rS=" + r);
			assertTrue(checkHcPos(r, MIN, MAX));
			seqPos = checkSequence(seq, seqPos, r, i);
			assertTrue(r >= prev);
			assertTrue(r > i);
			prev = r;
		}
	}
	
	private long[] getSequence(long MIN, long MAX) {
		//MIN ^ MAX seat all bits to `1' that are not filtered OR where both filter.
		//Since it is not allowed that BOTH filter, we only get `1' where no filtering occurs.
		int size = 1 << Long.bitCount(MIN ^ MAX);
		long[] ret = new long[size];
		ret[0] = MIN;
		for (int i = 1; i < size; i++) {
			ret[i] = inc(ret[i-1], MIN, MAX);
		}
		return ret;
	}
	
	private int checkSequence(long[] seq, int seqPos, long r, long i) {
		//System.out.println("i=" + i + "  --> r=" + r);
		if (r != seq[seqPos]) {
			assertTrue("i=" + i, i >= seq[seqPos]);
			seqPos++;
			//System.out.println("sPos=" + seqPos + "  -->  S=" + seq[seqPos] + " /" + seq[seqPos-1]);
			assertEquals(seq[seqPos], r);
			assertTrue("i=" + i, i < seq[seqPos]);
		}
		return seqPos;
	}
	
	private static long succ0(long v, long min, long maxI) {
		if (checkHcPos(v, min, maxI)) {
			return inc(v, min, maxI);
		}
		//v |= min;
		if (((v & maxI )|min) > v) {
			return (v & maxI) | min;
		}
		long r = inc(v, min, maxI);
		if (r < v && v < maxI) {
			r = inc(r, min, maxI);
		}
		return r;
	}
	
	private static long A = 0;
	private static long B = 0;
	private static long C = 0;
	
	/**
	 * SIGMOD version - simple
	 * @param v
	 * @param min
	 * @param max
	 * @param maxV
	 * @return
	 */
	private static long succ(long v, long min, long max) {
		if (checkHcPos(v, min, max)) {
			//okay, same as inc()
			A++;
			return inc(v, min, max);
		}

		long coll = ((v | min) & max) ^ v;
		long diffBit = Long.highestOneBit(coll);
		
		long mask = diffBit > 0 ? diffBit - 1 : 0;
		long confMin = (~v) & min;   
		long confMax = (v) & ~max;   

		if (confMin > confMax) {
			v &= ~mask;
			v |= min;
			B++;
			return v;
		}

		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~max);
		r &= ~mask;
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r+= coll & ~max;
		//remove invalid bits.
		r = (r & max) | min;
		C++;
		return r;
	}
	
	/**
	 * SIGMOD version - no-branch
	 * @param v
	 * @param min
	 * @param max
	 * @param maxV
	 * @return
	 */
	private static long succSS(long v, long min, long max) {
		long confMin = Long.highestOneBit((~v) & min | 1L);   
		long confMax = Long.highestOneBit((v) & ~max | 1L);   
		long maskMin = confMin-1L;
		long maskMax = confMax-1L;
		
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~max);
		//Set trailing bit after possible conflict to '0'
		r &= ~(maskMin | maskMax);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		//maskMin ensures that we don't add anything if the most significant conflict was a min-conflict
		r+= confMax & ~maskMin;
		//remove invalid bits.
		r = (r & max) | min;
		
		return r;
	}
	
	private static long succX(long v, long min, long maxI) {
		long coll = ((v | min) & maxI) ^ v;
		long diffBit = Long.highestOneBit(coll);
		
		long toAdd = 1;
		long mask = diffBit > 0 ? diffBit - 1 : 0;
		//long mask = (diffBit - 1) * (1>>>diffBit);

//		long dMin = (~v) & min;   
//		if (dMin > 0) {
//			long loBit = Long.highestOneBit(dMin);
//			toAdd = 0; 
//			//TODO toAdd *= coll & min > 0 ? 0 : 1;
//			//TODO dirty hack: toAdd >>>= coll & min;
//		}
		toAdd >>>= coll & min;
				
//		long dMax = (v) & ~maxI;   
//		if (dMax > 0) {
//			long hiBit = Long.highestOneBit(dMax);
//			//toAdd = hiBit; 
//			//toAdd = coll & ~maxI;
//			//use '|' in case they are both '1'.
//			//TODO toAdd = (toAdd | (coll & ~maxI)) & mask;
//		}
		//TODO use ?: branching in code and just suggest that this can be avoided with >>>xyz
		long x = (coll & ~maxI);
		toAdd >>>= x;
		toAdd |= x;
//		toAdd = toAdd | (coll & ~maxI);
//		toAdd >>>= toAdd-1;  //works only for toAdd>0 ...
			
		//TODO Use less compact SIGMOD format! 
		
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~maxI);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r &= ~mask;
		r+= toAdd;
		//remove invalid bits.
		r = (r & maxI) | min;
		
		//return -1 if we exceed 'max', cause and overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		return (r <= v) ? -1 : r;
	}

	private static long succ1(long v, long min, long maxI) {
		long coll = ((v | min) & maxI) ^ v;
		long diffBit = Long.highestOneBit(coll);
		
		long toAdd = 1;
		long mask = diffBit > 0 ? diffBit - 1 : 0;
		//long mask = (diffBit - 1) * (1>>>diffBit);

//		long dMin = (~v) & min;   
//		if (dMin > 0) {
//			long loBit = Long.highestOneBit(dMin);
//			toAdd = 0; 
//			//TODO toAdd *= coll & min > 0 ? 0 : 1;
//			//TODO dirty hack: toAdd >>>= coll & min;
//		}
		toAdd >>>= coll & min;
				
//		long dMax = (v) & ~maxI;   
//		if (dMax > 0) {
//			long hiBit = Long.highestOneBit(dMax);
//			//toAdd = hiBit; 
//			//toAdd = coll & ~maxI;
//			//use '|' in case they are both '1'.
//			//TODO toAdd = (toAdd | (coll & ~maxI)) & mask;
//		}
		//TODO use ?: branching in code and just suggest that this can be avoided with >>>xyz
		long x = (coll & ~maxI);
		toAdd >>>= x;
		toAdd |= x;
//		toAdd = toAdd | (coll & ~maxI);
//		toAdd >>>= toAdd-1;  //works only for toAdd>0 ...
			
		//TODO Use less compact SIGMOD format! 
		
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~maxI);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r &= ~mask;
		r+= toAdd;
		//remove invalid bits.
		r = (r & maxI) | min;
		
		//return -1 if we exceed 'max', cause and overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		return (r <= v) ? -1 : r;
	}

	
	private static long succ2(long v, long min, long maxI) {
		long coll = ((v | min) & maxI) ^ v;
		long diffBit = Long.highestOneBit(coll);
		
		long toAdd = 1;
		long mask = diffBit > 0 ? diffBit - 1 : 0;

		long dMin = (~v) & min;   
		if (dMin > 0) {
			long minBit = Long.highestOneBit(dMin);
//			long lowMask = minBit - 1;
//			System.out.println("lowmask=" + lowMask + "   " + Bits.toBinary(~lowMask));
//			System.out.println("v=" + v + " -->  " + ((v&~(lowMask))));
//			long toAdd = lowMask & min; 
//			System.out.println("toAdd=" + toAdd + " = " + Bits.toBinary(toAdd));
//			System.out.println("minBit=" + minBit + " = " + Bits.toBinary(minBit));

			//return ((v&~lowMask)+minBit) | min;
			
//			//first, fill all 'invalid' bits with '1' (bits that can have only one value).
//			long r = v | min | (~maxI);
//			//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
//			r &= ~lowMask;
//			r+= 0;
//			System.out.println("r1=" + r + "   " + Bits.toBinary(r)  +"   min=" + min + "  max=" + maxI);
//			//remove invalid bits.
//			r = (r & maxI) | min;
//			System.out.println("r2=" + r + "   " + Bits.toBinary(r)  +"   min=" + min + "  max=" + maxI);
//
//			//return -1 if we exceed 'max', cause and overflow or return the original value. The
//			//latter can happen if there is only one possible value (all filter bits are set).
//			//The <= is also owed to the bug tested in testBugDecrease()
//			return (r > maxI || r <= v) ? -1 : r;
//			mask = lowMask;
			toAdd = 0; 
		}
		
		long dMax = (v) & ~maxI;   
		if (dMax > 0) {
			long hiBit = Long.highestOneBit(dMax);
//			long hiMask = hiBit - 1;
//			System.out.println("himask=" + hiMask + "   " + Bits.toBinary(~hiMask));
//			System.out.println("v=" + v + " -->  " + ((v&~hiMask)));
//			long toAdd = hiMask & maxI;
//			System.out.println("toAdd=" + hiMask + " & " + maxI);
////			System.out.println("toAdd=" + Bits.toBinary(toAdd) + " & " + Bits.toBinary(min));
//			System.out.println("toAdd=" + toAdd + " = " + Bits.toBinary(toAdd));
//			return ((v&~hiMask)+hiBit)| min;
//			long r = v | min | (~maxI);
//			//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
//			r &= ~hiMask; 
//			r+= hiBit;
//			System.out.println("r1=" + r + "   " + Bits.toBinary(r)  +"   min=" + min + "  max=" + maxI);
//			//remove invalid bits.
//			r = (r & maxI) | min;
//			return (r > maxI || r <= v) ? -1 : r;
//			mask = hiMask;
			toAdd = hiBit; 
		}
			
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~maxI);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r &= ~mask;
		r+= toAdd;
		//remove invalid bits.
		r = (r & maxI) | min;

		//return -1 if we exceed 'max', cause and overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		return (r <= v) ? -1 : r;
	}

	
	/**
	 * Best HC incrementer ever. 
	 * @param v
	 * @param min
	 * @param max
	 * @return next valid value or -1.
	 */
	private static long inc(long v, long min, long max) {
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~max);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r++;
		//remove invalid bits.
		r = (r & max) | min;

		//return -1 if we exceed 'max' and cause an overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		return (r <= v) ? -1 : r;
	}

	
	private static boolean checkHcPos(long pos, long min, long max) {
//		TestPerf.STAT_X5++;
//		if ((pos & maskUpper) != pos) {
//			if ((pos | maskLower) != pos) {
//				TestPerf.STAT_X5ab++;
//				return false;
//			}
//			TestPerf.STAT_X5b++;
//			return false;
//		}
//		if ((pos | maskLower) != pos) {
//			TestPerf.STAT_X5a++;
//			return false;
//		}
//		return true;
		
//		if ((pos | min) != pos) {
//			return false;
//		}
//		if ((pos & max) != pos) {
//			return false;
//		}

		return ((pos | min) & max) == pos;
	}
	

	
	
}
