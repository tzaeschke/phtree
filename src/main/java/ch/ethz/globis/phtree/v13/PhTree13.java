/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable. All rights reserved.
 *
 * This file is part of the PH-Tree project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.ethz.globis.phtree.v13;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhDistanceL;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhRangeQuery;
import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.PhTreeConfig;
import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.*;
import ch.ethz.globis.phtree.util.unsynced.LongArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectPool;

import static ch.ethz.globis.phtree.PhTreeHelper.*;
import static ch.ethz.globis.phtree.PhTreeHelper.maskNull;

/**
 * n-dimensional index (quad-/oct-/n-tree).
 *
 *
 * Version 13:
 * Version 13SP: Based on Version 11. Some optimizations, for example store HC-Pos in postFix.
 * 				Version 13SP has 'synchronized' object pools and NT nodes for high dim.
 * 			    Version 13 has local unsynchronized pools. It also has the NT tree removed.
 * 
 * Version 12: This was an attempt at a persistent version.
 * 
 * Version 11: Use of NtTree for Nodes
 *             'null' values are replaced by NULL, this allows removal of AHC-exists bitmap
 *             Removal of recursion (and reimplementation) for get/insert/delete/update 
 * 
 * Version 10b: Moved infix into parent node.
 * 
 * Version 10: Store sub-nodes and postfixes in a common structure (one list/HC of key, one array)
 *             Advantages: much easier iteration through node, replacement of sub/post during 
 *             updates w/o bit shifting, can check infix without accessing sub-node (good for I/O).
 * 
 * Version 8b: Extended array pooling to all arrays
 * 
 * Version 8: Use 64bit depth everywhere. This should simplify a number of methods, especially
 *            regarding negative values.
 * 
 * Version 7: Uses PhEntry to store/return keys.
 *
 * Version 5: moved postCnt/subCnt into node.
 *
 * Version 4: Using long[] instead of int[]
 *
 * Version 3: This includes values for each key.
 *
 * Storage:
 * - classic: One node per combination of bits. Unused nodes can be cut off.
 * - use prefix-truncation: a node may contain a series of unique bit combinations
 *
 * Hypercube: expanded byte array that contains 2^DIM references to sub-nodes (and posts, depending 
 * on implementation)
 * Linearization: Storing Hypercube as paired array of index / non_null_reference 
 *
 * See also : T. Zaeschke, C. Zimmerli, M.C. Norrie; 
 * "The PH-Tree -- A Space-Efficient Storage Structure and Multi-Dimensional Index", 
 * (SIGMOD 2014)
 *
 * @author ztilmann (Tilmann Zaeschke)
 * 
 * @param <T> The value type of the tree 
 *
 */
public class PhTree13<T> implements PhTree<T> {

	//Enable HC incrementer / iteration
	public static final boolean HCI_ENABLED = true;
	//Enable AHC mode in nodes
	static final boolean AHC_ENABLED = true;

	//This threshold is used to decide during query iteration whether the first value
	//should be found by binary search or by full scan.
	public static final int LHC_BINARY_SEARCH_THRESHOLD = 50;
	
	static final int DEPTH_64 = 64;
	
	private static final int NO_INSERT_REQUIRED = Integer.MAX_VALUE;


	//Dimension. This is the number of attributes of an entity.
	private final int dims;

	private int nEntries = 0;

	private Node root = null;

	private final ObjectPool<Node> nodePool;
	private final ObjectArrayPool<Object> refPool;
	private final LongArrayPool bitPool;

	Node getRoot() {
		return root;
	}

	public PhTree13(int dim) {
		this.dims = dim;
		this.nodePool = ObjectPool.create(Node::createEmpty);
		this.refPool = ObjectArrayPool.create();
		this.bitPool = LongArrayPool.create();
		debugCheck();
	}

	public PhTree13(PhTreeConfig cnf) {
		this(cnf.getDimActual());
		if (cnf.getConcurrencyType() != PhTreeConfig.CONCURRENCY_NONE) {
			throw new UnsupportedOperationException("type= " + cnf.getConcurrencyType());
		}
	}

