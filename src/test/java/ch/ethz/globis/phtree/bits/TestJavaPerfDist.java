/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.bits;

import java.util.Arrays;
import java.util.Random;

import org.zoodb.index.critbit.BitTools;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhDistanceF;

/**
 * 10M/60
 * Distance: 3.157346912603036E7
 * t= 978  -> 9.78E-5
 * 
 * 10M/6
 * Distance: 9706377.796962857
 * t= 188  -> 1.88E-5
 * 
 * 100M/6
 * Distance: 9.706377796872553E7
 * t= 1929  -> 1.929E-5
 * 
 * @author ztilmann
 *
 */
public class TestJavaPerfDist {

	
	private PhDistance dist = PhDistanceF.THIS;
	
	public static void main(String[] args) {
		new TestJavaPerfDist().testDist();
	}

	private void testDist() {
		int N = 100_000_000;
		final int DIM = 6;

		Random R = new Random(0);
		long[][] v1 = new long[1_000][DIM];
		long[][] v2 = new long[1_000][DIM];
		for (int i = 0; i < v1.length; i++) {
			Arrays.setAll(v1[i], l -> BitTools.toSortableLong(R.nextDouble()));
			Arrays.setAll(v2[i], l -> BitTools.toSortableLong(R.nextDouble()));
		}
		
		testLoop(N/1000, v1, v2);
		
		long t1 = System.currentTimeMillis();
		
		testLoop(N, v1, v2);
		
		long t2 = System.currentTimeMillis();
		long t = t2 - t1;
		System.out.println("t= " + t + "  -> " + ((double)t/((double)N)));
	}
	
	private void testLoop(int N, long[][] v1, long[][] v2) {
		double d = 0;
		for (int i1 = 0; i1 < N; i1++) {
			int pos = i1 % v1.length;
			d += dist.dist(v1[pos], v2[pos]);
		}
		System.out.println("Distance: " + d);
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
