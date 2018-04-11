/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v14;

public class HTable2<T> {
	
	private static final int BITS_INNER = 3;
	private static final int MAX_N_INNER = 1 << BITS_INNER;
	
	private int nEntries = 0;
	private Object ht;
	
	
	//TODO use pool for Object[]
	
	@SuppressWarnings("unchecked")
	public T get(int slotId) {
		if (nEntries <= 1) {
			return (T) ht;
		}
		Chunk c = (Chunk) ht;
		int mask = getIdMask();
		while (c.level > 0) {
			//inner node
			int shift = c.level * BITS_INNER;
			c = (Chunk) c.children[(slotId >>> shift) & mask];
		} 
		
		return (T) c.children[slotId & mask];
	}
	
	public int append(T data) {
		if (needsRootResizeForAdd()) {
			Chunk c = new Chunk(ht instanceof Chunk ? ((Chunk)ht).level + 1 : 0);
			c.children[0] = ht;
			ht = c;
		}
		append(nEntries, data);
		return nEntries++;
	}
	
	@SuppressWarnings("unchecked")
	private T set(int slotId, T data, boolean shrink) {
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
			//TODO return [] to pool if shrink (do in calling method?)
			return (T) ret;
		}
		Chunk parent = null;
		Chunk c = (Chunk) ht;
		int mask = getIdMask();
		while (c.level > 0) {
			//inner node
			int pos = (slotId >>> SHIFT(c.level)) & mask;
			Chunk sub = (Chunk) c.children[pos];
			if (sub == null) {
				sub = new Chunk(c.level-1);
				c.children[pos] = sub;
			}
			parent = c;
			c = sub;
		} 
		
		T ret = (T) c.children[slotId & mask];
		//TODO resize array if required?
		c.children[slotId & mask] = data;
		
		if (shrink) {
			//To consider:
			//'shrink' mean also that we know we are looking at the rightmost element in the tree!
			//Also: 'parent.level==0' here!
			if (parent == null && (slotId & mask) == 1) {
				//TODO return to pool
				ht = c.children[0];
			} else if ((slotId & mask) == 0) {
				//TODO return to pool / resize-shrink
				if (parent != null) {
					parent.children[(slotId >>> SHIFT(parent.level)) & mask] = null;
					//TODO
					//TODO
					//TODO
					//TODO
					//TODO
					//Now the parent may have only a single child left -> remove!
				} else {
					
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
			//TODO return [] to pool if shrink (do in calling method?)
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
				Chunk c2 = new Chunk(0);
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
					Chunk c2 = new Chunk(cSub.level+1);
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
		T last = set(nEntries-1, null, true);
		nEntries--;
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
		return nEntries+1 > 1 << (++maxLevels*BITS_INNER);
	}
	
	private static class Chunk {
		final Object[] children = new Object[MAX_N_INNER];
		// TODO 'byte'
		int level;
		Chunk(int level) {
			this.level = level;
		}
	}
	
}
