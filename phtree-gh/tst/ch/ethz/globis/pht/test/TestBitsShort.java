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

import ch.ethz.globis.pht.util.BitsShort;

/**
 * 
 * @author ztilmann
 *
 */
public class TestBitsShort {

	@Test
	public void testCopy1() {
		short[] s = newBA(0xFF, 0xFF);
		//short[] s = newBA(0b1111_1111, 0b1111_1111);  //TODO java 7
		short[] t = new short[2];
		BitsShort.copyBitsLeft(s, 0, t, 0, 32);
		check(t, 0xFF, 0xFF);
	}
	
	
	@Test
	public void testCopy2() {
		short[] s = newBA(0x0F, 0xF0);
		short[] t = new short[2];
		BitsShort.copyBitsLeft(s, 0, t, 0, 32);
		check(t, 0x0F, 0xF0);
	}
	
	
	@Test
	public void testCopy3() {
		short[] s = newBA(0x0F, 0xF000);
		short[] t = new short[2];
		BitsShort.copyBitsLeft(s, 12, t, 8, 16);
		check(t, 0xFF, 0);
	}
	
	
	@Test
	public void testCopy4() {
		short[] s = newBA(0x0F, 0xF000);
		short[] t = new short[2];
		BitsShort.copyBitsLeft(s, 12, t, 16, 16);
		check(t, 0, 0xFF00);
	}

	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopy5() {
		short[] s = newBA(0x00, 0x00);
		short[] t = newBA(0xFF, 0xFF00);
		BitsShort.copyBitsLeft(s, 12, t, 12, 8);
		check(t, 0xF0, 0x0F00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopy6() {
		short[] s = newBA(0xF0, 0x0F00);
		short[] t = newBA(0x0F, 0xF000);
		BitsShort.copyBitsLeft(s, 12, t, 12, 8);
		check(t, 0x00, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle1() {
		short[] s = newBA(0xAAAA, 0xAAAA);
		BitsShort.copyBitsLeft(s, 12, s, 11, 1);
		check(s, 0xAABA, 0xAAAA);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle2() {
		short[] s = newBA(0xAAAA, 0xAAAA);
		BitsShort.copyBitsLeft(s, 11, s, 12, 1);
		check(s, 0xAAA2, 0xAAAA);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle3a() {
		short[] s = newBA(0x0008, 0x00);
		short[] t = newBA(0x00, 0x00);
		BitsShort.copyBitsLeft(s, 12, t, 11, 1);
		check(t, 0x0010, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle3b() {
		short[] s = newBA(0xFFF7, 0xFFFF);
		short[] t = newBA(0xFFFF, 0xFFFF);
		BitsShort.copyBitsLeft(s, 12, t, 11, 1);
		check(t, 0xFFEF, 0xFFFF);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle4a() {
		short[] s = newBA(0x0010, 0x00);
		short[] t = newBA(0x00, 0x00);
		BitsShort.copyBitsLeft(s, 11, t, 12, 1);
		check(t, 0x08, 0x00);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopySingle4b() {
		short[] s = newBA(0xFFEF, 0xFFFF);
		short[] t = newBA(0xFFFF, 0xFFFF);
		BitsShort.copyBitsLeft(s, 11, t, 12, 1);
		check(t, 0xFFF7, 0xFFFF);
	}
	
	@Test
	public void testCopyShift1_OneByteToTwoByte() {
		short[] s = newBA(0xAAAA, 0x0AAA);
		BitsShort.copyBitsLeft(s, 12, s, 14, 4);
		check(s, 0xAAAA, 0x8AAA);
	}
	
	@Test
	public void testCopyShift1_TwoByteToOneByte() {
		short[] s = newBA(0xAAAA, 0xF5AA);
		BitsShort.copyBitsLeft(s, 14, s, 16, 4);
		check(s, 0xAAAA, 0xB5AA);
	}
	
	@Test
	public void testCopyLong1() {
		short[] s = newBA(0x000A, 0xAAAA, 0xAAAA, 0xAAAA);
		BitsShort.copyBitsLeft(s, 12, s, 17, 33);
		check(s, 0x000A, 0xD555, 0x5555, 0x6AAA);
	}
	
	@Test
	public void testCopySplitForwardA() {
		short[] s = newBA(0x000F, 0xF000);
		short[] t = newBA(0x0000, 0x0000);
		BitsShort.copyBitsLeft(s, 12, t, 13, 8);
		check(t, 0x0007, 0xF800);
	}
	
	@Test
	public void testCopySplitForwardB() {
		short[] s = newBA(0xFFF0, 0x0FFF);
		short[] t = newBA(0xFFFF, 0xFFFF);
		BitsShort.copyBitsLeft(s, 12, t, 13, 8);
		check(t, 0xFFF8, 0x07FF);
	}
	
	@Test
	public void testCopySplitBackwardA() {
		short[] s = newBA(0x000F, 0xF000);
		short[] t = newBA(0x0000, 0x0000);
		BitsShort.copyBitsLeft(s, 12, t, 11, 8);
		check(t, 0x001F, 0xE000);
	}
	
	@Test
	public void testCopySplitBackwardB() {
		short[] s = newBA(0xFFF0, 0x0FFF);
		short[] t = newBA(0xFFFF, 0xFFFF);
		BitsShort.copyBitsLeft(s, 12, t, 11, 8);
		check(t, 0xFFE0, 0x1FFF);
	}
	
	
	@Test
	public void testCopyLeftA() {
		short[] s = newBA(0xFFFF, 0x00, 0x00);
		BitsShort.copyBitsLeft(s, 16, s, 14, 30);
		check(s, 0xFFFC, 0x00);
	}
	
	
	@Test
	public void testCopyLeftB() {
		short[] s = newBA(0x00, 0xFFFF, 0xFFFF);
		BitsShort.copyBitsLeft(s, 16, s, 14, 30);
		check(s, 0x03, 0xFFFF);
	}
	
	@Test
	public void testCopyLeftBug1() {
		short[] s = newBA(-27705, 31758, -32768, 0x00);
		short[] t = newBA(-28416, 0x00, 0x00, 0x00);
//		System.out.println("l=" + BitsShort.toBinary(1327362106));
//		System.out.println("src=" + BitsShort.toBinary(s));
//		System.out.println("trg=" + BitsShort.toBinary(new long[]{-28416, 0x00, 0x1C77, 0xC0E8}, 16));
		BitsShort.copyBitsLeft(s, 7, t, 35, 27);
//		System.out.println("trg=" + BitsShort.toBinary(t));
		check(t, -28416, 0x00, 0x1C77, 0xC0E8);
	}
	
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopyRSingle2() {
		short[] s = newBA(0xAAAA, 0xAAAA);
		BitsShort.copyBitsRight(s, 11, s, 12, 1);
		check(s, 0xAAA2, 0xAAAA);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopyRSingle4a() {
		short[] s = newBA(0x0010, 0x0000);
		short[] t = newBA(0x0000, 0x0000);
		BitsShort.copyBitsRight(s, 11, t, 12, 1);
		check(t, 0x0008, 0x0000);
	}
	
	/**
	 * Check retain set bits.
	 */
	@Test
	public void testCopyRSingle4b() {
		short[] s = newBA(0xFFEF, 0xFFFF);
		short[] t = newBA(0xFFFF, 0xFFFF);
		BitsShort.copyBitsRight(s, 11, t, 12, 1);
		check(t, 0xFFF7, 0xFFFF);
	}
	
	@Test
	public void testCopyRShift1_OneByteToTwoByte() {
		System.err.println("WARNING: Test disabled: TestBitsShort.testCopyRShift1_OneByteToTwoByte()");
//		short[] s = newBA(0xAAAA, 0x0AAA);
//		BitsShort.copyBitsRight(s, 12, s, 14, 4);
//		check(s, 0xAAAA, 0x8AAA);
	}
	
	@Test
	public void testCopyRShift1_TwoByteToOneByte() {
		System.err.println("WARNING: Test disabled: TestBitsShort.testCopyRShift1_TwoByteToOneByte()");
//		short[] s = newBA(0xAAAA, 0xF5AA);
//		BitsShort.copyBitsRight(s, 14, s, 16, 4);
//		check(s, 0xAAAA, 0xB5AA);
	}
	

	@Test
	public void testInsert1_OneByteToTwoByte() {
		short[] s = newBA(0xAAAA, 0xAAA0);
		BitsShort.insertBits(s, 12, 5);
		check(s, 0xAAAA, 0xD555);
	}
	
	@Test
	public void testInsert1_TwoByteToOneByte() {
		short[] s = newBA(0xAAAA, 0xAAA0);
		BitsShort.insertBits(s, 12, 3);
		check(s, 0xAAAB, 0x5554);
	}
	
	@Test
	public void testInsert2() {
		short[] s = newBA(0xAAAA, 0xAAA0);
		BitsShort.insertBits(s, 16, 1);
		check(s, 0xAAAA, 0xD550);
	}
	
	@Test
	public void testInsert3() {
		short[] s = newBA(0xAAAA, 0xAAA0);
		BitsShort.insertBits(s, 15, 1);
		check(s, 0xAAAA, 0x5550);
	}
	
	@Test
	public void testInsert4() {
		short[] s = newBA(0xAAAA, 0x5555);
		BitsShort.insertBits(s, 0, 16);
		check(s, 0xAAAA, 0xAAAA);
	}
	
	@Test
	public void testInsert5() {
		short[] s = newBA(0xAAAA, 0xAA55);
		BitsShort.insertBits(s, 16, 16);
		check(s, 0xAAAA, 0xAA55);
	}
	
	@Test
	public void testInsert_Bug1() {
		short[] s = newBA(0xAAAA, 0xAAAA, 0xAAAA, 0xAAAA);
		BitsShort.insertBits(s, 49, 5);
		check(s, 0xAAAA, 0xAAAA, 0xAAAA, 0xA955);
	}
	
	@Test
	public void testInsert_Bug2() {
		short[] s = newBA(0xAAAA, 0xAAAA, 0xAAAA, 0xAAAA);
		BitsShort.insertBits(s, 32, 19);
		check(s, 0xAAAA, 0xAAAA, 0xAAAA, 0xB555);
	}
	
	@Test
	public void testInsert_Bug2b() {
		short[] s = newBA(0xAAAA, 0x0000);
		BitsShort.insertBits(s, 0, 19);
		check(s, 0xAAAA, 0x1555);
	}
	
	@Test
	public void testInsert_Bug2c() {
		short[] s = newBA(0xAAAA);
		BitsShort.insertBits(s, 0, 3);
		check(s, 0xB555);
	}
	
	@Test
	public void testInsertRandom() {
		Random r = new Random();
		for (int i = 0; i < 1000; i++) {
			short[] s = newBA(0xAAAA, 0xAAAA, 0xAAAA, 0xAAAA);
			short[] x = s.clone();
			int start = r.nextInt(4*16);
			int maxIns = 4*16-start;
			int ins = r.nextInt(maxIns);
			BitsShort.insertBits(s, start, ins);
			BitsShort.insertBits1(x, start, ins);
//			System.out.println("s=" + start + " i=" + ins);
			check(x, s);
		}
	}
	

	@Test
	public void testCopyLeftRandom() {
		Random rnd = new Random();
		int BA_LEN = 6;
		short[] ba = new short[BA_LEN];
		for (int i1 = 0; i1 < 1000000; i1++) {
			//populate
			for (int i2 = 0; i2 < ba.length; i2++) {
				ba[i2] = (short) rnd.nextInt();
			}
			
			//clone
			short[] ba1 = Arrays.copyOf(ba, ba.length);
			short[] ba2 = Arrays.copyOf(ba, ba.length);
			
			int start = rnd.nextInt(8) + 8;  //start somewhere in fourth short
			int nBits = rnd.nextInt(8 * 3); //remove up to two shorts 
			//compute
			//System.out.println("i=" + i1 + " start=" + start + " nBits=" + nBits);
			BitsShort.copyBitsLeft(ba1, start+nBits, ba1, start, ba1.length*8-start-nBits);
			//compute backup
			removetBitsSlow(ba2, start, nBits);
			
			//check
			if (!Arrays.equals(ba2, ba1)) {
				System.out.println("i=" + i1 + " start=" + start + " nBits=" + nBits);
				System.out.println("ori. = " + BitsShort.toBinary(ba));
				System.out.println("act. = " + BitsShort.toBinary(ba1));
				System.out.println("exp. = " + BitsShort.toBinary(ba2));
				System.out.println("ori. = " + Arrays.toString(ba));
				System.out.println("act. = " + Arrays.toString(ba1));
				System.out.println("exp. = " + Arrays.toString(ba2));
				fail();
			}
		}
	}
	
	@Test
	public void testCopyRightRandom() {
		System.err.println("WARNING: Test disabled: TestBitsShort.testCopyRightRandom()");
//		Random rnd = new Random();
//		int BA_LEN = 6;
//		short[] ba = new short[BA_LEN];
//		for (int i1 = 0; i1 < 1000000; i1++) {
//			//populate
//			for (int i2 = 0; i2 < ba.length; i2++) {
//				ba[i2] = (short) rnd.nextInt();
//			}
//			
//			//clone
//			short[] ba1 = Arrays.copyOf(ba, ba.length);
//			short[] ba2 = Arrays.copyOf(ba, ba.length);
//			
//			int start = rnd.nextInt(8) + 8;  //start somewhere in fourth short
//			int nBits = rnd.nextInt(8 * 3); //remove up to three shorts 
//			//compute
//			//System.out.println("i=" + i1 + " start=" + start + " nBits=" + nBits);
//			BitsShort.copyBitsRight(ba1, start, ba1, start+nBits, ba1.length*8-start-nBits);
//			//compute backup
//			insertBitsSlow(ba2, start, nBits);
//			
//			//check
//			if (!Arrays.equals(ba2, ba1)) {
//				System.out.println("i=" + i1 + " start=" + start + " nBits=" + nBits);
//				System.out.println("ori. = " + BitsShort.toBinary(ba));
//				System.out.println("act. = " + BitsShort.toBinary(ba1));
//				System.out.println("exp. = " + BitsShort.toBinary(ba2));
//				System.out.println("ori. = " + Arrays.toString(ba));
//				System.out.println("act. = " + Arrays.toString(ba1));
//				System.out.println("exp. = " + Arrays.toString(ba2));
//				fail();
//			}
//		}
	}
	
	
//	private void insertBitsSlow(short[] ba, int start, int nBits) {
//		int bitsToShift = ba.length*8 - start - nBits;
//		for (int i = 0; i < bitsToShift; i++) {
//			int srcBit = ba.length*8 - nBits - i - 1;
//			int trgBit = ba.length*8 - i - 1;
//			BitsShort.setBit(ba, trgBit, BitsShort.getBit(ba, srcBit));
//		}
//
//	}
	
	private void removetBitsSlow(short[] ba, int start, int nBits) {
		int bitsToShift = ba.length*8 - start - (nBits);
		for (int i = 0; i < bitsToShift; i++) {
			int srcBit = start + (nBits) + i;
			int trgBit = start + i;
			BitsShort.setBit(ba, trgBit, BitsShort.getBit(ba, srcBit));
		}
	}
	
	
	@Test
	public void testBinarySearch() {
		short[] ba = {1, 34, 43, 123, 255, 1000};
		checkBinarySearch(ba, 0);
		checkBinarySearch(ba, 1);
		checkBinarySearch(ba, 2);
		checkBinarySearch(ba, 34);
		checkBinarySearch(ba, 40);
		checkBinarySearch(ba, 43);
		checkBinarySearch(ba, 45);
		checkBinarySearch(ba, 123);
		checkBinarySearch(ba, 255);
		checkBinarySearch(ba, 999);
		checkBinarySearch(ba, 1000);
		checkBinarySearch(ba, 1001);
	}
	
	private void checkBinarySearch(short[] ba, int key) {
		int i1 = Arrays.binarySearch(ba, (short)key);
		int i2 = BitsShort.binarySearch(ba, 0, ba.length, key, 16);
		assertEquals(i1, i2);
	}
	
	
	private void check(short[] t, long ... ints) {
		for (int i = 0; i < ints.length; i++) {
			assertEquals("i=" + i + " | " + BitsShort.toBinary(ints, 16) + " / " + 
					BitsShort.toBinary(t), (short)ints[i], (short)t[i]);
		}
	}

	private void check(short[] t, short[] s) {
		for (int i = 0; i < s.length; i++) {
			assertEquals("i=" + i + " | " + BitsShort.toBinary(s) + " / " + 
					BitsShort.toBinary(t), (short)s[i], (short)t[i]);
		}
	}


	private short[] newBA(int...ints) {
		short[] ba = new short[ints.length];
		for (int i = 0; i < ints.length; i++) {
			ba[i] = (short) ints[i];
		}
		return ba;
	}
}
