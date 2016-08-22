/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Random;

import org.junit.Test;

import ch.ethz.globis.phtree.nv.PhTreeNV;
import ch.ethz.globis.phtree.nv.PhTreeVProxy;
import ch.ethz.globis.phtree.test.util.TestSuper;
import ch.ethz.globis.phtree.test.util.TestUtil;
import ch.ethz.globis.phtree.util.BitTools;
import ch.ethz.globis.phtree.util.Bits;

public class TestIndexInsertion extends TestSuper {

	private PhTreeNV create(int dim, int depth) {
		return TestUtil.newTreeNV(dim, depth);
	}
	
	private void assertExists(PhTreeNV i, long... l) {
		if (!i.contains(l)) {
			System.out.println(i.toStringPlain());
			fail(Bits.toBinary(l, i.getDEPTH()));
		}
	}
	
	@Test
	public void test0() {
		//DIM=1, DEPTH=2
		PhTreeNV i = create(1, 2);
		i.insert(0);
    	assertTrue( i.contains(0) ) ;
	}

	
	@Test
	public void test1() {
		PhTreeNV i = create(2, 16);
		i.insert(130, 226);
    	assertTrue( i.contains(130, 226) ) ;
	}

	
	@Test
	public void test2() {
		PhTreeNV i = create(1, 16);
    	i.insert(165);  //.10100101
    	//i.printTree();
    	i.insert(118);  //.01110110
    	//i.printTree();
    	i.insert(110);  //.01101110
    	assertExists(i, 110);
//    	i.printTree();
    	i.insert(84);   //.01010100
//    	i.printTree();
     	assertExists(i, 110);
    	assertExists(i, 84);
	}
	
	@Test
	public void test3() {
		PhTreeNV i = create(1, 16);
    	i.insert(94); //.01011110
    	i.insert(165);
    	assertExists(i, 165);
    	i.insert(231); //.11100111
    	assertExists(i, 165);
    	assertExists(i, 231);
    	i.insert(84);
    	assertExists(i, 84);
    	i.insert(244);
    	assertExists(i, 165);
    	assertExists(i, 231);
    	assertExists(i, 84);
    	assertExists(i, 94);
    	assertExists(i, 244);
    	
    	i.insert(198);
    	i.insert(198);
	}
	
	
	@Test
	public void test4() {
		PhTreeNV i = create(2, 16);
		i.insert(125, 237); //.01111101,.11101101
		assertExists(i, 125, 237);
		i.insert(97, 231);
		assertExists(i, 125, 237);
	}
	
	
	@Test
	public void test5() {
		PhTreeNV i = create(2, 16);
		i.insert( 182, 169);
		assertExists(i, 182, 169);
		i.insert( 184, 160);
		assertExists(i, 182, 169);
	}
	
	@Test
	public void test6() {
		PhTreeNV i = create(2, 32);
		i.insert(584, 64024);
		i.insert(11210, 64625);
		assertExists(i, 584, 64024);
		i.insert(18225, 34844); //.01000111.0011001 - .10001000.00011100
		assertExists(i, 584, 64024);
		i.insert(13539, 56686);
		i.insert(8738, 51126); 
		assertExists(i, 584, 64024);
	}
	
	@Test
	public void test7() {
		PhTreeNV i = create(1, 8);
		i.insert(26);
		i.insert(80);
		//i.printTree();
		i.insert(90);
		assertExists(i, 26);
		assertExists(i, 80);
		assertExists(i, 90);
	}
	
	@Test
	public void test8() {
		PhTreeNV i = create(2, 32);
		i.insert(20574, 9827);
		i.insert(54559, 55739);
		i.insert(52326, 13370);
		i.insert(6801, 2726);
		i.insert(11141, 9613);
		assertExists(i, 11141, 9613);
	}
	
	@Test
	public void test9() {
		PhTreeNV i = create(2, 16);
		i.insert(134, 1);
		i.insert(191, 13);
		assertExists(i, 191, 13);
	}
	
