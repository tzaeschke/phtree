/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v8;

import static ch.ethz.globis.phtree.PhTreeHelper.applyHcPos;
import static ch.ethz.globis.phtree.PhTreeHelper.debugCheck;
import static ch.ethz.globis.phtree.PhTreeHelper.getMaxConflictingBitsWithMask;
import static ch.ethz.globis.phtree.PhTreeHelper.posInArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhDistanceL;
import ch.ethz.globis.phtree.PhEntry;
import ch.ethz.globis.phtree.PhFilter;
import ch.ethz.globis.phtree.PhFilterDistance;
import ch.ethz.globis.phtree.PhRangeQuery;
import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.PhTreeConfig;
import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.PhMapper;
import ch.ethz.globis.phtree.util.PhTreeStats;
import ch.ethz.globis.phtree.util.StringBuilderLn;

/**
 * n-dimensional index (quad-/oct-/n-tree).
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
 *
 * Hypercube: expanded byte array that contains 2^DIM references to sub-nodes (and posts, depending 
 * on implementation)
 * Linearization: Storing Hypercube as paired array of index / non_null_reference 
 *
 * @author ztilmann (Tilmann Zaeschke)
 *
 * @param <T> The value type of the tree 
 *
 */
public final class PhTree8<T> implements PhTree<T> {

	//Enable HC incrementer / iteration
	static final boolean HCI_ENABLED = true; 
	
	static final int DEPTH_64 = 64;

	//Dimension. This is the number of attributes of an entity.
	private final int DIM;

	private final AtomicInteger nEntries = new AtomicInteger();
	private final AtomicInteger nNodes = new AtomicInteger();

	static final int UNKNOWN = -1;

    private PhOperations<T> operations = new PhOperationsSimple<>(this);

    final long[] MIN;
    private final long[] MAX;
    
	static final class NodeEntry<T> extends PhEntry<T> {
        transient ReentrantLock lock;

		Node<T> node;
		NodeEntry(long[] key, T value, boolean useLock) {
			super(key, value);
			this.node = null;
			lock = useLock ? new ReentrantLock() : null;
		}
		NodeEntry(Node<T> node, boolean useLock) {
			super(null, null);
			this.node = node;
			lock = useLock ? new ReentrantLock() : null;
		}

		void setNode(Node<T> node) {
			set(null, null);
			this.node = node;
		}
		void setPost(long[] key, T val) {
			set(key, val);
			this.node = null;
		}
	}


	private Node<T> root = null;

	Node<T> getRoot() {
		return root;
	}

    void changeRoot(Node<T> newRoot) {
        this.root = newRoot;
    }

	public PhTree8(int dim) {
		DIM = dim;
		MIN = new long[DIM];
		Arrays.fill(MIN, Long.MIN_VALUE);
		MAX = new long[DIM];
		Arrays.fill(MAX, Long.MAX_VALUE);
		debugCheck();
	}

	public PhTree8(PhTreeConfig cnf) {
		DIM = cnf.getDimActual();
		MIN = new long[DIM];
		MAX = new long[DIM];
		Arrays.fill(MAX, Long.MAX_VALUE);
		Arrays.fill(MIN, Long.MIN_VALUE);
		debugCheck();
	}

	void increaseNrNodes() {
		nNodes.incrementAndGet();
	}

	void decreaseNrNodes() {
		nNodes.decrementAndGet();
	}

	void increaseNrEntries() {
		nEntries.incrementAndGet();
	}

	void decreaseNrEntries() {
		nEntries.decrementAndGet();
	}

	@Override
	public int size() {
		return nEntries.get();
	}

	@Override
	public PhTreeStats getStats() {
		if (getRoot() == null) {
			return new PhTreeStats(DEPTH_64);
		}
		return getQuality(0, getRoot(), new PhTreeStats(DEPTH_64));
	}

