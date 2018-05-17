/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.bits;



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
public class TestJavaPerf {

	public static void main(String[] args) {
		new TestJavaPerf().testTransposePos();
	}

	private void testTransposePos() {
		int N = 10000000;
		final int DIM = 6;
		final int DEPTH = 32;
		
		testLoop(DIM, DEPTH, N/1000);
		
		long t1 = System.currentTimeMillis();
		
		testLoop(DIM, DEPTH, N);
		
		long t2 = System.currentTimeMillis();
		long t = t2 - t1;
		System.out.println("t= " + t + "  -> " + ((double)t/((double)(N*DEPTH))));
	}
	
	private void testLoop(int DIM, int DEPTH, int N) {
		long[] val = new long[DIM];
		int somePos = 1234567;
		for (int i1 = 0; i1 < N; i1++) {
			somePos += 12345 % 1000000000;
			for (int d = 0; d < DEPTH; d++) {
//				PhTree.applyArrayPosToValue(somePos, d, val, DEPTH);
//				PhTree.applyArrayPosToValue2(somePos, d, val);
				//applyArrayPosToValue(somePos, d, val, DIM, DEPTH);
				//applyArrayPosToValue(somePos, d, val);
				applyArrayPosToValue2(somePos, d, val);
			}
		}
	}
	
	
    static void applyArrayPosToValue(long pos, int currentPostLen, long[] val) {
    	final int DIM = val.length;
    	long mask1 = 1l << currentPostLen;
    	long mask0 = ~mask1;
		for (int d = 0; d < DIM; d++) {
			long x = pos & (1L << (DIM-d-1));
			if (x!=0) {
				val[d] |= mask1;
			} else {
				val[d] &= mask0;
			}
		}
    }

    static void applyArrayPosToValue2(long pos, int currentPostLen, long[] val) {
    	final int DIM = val.length;
    	long mask1 = 1l << currentPostLen;
    	long mask0 = ~mask1;
    	long posMask = 1<<DIM;
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

    static void applyArrayPosToValue3(long pos, int currentPostLen, long[] val) {
    	final int DIM = val.length;
    	long mask1 = 1l << currentPostLen;
    	long mask0 = ~mask1;
    	long posMask = 1<<(DIM-1);
		for (int d = 0; d < DIM; d++) {
			long x = pos & (posMask>>>d);
			if (x!=0) {
				val[d] |= mask1;
			} else {
				val[d] &= mask0;
			}
		}
    }

   /**
     * Apply a HC-position to a value. This means setting one bit for
     * each dimension.
     * @param pos
     * @param val
     */
    static void applyArrayPosToValue(int pos, int currentDepth, long[] val, int DIM, int DEPTH) {
//    	int mask = 1 << (DIM-1); 
//    	final int xShift = (DEPTH-1-currentDepth); 
//		for (int dim = 0; dim < DIM; dim++) {
//			long x = pos & mask;//(1 << (DIM-dim-1));
//			//val[dim] = setBitLR(val[dim], currentDepth, (x>=1));
//			//val[dim] = setBitLR(val[dim], currentDepth, x>>>(DIM-dim-1));
//			x <<= xShift;
//			//val[dim] = (val[dim] | x) & (~x);
//			val[dim] = (val[dim] | x) & (~x);
//			mask >>>= 1;
//		}
		for (int dim = 0; dim < DIM; dim++) {
			int x = pos & (1 << (DIM-dim-1));
			val[dim] = setBitLR(val[dim], currentDepth, (x>=1), DEPTH);
		}
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
    
    //@Test
    public void testLogPerf() {
    	int N = 100*1000*1000;
    	long t1, t2;
    	double x = 0;
    	
    	t1 = System.currentTimeMillis();
    	for (int i = 1; i < N; i++) {
    		x += Math.log(i);
    	}
    	t2 = System.currentTimeMillis();
    	System.out.println("Time: " + (t2-t1));
    	
    	t1 = System.currentTimeMillis();
    	for (int i = 1; i < N; i++) {
    		x += 32-Long.numberOfLeadingZeros(i);
    	}
    	t2 = System.currentTimeMillis();
    	System.out.println("Time: " + (t2-t1));
    	
    	System.out.println("x=" + x);
    }

}
