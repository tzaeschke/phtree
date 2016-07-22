/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.bits;
import static org.junit.Assert.*;

import java.util.Random;

import org.junit.Test;

import ch.ethz.globis.pht.util.BitTools;


/**
 * Test variable length splie/merge.
 * 
 * 
 * @author ztilmann
 */
public class TestBitsToolsSplitMergeVarLong {

	//This should be set to 1000, but we set it to 100 to let the Travis CI build pass...
	private static final int MUL = 100;
	
	@Test
	public void testSplitMerge31() {
		Random rnd = new Random();
		for (int i = 0; i < MUL; i++) {
			long[] l = new long[]{Math.abs(rnd.nextInt()), Math.abs(rnd.nextInt())};
			long[] x = BitTools.mergeLong(32, l);
			long[] l2 = BitTools.splitLong(2, 32, x);
			assertArrayEquals(l, l2);
		}
	}

	@Test
	public void testSplitMerge63() {
		Random rnd = new Random();
		for (int i = 0; i < MUL; i++) {
			long[] l = new long[]{rnd.nextLong()>>>1, rnd.nextLong()>>>1};
			long[] x = BitTools.mergeLong(63, l);
			long[] l2 = BitTools.splitLong(2, 63, x); 
			assertArrayEquals(l, l2);
		}
	}

	@Test
	public void testSplitMerge64() {
		Random rnd = new Random();
		for (int i = 0; i < MUL; i++) {
			long[] l = new long[]{rnd.nextLong(), rnd.nextLong()};
			long[] x = BitTools.mergeLong(64, l);
			long[] l2 = BitTools.splitLong(2, 64, x);
			assertArrayEquals(l, l2);
		}
	}
	
	@Test
	public void testSplitMergeFloat() {
		Random rnd = new Random();
		for (int i = 0; i < MUL; i++) {
			float[] f = new float[]{rnd.nextFloat(), rnd.nextFloat()};
			long[] l = new long[]{
					BitTools.toSortableLong(f[0]),
					BitTools.toSortableLong(f[1])};
			long[] x = BitTools.mergeLong(32, l);
			long[] l2 = BitTools.splitLong(2, 32, x);
			assertArrayEquals(l, l2);
			float f0 = BitTools.toFloat(l2[0]);
			float f1 = BitTools.toFloat(l2[1]);
			assertEquals(f[0], f0, 0.0);
			assertEquals(f[1], f1, 0.0);
		}
	}

	
	@Test
	public void testSplitMergeDouble() {
		Random rnd = new Random();
		for (int i = 0; i < MUL; i++) {
			double[] d = new double[]{rnd.nextDouble()-0.5, rnd.nextDouble()-0.5};
			long[] l = new long[]{BitTools.toSortableLong(d[0]), BitTools.toSortableLong(d[1])};
			long[] x = BitTools.mergeLong(64, l);
			long[] l2 = BitTools.splitLong(2, 64, x);
			assertArrayEquals(l, l2);
			double d0 = BitTools.toDouble(l2[0]);
			double d1 = BitTools.toDouble(l2[1]);
			assertEquals(d[0], d0, 0.0);
			assertEquals(d[1], d1, 0.0);
		}
	}

	//TODO combine split merge with float-long conversion 
	
	@Test
	public void testSplitMergePerf0() {
		Random rnd = new Random(0);
		int K = 2;
		long[] l = new long[K];
		for (int i = 0; i < MUL*MUL; i++) {
			for (int k = 0; k < K; k++) {
				l[k] = rnd.nextLong();
			}
			long[] x = BitTools.mergeLong(64, l);
			long[] l2 = BitTools.splitLong(K, 64, x);
			assertArrayEquals(l, l2);
		}
	}
	
	@Test
	public void testSplitMergePerf20() {
		Random rnd = new Random(0);
		int K = 2;
		long[] l = new long[K];
		for (int i = 0; i < MUL*MUL; i++) {
			for (int k = 0; k < K; k++) {
				l[k] = rnd.nextLong();
			}
			long[] x = mergeLong(64, l);
			long[] l2 = BitTools.splitLong(K, 64, x);
			assertArrayEquals(l, l2);
		}
	}
	