	private PhTreeStats getQuality(int currentDepth, Node<T> node, PhTreeStats stats) {
		stats.nNodes++;
		if (node.isPostHC()) {
			stats.nAHC++;
		}
		if (node.isSubHC()) {
			stats.nNtNodes++;
		}
		if (node.isPostNI()) {
			stats.nNT++;
		}
		stats.infixHist[node.getInfixLen()]++;
		stats.nodeDepthHist[currentDepth]++;
		int size = node.getPostCount() + node.getSubCount();
		stats.nodeSizeLogHist[32-Integer.numberOfLeadingZeros(size)]++;
		
		currentDepth += node.getInfixLen();
		stats.q_totalDepth += currentDepth;

		if (node.subNRef() != null) {
			for (Node<T> sub: node.subNRef()) {
				if (sub != null) {
					getQuality(currentDepth + 1, sub, stats);
				}
			}
		} else {
			if (node.ind() != null) {
				for (NodeEntry<T> n: node.ind()) {
					if (n.node != null) {
						getQuality(currentDepth + 1, n.node, stats);
					}
				}
			}
		}

		//count post-fixes
		stats.q_nPostFixN[currentDepth] += node.getPostCount();

		return stats;
	}


	@Override
	public T put(long[] key, T value) {
        return operations.put(key, value);
    }

    void insertRoot(long[] key, T value) {
        root = operations.createNode(this, 0, DEPTH_64-1, 1);
        //calcPostfixes(valueSet, root, 0);
        long pos = posInArray(key, root.getPostLen());
        root.addPost(pos, key, value);
        increaseNrEntries();
    }

	@Override
	public boolean contains(long... key) {
		if (getRoot() == null) {
			return false;
		}
		return contains(key, getRoot());
	}


	private boolean contains(long[] key, Node<T> node) {
		if (node.getInfixLen() > 0) {
			long mask = ~((-1l)<<node.getInfixLen()); // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
			int shiftMask = node.getPostLen()+1;
			//mask <<= shiftMask; //last bit is stored in bool-array
			mask = shiftMask==64 ? 0 : mask<<shiftMask;
			for (int i = 0; i < key.length; i++) {
				if (((key[i] ^ node.getInfix(i)) & mask) != 0) {
					//infix does not match
					return false;
				}
			}
		}

		long pos = posInArray(key, node.getPostLen());

		//NI-node?
		if (node.isPostNI()) {
			NodeEntry<T> e = node.getChildNI(pos);
			if (e == null) {
				return false;
			} else if (e.node != null) {
				return contains(key, e.node);
			}
			return node.postEquals(e.getKey(), key);
		}

		//check sub-node (more likely than postfix, because there can be more than one value)
		Node<T> sub = node.getSubNode(pos, DIM);
		if (sub != null) {
			return contains(key, sub);
		}

		//check postfix
		int pob = node.getPostOffsetBits(pos, DIM);
		if (pob >= 0) {
			return node.postEqualsPOB(pob, pos, key);
		}

		return false;
	}

	@Override
	public T get(long... key) {
		if (getRoot() == null) {
			return null;
		}
		return get(key, getRoot());
	}