	@Test
	public void test10() {
		PhTreeNV i = create(2, 16);
		i.insert(79, 245);
		i.insert(7, 95);
		i.insert(89, 1);
		assertExists(i, 79, 245);
	}
	
	/**
	 * This causes a linear-->HC switch, which resulted in corruption.
	 */
	@Test
	public void test11() {
		PhTreeNV i = create(2, 16);
		i.insert(47, 150);
		i.insert(126, 34);
		i.insert(187, 133);
		i.insert(218, 60);
		assertExists(i, 218, 60);
	}
	
	@Test
	public void test12() {
		PhTreeNV i = create(2, 16);
		i.insert( 230, 74);
		i.insert( 30, 38);
		i.insert( 163, 54);
		i.insert( 204, 10);
		i.insert( 139, 119);
		i.insert( 176, 89);
		assertExists(i, 176, 89);
	}
	
	@Test
	public void test13() {
		PhTreeNV i = create(3, 64);
		i.insert(2421495525L, 3660847787L, 3008816559L);
		assertExists(i, 2421495525L, 3660847787L, 3008816559L);
	}
	
	
	@Test
	public void test14() {
		PhTreeNV i = create(2, 16);
		i.insert(100, 135);
		i.insert(33, 114);
		i.insert(238, 84);
		i.insert(80, 88);
		i.insert(215, 245);
		i.insert(247, 74);
		i.insert(16, 188);
		assertExists(i, 33, 114);
		
		assertExists(i, 100, 135);
		assertExists(i, 33, 114);
		assertExists(i, 238, 84);
		assertExists(i, 80, 88);
		assertExists(i, 215, 245);
		assertExists(i, 247, 74);
		assertExists(i, 16, 188);
	}

	@Test
	public void test64bit() {
		PhTreeNV i = create(2, 64);
		i.insert(-100, 135);
		i.insert(33, -114);
		i.insert(238, 84);
		i.insert(-80, -88);
		assertExists(i, -100, 135);
		assertExists(i, 33, -114);
		assertExists(i, 238, 84);
		assertExists(i, -80, -88);
	}
	
	@Test
	public void test15() {
		PhTreeNV ind = create(2, 32);
		Random R = new Random(1);
		int N = 200000;
		long[][] vals = new long[N][];
		for (int i = 0; i < N; i++) {
			long[] v = new long[]{R.nextInt(), R.nextInt()};
			//System.out.println("i=" + i + "  vA.add(new long[]{" + v[0] + ", " + v[1] + "});");
			vals[i] = v;
			if (ind.insert(v)) {
				//catch duplicates, maybe in future we should just skip them
				i--;
				continue;
			}
			assertTrue(ind.contains(v));
		}
		
		for (int i = 0; i < N; i++) {
			long[] v = vals[i];
			//System.out.println("i=" + i + "  " + Bits.toBinary(v, 32));
			assertTrue(ind.delete(v));
			assertFalse(ind.contains(v));
			//try again.
			assertFalse(ind.delete(v));
			assertFalse(ind.contains(v));
		}
	}
	
	@Test
	public void test16BLHC() {
		PhTreeNV ind = create(2, 32);
		ArrayList<long[]> vA = new ArrayList<long[]>();
		vA.add(new long[]{-1155869325, 431529176});
		vA.add(new long[]{1761283695, 1749940626});
		vA.add(new long[]{892128508, 155629808});
		vA.add(new long[]{-155886662, 685382526});
		for (long[] v: vA) {
			ind.insert(v);
		}
		for (long[] v: vA) {
			assertTrue(ind.contains(v));
		}
	}
	
