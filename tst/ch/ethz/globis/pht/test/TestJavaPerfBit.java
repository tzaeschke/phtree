/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.test;

import java.util.Random;


/**
 * WEEEIIRRD!!!!!
 * 
 * - Calling the warm-up loop makes the actual test loop ~10% slower (400ms)
 * 	 --> Probably had to do with DIM being initialised later, resulting in multiple DIM values...
 * - Moving the 'mask' creation outside the loop makes the whole thing 5% slower!
 * - Every operation (&, ~), appears to cost ~6cycles (on operation per 500MHz).
 * 
 * @author ztilmann
 *
 */
public class TestJavaPerfBit {

	public static void main(String[] args) {
		new TestJavaPerfBit().testBitPerf();
	}

	private void testBitPerf() {
		int N = 10000000;
		final int DIM = 6;
		final int DEPTH = 32;
		Random R = new Random(0);
		
		long[][] vals  = new long[1000][];
		for (int i = 0; i < vals.length; i++) {
			long[] val = new long[DIM];
			for (int d = 0; d < DIM; d++) {
				val[d] = R.nextLong();
			}
			vals[i] = val;
		}
		
		//testLoop(DIM, DEPTH, N);
		
		long t1 = System.currentTimeMillis();
		
		long l = 0;
		for (int i = 0; i < 20000; i++) {
			l += testLoop(DIM, DEPTH, N, vals);
		}
		
		long t2 = System.currentTimeMillis();
		long t = t2 - t1;
		System.out.println("t= " + t + "  -> " + ((double)t/((double)(N*DEPTH))));
		System.out.println("l=" + l);
	}
	
	private long testLoop(int DIM, int DEPTH, int N, long[][] vals) {
		long l = 0;
		for (long[] val: vals) {
			for (int d = 0; d < DEPTH; d++) {
				//applyPos0: 15sec
				//applyPos1: 10sec
				//applyPos1b: 8.8sec  --> verify correctness!
				//applyPos2: 8.8sec
				applyHcPos2(val[d%DIM], d, val);
				l += val[d%DIM];
			}
		}
		return l;
	}
	
	private long testLoop2(int DIM, int DEPTH, int N, long[][] vals) {
		long l = 0;
		for (long[] val: vals) {
			for (int d = 0; d < DEPTH; d++) {
				l += posInArray(DIM, DEPTH, val, d);
			}
		}
		return l;
	}
	
	
    private int posInArray(int DIM, int DEPTH, long[] valSet, int currentDepth) {
        //n=DIM,  i={0..n-1}
        // i = 0 :  |0|1|0|1|0|1|0|1|
        // i = 1 :  | 0 | 1 | 0 | 1 |
        // i = 2 :  |   0   |   1   |
        //len = 2^n
        //Following formula was for inverse ordering of current ordering...
        //pos = sum (i=1..n, len/2^i) = sum (..., 2^(n-i))

    	long valMask = (1l << (DEPTH-1-currentDepth));
    	
        int pos = 0;
        for (int i = 0; i < DIM; i++) {
        	pos <<= 1;
//        	pos |= valMask & valSet[i];
            if ((valMask & valSet[i]) != 0) {
                pos |= 1;
            }
            
//            if (bit(DEPTH, valSet[i], currentDepth)) {
//                pos |= (1 << i);//(DIM-(i+1)));
//            }
        }
        return pos;
    }
    private boolean bit(int DEPTH, long l, int pos) {
        return (l & (1l << (DEPTH-1-pos))) != 0;
    }
   

    /**
     * Sets a bit, counting from left to right.
     */
    private static long setBitLR(long l, int pos, boolean x, int DEPTH) {
//      if (x) {
//          return (l | (1l << (DEPTH-1-pos)));
//      } else {
//          return (l & (~(1l << (DEPTH-1-pos))));
//      }

    	return (x) ? (l | (1l << (DEPTH-1-pos))) : (l & (~(1l << (DEPTH-1-pos))));
  }
    private static long setBitLR(long l, int pos, int x, int DEPTH) {
//        if (val) {
//            return (l | (1l << (DEPTH-1-pos)));
//        } else {
//            return (l & (~(1l << (DEPTH-1-pos))));
//        }

//    	return (x>=1) ? (l | (1l << (DEPTH-1-pos))) : (l & (~(1l << (DEPTH-1-pos))));
        
    	x <<= (DEPTH-1-pos);
//    	l |= x; //set bit to 1 if applicable
//    	l &= (~x);
//    	return l;
    	return (l | x) & (~x);
    }

    
    public static void applyHcPos2(long pos, int currentPostLen, long[] val) {
    	final int DIM = val.length;
    	long mask = 1L << currentPostLen;
    	long posMask = 1L<<DIM;
		for (int d = 0; d < DIM; d++) {
			posMask >>>= 1;
			long x = pos & posMask;
			val[d] = (val[d] & ~mask) | (Long.bitCount(x) * mask);
//			if (x != 0) {
//				val[d] |= mask;
//			} else {
//				val[d] &= ~mask;
//			}
		}
    }

    public static void applyHcPos1b(long pos, int currentPostLen, long[] val) {
    	final int DIM = val.length;
    	long mask = 1L << currentPostLen;
    	for (int d = DIM-1; d >= 0; d--) {
			val[d] = (val[d] & ~mask) | ((pos & 1) * mask);
			pos >>>= 1;
//			if ((pos2 & 1) != 0) {
//				val[d] |= mask;
//			} else {
//				val[d] &= ~mask;
//			}
		}
    }

    public static void applyHcPos1(long pos, int currentPostLen, long[] val) {
    	final int DIM = val.length;
    	long mask = 1L << currentPostLen;
    	long pos2 = Long.rotateLeft(pos, Long.SIZE-DIM);
    	for (int d = 0; d < DIM; d++) {
    		pos2 = Long.rotateLeft(pos2, 1);
			val[d] = (val[d] & ~mask) | ((pos2 & 1) * mask);
//			if ((pos2 & 1) != 0) {
//				val[d] |= mask;
//			} else {
//				val[d] &= ~mask;
//			}
		}
    }

    public static void applyHcPos0(long pos, int currentPostLen, long[] val) {
    	final int DIM = val.length;
    	long mask = 1L << currentPostLen;
    	long posMask = 1L<<DIM;
		for (int d = 0; d < DIM; d++) {
			posMask >>>= 1;
			long x = pos & posMask;
			if (x != 0) {
				val[d] |= mask;
			} else {
				val[d] &= ~mask;
			}
		}
    }

}
