/*
 * Copyright 2011 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import ch.ethz.globis.pht.util.Bits;
import ch.ethz.globis.pht.util.BitsInt;

/**
 * 
 * @author ztilmann
 *
 */
public class TestBitsIntRemove {

	private static final int BITS = 32;
	
	@Test
	public void testCopy1() {
		int[] s = newBA(0xFFFF, 0xFFFF);
		BitsInt.removeBits(s, 0, 64);
		check(s, 0xFFFF, 0xFFFF);
	}
	
	
	@Test
	public void testCopy2() {
		int[] s = newBA(0x0F0F, 0xF0F0);
		BitsInt.removeBits(s, 0, 64);
		check(s, 0x0F0F, 0xF0F0);
	}
	
	
	@Test
	public void testCopy3() {
		int[] s = newBA(0x0F, 0xF0000000);
		BitsInt.removeBits(s, 24, 4);
		check(s, 0xFF, 0);
	}
	
	
	@Test
	public void testCopy4() {
		int[] s = newBA(0x0F, 0xFF000000);
		BitsInt.removeBits(s, 32, 4);
		checkIgnoreTrailing(4, s, 0xF, 0xF0000000);
	}

	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopy5() {
		int[] s = newBA(0xFF, 0xFF000000);
		BitsInt.removeBits(s, 28, 8);
		check(s, 0xFF, 0x00000000);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopy6() {
		int[] s = newBA(0x0F, 0xF0000000);
		BitsInt.removeBits(s, 28, 8);
		check(s, 0x00, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle1() {
		int[] s = newBA(0xAAAAAAAA, 0xAAAAAAAA);
		BitsInt.removeBits(s, 27, 1);
		check(s, 0xAAAAAAB5, 0x55555554);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle2() {
		int[] s = newBA(0xAAAAAAAA, 0xAAAAAAAA);
		BitsInt.removeBits(s, 28, 1);
		check(s, 0xAAAAAAA5, 0x55555554);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle3a() {
		int[] s = newBA(0x0008, 0x00);
		BitsInt.removeBits(s, 27, 1);
		check(s, 0x0010, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle3b() {
		int[] s = newBA(0xFFFFFFF7, 0xFFFFFFFF);
		BitsInt.removeBits(s, 27, 1);
		checkIgnoreTrailing(1, s, 0xFFFFFFEF, 0xFFFFFFFE);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle4a() {
		int[] s = newBA(0x0010, 0x00);
		BitsInt.removeBits(s, 28, 1);
		check(s, 0x010, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle4b() {
		int[] s = newBA(0xFFFFFFEF, 0xFFFFFFFF);
		BitsInt.removeBits(s, 27, 1);
		checkIgnoreTrailing(1, s, 0xFFFFFFFF, 0xFFFFFFFE);
	}
	
	@Test
	public void testCopyShift1_OneByteToTwoByte() {
		int[] s = newBA(0xAAAAAAAA, 0x0AAAAAAA);
		BitsInt.removeBits(s, 28, 4);
		checkIgnoreTrailing(4, s, 0xAAAAAAA0, 0xAAAAAAAA);
	}
	
	@Test
	public void testCopyShift1_TwoByteToOneByte() {
		int[] s = newBA(0xAAAAAAAA, 0xF5AAAAAA);
		BitsInt.removeBits(s, 30, 4);
		checkIgnoreTrailing(4, s, 0xAAAAAAAB, 0x5AAAAAAA);
	}
	
	@Test
	public void testCopyLong1() {
		int[] s = newBA(0x000A, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA);
		BitsInt.removeBits(s, 28, 5);
		checkIgnoreTrailing(5, s, 0x0005, 0x55555555, 0x55555555, 0x55555555);
	}
	
	@Test
	public void testCopySplitForwardA() {
		int[] s = newBA(0x000F, 0xF0000000);
		BitsInt.removeBits(s, 29, 1);
		check(s, 0x000F, 0xE0000000);
	}
	
	@Test
	public void testCopySplitForwardB() {
		int[] s = newBA(0xFFFFFFF0, 0x0FFFFFFF);
		BitsInt.removeBits(s, 29, 1);
		checkIgnoreTrailing(1, s, 0xFFFFFFF0, 0x1FFFFFFF);
	}
	
	@Test
	public void testCopySplitBackwardA() {
		int[] s = newBA(0x000F, 0xF0000000);
		BitsInt.removeBits(s, 26, 1);
		check(s, 0x001F, 0xE0000000);
	}
	
	@Test
	public void testCopySplitBackwardB() {
		int[] s = newBA(0xFFFFFFF0, 0x0FFFFFFF);
		BitsInt.removeBits(s, 26, 1);
		checkIgnoreTrailing(1, s, 0xFFFFFFE0, 0x1FFFFFFF);
	}
	
	
	@Test
	public void testCopyLeftA() {
		int[] s = newBA(0xFFFFFFFF, 0x00, 0x00);
		BitsInt.removeBits(s, 30, 2);
		checkIgnoreTrailing(2, s, 0xFFFFFFFC, 0x00, 0x00);
	}
	
	
	@Test
	public void testCopyLeftB() {
		int[] s = newBA(0x00, 0xFFFFFFFF, 0xFFFFFFFF);
		BitsInt.removeBits(s, 30, 2);
		checkIgnoreTrailing(2, s, 0x03, 0xFFFFFFFF, 0xFFFFFFFF);
	}
	
	@Test
	public void testRemoveRandom() {
		Random r = new Random(0);
		int N = 10*1000*1000;
		int[][] data = new int[N][];
		int[][] data2 = new int[N][];
		int[] start = new int[N];
		int[] del = new int[N];
		long t1 = System.currentTimeMillis();
		for (int i = 0; i < N; i++) {
			int LEN = r.nextInt(14)+1;
			data[i] = newBaPattern(LEN, 0xA5A5A5A5);
			data2[i] = data[i].clone();
			start[i] = r.nextInt(LEN*32);
			int maxIns = LEN*32-start[i];
			del[i] = r.nextInt(maxIns);
		}
		long t2 = System.currentTimeMillis();
//		System.out.println("prepare: " + (t2-t1));
		t1 = System.currentTimeMillis();
		for (int i = 0; i < N; i++) {
			BitsInt.removeBits(data[i], start[i], del[i]);
			//This is the old version
			BitsInt.removeBits0(data2[i], start[i], del[i]);
//			System.out.println("s=" + start + " i=" + ins);
//			System.out.println("x=" + BitsInt.toBinary(x));
//			System.out.println("s=" + BitsInt.toBinary(s));
			checkIgnoreTrailingBits(del[i], data2[i], data[i]);
		}
		t2 = System.currentTimeMillis();
//		System.out.println("shift: " + (t2-t1));
//		System.out.println("n=" + BitsInt.getStats());
	}
	
	@Test
	public void testCopyLeftRandom() {
		Random rnd = new Random();
		int BA_LEN = 6;
		int[] ba = new int[BA_LEN];
		for (int i1 = 0; i1 < 1000000; i1++) {
			//populate
			for (int i2 = 0; i2 < ba.length; i2++) {
				ba[i2] = rnd.nextInt();
			}
			
			//clone
			int[] ba1 = Arrays.copyOf(ba, ba.length);
			int[] ba2 = Arrays.copyOf(ba, ba.length);
			
			int start = rnd.nextInt(8) + 8;  //start somewhere in fourth short
			int nBits = rnd.nextInt(8 * 3); //remove up to two shorts 
			//compute
			//System.out.println("i=" + i1 + " start=" + start + " nBits=" + nBits);
			BitsInt.copyBitsLeft(ba1, start+nBits, ba1, start, ba1.length*BITS-start-nBits);
			//compute backup
			removetBitsSlow(ba2, start, nBits);
			
			//check
			if (!Arrays.equals(ba2, ba1)) {
				System.out.println("i=" + i1 + " start=" + start + " nBits=" + nBits);
				System.out.println("ori. = " + BitsInt.toBinary(ba));
				System.out.println("act. = " + BitsInt.toBinary(ba1));
				System.out.println("exp. = " + BitsInt.toBinary(ba2));
				System.out.println("ori. = " + Arrays.toString(ba));
				System.out.println("act. = " + Arrays.toString(ba1));
				System.out.println("exp. = " + Arrays.toString(ba2));
				fail();
			}
		}
	}
	
	private void removetBitsSlow(int[] ba, int start, int nBits) {
		int bitsToShift = ba.length*BITS - start - (nBits);
		for (int i = 0; i < bitsToShift; i++) {
			int srcBit = start + (nBits) + i;
			int trgBit = start + i;
			BitsInt.setBit(ba, trgBit, BitsInt.getBit(ba, srcBit));
		}
	}
	
	private void check(int[] t, long ... expected) {
		for (int i = 0; i < expected.length; i++) {
			assertEquals("i=" + i + " | " + BitsInt.toBinary(expected, 32) + " / " + 
					BitsInt.toBinary(t), expected[i], t[i]);
		}
	}

	private void checkIgnoreTrailing(int nTrailingBits, int[] t, int ... expected) {
		for (int i = 0; i < expected.length; i++) {
			if (i == expected.length-1) {
				int mask = 0xFFFFFFFF << nTrailingBits;
//				System.out.println("c-mask: "+ Bits.toBinary(mask));
				t[i] &= mask;
				expected[i] &= mask;
			}
//			System.out.println("i=" + i + " \nex= " + BitsInt.toBinary(expected, 32) + " \nac= " + 
//					BitsInt.toBinary(t));
			assertEquals("i=" + i + " | " + BitsInt.toBinary(expected, 32) + " / " + 
					BitsInt.toBinary(t), expected[i], t[i]);
		}
	}

	private void checkIgnoreTrailingBits(int nTrailingBits, int[] t, int[] s) {
		int bytesToTest = s.length-nTrailingBits/32;
		for (int i = 0; i < bytesToTest; i++) {
			//this makes it much faster!
			if (s[i] != t[i]) {
				if (i == bytesToTest-1) {
					//ignore trailing bits
					int localBits = nTrailingBits%32;
					int mask = 0xFFFFFFFF<<localBits;
					if ((s[i]&mask) == (t[i]&mask)) {
						//ignore
						continue;
					}
				}
				assertEquals("i=" + i + " | " + BitsInt.toBinary(s) + " / " + 
						BitsInt.toBinary(t), s[i], t[i]);
			}
		}
	}

	private int[] newBA(int...ints) {
		int[] ba = new int[ints.length];
		for (int i = 0; i < ints.length; i++) {
			ba[i] = ints[i];
		}
		return ba;
	}
	
	private int[] newBaPattern(int n, int pattern) {
		int[] ba = new int[n];
		for (int i = 0; i < n; i++) {
			ba[i] = pattern;
		}
		return ba;
	}
}
