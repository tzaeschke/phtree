/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.pre;

import ch.ethz.globis.phtree.util.BitTools;

/**
 * A preprocessor for range-to-point preprocessing between floating point and integer vectors.
 * @author ztilmann
 *
 */
public interface PreProcessorRangeF {
	
	/**
	 * 
	 * @param raw1 raw data (input) of lower left corner
	 * @param raw2 raw data (input) of upper right corner
	 * @param pre pre-processed data (output, must be non-null and twice the size as input array)
	 */
	public void pre(double[] raw1, double[] raw2, long[] pre);
	
	
	/**
	 * 
	 * @param raw1 raw data (input) of lower left corner
	 * @param pos1 start position in raw1
	 * @param raw2 raw data (input) of upper right corner
	 * @param pos2 start position in raw2
	 * @param pre pre-processed data (output, must be non-null and twice the size as input array)
	 */
	public void pre(double[] raw1, int pos1, double[] raw2, int pos2, long[] pre);
	
	
	/**
	 * @param pre pre-processed data (input)
	 * @param post1 post-processed data (output, must be non-null and half the size as input array)
	 *              of lower left corner
	 * @param post2 post-processed data (output, must be non-null and half the size as input array)
	 *              of upper right corner
	 */
	public void post(long[] pre, double[] post1, double[] post2);
	
	
	/**
	 * Simple IEEE preprocessor that converts the bits directly to an integer value.
	 * The conversion is lossless. Euclidean space properties are not fully maintained in
	 * the converted space because the space is stretched.
	 */
	public class IEEE implements PreProcessorRangeF {

		private final int dims;
		
		/**
		 * @param dims dimensions per point
		 */
		public IEEE(int dims) {
			this.dims = dims;
		}
		
		@Override
		public void pre(double[] raw1, double[] raw2, long[] pre) {
			final int pDIM = raw1.length;
			for (int d = 0; d < pDIM; d++) {
				pre[d] = BitTools.toSortableLong(raw1[d]);
				pre[d+pDIM] = BitTools.toSortableLong(raw2[d]);
			}
		}

		@Override
		public void pre(double[] raw1, int pos1, double[] raw2, int pos2, long[] pre) {
			for (int d = 0; d < dims; d++) {
				pre[d] = BitTools.toSortableLong(raw1[pos1+d]);
				pre[d+dims] = BitTools.toSortableLong(raw2[pos2+d]);
			}
		}

		@Override
		public void post(long[] pre, double[] post1, double[] post2) {
			final int pDIM = post1.length;
			for (int d = 0; d < pDIM; d++) {
				post1[d] = BitTools.toDouble(pre[d]);
				post2[d] = BitTools.toDouble(pre[d+pDIM]);
			}
		}

	}
	
	
	/**
	 * Multiply-preprocessor. Values are multiplied by a large constant before converted (cast) to 
	 * integer values. 
	 * Best values depend on the dataset, examples are 10^9, 10^10, 10^11.
	 * This is a lossy conversion, due to the 'cast' to integer.
	 */
	public static class Multiply implements PreProcessorRangeF {
		private final int dims;
		private final double mul;
		private final double div;
		
		/**
		 * @param dims dimensions per point
		 * @param pre
		 */
		public Multiply(int dims, double pre) {
			this.dims = dims;
			this.mul = pre;
			this.div = 1/pre;
		}
		
		@Override
		public void pre(double[] raw1, double[] raw2, long[] pre) {
			for (int i = 0; i < dims; i++) {
				pre[i] = (long) (raw1[i]*mul);
				pre[i+dims] = (long) (raw2[i]*mul);
			}
		}
		
		@Override
		public void pre(double[] raw1, int pos1, double[] raw2, int pos2, long[] pre) {
			for (int i = 0; i < dims; i++) {
				pre[i] = (long) (raw1[pos1+i]*mul);
				pre[i+dims] = (long) (raw2[pos2+i]*mul);
			}
		}
		
		@Override
		public void post(long[] pre, double[] post1, double[] post2) {
			for (int i = 0; i < dims; i++) {
				post1[i] = pre[i]*div;
				post2[i] = pre[i+dims]*div;
			}
		}
	}
	
	/**
	 * Shift-preprocessor. Adds a constant before applying IEEE conversion to integer.
	 * This is a lossless conversion, except for precision lost during the addition.
	 * The shift can help avoiding performance problems with values that differ in their
	 * exponent, for example by shifting data clusters around 0.5 (exponent change in IEEE) to
	 * 0.4 . Best values have to be found experimentally, depending on the dataset, a
	 * basic suggestion is to use 1.0 for datasets [0.0,1.0].
	 */
	public static class ShiftIEEE implements PreProcessorRangeF {
		private final int dims;
		private final double shift;
		
		/**
		 * @param dims dimensions per point
		 * @param preShift
		 */
		public ShiftIEEE(int dims, double preShift) {
			this.dims = dims;
			this.shift = preShift;
		}
		
		@Override
		public void pre(double[] raw1, double[] raw2, long[] pre) {
			for (int i = 0; i < dims; i++) {
				pre[i] = BitTools.toSortableLong(raw1[i]+shift);
				pre[i+dims] = BitTools.toSortableLong(raw2[i]+shift);
			}
		}
		
		@Override
		public void pre(double[] raw1, int pos1, double[] raw2, int pos2, long[] pre) {
			for (int i = 0; i < dims; i++) {
				pre[i] = BitTools.toSortableLong(raw1[pos1+i]+shift);
				pre[i+dims] = BitTools.toSortableLong(raw2[pos2+i]+shift);
			}
		}
		
		@Override
		public void post(long[] pre, double[] post1, double[] post2) {
			for (int i = 0; i < dims; i++) {
				post1[i] = BitTools.toDouble(pre[i])-shift;
				post2[i] = BitTools.toDouble(pre[i+dims])-shift;
			}
		}
	}
	
	/**
	 * Combined Shift-Multiply-preprocessor. 
	 * Values are first shifted by a constant (add), then multiplied by a another constant before 
	 * being converted (cast) to integer values. 
	 * Best values depend on the dataset, examples are 10^9, 10^10, 10^11.
	 * This is a lossy conversion, due to the 'cast' to integer.
	 */
	public static class ShiftMulIPP implements PreProcessorRangeF {
		private final int dims;
		private final double mul;
		private final double div;
		private final double shift;
		
		/**
		 * @param dims dimensions per point
		 * @param preMul
		 * @param preShift
		 */
		public ShiftMulIPP(int dims, double preMul, double preShift) {
			this.dims = dims;
			this.mul = preMul;
			this.div = 1/preMul;
			this.shift = preShift;
		}
		
		@Override
		public void pre(double[] raw1, double[] raw2, long[] pre) {
			for (int i = 0; i < dims; i++) {
				pre[i] = (long) ((raw1[i]+shift)*mul);
				pre[i+dims] = (long) ((raw2[i]+shift)*mul);
			}
		}
		
		@Override
		public void pre(double[] raw1, int pos1, double[] raw2, int pos2, long[] pre) {
			for (int i = 0; i < dims; i++) {
				pre[i] = (long) ((raw1[pos1+i]+shift)*mul);
				pre[i+dims] = (long) ((raw2[pos2+i]+shift)*mul);
			}
		}
		
		@Override
		public void post(long[] pre, double[] post1, double[] post2) {
			for (int i = 0; i < dims; i++) {
				post1[i] = pre[i]*div-shift;
				post2[i] = pre[i+dims]*div-shift;
			}
		}
	}
	

}
