/*
 * Copyright 2011-2015 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.test;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;
import org.zoodb.index.critbit.BitTools;

import ch.ethz.globis.pht.pre.ExponentPP;
import ch.ethz.globis.pht.pre.ExponentPPAnalyzer;

public class TestPP {

	@Test
	public void testExponentPP() {
		int DIM = 2;
		double[][] data = {{0.4,0.4}, {0.6,0.6}};
		ExponentPP pp = ExponentPPAnalyzer.analyze(data);
		double[][] dataDis = new double[data.length][DIM];
		double[] min = new double[DIM];
		double[] max = new double[DIM];
		Arrays.fill(min, Double.MAX_VALUE);
		Arrays.fill(max, -Double.MAX_VALUE);
		for (int r = 0; r < data.length; r++) {
			for (int i = 0; i < DIM; i++) {
				double dis = pp.getDisplacement(i);
				double d2 = data[r][i]+dis;
				dataDis[r][i]  = d2;
				if (d2 > max[i]) {
					max[i] = d2;
				}
				if (d2 < min[i]) {
					min[i] = d2;
				}
			}
		}
		
		for (int i = 0; i < DIM; i++) {
			int pl = getSharedPrefixLen(min[i], max[i]);
			//System.out.println("pl=" + pl);
			assertTrue("pl=" + pl, pl >= 12);
		}
	}

	@Test
	public void testExponentPpRandom() {
		int DIM = 3;
		double SCALE = 0.1;
		Random R = new Random(0);
		for (int x = -1000; x < 1000; x++) {
			double[][] data = new double[2][DIM];
			for (double[] dd: data) {
				for (int d = 0; d < DIM; d++) {
					dd[d] = R.nextDouble()*SCALE*x; 
				}
			}
			ExponentPP pp = ExponentPPAnalyzer.analyze(data);
			double[][] dataDis = new double[data.length][DIM];
			double[] min = new double[DIM];
			double[] max = new double[DIM];
			Arrays.fill(min, Double.MAX_VALUE);
			Arrays.fill(max, -Double.MAX_VALUE);
			for (int r = 0; r < data.length; r++) {
				for (int i = 0; i < DIM; i++) {
					double dis = pp.getDisplacement(i);
					double d2 = data[r][i]+dis;
					dataDis[r][i]  = d2;
					if (d2 > max[i]) {
						max[i] = d2;
					}
					if (d2 < min[i]) {
						min[i] = d2;
					}
					//System.out.print(data[r][i] + " -> " + (data[r][i]+dis) 
					//		+ " dis=" + dis + " , ");
				}
				//System.out.println();
			}
			
			for (int i = 0; i < DIM; i++) {
				int pl = getSharedPrefixLen(min[i], max[i]);
				//System.out.println("pl=" + pl + "  " + (max[i]/min[i]));
				assertTrue("pl=" + pl, pl >= 12);
			}
			//System.out.println("*********************");
		}
	}
	

	private int getSharedPrefixLen(double d1, double d2) {
		long l1 = BitTools.toSortableLong(d1);
		long l2 = BitTools.toSortableLong(d2);
		long x = l2 ^ l1;
		int len = Long.numberOfLeadingZeros(x);
		return len;
	}

}
