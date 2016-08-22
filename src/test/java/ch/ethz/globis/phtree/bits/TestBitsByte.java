/*
 * Copyright 2012-2013 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.bits;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import ch.ethz.globis.phtree.util.Bits;
import ch.ethz.globis.phtree.util.BitsByte;

/**
 * 
 * @author ztilmann
 *
 */
public class TestBitsByte {

	@Test
	public void testCopy1() {
		//byte[] s = newBA(0xFF, 0xFF);
		byte[] s = newBA(0b1111_1111, 0b1111_1111); 
		byte[] t = new byte[2];
		BitsByte.copyBitsLeft(s, 0, t, 0, 16);
		check(t, 0xFF, 0xFF);
	}
	
	
	@Test
	public void testCopy2() {
		byte[] s = newBA(0x0F, 0xF0);
		byte[] t = new byte[2];
		BitsByte.copyBitsLeft(s, 0, t, 0, 16);
		check(t, 0x0F, 0xF0);
	}
	
	
	@Test
	public void testCopy3() {
		byte[] s = newBA(0x0F, 0xF0);
		byte[] t = new byte[2];
		BitsByte.copyBitsLeft(s, 4, t, 0, 8);
		check(t, 0xFF, 0);
	}
	
	
	@Test
	public void testCopy4() {
		byte[] s = newBA(0x0F, 0xF0);
		byte[] t = new byte[2];
		BitsByte.copyBitsLeft(s, 4, t, 8, 8);
		check(t, 0, 0xFF);
	}

	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopy5() {
		byte[] s = newBA(0x00, 0x00);
		byte[] t = newBA(0xFF, 0xFF);
		BitsByte.copyBitsLeft(s, 4, t, 4, 8);
		check(t, 0xF0, 0x0F);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopy6() {
		byte[] s = newBA(0xF0, 0x0F);
		byte[] t = newBA(0x0F, 0xF0);
		BitsByte.copyBitsLeft(s, 4, t, 4, 8);
		check(t, 0x00, 0x0);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle1() {
		byte[] s = newBA(0xAA, 0xAA);
		BitsByte.copyBitsLeft(s, 4, s, 3, 1);
		check(s, 0xBA, 0xAA);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle2() {
		byte[] s = newBA(0xAA, 0xAA);
		BitsByte.copyBitsLeft(s, 3, s, 4, 1);
		check(s, 0xA2, 0xAA);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle3a() {
		byte[] s = newBA(0x08, 0x00);
		byte[] t = newBA(0x00, 0x00);
		BitsByte.copyBitsLeft(s, 4, t, 3, 1);
		check(t, 0x10, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle3b() {
		byte[] s = newBA(0xF7, 0xFF);
		byte[] t = newBA(0xFF, 0xFF);
		BitsByte.copyBitsLeft(s, 4, t, 3, 1);
		check(t, 0xEF, 0xFF);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle4a() {
		byte[] s = newBA(0x10, 0x00);
		byte[] t = newBA(0x00, 0x00);
		BitsByte.copyBitsLeft(s, 3, t, 4, 1);
		check(t, 0x08, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle4b() {
		byte[] s = newBA(0xEF, 0xFF);
		byte[] t = newBA(0xFF, 0xFF);
		BitsByte.copyBitsLeft(s, 3, t, 4, 1);
		check(t, 0xF7, 0xFF);
	}
	
	@Test
	public void testCopyShift1_OneByteToTwoByte() {
		byte[] s = newBA(0xAA, 0x0A);
		BitsByte.copyBitsLeft(s, 4, s, 6, 4);
		check(s, 0xAA, 0x8A);
	}
	
	@Test
	public void testCopyShift1_TwoByteToOneByte() {
		byte[] s = newBA(0xAA, 0xF5);
		BitsByte.copyBitsLeft(s, 6, s, 8, 4);
		check(s, 0xAA, 0xB5);
	}
	
	@Test
	public void testCopyLong1() {
		byte[] s = newBA(0x0A, 0xAA, 0xAA, 0xAA);
		BitsByte.copyBitsLeft(s, 4, s, 9, 17);
		check(s, 0x0A, 0xD5, 0x55, 0x6A);
	}
	
	@Test
	public void testCopySplitForwardA() {
		byte[] s = newBA(0x0F, 0xF0);
		byte[] t = newBA(0x00, 0x00);
		BitsByte.copyBitsLeft(s, 4, t, 5, 8);
		check(t, 0x07, 0xF8);
	}
	
	@Test
	public void testCopySplitForwardB() {
		byte[] s = newBA(0xF0, 0x0F);
		byte[] t = newBA(0xFF, 0xFF);
		BitsByte.copyBitsLeft(s, 4, t, 5, 8);
		check(t, 0xF8, 0x07);
	}
	
	@Test
	public void testCopySplitBackwardA() {
		byte[] s = newBA(0x0F, 0xF0);
		byte[] t = newBA(0x00, 0x00);
		BitsByte.copyBitsLeft(s, 4, t, 3, 8);
		check(t, 0x1F, 0xE0);
	}
	
	@Test
	public void testCopySplitBackwardB() {
		byte[] s = newBA(0xF0, 0x0F);
		byte[] t = newBA(0xFF, 0xFF);
		BitsByte.copyBitsLeft(s, 4, t, 3, 8);
		check(t, 0xE0, 0x1F);
	}
	
	
	@Test
	public void testCopyLeftA() {
		byte[] s = newBA(0xFF, 0x00, 0x00);
		BitsByte.copyBitsLeft(s, 8, s, 6, 14);
		check(s, 0xFC, 0x00);
	}
	
	
	@Test
	public void testCopyLeftB() {
		byte[] s = newBA(0x00, 0xFF, 0xFF);
		BitsByte.copyBitsLeft(s, 8, s, 6, 14);
		check(s, 0x03, 0xFF);
	}
	
	@Test
	public void testCopyLeftRnd1() {
		byte[] s = newBA(48, 113, 71, 70);
		BitsByte.copyBitsLeft(s, 8, s, 7, 24);
		check(s, 48, -30, -114, -116);
	}
	
	@Test
	public void testCopyLeftRnd2() {
		byte[] s = newBA(48, 113, 0xFF);
		BitsByte.copyBitsLeft(s, 8, s, 7, 16);
		check(s, 48, -29, 0xFF);
	}
	
	@Test
	public void testCopyLeftRnd3() {
		byte[] s = newBA(95, 9, 112);
		BitsByte.copyBitsLeft(s, 7+6, s, 7, 3*8-7-6);
		check(s, 94, 92, 48);
	}

	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopyRSingle2() {
		byte[] s = newBA(0xAA, 0xAA);
		BitsByte.copyBitsRight(s, 3, s, 4, 1);
		check(s, 0xA2, 0xAA);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopyRSingle4a() {
		byte[] s = newBA(0x10, 0x00);
		byte[] t = newBA(0x00, 0x00);
		BitsByte.copyBitsRight(s, 3, t, 4, 1);
		check(t, 0x08, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopyRSingle4b() {
		byte[] s = newBA(0xEF, 0xFF);
		byte[] t = newBA(0xFF, 0xFF);
		BitsByte.copyBitsRight(s, 3, t, 4, 1);
		check(t, 0xF7, 0xFF);
	}
	
	@Test
	public void testCopyLeftRandom() {
		Random rnd = new Random();
		int BA_LEN = 6;
		byte[] ba = new byte[BA_LEN];
		for (int i1 = 0; i1 < 1000000; i1++) {
			//populate
			for (int i2 = 0; i2 < ba.length; i2++) {
				ba[i2] = (byte) rnd.nextInt();
			}
			
			//clone
			byte[] ba1 = Arrays.copyOf(ba, ba.length);
			byte[] ba2 = Arrays.copyOf(ba, ba.length);
			
			int start = rnd.nextInt(8) + 8;  //start somewhere in fourth byte
			int nBits = rnd.nextInt(8 * 3); //remove up to two bytes 
			//compute
			//System.out.println("i=" + i1 + " start=" + start + " nBits=" + nBits);
			BitsByte.copyBitsLeft(ba1, start+nBits, ba1, start, ba1.length*8-start-nBits);
			//compute backup
			removetBitsSlow(ba2, start, nBits);
			
			//check
			if (!Arrays.equals(ba2, ba1)) {
				System.out.println("i=" + i1 + " start=" + start + " nBits=" + nBits);
				System.out.println("ori. = " + BitsByte.toBinary(ba));
				System.out.println("act. = " + BitsByte.toBinary(ba1));
				System.out.println("exp. = " + BitsByte.toBinary(ba2));
				System.out.println("ori. = " + Arrays.toString(ba));
				System.out.println("act. = " + Arrays.toString(ba1));
				System.out.println("exp. = " + Arrays.toString(ba2));
				fail();
			}
		}
	}
	
//	private void insertBitsSlow(byte[] ba, int start, int nBits) {
//		int bitsToShift = ba.length*8 - start - nBits;
//		for (int i = 0; i < bitsToShift; i++) {
//			int srcBit = ba.length*8 - nBits - i - 1;
//			int trgBit = ba.length*8 - i - 1;
//			BitsByte.setBit(ba, trgBit, BitsByte.getBit(ba, srcBit));
//		}
//
//	}
	
	private void removetBitsSlow(byte[] ba, int start, int nBits) {
		int bitsToShift = ba.length*8 - start - (nBits);
		for (int i = 0; i < bitsToShift; i++) {
			int srcBit = start + (nBits) + i;
			int trgBit = start + i;
			BitsByte.setBit(ba, trgBit, BitsByte.getBit(ba, srcBit));
		}
	}
	
	
	@Test
	public void testBinarySearch() {
		byte[] ba = {1, 34, 43, 123};
		checkBinarySearch(ba, 0);
		checkBinarySearch(ba, 1);
		checkBinarySearch(ba, 2);
		checkBinarySearch(ba, 34);
		checkBinarySearch(ba, 40);
		checkBinarySearch(ba, 43);
		checkBinarySearch(ba, 45);
		checkBinarySearch(ba, 123);
		checkBinarySearch(ba, 124);
	}
	
	private void checkBinarySearch(byte[] ba, int key) {
		int i1 = Arrays.binarySearch(ba, (byte)key);
		int i2 = BitsByte.binarySearch(ba, 0, ba.length, key, 8);
		assertEquals(i1, i2);
	}
	
	
	private void check(byte[] t, long ... ints) {
		for (int i = 0; i < ints.length; i++) {
			assertEquals("i=" + i + " " + Bits.toBinary(ints, 8) + " / " + 
					BitsByte.toBinary(t), (byte)ints[i], (byte)t[i]);
		}
	}


	private byte[] newBA(int...ints) {
		byte[] ba = new byte[ints.length];
		for (int i = 0; i < ints.length; i++) {
			ba[i] = (byte) ints[i];
		}
		return ba;
	}
}