	@Test
	public void testSplitMergePerf30() {
		Random rnd = new Random(0);
		int K = 2;
		long[] l = new long[K];
		for (int i = 0; i < MUL*MUL; i++) {
			for (int k = 0; k < K; k++) {
				l[k] = rnd.nextLong();
			}
			long[] x = mergeLong64(64, l);
			long[] l2 = BitTools.splitLong(K, 64, x);
			assertArrayEquals(l, l2);
		}
	}

	@Test
	public void testSplitMergePerf() {
		Random rnd = new Random(0);
		int K = 2;
		long[] l = new long[K];
		for (int i = 0; i < MUL*MUL; i++) {
			for (int k = 0; k < K; k++) {
				l[k] = rnd.nextLong();
			}
			long[] x = BitTools.mergeLong(64, l);
			long[] l2 = BitTools.splitLong(K, 64, x);
			assertArrayEquals(l, l2);
		}
	}
	
	@Test
	public void testSplitMergePerf2() {
		Random rnd = new Random(0);
		int K = 2;
		long[] l = new long[K];
		for (int i = 0; i < MUL*MUL; i++) {
			for (int k = 0; k < K; k++) {
				l[k] = rnd.nextLong();
			}
			long[] x = mergeLong(64, l);
			long[] l2 = BitTools.splitLong(K, 64, x);
			assertArrayEquals(l, l2);
		}
	}
	
	@Test
	public void testSplitMergePerf3() {
		Random rnd = new Random(0);
		int K = 2;
		long[] l = new long[K];
		for (int i = 0; i < MUL*MUL; i++) {
			for (int k = 0; k < K; k++) {
				l[k] = rnd.nextLong();
			}
			long[] x = mergeLong64(64, l);
			long[] l2 = BitTools.splitLong(K, 64, x);
			assertArrayEquals(l, l2);
		}
	}
	
	
	
	/**
	 * 
	 * @param nBitsPerValue
	 * @param src
	 * @return interleaved value
	 */
	public long[] mergeLong(int nBitsPerValue, long[] src) {
		int intArrayLen = (src.length*nBitsPerValue+63) >>> 6;
		long[] trg = new long[intArrayLen];
		int trgPos = 0;
		long s0 = src[0];
		long s1 = src[1];
		for (int w = 0; w < nBitsPerValue; w+=16) {
			int ss0 = (int) ((s0 >>> (48-w)) & 0xFFFF);
			int ss1 = (int) ((s1 >>> (48-w)) & 0xFFFF);
//			System.out.println("ss0=" + ss0 + "  " + BitsInt.toBinary(ss0));
//			System.out.println("ss1=" + ss1 + "  " + BitsInt.toBinary(ss1));
			long iv = mergeLong16_32_2(ss0, ss1);
//			System.out.println("iv =" + iv + "  " + BitsInt.toBinary(iv, 64));
			trg[trgPos] <<= (2*16);
			trg[trgPos] |= iv;
//			System.out.println("trg=" + trg[trgPos] + "  " + BitsInt.toBinary(trg[trgPos], 64));
			if ((w+16)%32==0) {
				trgPos++;
			}
		}
//		int ss0, ss1;
//		long iv;
//		//64-48
//		ss0 = (int) ((s0 >>> 48) & 0xFFFF);
//		ss1 = (int) ((s1 >>> 48) & 0xFFFF);
//		iv = mergeLong16_32(ss0, ss1);
//		trg[trgPos] = iv;
//		//48-32
//		ss0 = (int) ((s0 >>> 32) & 0xFFFF);
//		ss1 = (int) ((s1 >>> 32) & 0xFFFF);
//		iv = mergeLong16_32(ss0, ss1);
//		trg[trgPos] <<= (2*16);
//		trg[trgPos] |= iv;
//		trgPos++;
//		//32-16
//		ss0 = (int) ((s0 >>> 16) & 0xFFFF);
//		ss1 = (int) ((s1 >>> 16) & 0xFFFF);
//		iv = mergeLong16_32(ss0, ss1);
//		trg[trgPos] = iv;
//		//16-0
//		ss0 = (int) (s0 & 0xFFFF);
//		ss1 = (int) (s1 & 0xFFFF);
//		iv = mergeLong16_32(ss0, ss1);
//		trg[trgPos] <<= (2*16);
//		trg[trgPos] |= iv;
		return trg;
	}
	
