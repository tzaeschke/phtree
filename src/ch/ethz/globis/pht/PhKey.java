/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht;

import ch.ethz.globis.pht.util.BitTools;

public interface PhKey {

	long[] getKeyBits();
	
	public static class PhKeyLong<T> implements PhKey {
		long[] key;
		T value;
		
		public PhKeyLong(long[] key, T value) {
			this.key = key;
			this.value = value;
		}
		
		public void set(long[] key, T value) {
			this.key = key;
			this.value = value;
		}
		
		public T getValue() {
			return value;
		}
		
		public long[] getKey() {
			return key;
		}
		
		@Override
		public long[] getKeyBits() {
			return key;
		}
	}
	
	public static class PhKeyDouble<T> implements PhKey {
		double[] key;
		T value;
		
		public PhKeyDouble(double[] key, T value) {
			this.key = key;
			this.value = value;
		}
		
		public void set(double[] key, T value) {
			this.key = key;
			this.value = value;
		}

		public T getValue() {
			return value;
		}

		public double[] getKey() {
			return key;
		}

		@Override
		public long[] getKeyBits() {
			return BitTools.toSortableLong(key, new long[key.length]);
		}
	}
}
