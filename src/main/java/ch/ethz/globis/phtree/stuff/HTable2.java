/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.stuff;

/**
 * Hierachical lookup table.
 * 
 *  
 * Motivation/requirements:
 * - fast lookup, insertion and deletion; ideally O(log N) or better.
 *   This excludes LinkedList, ...
 * - space efficient (little overhead, ideally like an ArrayList with overhead less than one Object per entry.
 *   This excludes HashMap, LinkedList, binary trees, ...
 * - Insertion scalable in size without requiring copying arrays, and with low preallocation. 
 *   This excludes arrays or ArrayList (requires copying arrays for insertion/deletion)
 * - Fast deletion.
 * - Fast traversal/iteration over all elements.
 *  
 * The append() operation returns a unique key that allows fast lookup.
 * The remove() operation is a bit unusual: it allows removing an entry by key. Internally, it performs no shifting
 * of elements, but instead moves the last entry into the slot that was freed up by the removal. It then returns
 * the element that has been moved(!). The idea is that after a removal, the caller knows that an entry has a 
 * new slotID: The element with the new ID is the returned element and the new ID is the ID of the element
 * that was deleted.     
 *  
 * This datastructure has the following properties:
 * - Operations:
 *   Lookup by key: O(log N)
 *   Append: O(log N)
 *   Remove: O(log N)
 *   Space: O(N): All nodes (except one on each level) are 100% filled. 
 *   All are worst-case complexities! (Except space, up to O(log N/nodeSize) nodes are not 100% filled)  
 * - Insertion-ordered list, becomes Bag (unsorted list) after first removal.
 * 
 * Invariants:
 * - Every inner node has hat least two children and level>0.
 * - Very leaf node has at least one entry and level=0.
 * - Leaf nodes have level=0, root-node has the highest level. On the 'right' part of the tree, levels are
 *   not necessarily consecutive, for example, the root node with level=5 can reference an inner node with
 *   level=2 which references a leaf node with level=0.
 * - Every inner node references only nodes with a lower level.
 * - TODO: Horizontal traversal, All leaf nodes are horizontally connected, allowing fast horizontal traversal. 
 * 
 * @author Tilmann Zäschke
 *
 * @param <T>
 */
@Deprecated
public class HTable2<T> {
	
	private static final int BITS_INNER = 7;
	private static final int MAX_N_INNER = 1 << BITS_INNER;
	
	private int nEntries = 0;
	private Object ht;
	
	@SuppressWarnings("unchecked")
	public T get(int slotId) {
		if (slotId >= nEntries) {
			throw new IllegalStateException("Id=" + slotId);
		}
		if (nEntries <= 1) {
			return (T) ht;
		}
		Chunk c = (Chunk) ht;
		int mask = getIdMask();
		while (c.level > 0) {
			//inner node
			c = (Chunk) c.children[(slotId >>> SHIFT(c.level)) & mask];
		} 

		//leaf node
		return (T) c.children[slotId & mask];
	}
	
	public int append(T data) {
		if (needsRootResizeForAdd()) {
			Chunk c = createChunk(ht instanceof Chunk ? ((Chunk)ht).level + 1 : 0);
			c.children[0] = ht;
			ht = c;
		}
		append(nEntries, data);
		return nEntries++;
	}
	
