/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
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
package ch.ethz.globis.phtree.v16hd;

import static ch.ethz.globis.phtree.PhTreeHelper.align8;
import static ch.ethz.globis.phtree.PhTreeHelper.debugCheck;
import static ch.ethz.globis.phtree.PhTreeHelperHD.posInArrayHD;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhDistanceL;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhFilterWindow;
import ch.ethz.globis.phtree.PhRangeQuery;
import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.PhTreeConfig;
import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.PhMapper;
import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.util.unsynced.LongArrayPool;
import ch.ethz.globis.phtree.util.unsynced.ObjectPool;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.bst.BSTIteratorAll;
import ch.ethz.globis.phtree.v16hd.bst.BSTPool;

/**
 * n-dimensional index (quad-/oct-/n-tree).
 * 
 * Version 16: BST-only, directly integrated with Node
 * 
 * Version 15: BST-Only
 * 
 * Version 14: Removed NT (nested tree) and replaced it with hierarchical table.
 * 
 * Version 13: Based on Version 11. Some optimizations, for example store HC-Pos in postFix.
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
public class PhTree16HD<T> implements PhTree<T> {

	//Enable HC incrementer / iteration
	public static final boolean HCI_ENABLED = true; 
	
	static final int DEPTH_64 = 64;
	
	private static final int NO_INSERT_REQUIRED = Integer.MAX_VALUE;

	private final int maxLeafN;// = 100;//10;//340;
	/** Max number of keys in inner page (there can be max+1 page-refs) */
	private final int maxInnerN;// = 100;//11;//509;

	//Dimension. This is the number of attributes of an entity.
	private final int dims;

	private int nEntries;

	private Node root = null;

	Node getRoot() {
		return root;
	}

    private final ObjectPool<Node> nodePool;
    private final ObjectPool<UpdateInfo> uiPool;
    private final LongArrayPool bitPool;
    private final BSTPool bstPool;

	public PhTree16HD(int dim) {
		dims = dim;
        this.nodePool = ObjectPool.create(Node::new);
        this.uiPool = ObjectPool.create(UpdateInfo::new);
        this.bitPool = LongArrayPool.create();
        this.bstPool = BSTPool.create();
		debugCheck();

		switch (dims) {
		case 1: maxLeafN = 2; maxInnerN = 2; break;
		case 2: maxLeafN = 4; maxInnerN = 2; break;
		case 3: maxLeafN = 8; maxInnerN = 2; break;
		case 4: maxLeafN = 16; maxInnerN = 2; break;
		case 5: maxLeafN = 16; maxInnerN = 2+1; break;
		case 6: maxLeafN = 16; maxInnerN = 4+1; break;
		case 7: maxLeafN = 16; maxInnerN = 8+1; break;
		case 8: maxLeafN = 16; maxInnerN = 16+1; break;
		case 9: maxLeafN = 32; maxInnerN = 16+1; break;
		case 10: maxLeafN = 32; maxInnerN = 32+1; break;
		case 11: maxLeafN = 32; maxInnerN = 64+1; break;
		case 12: maxLeafN = 64; maxInnerN = 64+1; break;
		default: maxLeafN = 100; maxInnerN = 100; break;
		}
	}

	public PhTree16HD(PhTreeConfig cnf) {
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
		if (getRoot() == null) {
			return new PhTreeStats(DEPTH_64);
		}
		return getStats(0, getRoot(), new PhTreeStats(DEPTH_64));
	}

	private PhTreeStats getStats(int currentDepth, Node node, PhTreeStats stats) {
		stats.nNodes++;
		stats.infixHist[node.getInfixLen()]++;
		stats.nodeDepthHist[currentDepth]++;
		int size = node.getEntryCount();
		stats.nodeSizeLogHist[32-Integer.numberOfLeadingZeros(size)]++;
		
		currentDepth += node.getInfixLen();
		stats.q_totalDepth += currentDepth;

		List<BSTEntry> entries = new ArrayList<>();
		node.getStats(stats, entries);
		for (BSTEntry child: entries) {
			if (child.getValue() instanceof Node) {
				Node sub = (Node) child.getValue();
				if (sub.getInfixLen() + 1 + sub.getPostLen() != node.getPostLen()) {
					throw new IllegalStateException();
				}
				getStats(currentDepth + 1, sub, stats);
			} else {
				stats.q_nPostFixN[currentDepth]++;
			}
		}
		if (entries.size() != node.getEntryCount()) {
			System.err.println("WARNING: entry count mismatch: a-found/ec=" + 
					entries.size() + "/" + node.getEntryCount());
		}
		
		final int REF = 4;//bytes for a reference
		// this +  value[] + ba[] + ind() + isHC + postLen + infLen + nEntries
		stats.size += align8(12 + REF + REF + REF + 1 + 1 + 1 + 4);
		//count children
		int nChildren = node.getEntryCount();
		stats.size += 16;
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
		stats.nTotalChildren += nChildren;
		
		return stats;
	}


	@SuppressWarnings("unchecked")
	@Override
	public T put(long[] key, T value) {
		long[] hcBuf = BitsHD.newArray(dims);
		Object nonNullValue = value == null ? PhTreeHelper.NULL : value;
		if (getRoot() == null) {
			insertRoot(key, nonNullValue, hcBuf);
			return null;
		}

		Object o = getRoot();
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doInsertIfMatching(key, nonNullValue, this, hcBuf);
		}
		return (T) o;
    }

    private void insertRoot(long[] key, Object value, long[] hcBuf) {
        root = Node.createNode(dims, 0, DEPTH_64-1, this);
        posInArrayHD(key, root.getPostLen(), hcBuf);
        root.addEntry(hcBuf, key, value, this);
        increaseNrEntries();
    }

	@Override
	public boolean contains(long... key) {
		long[] hcBuf = BitsHD.newArray(dims);
		Object o = getRoot();
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doIfMatching(key, true, null, null, this, hcBuf);
		}
		return o != null;
	}


	@SuppressWarnings("unchecked")
	@Override
	public T get(long... key) {
		long[] hcBuf = BitsHD.newArray(dims);
		Object o = getRoot();
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doIfMatching(key, true, null, null, this, hcBuf);
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
		long[] hcBuf = BitsHD.newArray(dims);
		Object o = getRoot();
		Node parentNode = null;
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			o = currentNode.doIfMatching(key, false, parentNode, null, this, hcBuf);
			parentNode = currentNode;
		}
		return (T) o;
	}

	public static class UpdateInfo {
		long[] newKey;
		int insertRequired = NO_INSERT_REQUIRED;
		UpdateInfo init(long[] newKey) {
			this.newKey = newKey;
			return this;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public T update(long[] oldKey, long[] newKey) {
		long[] hcBuf = BitsHD.newArray(dims);
		Node[] stack = new Node[64];
		int stackSize = 0;
		
		Object o = getRoot();
		Node parentNode = null;
		final UpdateInfo ui = uiPool.get().init(newKey);
		
		while (o instanceof Node) {
			Node currentNode = (Node) o;
			stack[stackSize++] = currentNode;
			o = currentNode.doIfMatching(oldKey, false, parentNode, ui, this, hcBuf);
			parentNode = currentNode;
		}
		
		Object value = o == PhTreeHelper.NULL ? null : o;

		//traverse the tree from bottom to top
		//this avoids extracting and checking infixes.
		if (ui.insertRequired != NO_INSERT_REQUIRED) {
			//ignore lowest node, except if it is the root node
			if (stack[stackSize-1].getEntryCount() == 0 && stackSize > 1) {
				//The node may have been deleted
				stackSize--;
			}
			while (stackSize > 0) {
				if (stack[--stackSize].getPostLen()+1 >= ui.insertRequired) {
					o = stack[stackSize];
					while (o instanceof Node) {
						Node currentNode = (Node) o;
						o = currentNode.doInsertIfMatching(newKey, value, this, hcBuf);
					}
					ui.insertRequired = NO_INSERT_REQUIRED;
					break;
				}
			}
		}		
		uiPool.offer(ui);
		return (T) value;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + 
				" HCI-on=" + HCI_ENABLED +  
				" BstSize=" + maxInnerN + "/" + maxLeafN +  
				" DEBUG=" + PhTreeHelper.DEBUG;
	}

	@Override
	public String toStringPlain() {
		StringBuilderLn sb = new StringBuilderLn();
		if (getRoot() != null) {
			toStringPlain(sb, getRoot());
		}
		return sb.toString();
	}

	private void toStringPlain(StringBuilderLn sb, Node node) {
		BSTIteratorAll iter = node.iterator();
		while (iter.hasNextEntry()) {
			BSTEntry o = iter.nextEntry();
			//inner node?
			if (o.getValue() instanceof Node) {
				toStringPlain(sb, (Node) o.getValue());
			} else {
				sb.append(Bits.toBinary(o.getKdKey(), DEPTH_64));
				sb.appendLn("  v=" + o.getValue());
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

	private void toStringTree(StringBuilderLn sb, int currentDepth, Node node, long[] prefix, boolean printValue) {
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
				sb.append(Bits.toBinary(prefix[i] & mask) + ",");
			}
		}
		currentDepth += node.getInfixLen();
		sb.appendLn("]  " + node);

		//To clean previous postfixes.
		BSTIteratorAll iter = node.iterator();
		while (iter.hasNextEntry()) {
			BSTEntry o = iter.nextEntry();
			if (o.getValue() instanceof Node) {
				sb.appendLn(ind + "# " + Arrays.toString(o.getKey()) + "  +");
				toStringTree(sb, currentDepth + 1, (Node) o.getValue(), o.getKdKey(), printValue);
			}  else {
				//post-fix
				sb.append(ind + Bits.toBinary(o.getKdKey(), DEPTH_64));
				sb.append("  hcPos=" + Arrays.toString(o.getKey()));
				if (printValue) {
					sb.append("  v=" + o.getValue());
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
	 * @param filter A filter function. The iterator will only return results that match the filter. 
	 * @return Result iterator.
	 */
	@Override
	public PhQuery<T> query(long[] min, long[] max, PhFilter filter) {
		if (min.length != dims || max.length != dims) {
			throw new IllegalArgumentException("Invalid number of arguments: " + min.length +  
					" / " + max.length + "  DIM=" + dims);
		}
		PhQuery<T> q = new PhIteratorNoGC<>(this, filter);
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
		
		if (filter == null) {
			PhFilterWindow wf = new PhFilterWindow();
			wf.set(min, max);
			filter = wf;
		}
		
		PhResultList<T, R> list = new PhResultList.MappingResultList<>(filter, mapper,
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
		return PhTree16HD.DEPTH_64;
	}

	/**
	 * Locate nearest neighbors for a given point in space.
	 * @param nMin number of values to be returned. More values may or may not be returned when 
	 * several have	the same distance.
	 * @param v center point
	 * @return Result iterator.
	 */
	@Override
	public PhKnnQuery<T> nearestNeighbour(int nMin, long... v) {
		return new PhQueryKnnHS<>(this).reset(nMin, PhDistanceL.THIS, v);
		//return new PhQueryKnnHSZ<>(this).reset(nMin, PhDistanceL.THIS, v);
	}

	@Override
	public PhKnnQuery<T> nearestNeighbour(int nMin, PhDistance dist,
			PhFilter dimsFilter, long... center) {
		return new PhIteratorKnn<>(this, nMin, center, dist);
		//return new PhQueryKnnHS<>(this).reset(nMin, dist, center);
		//return new PhQueryKnnHSZ<>(this).reset(nMin, dist, center);
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

    ObjectPool<Node> nodePool() {
        return nodePool;
    }

    LongArrayPool longPool() {
        return bitPool;
    }

    public BSTPool bstPool() {
        return bstPool;
    }
}