	void increaseNrEntries() {
		nEntries++;
	}

	void decreaseNrEntries() {
		nEntries--;
	}

	@Override
	public int size() {
		return nEntries;
	}

	@Override
	public PhTreeStats getStats() {
		return getStats(0, getRoot(), new PhTreeStats(DEPTH_64));
	}

	private PhTreeStats getStats(int currentDepth, Node node, PhTreeStats stats) {
		stats.nNodes++;
		if (node.isAHC()) {
			stats.nAHC++;
		}
		stats.infixHist[node.getInfixLen()]++;
		stats.nodeDepthHist[currentDepth]++;
		int size = node.getEntryCount();
		stats.nodeSizeLogHist[32-Integer.numberOfLeadingZeros(size)]++;
		
		currentDepth += node.getInfixLen();
		stats.q_totalDepth += currentDepth;

		for (Object o: node.values()) {
			if (o instanceof Node) {
				getStats(currentDepth + 1, (Node) o, stats);
			} else if (o != null) {
				stats.q_nPostFixN[currentDepth]++;
			}
		}

		final int REF = 4;//bytes for a reference
		// this +  value[] + ba[] + ind() + isHC + postLen + infLen + nEntries
		stats.size += align8(12 + REF + REF + REF + 1 + 1 + 1 + 4);
		//count children
		int nChildren = node.getEntryCount();
		stats.size += 16 + align8(Bits.arraySizeInByte(node.ba()));
		stats.size += node.values() != null ? 16 + align8(node.values().length * REF) : 0;
		if (nChildren == 1 && (node != getRoot()) && nEntries > 1) {
			//This should not happen! Except for a root node if the tree has <2 entries.
			System.err.println("WARNING: found lonely node...");
		}
		if (nChildren == 0 && (node != getRoot())) {
			//This should not happen! Except for a root node if the tree has <2 entries.
			System.err.println("WARNING: found ZOMBIE node...");
		}
		if (dims<=31 && node.getEntryCount() > (1L<<dims)) {
			System.err.println("WARNING: Over-populated node found: ec=" + node.getEntryCount());
		}
		//check space
		int baS = node.calcArraySizeTotalBits(node.getEntryCount(), dims);
		baS = Bits.calcArraySize(baS);
		if (baS < node.ba().length) {
			System.err.println("Array too large: " + node.ba().length + " - " + baS + " = " + 
					(node.ba().length - baS));
		}
		stats.nTotalChildren += nChildren;
		
		return stats;
	}