	@SuppressWarnings("unchecked")
	private T set(int slotId, T data, boolean shrink) {
		if (slotId > nEntries) {
			throw new IllegalStateException("Id=" + slotId);
		}
		//How do we handle unbalanced trees? Depth may be lower in the rightmost branch
		//Append:
		// - If child == Object[]: Traverse
		// - If child == null: Abort loop, set shift=0, insert element
		// - If child == T: 
		//   - if child has room (Insert new Object[] into parent,
		
		//New plan: look at nEntries!
		//- if bits in a certain depth are '0' then we can skip the level:
		//  There is no (need for an) array.
		
		if (nEntries <= 1 && slotId == 0) {
			Object ret = ht;
			ht = data;
			return (T) ret;
		}
		Chunk parent = null;
		Chunk parentParent = null;
		Chunk c = (Chunk) ht;
		int mask = getIdMask();
		while (c.level > 0) {
			//inner node
			int pos = (slotId >>> SHIFT(c.level)) & mask;
			parentParent = parent;
			parent = c;
			c = (Chunk) c.children[pos];
		} 
		
		T ret = (T) c.children[slotId & mask];
		//TODO resize array if required?
		c.children[slotId & mask] = data;
		
		if (shrink) {
			//To consider:
			//'shrink' mean also that we know we are looking at the rightmost element in the tree!
			//Also: 'parent.level==0' here!
			if (parent == null && (slotId & mask) == 1) {
				ht = c.children[0];
				freeChunk(c);
			} else if ((slotId & mask) == 0) {
				//Now the parent may have only a single child left -> remove!
				if (parent == null) {
					//This should have been moved to 'ht' already...
					throw new IllegalStateException();
				}
				
				//TODO return to pool / resize-shrink
				int posInParent = (slotId >>> SHIFT(parent.level)) & mask; 
				parent.children[posInParent] = null;
				freeChunk(c);
				if (posInParent == 1) {
					//Remove parent (no need to recurse any further, never more than the immediate
					//parent can become empty.
					if (parentParent == null) {
						//This should have been moved to 'ht' already...
						ht = parent.children[0];
					} else {
						parentParent.children[(slotId >>> SHIFT(parentParent.level)) & mask] = parent.children[0];
					}
					freeChunk(parent);
				}
			}
		}
		
		return ret;
	}
	
	@SuppressWarnings("unchecked")
	private T append(int slotId, T data) {
		if (nEntries <= 1 && slotId == 0) {
			Object ret = ht;
			ht = data;
			return (T) ret;
		}
		Chunk c = (Chunk) ht;
		int mask = getIdMask();
		int expectedLevel = c.level;
		while (c.level > 0) {
			//inner node
			int pos = (slotId >>> SHIFT(c.level)) & mask;
			Object sub = c.children[pos];
			if (sub == null) {
				//free slot, but not leaf (level > 0) -> create sub-node
				Chunk c2 = createChunk(0);
				c.children[pos] = c2;
				c = c2;
				break;
			} 
			//it is a chunk, but is it the correct level?
			Chunk cSub = (Chunk) sub;
			while (--expectedLevel != cSub.level) {
				//Okay, some levels are unused, but do we need to insert one?
				int pos2 = (slotId >>> SHIFT(expectedLevel)) & mask;
				if (pos2 != 0) {  //can only be '0' or '1'...
					//insert level!
					Chunk c2 = createChunk(cSub.level+1);
					c2.children[0] = sub;
					c.children[pos] = c2;
					//Hack: jump directly to c2, because cSub has only one child! 
					cSub = c2;
					break;
				}
			}
			c = cSub;
		} 
		
		T ret = (T) c.children[slotId & mask];
		//TODO resize array if required?
		c.children[slotId & mask] = data;
		
		return ret;
	}
	
	
	public T replaceWithLast(int slotId) {
		if (slotId > nEntries) {
			throw new IllegalStateException("Id=" + slotId + " size=" + nEntries);
		}
		T last = set(nEntries-1, null, true);
		nEntries--;
		if (nEntries == slotId) {
			//The 'last' is actually what was meant to be removed
			return null;
		}
		set(slotId, last, false);
		return last;
	}
	
	private static int getIdMask() {
		return ~((-1) << BITS_INNER);
	}
	
	private static int SHIFT(int level) {
		return level*BITS_INNER;
	}
	
	private boolean needsRootResizeForAdd() {
		if (nEntries <= 1) {
			return nEntries == 1;
		}
		
		int maxLevels = ((Chunk)ht).level;
		boolean result = nEntries+1 > 1 << (++maxLevels*BITS_INNER);
		if (result && (maxLevels+1)*BITS_INNER >= 32) {
			throw new IllegalStateException("Addressing exceed 31 bits. Please adjust BITS_INNER");
		}
		return result;
	}
	
	public String toStringPlain() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < nEntries; i++) {
			sb.append("i=" + get(i));
			sb.append("\n");
		}
		return sb.toString();
	}
	
	private static Chunk createChunk(int level) {
		//TODO pooling
		return new Chunk(level);
	}
	
	private static void freeChunk(Chunk chunk) {
		if (chunk instanceof Chunk) {
			chunk.level = -1;
		}
		//TODO pooling
	}
	
	private static class Chunk {
		final Object[] children = new Object[MAX_N_INNER];
		int level;
		//Chunk prev, next;
		Chunk(int level) {
			this.level = level;
		}
	}
	
}
