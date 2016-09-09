/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * Interface for persistence providers. Persistence providers can be used by
 * PhTrees (such as v12) to serialize the tree or to measure I/O access.
 */
public interface PersistenceProvider {
	
	/**
	 * The empty implementation of a persistence provide, it does not provide persistence.
	 */
	public static final PersistenceProvider NONE = new PersistenceProviderNone();
	
	/**
	 * The empty implementation of a persistence provide, it does not provide persistence.
	 */
	public static class PersistenceProviderNone implements PersistenceProvider {
		private PhTree<?> tree;
		@Override
		public Object registerNode(Externalizable o) {
			return o;
		}
		
		@Override
		public Object loadNode(Object o) {
			return o;
		}

		@Override
		public void updateNode(Externalizable o) {
			//
		}

		@Override
		public String getDescription() {
			return "NONE";
		}

		@Override
		public int statsGetPageReads() {
			return -1;
		}

		@Override
		public int statsGetPageWrites() {
			return -1;
		}

		@Override
		public void statsReset() {
			//
		}

		@Override
		public void writeTree(PhTree<?> tree, int dims) {
			this.tree = tree;
		}

		@Override
		public void updateTree(PhTree<?> tree, int dims, int nEntries, Object rootId) {
			this.tree = tree;
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> PhTree<T> loadTree() {
			return (PhTree<T>) tree;
		}

		@Override
		public void flush() {
			// nothing 
		}
	}
	
	public Object loadNode(Object o);
	
	/**
	 * Register a new node.
	 * @param o the new node
	 * @return A node identifier
	 */
	public Object registerNode(Externalizable o);
	public void updateNode(Externalizable o);
	
	public String getDescription();
	public int statsGetPageReads();
	public int statsGetPageWrites();
	public void statsReset();

	public void writeTree(PhTree<?> tree, int dims);

	public void updateTree(PhTree<?> tree, int dims, int nEntries, Object rootId);

	public <T> PhTree<T> loadTree();
	
	public void flush();

	public static void write(Object[] values, ObjectOutput out) throws IOException {
		out.writeShort(values.length);
		for (int i = 0; i < values.length; i++) {
			Object v = values[i];
			int vi = v != null ? (int)v : -1;
			out.writeInt(vi);
		}
	}

	public static Object[] read(ObjectInput in) throws IOException {
		int size = in.readShort();
		Object[] ret = new Object[size];
		for (int i = 0; i < size; i++) {
			int vi = in.readInt();
			ret[i] = vi == -1 ? null : Integer.valueOf(vi);
		}
		return ret;
	}
}