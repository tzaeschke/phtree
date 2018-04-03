/*
 * Copyright 2011 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.bits;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ch.ethz.globis.phtree.v11hd.BitsHD;

/**
 * 
 * @author ztilmann
 *
 */
public class TestBitsHD {


	@Test
	public void testInc() {
		assertTrue(BitsHD.isEq(new long[] {1}, BitsHD.inc(new long[] {0})));
		assertTrue(BitsHD.isEq(new long[] {1, 0}, BitsHD.inc(new long[] {0, -1L})));
		assertTrue(BitsHD.isEq(new long[] {1, 1}, BitsHD.inc(new long[] {1, 0})));
		assertTrue(BitsHD.isEq(new long[] {0, 0}, BitsHD.inc(new long[] {-1L, -1L})));
	}

	@Test
	public void testDec() {
		assertTrue(BitsHD.isEq(new long[] {0}, BitsHD.dec(new long[] {1})));
		assertTrue(BitsHD.isEq(new long[] {0, -1L}, BitsHD.dec(new long[] {1, 0})));
		assertTrue(BitsHD.isEq(new long[] {1, 0}, BitsHD.dec(new long[] {1, 1})));
		assertTrue(BitsHD.isEq(new long[] {-1L, -1L}, BitsHD.dec(new long[] {0, 0})));
	}

	@Test
	public void testInc1() {
		long[] min = new long[] {0xFFFFFFF0, 0x0FFFFFFFFFFFFFFFL};
    	long[] max = new long[] {0xFFFFFFFF, 0xFFFFFFFFFFFFFFFFL};
    	long[] val = min.clone();
    	long[] valPrev = min.clone();
    	int n = 0;
    	while (BitsHD.incHD(val, min, max)) {
    		n++;
    		assertTrue(BitsHD.isLess(valPrev, val));
    		BitsHD.set(valPrev, val);
    	}
    	assertEquals(255, n);
    	assertTrue(BitsHD.isEq(min, val));
    }
    
    @Test
    public void testInc2() {
    	long[] min = new long[] {0x00, 0x00L};
    	long[] max = new long[] {0x0F, 0xF000000000000000L};
    	long[] val = min.clone();
    	long[] valPrev = min.clone();
    	int n = 0;
    	while (BitsHD.incHD(val, min, max)) {
    		n++;
    		assertTrue(BitsHD.isLess(valPrev, val));
    		BitsHD.set(valPrev, val);
    	}
    	assertEquals(255, n);
    	assertTrue(BitsHD.isEq(min, val));
    }
    
    @Test
    public void testGetFilterBits() {
    	assertEquals(0, BitsHD.getFilterBits(new long[] {0}, new long[] {-1L}, 64));
    	assertEquals(64, BitsHD.getFilterBits(new long[] {-1L}, new long[] {-1L}, 64));
    	assertEquals(64, BitsHD.getFilterBits(new long[] {0}, new long[] {0}, 64));

    	assertEquals(0, BitsHD.getFilterBits(new long[] {0, 0}, new long[] {-1L,-1L}, 96));
    	assertEquals(96, BitsHD.getFilterBits(new long[] {0xFFFFFFFFL, -1L}, new long[] {-1L,-1L}, 96));
    	assertEquals(96, BitsHD.getFilterBits(new long[] {0, 0}, new long[] {0, 0}, 96));

    	assertEquals(0, BitsHD.getFilterBits(new long[] {0, 0}, new long[] {-1L, -1L}, 128));
    	assertEquals(128, BitsHD.getFilterBits(new long[] {-1L, -1L}, new long[] {-1L, -1L}, 128));
    	assertEquals(128, BitsHD.getFilterBits(new long[] {0, 0}, new long[] {0, 0}, 128));
}
    
    @Test
    public void testLess() {
    	assertTrue(BitsHD.isLess(new long[] {0, 1}, new long[] {1,0}));
    	assertTrue(BitsHD.isLess(new long[] {0, (-1L)}, new long[] {1,0}));
    	assertTrue(BitsHD.isLess(new long[] {0, 1}, new long[] {1,-1L}));
    	assertFalse(BitsHD.isLess(new long[] {1, 1}, new long[] {1,0}));
    	assertFalse(BitsHD.isLess(new long[] {1, 1}, new long[] {1,1}));
    }
    
    @Test
    public void testLessEq() {
    	assertTrue(BitsHD.isLessEq(new long[] {0, 1}, new long[] {1,0}));
    	assertTrue(BitsHD.isLessEq(new long[] {0, (-1L)}, new long[] {1,0}));
    	assertTrue(BitsHD.isLessEq(new long[] {0, 1}, new long[] {1,-1L}));
    	assertFalse(BitsHD.isLessEq(new long[] {1, 1}, new long[] {1,0}));
    	assertTrue(BitsHD.isLessEq(new long[] {1, 1}, new long[] {1,1}));
    }
    
	@Test
	public void testBinarySearch() {
		long[] ba = {0,1, 0,34, 0,43, 10,12, 100,255, 100,1000, 100,-1L, 101, 1};
		checkBinarySearch(ba, -1, 0, 0);
		checkBinarySearch(ba,  0, 0, 1);
		checkBinarySearch(ba, -2, 0, 2);
		checkBinarySearch(ba,  1, 0, 34);
		checkBinarySearch(ba, -3, 0, 40);
		checkBinarySearch(ba,  2, 0, 43);
		checkBinarySearch(ba, -4, 0, 45);
		checkBinarySearch(ba, -4, 3, 45);
		checkBinarySearch(ba, -4, 10, 4);
		checkBinarySearch(ba, -5, 11, 45);
		checkBinarySearch(ba, -9, 1000, 45);
		checkBinarySearch(ba,  3, 10, 12);
		checkBinarySearch(ba,  4, 100, 255);
		checkBinarySearch(ba, -6, 100, 999);
		checkBinarySearch(ba,  5, 100, 1000);
		checkBinarySearch(ba, -7, 100, 1001);
		checkBinarySearch(ba,  6, 100, (-1L));
		checkBinarySearch(ba, -8, 101, 0);
		checkBinarySearch(ba,  7, 101, 1);
		checkBinarySearch(ba, -9, 101, 2);
		checkBinarySearch(ba, -9, 102, 0);
		checkBinarySearch(ba, -9, (-1L), 0);
	}
	
	private void checkBinarySearch(long[] ba, int expectedPos, long ... key) {
		int i2 = BitsHD.binarySearch(ba, 0, ba.length/2, key, 128, 0);
		assertEquals(expectedPos, i2);
	}
	
}