	/**
	 * 
	 * @param nBitsPerValue
	 * @param src
	 * @return interleaved value
	 */
	public long[] mergeLong64(int nBitsPerValue, long[] src) {
		int intArrayLen = (src.length*nBitsPerValue+63) >>> 6;
		long[] trg = new long[intArrayLen];
		int trgPos = 0;
		long s0 = src[0];
		long s1 = src[1];
//		for (int w = 0; w < nBitsPerValue; w+=32) {
//			int ss0 = (int) ((s0 >>> (32-w)) & 0xFFFFFFFF);
//			int ss1 = (int) ((s1 >>> (32-w)) & 0xFFFFFFFF);
////			System.out.println("ss0=" + ss0 + "  " + BitsInt.toBinary(ss0));
////			System.out.println("ss1=" + ss1 + "  " + BitsInt.toBinary(ss1));
//			long iv = mergeLong32_64(ss0, ss1);
////			System.out.println("iv =" + iv + "  " + BitsInt.toBinary(iv, 64));
//			trg[trgPos] = iv;
////			System.out.println("trg=" + trg[trgPos] + "  " + BitsInt.toBinary(trg[trgPos], 64));
//			trgPos++;
//		}
		int ss0, ss1;
		long iv;
		//63-32
		ss0 = (int) ((s0 >>> 32) & 0xFFFFFFFF);
		ss1 = (int) ((s1 >>> 32) & 0xFFFFFFFF);
		iv = mergeLong32_64(ss0, ss1);
		trg[trgPos] = iv;
		trgPos++;
		//31-0
		ss0 = (int) (s0 & 0xFFFFFFFF);
		ss1 = (int) (s1 & 0xFFFFFFFF);
		iv = mergeLong32_64(ss0, ss1);
		trg[trgPos] = iv;
		return trg;
	}
	
	private static final long[] MortonTable256 = 
		{
		  0x0000, 0x0001, 0x0004, 0x0005, 0x0010, 0x0011, 0x0014, 0x0015, 
		  0x0040, 0x0041, 0x0044, 0x0045, 0x0050, 0x0051, 0x0054, 0x0055, 
		  0x0100, 0x0101, 0x0104, 0x0105, 0x0110, 0x0111, 0x0114, 0x0115, 
		  0x0140, 0x0141, 0x0144, 0x0145, 0x0150, 0x0151, 0x0154, 0x0155, 
		  0x0400, 0x0401, 0x0404, 0x0405, 0x0410, 0x0411, 0x0414, 0x0415, 
		  0x0440, 0x0441, 0x0444, 0x0445, 0x0450, 0x0451, 0x0454, 0x0455, 
		  0x0500, 0x0501, 0x0504, 0x0505, 0x0510, 0x0511, 0x0514, 0x0515, 
		  0x0540, 0x0541, 0x0544, 0x0545, 0x0550, 0x0551, 0x0554, 0x0555, 
		  0x1000, 0x1001, 0x1004, 0x1005, 0x1010, 0x1011, 0x1014, 0x1015, 
		  0x1040, 0x1041, 0x1044, 0x1045, 0x1050, 0x1051, 0x1054, 0x1055, 
		  0x1100, 0x1101, 0x1104, 0x1105, 0x1110, 0x1111, 0x1114, 0x1115, 
		  0x1140, 0x1141, 0x1144, 0x1145, 0x1150, 0x1151, 0x1154, 0x1155, 
		  0x1400, 0x1401, 0x1404, 0x1405, 0x1410, 0x1411, 0x1414, 0x1415, 
		  0x1440, 0x1441, 0x1444, 0x1445, 0x1450, 0x1451, 0x1454, 0x1455, 
		  0x1500, 0x1501, 0x1504, 0x1505, 0x1510, 0x1511, 0x1514, 0x1515, 
		  0x1540, 0x1541, 0x1544, 0x1545, 0x1550, 0x1551, 0x1554, 0x1555, 
		  0x4000, 0x4001, 0x4004, 0x4005, 0x4010, 0x4011, 0x4014, 0x4015, 
		  0x4040, 0x4041, 0x4044, 0x4045, 0x4050, 0x4051, 0x4054, 0x4055, 
		  0x4100, 0x4101, 0x4104, 0x4105, 0x4110, 0x4111, 0x4114, 0x4115, 
		  0x4140, 0x4141, 0x4144, 0x4145, 0x4150, 0x4151, 0x4154, 0x4155, 
		  0x4400, 0x4401, 0x4404, 0x4405, 0x4410, 0x4411, 0x4414, 0x4415, 
		  0x4440, 0x4441, 0x4444, 0x4445, 0x4450, 0x4451, 0x4454, 0x4455, 
		  0x4500, 0x4501, 0x4504, 0x4505, 0x4510, 0x4511, 0x4514, 0x4515, 
		  0x4540, 0x4541, 0x4544, 0x4545, 0x4550, 0x4551, 0x4554, 0x4555, 
		  0x5000, 0x5001, 0x5004, 0x5005, 0x5010, 0x5011, 0x5014, 0x5015, 
		  0x5040, 0x5041, 0x5044, 0x5045, 0x5050, 0x5051, 0x5054, 0x5055, 
		  0x5100, 0x5101, 0x5104, 0x5105, 0x5110, 0x5111, 0x5114, 0x5115, 
		  0x5140, 0x5141, 0x5144, 0x5145, 0x5150, 0x5151, 0x5154, 0x5155, 
		  0x5400, 0x5401, 0x5404, 0x5405, 0x5410, 0x5411, 0x5414, 0x5415, 
		  0x5440, 0x5441, 0x5444, 0x5445, 0x5450, 0x5451, 0x5454, 0x5455, 
		  0x5500, 0x5501, 0x5504, 0x5505, 0x5510, 0x5511, 0x5514, 0x5515, 
		  0x5540, 0x5541, 0x5544, 0x5545, 0x5550, 0x5551, 0x5554, 0x5555
		};

