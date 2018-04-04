/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.pht64kd;

import java.util.Arrays;
import java.util.Iterator;

/**
 * NodeTrees are a way to represent Nodes that are to big to be represented as AHC or LHC nodes.
 * 
 * A NodeTree splits a k-dimensional node into a hierarchy of smaller nodes by splitting,
 * for example, the 16-dim key into 2 8-dim keys.
 * 
 * Unlike the normal PH-Tree, NodeTrees do not support infixes.
 * 
 * @author ztilmann
 *
 */
public interface MaxKTreeHdI {

	public static interface PhIterator64<T> extends Iterator<T> {

		public long[] nextKey();

		public long[] nextKdKey();

		public T nextValue();

		public NtEntry<T> nextEntry();

		/**
		 * Special 'next' method that avoids creating new objects internally by reusing Entry objects.
		 * Advantage: Should completely avoid any GC effort.
		 * Disadvantage: Returned PhEntries are not stable and are only valid until the
		 * next call to next(). After that they may change state. Modifying returned entries may
		 * invalidate the backing tree.
		 * @return The next entry
		 */
		NtEntry<T> nextEntryReuse();

		public void reset(MaxKTreeHdI tree, long[] min, long[] max);
		public void reset(MaxKTreeHdI tree);
	}

	public static class NtEntry<T> {
		private long[] key;
		private long[] kdKey;
		private T value;
		public NtEntry(long[] key, long[] kdKey, T value) {
			this.key = key;
			this.kdKey = kdKey;
			this.value = value;
		}
		
		public NtEntry(NtEntry<T> e) {
			this.key = e.getKey();
			this.kdKey = Arrays.copyOf(e.getKdKey(), e.getKdKey().length);
			this.value = e.getValue();
		}
		
		public long[] getKey() {
			return key;
		}
		
		public long[] getKdKey() {
			return kdKey;
		}
		
		public T getValue() {
			return value;
		}

		protected void set(long[] key, long[] kdKey, T value) {
			this.key = key;
			this.kdKey = kdKey;
			this.value = value;
		}
		
		public void setValue(T value) {
			this.value = value;
		}
		
		@Override
		public String toString() {
			return Arrays.toString(key);
		}
		
		public long[] key() {
			return getKey();
		}
		public T value() {
			return getValue();
		}

		public void setKey(long[] key) {
			this.key = key;
		}
	}
	
	

	public int size();

	public int getKeyBitWidth();
	
	public Object getRoot();
	
}
