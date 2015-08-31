/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;


/**
 *
 * For the lower limit, a '1' indicates that the 'lower' half of this dimension does
 * NOT need to be queried.
 * For the upper limit, a '1' indicates that the 'higher' half DOES need to be queried.
 * 
 * @author ztilmann
 */
public class TestIncrementor {

	private final int DIM = 10;
	
	@Test
	public void testIncMin() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//NOT need to be queried.
    	//For the upper limit, a '1' indicates that the 'higher' half DOES need to be queried.
		long minMask = 0x2;  // ...0.0000.0010 
		long maxMask = 0xFF; // ...0.1111.1111
		long maxHcAddr = (1L<<8)-1;
		// 2=..010, 3=..011, 6=..110, 7=..111, 10=..1010, 11=1011, 14=1110, 15=1111 
		long[] res = {2, 3, 6, 7, 10, 11, 14, 15};
		long v = minMask;
		for (int i = 0; i < res.length; i++) {
			assertEquals(res[i], v);
			v = inc(v, minMask, maxMask, maxHcAddr);
		}
	}
	
	@Test
	public void testIncMin2() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//NOT need to be queried.
    	//For the upper limit, a '1' indicates that the 'higher' half DOES need to be queried.
		long minMask = 0x0A; // ...01010
		long maxMask = 0xFF;
		long maxHcAddr = (1L<<8)-1;
		long[] res = {10, 11, 14, 15, 26, 27};
		long v = minMask;
		for (int i = 0; i < res.length; i++) {
			assertEquals(res[i], v);
			v = inc(v, minMask, maxMask, maxHcAddr);
		}
	}
	
	@Test
	public void testIncMax() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//NOT need to be queried.
    	//For the upper limit, a '1' indicates that the 'higher' half DOES need to be queried.
		long minMask = 0x0;
		long maxMask = 0xFD; //..11101 
		long maxHcAddr = (1L<<8)-1;
		long[] res = {0, 1, 4, 5, 8, 9, 12, 13};
		long v = minMask;
		for (int i = 0; i < res.length; i++) {
			assertEquals(res[i], v);
			v = inc(v, minMask, maxMask, maxHcAddr);
		}
	}
	
	@Test
	public void testIncMax2() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//NOT need to be queried.
    	//For the upper limit, a '1' indicates that the 'higher' half DOES need to be queried.
		long minMask = 0x0;
		long maxMask = 0xF5; // ..1110101
		long maxHcAddr = (1L<<8)-1;
		long[] res = {0, 1, 4, 5, 16, 17, 20, 21};
		long v = minMask;
		for (int i = 0; i < res.length; i++) {
			assertEquals(res[i], v);
			v = inc(v, minMask, maxMask, maxHcAddr);
		}
	}
	
	
	@Test
	public void testIncMinMax() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//NOT need to be queried.
    	//For the upper limit, a '1' indicates that the 'higher' half DOES need to be queried.
		long minMask = 0x02;   //  ..0010
		long maxMask = 0xFB;   //  ..1011
		long maxHcAddr = (1L<<8)-1;
		long[] res = {2, 3, 10, 11, 18, 19, 26, 27};
		long v = minMask;
		for (int i = 0; i < res.length; i++) {
			assertEquals(res[i], v);
			v = inc(v, minMask, maxMask, maxHcAddr);
		}
	}
	
	@Test
	public void testIncMin0Max0() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//NOT need to be queried.
    	//For the upper limit, a '1' indicates that the 'higher' half DOES need to be queried.
		long minMask = 0x0; // ...01010
		long maxMask = 0x0;
		long maxHcAddr = (1L<<8)-1;
		long[] res = {0};
		long v = minMask;
		for (int i = 0; i < res.length; i++) {
			assertEquals(res[i], v);
			v = inc(v, minMask, maxMask, maxHcAddr);
		}
		assertTrue("v=" + v, v < 0);
	}
	
	@Test
	public void testIncMin32767Max32767() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//not need to be queried.
    	//For the upper limit, a '0' indicates that the 'higher' half does not need to be queried.
		long minMask = 0x7FFF; 
		long maxMask = 0x7FFF;
		long maxHcAddr = (1L<<16)-1;
		long[] res = {32767};
		long v = minMask;
		for (int i = 0; i < res.length; i++) {
			assertEquals(res[i], v);
			v = inc(v, minMask, maxMask, maxHcAddr);
		}
		assertTrue("v=" + v, v < 0);
	}
	
	@Test
	public void testBugDecrease() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//not need to be queried.
    	//For the upper limit, a '0' indicates that the 'higher' half does not need to be queried.
		long minMask = 0x0D; //13 
		long maxMask = 0x0F; //15
		long maxHcAddr = (1L<<8)-1;
		long[] res = {13, 15};
		long v = minMask;
		for (int i = 0; i < res.length; i++) {
			assertEquals(res[i], v);
			v = inc(v, minMask, maxMask, maxHcAddr);
		}
		assertTrue("v=" + v, v < 0);
	}
	
	@Test
	public void testBugNoFilter() {
    	//For the lower limit, a '1' indicates that the 'lower' half of this dimension does 
    	//not need to be queried.
    	//For the upper limit, a '0' indicates that the 'higher' half does not need to be queried.
		long minMask = 0x00;  
		long maxMask = 0x3FF;
		long maxHcAddr = (1L<<10)-1;
		long v = minMask;
		long vPrev = v-1;
		while (v >= 0) {
			assertEquals(vPrev + 1, v);
			vPrev = v;
			v = inc(v, minMask, maxMask, maxHcAddr);
			//System.out.println("v=" + v);
		}
		assertTrue("v=" + v, v < 0);
	}
	
	public static void main(String[] args) {
		new TestIncrementor().testPerf();
	}
	
	public void testPerf() {
//		long min = 0x000000000F000F00L;
//		long max = 0x00000000FFF0FFF0L;
//		long min = 0x0000000002000200L;
//		long max = 0x00000000FFFDFFFDL;
		long min = 0x0000000000000200L;
		long max = 0x00000000FFFFFFFDL;
//		long min = 0x0000000000000000L;
//		long max = 0x00000000FFFFFFFFL;
//		t=32874  n=21474836470
//				t=32802  n=21474836470
//				t=27887  n=21474836470
//				t=33962  n=21474836470
//				t=28450  n=21474836470
//				t=33928  n=21474836470
//				Done.

		long maxHcAddr = (1L<<32)-1;
		int N = 1;
		int MAX = Integer.MAX_VALUE;
		long n = 0;
		long t1, t2;
		
		if (N <=10) {
			t1 = System.currentTimeMillis();
			n = pIncDummy(N, MAX, min, max);
			t2 = System.currentTimeMillis();
			System.out.println("tD=" + (t2-t1) + "  n="+n);
		}

		t1 = System.currentTimeMillis();
		n = pInc(N, MAX, min, max, maxHcAddr);
		t2 = System.currentTimeMillis();
		System.out.println("t=" + (t2-t1) + "  n="+n);
				
		t1 = System.currentTimeMillis();
		n = pInc1(N, MAX, min, max, maxHcAddr);
		t2 = System.currentTimeMillis();
		System.out.println("t1=" + (t2-t1) + "  n="+n);
		
		t1 = System.currentTimeMillis();
		n = pInc(N, MAX, min, max, maxHcAddr);
		t2 = System.currentTimeMillis();
		System.out.println("t=" + (t2-t1) + "  n="+n);
				
		t1 = System.currentTimeMillis();
		n = pInc1(N, MAX, min, max, maxHcAddr);
		t2 = System.currentTimeMillis();
		System.out.println("t1=" + (t2-t1) + "  n="+n);
		
		t1 = System.currentTimeMillis();
		n = pInc(N, MAX, min, max, maxHcAddr);
		t2 = System.currentTimeMillis();
		System.out.println("t=" + (t2-t1) + "  n="+n);
				
		t1 = System.currentTimeMillis();
		n = pInc1(N, MAX, min, max, maxHcAddr);
		t2 = System.currentTimeMillis();
		System.out.println("t1=" + (t2-t1) + "  n="+n);
		
		if (N <=10) {
			t1 = System.currentTimeMillis();
			n = pIncDummy(N, MAX, min, max);
			t2 = System.currentTimeMillis();
			System.out.println("tD=" + (t2-t1) + "  n="+n);
		}
		System.out.println("Done.");
	}
	
	private long pInc(int N, int MAX, long min, long max, long maxHcAddr) {
		long n = 0;
		for (int i = 0; i < N; i++) {
			long v = min;
			while (v >= 0 && v < MAX) {
				n++;
				v = inc(v, min, max, maxHcAddr);
			}
		}
		return n;
	}
	
	private long pInc1(int N, int MAX, long min, long max, long maxHcAddr) {
		long n = 0;
		for (int i = 0; i < N; i++) {
			long v = min;
			while (v >= 0 && v < MAX) {
				n++;
				v = inc1(v, min, max, maxHcAddr);
			}
		}
		return n;
	}
	
	private long pIncDummy(int N, int MAX, long min, long max) {
		long n = 0;
		for (int i = 0; i < N; i++) {
			long v = min;
			while (v >= 0 && v < MAX) {
				n++;
				v = incDummy(v, min, max);
			}
		}
		return n;
	}
	
	
	private long inc1(long v, long min, long max, long dummy) {
		long r = v+1;
		//Argh! Get rid of this while!!!!! How??? With 'if' it already works quite well...
		while ( (r|min) != r || (r & max) != r) {
			long conflictMaskMin = (r|min) ^ r;
			long conflictMaskMax = (r&max) ^ r;
			r += (conflictMaskMin & min) + (conflictMaskMax & ~max);
			if (r > max) {
				return -1;
			}
		}
		return r;
	}
	
	/**
	 * Best HC incrementer ever. 
	 * @param v
	 * @param min
	 * @param max
	 * @param maxHcHdr ((1L<DIM)-1) = the maximum value for max.
	 * @return next valid value or -1.
	 */
	private static long inc(long v, long min, long max, long maxHcAddr) {
		//The first three blocks are just simplifications of the last block where the comments are
		long r;
//		if (min == 0 && max == maxHcAddr) {
//			r = v+1;
//		}
//		if (min == 0) {
//			if (max == maxHcAddr) {
//				r = v+1;
//			} else {
//				r = v | (~max);
//				r++;
//				r = r & max;
//			}
//		} else if (max == maxHcAddr) {
//			r = v | min;
//			r++;
//			r = r | min;
//		} else {
			//first, fill all 'invalid' bits with '1' (bits that can have only one value).
			r = v | (~max);
			//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
			r++;
			//remove invalid bits.
			r = (r & max) | min;
//		}		
		//return -1 if we exceed 'max' and cause an overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		return (r <= v) ? -1 : r;
		//return r;
	}

	/**
	 * Best HC incrementer ever. 
	 * @param v
	 * @param min
	 * @param max
	 * @return next valid value or -1.
	 */
	private long inc0(long v, long min, long max, long dummy) {
		if (min == 0) {
			//this is a short version of the stuff below. This branch makes inc() quicker than
			//other searches for ANY case!
			long r = v | (~max);
			r++;
			r = r & max;
			
			if (r > max || r <= v) {
				return -1;
			}
			return r;
		}
		
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~max);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r++;
		//remove invalid bits.
		r = (r & max) | min;
		
		//return -1 if we exceed 'max', cause and overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		if (r <= v) {
			return -1;
		}
		return r;
	}
	
	private long incDummy(long v, long min, long max) {
		long r = v+1;
		while ( (r|min) != r || (r & max) != r) {
			r++;
			if (r > max || r < 0) {
				return -1;
			}
		}
		return r;
	}
	
	private IncContext mgInc = null;
	
	private long inc2(long v, long min, long max) {
		if (mgInc == null) {
			mgInc = new IncContext(min, max, DIM);
		}
		long r = mgInc.getNext();
		if (r == -1) {
			mgInc = null;
		}
		return r;
	}
	
	private static class IncContext {
		private final long min, max;
		private final long nPerm; //number of permutations
		private int iPerm = 0;
		private long v;
		//store variable bits (bits that allow 0 and 1.
		private final int[] pattern;
		
		public IncContext(long min, long max, int DIM) {
			this.min = min;
			this.max = max;
			v = min;
			int nP = Long.bitCount(min) + Long.bitCount(~max);
			pattern = new int[nP];
			long minmax = min | ~max;
			int iP = 0;
			for (int i = 0; i < DIM; i++) {
				//TODO optimise: we only need the '1' bits!
				if ((minmax & 0x1) == 0) {
					pattern[iP++] = i;
				}
				minmax >>>= 1;
			}
			nPerm = (1L << nP);
		}
		
		boolean hasNext() {
			return v != -1;
		}
		
		long getNext() {
			long ret = v;
			if (iPerm++ >= nPerm) {
				v = -1;
				return ret;
			}
			
			//'min' is the template
			v = min;
			long bit = 1;
			for (int i = 0; i < pattern.length; i++) {
				int b = (iPerm >>> i) & 0x1;
				if (b > 0) {
					v |= (1L<<pattern[i]);
				}
			}
			
			return ret;
		}
	}
	
//	public static void main(String[] args) {
//		long min = 0x0000;
//		long max = 0xFFF5;  //   f=1111  e=1110  d=1101  c=1100  b=1011  a=1010  9=1001
//		System.out.println("min=" + Bits.toBinary(min, 8));
//		System.out.println("max=" + Bits.toBinary(max, 8));
//		for (int i = 0; i < 18; i++) {
//			String s = Bits.toBinary(i, 8);
//			if ((i | min) == i && (i & max) == i) {
//				System.out.println("i=" + i + " okay " + s);
//			} else {
//				System.out.println("i=" + i + " **** " + s);
//			}
//		}
//	}
}