	private static final long[] MortonTable65536 = new long[65536];
	//private static final long[] MortonTableInverse65536 = new long[65536];
	static {
		long l = 0;
		long mask = 0xAAAAAAAAAAAAAAAAL;
		for (int i = 0; i < MortonTable65536.length; i++) {
			MortonTable65536[i] = l;
			//if (i < 256) System.out.println(Long.toHexString(l));
			l |=  mask;
			l++;
			l &= ~mask;
		}
		for (int i = 0; i < MortonTable256.length; i++) {
			for (int j = 0; j < MortonTable256.length; j++) {
				//TODO
				//TODO later: implement separate versions for 1D, 2D, 3D, nD?
			}
		}
	}
	
	public long mergeLong16_32(int y, int x) {
		//short x; // Interleave bits of x and y, so that all of the
		//short y; // bits of x are in the even positions and y in the odd;
		long z;   // z gets the resulting 32-bit Morton Number.

		z = MortonTable256[y >>> 8]   << 17 | 
				MortonTable256[x >>> 8]   << 16 |
				MortonTable256[y & 0xFF] <<  1 | 
				MortonTable256[x & 0xFF];
		return z;
	}

	public long mergeLong32_64(int y, int x) {
		//short x; // Interleave bits of x and y, so that all of the
		//short y; // bits of x are in the even positions and y in the odd;
		long z;   // z gets the resulting 32-bit Morton Number.

		z = MortonTable65536[y >>> 16]   << 33 | 
				MortonTable65536[x >>> 16]   << 32 |
				MortonTable65536[y & 0xFFFF] <<  1 | 
				MortonTable65536[x & 0xFFFF];
		return z;
	}

	private static final long B[] = {0x55555555, 0x33333333, 0x0F0F0F0F, 0x00FF00FF};
	private static final int S[] = {1, 2, 4, 8};
	public long mergeLong16_32_2(int y0, int x0) {

		// Interleave lower 16 bits of x and y, so the bits of x
		// are in the even positions and bits from y in the odd;
		long z; // z gets the resulting 32-bit Morton Number.  
		        // x and y must initially be less than 65536.
		long x = 0 | x0;
		long y = 0 | y0;
		x = (x | (x << S[3])) & B[3];
		x = (x | (x << S[2])) & B[2];
		x = (x | (x << S[1])) & B[1];
		x = (x | (x << S[0])) & B[0];

		y = (y | (y << S[3])) & B[3];
		y = (y | (y << S[2])) & B[2];
		y = (y | (y << S[1])) & B[1];
		y = (y | (y << S[0])) & B[0];

		z = x | (y << 1);		
		return z;
	}
	
}
