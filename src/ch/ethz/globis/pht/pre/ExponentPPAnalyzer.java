/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht.pre;

import java.util.Arrays;


/**
 * Exponent shift analyzer for creating exponent-shift-preprocessors.
 * 
 * 
 * @author Tilmann Zaeschke, Adrien Favre-Bully
 *
 */
public class ExponentPPAnalyzer {

	/**
	 * Creates a preprocessor from a data sample.
	 * The data sample does not need to be complete, but it should contains
	 * representative minimum and maximum values for each dimension.
	 * @param data The data [N][DIM]
	 * @return Preprocessor instance
	 */
	public static ExponentPP analyze(double[][] data) {
		int N = data.length;
		int dims = data[0].length;
		double[] min = new double[dims];
		double[] max = new double[dims];
		Arrays.fill(min, Double.MAX_VALUE);
		Arrays.fill(max, -Double.MAX_VALUE);
		for (int i = 0; i < N; i++) {
			double[] da = data[i];
			for (int d = 0; d < dims; d++) {
				min[d] = da[d] < min[d] ? da[d] : min[d]; 
				max[d] = da[d] > max[d] ? da[d] : max[d]; 
			}
		}
		
		double[] shifts = new double[dims];
		for (int d = 0; d < dims; d++) {
			shifts[d] = getDisplacement(min[d], max[d]);
			//System.out.println("shift=" + shifts[d]);
		}
		return new ExponentPP(shifts);
	}
	
	/**
	 * Creates a preprocessor from a data sample.
	 * The data sample does not need to be complete, but it should contains
	 * representative minimum and maximum values for each dimension.
	 * @param data The data [N*DIM]
	 * @param dims dimensionality
	 * @return Preprocessor instance
	 */
	public static ExponentPP analyze(double[] data, int dims) {
		int N = data.length/dims;
		double[] min = new double[dims];
		double[] max = new double[dims];
		Arrays.fill(min, Double.MAX_VALUE);
		Arrays.fill(max, -Double.MAX_VALUE);
		for (int i = 0; i < N; i++) {
			int offs = i*dims;
			for (int d = 0; d < dims; d++) {
				min[d] = data[d+offs] < min[d] ? data[d+offs] : min[d]; 
				max[d] = data[d+offs] > max[d] ? data[d+offs] : max[d]; 
			}
		}
		
		double[] shifts = new double[dims];
		for (int d = 0; d < dims; d++) {
			shifts[d] = getDisplacement(min[d], max[d]);
			//System.out.println("shift=" + shifts[d]);
		}
		return new ExponentPP(shifts);
	}
	
	
	private static double getDisplacement(double d1, double d2) {
		//apply safety margins for rounding errors
		double MARGIN = 1.0001; 
		d1 *= d1 > 0 ? 1/MARGIN : MARGIN;
		d2 *= d2 > 0 ? MARGIN : 1/MARGIN;
		
		//1) We shift in ANY case (small range still needs shift)
		//2) We shift to the next range that can hold the DELTA
		double range = d2-d1;
		if (d1 > 0 && range < d1) {
			range = d1;
		} else if (d2 < 0 && d2 < -range) {
			range = -d2;
		}
		double log2;
		if (range == 0) {
			return 0;
		} else if (Math.abs(range - 1.0) < 0.0001) {
			log2 = Math.log1p(range-1.0)/Math.log(2);
		} else {
			log2 = Math.log(range)/Math.log(2);
		}
		
		log2 = Math.ceil(log2);
		double pos = Math.pow(2,log2);
		if (d2 <= 0) {
			//Shift to negative position
			// pos = d2+ret -> ret = pos-d2;
			return -pos - d2;
		}
		if (pos < d1) {
			//avoid shifting too far, delta is small enough to
		}
		return pos - d1;
	}

}