	private T get(long[] key, Node<T> node) {
		if (node.getInfixLen() > 0) {
			long mask = (1l<<node.getInfixLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
			int shiftMask = node.getPostLen()+1;
			//mask <<= shiftMask; //last bit is stored in bool-array
			mask = shiftMask==64 ? 0 : mask<<shiftMask;
			for (int i = 0; i < key.length; i++) {
				if (((key[i] ^ node.getInfix(i)) & mask) != 0) {
					//infix does not match
					return null;
				}
			}
		}

		long pos = posInArray(key, node.getPostLen());

		//check sub-node (more likely than postfix, because there can be more than one value)
		Node<T> sub = node.getSubNode(pos, DIM);
		if (sub != null) {
			return get(key, sub);
		}

		//check postfix
		int pob = node.getPostOffsetBits(pos, DIM);
		if (pob >= 0) {
			if (node.postEqualsPOB(pob, pos, key)) {
				return node.getPostValuePOB(pob, pos, DIM);
			}
		}

		return null;
	}

	/**
	 * A value-set is an object with n=DIM values.
	 * @param key key to remove
	 * @return true if the value was found
	 */
	@Override
	public T remove(long... key) {
        return operations.remove(key);
	}

	int getConflictingInfixBits(long[] key, long[] infix, Node<T> node) {
		if (node.getInfixLen() == 0) {
			return 0;
		}
		long mask = (1l<<node.getInfixLen()) - 1l; // e.g. (0-->0), (1-->1), (8-->127=0x01111111)
		int maskOffset = node.getPostLen()+1;
		mask = maskOffset==64 ? 0 : mask<< maskOffset; //last bit is stored in bool-array
		return getMaxConflictingBitsWithMask(key, infix, mask);
	}

	long posInArrayFromInfixes(Node<T> node, int infixInternalOffset) {
		//n=DIM,  i={0..n-1}
		// i = 0 :  |   0   |   1   |
		// i = 1 :  | 0 | 1 | 0 | 1 |
		// i = 2 :  |0|1|0|1|0|1|0|1|
		//len = 2^n

		long pos = 0;
		for (int i = 0; i < DIM; i++) {
			pos <<= 1;
//			if (node.getInfixBit(i, infixInternalOffset)) {
//				pos |= 1L;
//			}
			pos |= node.getInfixBit(i, infixInternalOffset);
		}
		return pos;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + 
				" HCI-on=" + HCI_ENABLED +  
				" DEBUG=" + PhTreeHelper.DEBUG;
	}

	@Override
	public String toStringPlain() {
		StringBuilderLn sb = new StringBuilderLn();
		if (getRoot() != null) {
			toStringPlain(sb, getRoot(), new long[DIM]);
		}
		return sb.toString();
	}

	private void toStringPlain(StringBuilderLn sb, Node<T> node, long[] key) {
		//for a leaf node, the existence of a sub just indicates that the value exists.
		node.getInfix(key);

		for (int i = 0; i < 1L << DIM; i++) {
			applyHcPos(i, node.getPostLen(), key);
			//inner node?
			Node<T> sub = node.getSubNode(i, DIM);
			if (sub != null) {
				toStringPlain(sb, sub, key);
			}

			//post-fix?
			if (node.hasPostFix(i, DIM)) {
				node.getPost(i, key);
				sb.append(Bits.toBinary(key, DEPTH_64));
				sb.appendLn("  v=" + node.getPostValue(i, DIM));
			}
		}
	}


	@Override
	public String toStringTree() {
		StringBuilderLn sb = new StringBuilderLn();
		if (getRoot() != null) {
			toStringTree(sb, 0, getRoot(), new long[DIM], true);
		}
		return sb.toString();
	}

	private void toStringTree(StringBuilderLn sb, int currentDepth, Node<T> node, long[] key, 
			boolean printValue) {
		String ind = "*";
		for (int i = 0; i < currentDepth; i++) ind += "-";
		sb.append( ind + "il=" + node.getInfixLen() + " io=" + (node.getPostLen()+1) + 
				" sc=" + node.getSubCount() + " pc=" + node.getPostCount() + " inf=[");

		//for a leaf node, the existence of a sub just indicates that the value exists.
		node.getInfix(key);
		if (node.getInfixLen() > 0) {
			long[] inf = new long[DIM];
			node.getInfix(inf);
			sb.append(Bits.toBinary(inf, DEPTH_64));
			currentDepth += node.getInfixLen();
		}
		sb.appendLn("]");

		//To clean previous postfixes.
		for (int i = 0; i < 1L << DIM; i++) {
			applyHcPos(i, node.getPostLen(), key);
			Node<T> sub = node.getSubNode(i, DIM);
			if (sub != null) {
				sb.appendLn(ind + "# " + i + "  +");
				toStringTree(sb, currentDepth + 1, sub, key, printValue);
			}

			//post-fix?
			if (node.hasPostFix(i, DIM)) {
				T v = node.getPost(i, key);
				sb.append(ind + Bits.toBinary(key, DEPTH_64));
				if (printValue) {
					sb.append("  v=" + v);
				}
				sb.appendLn("");
			}
		}
	}


	@Override
	public PhExtent<T> queryExtent() {
		return new PhIteratorFullNoGC<T>(this, null).reset();
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
		if (min.length != DIM || max.length != DIM) {
			throw new IllegalArgumentException("Invalid number of arguments: " + min.length +  
					" / " + max.length + "  DIM=" + DIM);
		}
		//return new PhIteratorHighK<T>(getRoot(), min, max, DIM, DEPTH);
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
		if (min.length != DIM || max.length != DIM) {
			throw new IllegalArgumentException("Invalid number of arguments: " + min.length +  
					" / " + max.length + "  DIM=" + DIM);
		}
		
		if (getRoot() == null) {
			return new ArrayList<>();
		}
		
//		if (mapper == null) {
//			mapper = (PhMapper<T, R>) PhMapper.PVENTRY();
//		}
		
//		ArrayList<R> list = NodeIteratorList.query(root, valTemplate, min, max, 
//				DIM, true, maxResults, filter, mapper);
		ArrayList<R> list = NodeIteratorListReuse.query(root, min, max, 
				DIM, maxResults, filter, mapper);
		return list;
	}

	static final <T> boolean checkAndApplyInfix(Node<T> node, long[] valTemplate, 
			long[] rangeMin, long[] rangeMax) {
		//first check if node-prefix allows sub-node to contain any useful values
		int infixLen = node.getInfixLen();
		if (infixLen > 0) {
			int postLen = node.getPostLen();

			//assign infix
            //Shift in two steps in case they add up to 64.
			//int postHcInfixLen = postLen + 1 + infixLen;
  			long maskClean = (-1L) << (postLen + infixLen);
			maskClean <<= 1;

			//first, clean trailing bits
			//Mask for comparing the tempVal with the ranges, except for bit that have not been
			//extracted yet.
			long compMask = (-1L)<<(postLen + 1);
			for (int dim = 0; dim < valTemplate.length; dim++) {
				//there would be no need to extract it each time.
				//--> For high k, infixLen is usually 0 (except root node), so the check below very 
				//rarely fails. Is an optimisation really useful?
				long in = node.getInfix(dim);
				valTemplate[dim] = (valTemplate[dim] & maskClean) | in;
				if (valTemplate[dim] > rangeMax[dim] || 
						valTemplate[dim] < (rangeMin[dim]&compMask)) {
					return false;
				}
			}
		}

		return true;
	}

	@Override
	public int getDim() {
		return DIM;
	}

	@Override
	public int getBitDepth() {
		return PhTree8.DEPTH_64;
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
		return new PhQueryKnnMbbPP<T>(this).reset(nMin, PhDistanceL.THIS, v);
	}

	@Override
	public PhKnnQuery<T> nearestNeighbour(int nMin, PhDistance dist,
			PhFilter dimsFilter, long... center) {
		return new PhQueryKnnMbbPP<T>(this).reset(nMin, dist, center);
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
		PhQuery<T> q = new PhIteratorNoGC<T>(this, filter);
		PhRangeQuery<T> qr = new PhRangeQuery<>(q, this, optionalDist, filter);
		qr.reset(dist, center);
		return qr;
	}

	@Override
	public T update(long[] oldKey, long[] newKey) {
        return operations.update(oldKey, newKey);
	}
	

	/**
	 * Remove all entries from the tree.
	 */
	@Override
	public void clear() {
		root = null;
		nEntries.set(0);
		nNodes.set(0);
	}

	void adjustCounts(int deletedPosts, int deletedNodes) {
		nEntries.addAndGet(-deletedPosts);
		nNodes.addAndGet(-deletedNodes);
	}


	/**
	 * Best HC incrementer ever. 
	 * @param v
	 * @param min
	 * @param max
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
}