	@Test
	public void test17() {
		//TODO this fails only with BLHC enabled
		//*******************************
		//*******************************
		//** Interesting, it works with 16 but fails with 17. Why, maybe because of hard coded
		//** 16 somewhere? There is one, for the splitting of blhc-pos into pageID and something 
		//** else.
		//** See for example BLHC_POS_SPLIT_BITS used in blhcFromBIdPageOffs() which seems to 
		//** assume that pageId uses at most 16 bits. 
		//*******************************
		//*******************************
		//DEPTH=1 !!!
		PhTreeNV ind = create(24, 32);
		ArrayList<long[]> vA = generateCluster17_18(34000, 24, 0.00001);
//		ArrayList<long[]> vA = new ArrayList<long[]>();
//		vA.add(new long[]{-1155869325, 431529176});
//		vA.add(new long[]{1761283695, 1749940626});
//		vA.add(new long[]{892128508, 155629808});
//		vA.add(new long[]{-155886662, 685382526});
		for (long[] v: vA) {
			ind.insert(v);
		}
		for (long[] v: vA) {
			assertTrue(ind.contains(v));
		}
	}
	
	@Test
	public void test18() {
		//TODO this fails only with BLHC enabled
		PhTreeNV ind = create(30, 64);
		ArrayList<long[]> vA = generateCluster17_18(40000, 30, 0.00001);
//		ArrayList<long[]> vA = new ArrayList<long[]>();
//		vA.add(new long[]{-1155869325, 431529176});
//		vA.add(new long[]{1761283695, 1749940626});
//		vA.add(new long[]{892128508, 155629808});
//		vA.add(new long[]{-155886662, 685382526});
		for (long[] v: vA) {
			ind.insert(v);
		}
		for (long[] v: vA) {
			assertTrue(ind.contains(v));
		}
	}
	
	@Test
	public void test19() {
//		double d = 0.00001;
//		long l = BitTools.toSortableLong(0.5);
//		System.out.println("0.50000: " + l + " " + Bits.toBinary(l, 64) + " " + (0.5));
//		l = BitTools.toSortableLong(0.5+d);
//		System.out.println("0.50001: " + l + " " + Bits.toBinary(l, 64) + " " + (0.5+d));
//		l = BitTools.toSortableLong(0.5-d);
//		System.out.println("0.49999: " + l + " " + Bits.toBinary(l, 64) + " " + (0.5-d));
//		
//		l = BitTools.toSortableLong(0.1);
//		System.out.println("0.10000: " + l + " " + Bits.toBinary(l, 64) + " " + (0.1));
//		l = BitTools.toSortableLong(0.1+d);
//		System.out.println("0.10001: " + l + " " + Bits.toBinary(l, 64) + " " + (0.1+d));
//		l = BitTools.toSortableLong(0.1-d);
//		System.out.println("0.49999: " + l + " " + Bits.toBinary(l, 64) + " " + (0.1-d));
//		
//		l = BitTools.toSortableLong(0.4);
//		System.out.println("0.10000: " + l + " " + Bits.toBinary(l, 64) + " " + (0.4));
//		l = BitTools.toSortableLong(0.4+d);
//		System.out.println("0.10001: " + l + " " + Bits.toBinary(l, 64) + " " + (0.4+d));
//		l = BitTools.toSortableLong(0.4-d);
//		System.out.println("0.49999: " + l + " " + Bits.toBinary(l, 64) + " " + (0.4-d));

		//TODO
//		PhTree ind = create(15, 64);
//		ArrayList<long[]> vA = generateCluster17_18(1*1000*1000, 15, 0.000001);
//		for (long[] v: vA) {
//			ind.insert(v);
//		}
//		for (long[] v: vA) {
//			assertTrue(ind.contains(v));
//		}
	}