	@SuppressWarnings("unchecked")
	@Override
	public T put(long[] key, T value) {
		Object nonNullValue = value == null ? PhTreeHelper.NULL : value;
		if (getRoot() == null) {
			insertRoot(key, nonNullValue);
			return null;
		}

		Object o = getRoot();
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doInsertIfMatching(key, nonNullValue, this);
		}
		return (T) o;
    }

    private void insertRoot(long[] key, Object value) {
        root = Node.createNode(dims, 0, DEPTH_64-1, this);
        long pos = posInArray(key, root.getPostLen());
        root.addPostPIN(pos, -1, key, value, this);
        increaseNrEntries();
    }

	@Override
	public boolean contains(long... key) {
		Object o = getRoot();
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doIfMatching(key, true, null, null, null, this);
		}
		return o != null;
	}


	@SuppressWarnings("unchecked")
	@Override
	public T get(long... key) {
		Object o = getRoot();
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doIfMatching(key, true, null, null, null, this);
		}
		return o == PhTreeHelper.NULL ? null : (T) o;
	}


	/**
	 * A value-set is an object with n=DIM values.
	 * @param key key to insert
	 * @return true if the value was found
	 */
	@SuppressWarnings("unchecked")
	@Override
	public T remove(long... key) {
		Object o = getRoot();
		Node parentNode = null;
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doIfMatching(key, false, parentNode, null, null, this);
			parentNode = currentNode;
		}
		return (T) o;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T update(long[] oldKey, long[] newKey) {
		Node[] stack = new Node[64];
		int stackSize = 0;
		
		Object o = getRoot();
		Node parentNode = null;
		final int[] insertRequired = new int[]{NO_INSERT_REQUIRED};
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			stack[stackSize++] = currentNode;
			o = currentNode.doIfMatching(oldKey, false, parentNode, newKey, insertRequired, this);
			parentNode = currentNode;
		}
		
		Object value = o == PhTreeHelper.NULL ? null : o;

		//traverse the tree from bottom to top
		//this avoids extracting and checking infixes.
		if (insertRequired[0] != NO_INSERT_REQUIRED) {
			//ignore lowest node, except if it is the root node
			if (stack[stackSize-1].getEntryCount() == 0 && stackSize > 1) {
				//The node may have been deleted
				stackSize--;
			}
			while (stackSize > 0) {
				if (stack[--stackSize].getPostLen()+1 >= insertRequired[0]) {
					o = stack[stackSize];
					while (o instanceof Node) {
						Node currentNode = (Node) o;
						o = currentNode.doInsertIfMatching(newKey, value, this);
					}
					insertRequired[0] = NO_INSERT_REQUIRED;
					break;
				}
			}
		}		
		
		return (T) value;
	}



	// Overrides of new  Java 8 methods

	@Override
	public T getOrDefault(long[] key, T defaultValue) {
		T e = get(key);
		return e == null ? defaultValue : e;
	}

	@Override
	public T putIfAbsent(long[] key, T value) {
		if (getRoot() == null) {
			insertRoot(key, maskNull(value));
			return null;
		}

		T o = get(key);
		if (o == null) {
			put(key, value);
		}
		return o;
	}

	@Override
	public boolean remove(long[] key, T value) {
		boolean[] o = new boolean[1];
		computeIfPresent(key, (longs, t) -> (o[0] = Objects.equals(t, value)) ? null : t);
		return o[0];
	}

	@Override
	public boolean replace(long[] key, T oldValue, T newValue) {
		if (getRoot() == null) {
			return false;
		}

		Object o = getRoot();
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doIfMatching(key, true, null, null, null, this);
		}

		if (o != null && Objects.equals(o, oldValue)) {
			put(key, newValue);
			return true;
		}
		return false;
	}

	@Override
	public T replace(long[] key, T value) {
		if (getRoot() == null) {
			return null;
		}

		Object o = getRoot();
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doIfMatching(key, true, null, null, null, this);
		}

		if (o != null) {
			put(key, value);
			return PhTreeHelper.unmaskNull(o);
		}
		return null;
	}

	@Override
	public T computeIfAbsent(long[] key, Function<long[], ? extends T> mappingFunction) {
		if (getRoot() == null) {
			T newValue = mappingFunction.apply(key);
			if (newValue != null) {
				insertRoot(key, maskNull(newValue));
			}
			return newValue;
		}

		T currentValue = get(key);
		if (currentValue == null) {
			T newValue = mappingFunction.apply(key);
			if (newValue != null) {
				put(key, newValue);
			}
			return newValue;
		}
		return currentValue;
	}

    @SuppressWarnings("unchecked")
	@Override
	public T computeIfPresent(long[] key, BiFunction<long[], ? super T, ? extends T> remappingFunction) {
		if (getRoot() == null) {
			return null;
		}

        Object o = getRoot();
        Node parentNode = null;
        while (o instanceof Node) {
            Node currentNode = (Node) o;
            o = currentNode.doCompute(key, false, parentNode, this, remappingFunction);
            parentNode = currentNode;
        }
        return (T) o;
	}

    @SuppressWarnings("unchecked")
	@Override
	public T compute(long[] key, BiFunction<long[], ? super T, ? extends T> remappingFunction) {
		if (getRoot() == null) {
			T newValue = remappingFunction.apply(key, null);
			if (newValue != null) {
				insertRoot(key, maskNull(newValue));
			}
			return newValue;
		}

        Object o = getRoot();
        Node parentNode = null;
        while (o instanceof Node) {
            Node currentNode = (Node) o;
            o = currentNode.doCompute(key, true, parentNode, this, remappingFunction);
            parentNode = currentNode;
        }
        return (T) o;
    }

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + 
				" AHC/LHC=" + Node.AHC_LHC_BIAS +  
				" AHC-on=" + AHC_ENABLED +  
				" HCI-on=" + HCI_ENABLED +  
				" NtLimit=NT_DISABLED" +
				" DEBUG=" + PhTreeHelper.DEBUG;
	}

	@Override
	public String toStringPlain() {
		StringBuilderLn sb = new StringBuilderLn();
		if (getRoot() != null) {
			toStringPlain(sb, getRoot(), new long[dims]);
		}
		return sb.toString();
	}

	private void toStringPlain(StringBuilderLn sb, Node node, long[] key) {
		for (int i = 0; i < 1L << dims; i++) {
			Object o = node.getEntry(i, key);
			if (o == null) {
				continue;
			}
			//inner node?
			if (o instanceof Node) {
				toStringPlain(sb, (Node) o, key);
			} else {
				sb.append(Bits.toBinary(key, DEPTH_64));
				sb.appendLn("  v=" + o);
			}
		}
	}


	@Override
	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		if (getRoot() != null) {
			toStringTree(sb, 0, getRoot(), new long[dims], true);
		}
		return sb.toString();
	}

	private void toStringTree(StringBuilderLn sb, int currentDepth, Node node, long[] key, 
			boolean printValue) {
		String ind = "*";
		for (int i = 0; i < currentDepth; i++) {
			ind += "-";
		}
		sb.append( ind + "il=" + node.getInfixLen() + " pl=" + (node.getPostLen()) + 
				" ec=" + node.getEntryCount() + " inf=[");

		//for a leaf node, the existence of a sub just indicates that the value exists.
		if (node.getInfixLen() > 0) {
			long mask = (-1L) << node.getInfixLen();
			mask = ~mask;
			mask <<= node.getPostLen()+1;
			for (int i = 0; i < dims; i++) {
				sb.append(Bits.toBinary(key[i] & mask) + ",");
			}
		}
		currentDepth += node.getInfixLen();
		sb.appendLn("]  " + node);

		//To clean previous postfixes.
		for (int i = 0; i < 1L << dims; i++) {
			Object o = node.getEntry(i, key);
			if (o == null) {
				continue;
			}
			if (o instanceof Node) {
				sb.appendLn(ind + "# " + i + "  +");
				toStringTree(sb, currentDepth + 1, (Node) o, key, printValue);
			}  else {
				//post-fix
				sb.append(ind + Bits.toBinary(key, DEPTH_64));
				sb.append("  hcPos=" + i);
				if (printValue) {
					sb.append("  v=" + o);
				}
				sb.appendLn("");
			}
		}
	}


	@Override
	public PhExtent<T> queryExtent() {
		return new PhIteratorFullNoGC<>(this, null).reset();
	}


	/**
	 * Performs a rectangular window query. The parameters are the min and max keys which 
	 * contain the minimum respectively the maximum keys in every dimension.
	 * @param min Minimum values
	 * @param max Maximum values
	 * @return Result iterator.
	 */
	@Override
	public PhQuery<T> query(long[] min, long[] max) {
		if (min.length != dims || max.length != dims) {
			throw new IllegalArgumentException("Invalid number of arguments: " + min.length +  
					" / " + max.length + "  DIM=" + dims);
		}
		PhQuery<T> q = new PhIteratorNoGC<>(this, null);
		q.reset(min, max);
		return q;
	}

	/**
	 * Performs a rectangular window query. The parameters are the min and max keys which 
	 * contain the minimum respectively the maximum keys in every dimension.
	 * @param min Minimum values
	 * @param max Maximum values
	 * @return Result list.
	 */
	@Override
	public List<PhEntry<T>> queryAll(long[] min, long[] max) {
		return queryAll(min, max, Integer.MAX_VALUE, null, PhMapper.PVENTRY());
	}
	
	/**
	 * Performs a rectangular window query. The parameters are the min and max keys which 
	 * contain the minimum respectively the maximum keys in every dimension.
	 * @param min Minimum values
	 * @param max Maximum values
	 * @return Result list.
	 */
	@Override
	public <R> List<R> queryAll(long[] min, long[] max, int maxResults, 
			PhFilter filter, PhMapper<T, R> mapper) {
		if (min.length != dims || max.length != dims) {
			throw new IllegalArgumentException("Invalid number of arguments: " + min.length +  
					" / " + max.length + "  DIM=" + dims);
		}
		
		if (getRoot() == null) {
			return new ArrayList<>();
		}
		
//		if (mapper == null) {
//			mapper = (PhMapper<T, R>) PhMapper.PVENTRY();
//		}
		
		PhResultList<T, R> list = new PhResultList.MappingResultList<>(null, mapper,
				() -> new PhEntry<>(new long[dims], null));
		
		NodeIteratorListReuse<T, R> it = new NodeIteratorListReuse<>(dims, list);
		return it.resetAndRun(getRoot(), min, max, maxResults);
	}

	@Override
	public int getDim() {
		return dims;
	}

	@Override
	public int getBitDepth() {
		return PhTree13.DEPTH_64;
	}

	/**
	 * Locate nearest neighbours for a given point in space.
	 * @param nMin number of values to be returned. More values may or may not be returned when 
	 * several have	the same distance.
	 * @param v center point
	 * @return Result iterator.
	 */
	@Override
	public PhKnnQuery<T> nearestNeighbour(int nMin, long... v) {
		//return new PhQueryKnnMbbPP<T>(this).reset(nMin, PhDistanceL.THIS, v);
		//return new PhQueryKnnMbbPPList<T>(this).reset(nMin, PhDistanceL.THIS, v);
		return new PhQueryKnnHS<>(this).reset(nMin, PhDistanceL.THIS, v);
	}

	@Override
	public PhKnnQuery<T> nearestNeighbour(int nMin, PhDistance dist,
			PhFilter dimsFilter, long... center) {
		//return new PhQueryKnnMbbPP<T>(this).reset(nMin, dist, center);
		//return new PhQueryKnnMbbPPList<T>(this).reset(nMin, dist, center);
		return new PhQueryKnnHS<>(this).reset(nMin, dist, center);
	}

	@Override
	public PhRangeQuery<T> rangeQuery(double dist, long... center) {
		return rangeQuery(dist, null, center);
	}

	@Override
	public PhRangeQuery<T> rangeQuery(double dist, PhDistance optionalDist, long...center) {
		PhFilterDistance filter = new PhFilterDistance();
		if (optionalDist == null) {
			optionalDist = PhDistanceL.THIS;
		}
		filter.set(center, optionalDist, dist);
		PhQuery<T> q = new PhIteratorNoGC<>(this, filter);
		PhRangeQuery<T> qr = new PhRangeQuery<>(q, this, optionalDist, filter);
		qr.reset(dist, center);
		return qr;
	}

	/**
	 * Remove all entries from the tree.
	 */
	@Override
	public void clear() {
		root = null;
		nEntries = 0;
	}

	/**
	 * Best HC incrementer ever. 
	 * @param v current value
	 * @param min min mask
	 * @param max max mask
	 * @return next valid value or min.
	 */
	static long inc(long v, long min, long max) {
		//first, fill all 'invalid' bits with '1' (bits that can have only one value).
		long r = v | (~max);
		//increment. The '1's in the invalid bits will cause bitwise overflow to the next valid bit.
		r++;
		//remove invalid bits.
		return (r & max) | min;

		//return -1 if we exceed 'max' and cause an overflow or return the original value. The
		//latter can happen if there is only one possible value (all filter bits are set).
		//The <= is also owed to the bug tested in testBugDecrease()
		//return (r <= v) ? -1 : r;
	}

	ObjectPool<Node> nodePool() {
		return nodePool;
	}

	ObjectArrayPool<Object> objPool() {
		return refPool;
	}

	LongArrayPool longPool() {
		return bitPool;
	}
}