	@Test
	public void test20() {
//		{-113.041573, 31.935317, -113.000919, 31.947497, },
//		{-113.125961, 31.96523, -113.100762, 31.97278, },
//		{-113.210763, 31.999171, -113.208836, 31.999785, },
//		{-113.208836, 31.996797, -113.201381, 31.999171, },
//		{-113.201381, 31.989676, -113.179019, 31.996797, },
//		{-113.153897, 31.97278, -113.125961, 31.981676, },
//		{-113.171565, 31.981676, -113.153897, 31.987303, },
//		{-113.179019, 31.987303, -113.171565, 31.989676, },
//		{-113.083001, 31.947497, -113.041573, 31.959909, },
//		{-113.100762, 31.959909, -113.083001, 31.96523, },
//		{-113.000919, 31.915048, -112.932692, 31.935317, },
//		{-112.375759, 31.743967, -112.375693, 31.743987, },
//		{-112.383589, 31.743987, -112.375759, 31.746583, },
//		{-112.388117, 31.746583, -112.383589, 31.748085, },
//		{-112.390348, 31.748085, -112.388117, 31.748782, },
//		{-112.388118, 31.748085, -112.388117, 31.748112, },
		
		long[][] vA = {
				{-4637655010468747619L, 4629682210290951313L, -4637652149697821820L, 4629685638656167648L, },
				{-4637660948746331284L, 4629690630051929658L, -4637659175524346751L, 4629692755188003824L, },
				{-4637666916156575038L, 4629700183594114195L, -4637666780556005008L, 4629700356419749895L, },
				{-4637666780556005008L, 4629699515372519484L, -4637666255957017163L, 4629700183594114195L, },
				{-4637666255957017163L, 4629697510989210327L, -4637664682371159862L, 4629699515372519484L, },
				{-4637662914567568631L, 4629692755188003824L, -4637660948746331284L, 4629695259189396642L, },
				{-4637664157842540762L, 4629695259189396642L, -4637662914567568631L, 4629696843049090593L, },
				{-4637664682371159862L, 4629696843049090593L, -4637664157842540762L, 4629697510989210327L, },
				{-4637657925705081411L, 4629685638656167648L, -4637655010468747619L, 4629689132323578581L, },
				{-4637659175524346751L, 4629689132323578581L, -4637657925705081411L, 4629690630051929658L, },
				{-4637652149697821820L, 4629676505074648364L, -4637647348649512811L, 4629682210290951313L, },
				{-4637608157973711712L, 4629628350054157729L, -4637608153329374596L, 4629628355683657263L, },
				{-4637608708960978623L, 4629628355683657263L, -4637608157973711712L, 4629629086392696804L, },
				{-4637609027590652259L, 4629629086392696804L, -4637608708960978623L, 4629629509168111823L, },
				{-4637609184583320520L, 4629629509168111823L, -4637609027590652259L, 4629629705356170590L, },
				{-4637609027661021004L, 4629629509168111823L, -4637609027590652259L, 4629629516767936194L, }
		};
		
		PhTreeNV ind = new PhTreeVProxy(4);
		for (long[] v: vA) {
			ind.insert(v);
		}
		for (long[] v: vA) {
			assertTrue(ind.contains(v));
		}
	}
	
	private ArrayList<long[]> generateCluster17_18(int N, int DIM, double CLUSTER_LEN) {
		double LEN = 1;
		Random R = new Random(0);
		int N_C = N/1000; //=points per cluster
		
		ArrayList<long[]> data = new ArrayList<long[]>(N);

		//loop over clusters
		for (int c = 0; c < 1000; c++) {
			double x0 = LEN * (c+0.5)/(double)1000; //=0.5/1000 ||  1.5/1000  ||  ...
			double yz0 = LEN * 0.5; //line is centered in all dimensions
			for (int p = 0; p < N_C; p++) { 
				//int ii = (c*N_C+p) * DIM;
				long[] data2 = new long[DIM];
				data.add(data2);
				for (int d = 0; d < DIM; d++) {
					double dd = LEN * (R.nextDouble()-0.5)*CLUSTER_LEN; //confine to small rectangle
					if (d==0) {
						dd += x0;
					} else {
						dd += yz0;
					}
					data2[d] = BitTools.toSortableLong(dd);
				}
			}
		}
		return data;
	}

	
	@Test
	public void test20_NewInsert() {
		PhTreeNV i = create(3, 32);
		i.insert(3925440664L, 684358198, 1584853918);
		//i.printTree();
		i.insert(181670012, 3271367910L, 2679941640L);
		assertExists(i, 3925440664L, 684358198, 1584853918);
	}
}
