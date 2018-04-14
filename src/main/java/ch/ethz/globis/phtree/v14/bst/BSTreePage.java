/*
 * Copyright 2009-2016 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of ZooDB.
 * 
 * ZooDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ZooDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ZooDB.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * See the README and COPYING files for further information. 
 */
package ch.ethz.globis.phtree.v14.bst;

import java.util.Arrays;
import java.util.NoSuchElementException;

import ch.ethz.globis.phtree.v14.bst.BSTreeIterator.LLEntry;

class BSTreePage {
	
	private BSTreePage parent;
	private final long[] keys;
	private final long[] values;
	/** number of keys. There are nEntries+1 subPages in any leaf page. */
	private short nEntries;

	protected final BSTree ind;
	final transient boolean isLeaf;
	final BSTreePage[] subPages;
	
	
	public BSTreePage(BSTree ind, BSTreePage parent, boolean isLeaf) {
		this.parent = parent;
		if (isLeaf) {
			nEntries = 0;
			keys = new long[ind.maxLeafN];
			values = new long[ind.maxLeafN];
		} else {
			nEntries = -1;
			keys = new long[ind.maxInnerN];
			if (ind.isUnique()) {
				values = null;
			} else {
				values = new long[ind.maxInnerN];
			}
		}
		
		this.ind = ind;
		if (!isLeaf) {	
			subPages = new BSTreePage[ind.maxInnerN + 1];
			BSTree.statNInner++;
		} else {
			subPages = null;
			BSTree.statNLeaves++;
		}
		this.isLeaf = isLeaf;
	}


	/**
	 * Locate the (first) page that could contain the given key.
	 * In the inner pages, the keys are the minimum values of the following page.
	 * @param key
	 * @return Page for that key
	 */
	public BSTreePage locatePageForKey(long key, boolean allowCreate) {
		if (isLeaf) {
			return this;
		}
		if (nEntries == -1 && !allowCreate) {
			return null;
		}

		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(0, nEntries, key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage page = readOrCreatePage(pos, allowCreate);
        return page.locatePageForKey(key, allowCreate);
	}
	
	public LLEntry getValueFromLeafUnique(long oid) {
		if (!isLeaf) {
			throw new IllegalStateException("Leaf inconsistency.");
		}
		int pos = binarySearch(0, nEntries, oid);
		if (pos >= 0) {
            return new LLEntry( oid, values[pos]);
		}
		//If the value could is not on this page, it does not exist.
		return null;
	}


    /**
     * Add an entry at 'key'/'value'. If the PAIR already exists, nothing happens.
     * @param key
     * @param value
     */
	public void insert(long key, long value) {
		put(key, value); 
	}

	/**
	 * Binary search.
	 * 
	 * @param toIndex Exclusive, search stops at (toIndex-1).
	 * @param value For non-unique trees, the value is taken into account as well.
	 */
	int binarySearch(int fromIndex, int toIndex, long key) {
		int low = fromIndex;
		int high = toIndex - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
        	long midVal = keys[mid];

        	if (midVal < key)
        		low = mid + 1;
        	else if (midVal > key)
        		high = mid - 1;
        	else {
       			return mid; // key found
        	}
		}
		return -(low + 1);  // key not found.
	}

    /**
     * Overwrite the entry at 'key'.
     * @param key
     * @param value
     */
	public final void put(long key, long value) {
		if (!isLeaf) {
			throw new IllegalStateException("Tree inconsistency.");
		}

		//in any case, check whether the key(+value) already exists
        int pos = binarySearch(0, nEntries, key);
        //key found? -> pos >=0
        if (pos >= 0) {
        	//TODO why check this?
        	//check if values changes
            if (value != values[pos]) {
                values[pos] = value;
            }
            return;
        } 

        if (nEntries < ind.maxLeafN) {
            //okay so we add it locally
            pos = -(pos+1);
            if (pos < nEntries) {
                System.arraycopy(keys, pos, keys, pos+1, nEntries-pos);
                System.arraycopy(values, pos, values, pos+1, nEntries-pos);
            }
            keys[pos] = key;
            values[pos] = value;
            nEntries++;
            return;
		} else {
			//treat page overflow
			BSTreePage newP;
			boolean isNew = false;
			boolean isPrev = false;
			//use ind.maxLeafN -1 to avoid pretty much pointless copying (and possible endless 
			//loops, see iterator tests)
			BSTreePage next = (BSTreePage) parent.getNextLeafPage(this);
			if (next != null && next.nEntries < ind.maxLeafN-1) {
				//merge
				newP = next;
				isPrev = false;
			} else {
				//Merging with prev is not make a big difference, maybe we should remove it...
				BSTreePage prev = (BSTreePage) parent.getPrevLeafPage(this);
				if (prev != null && prev.nEntries < ind.maxLeafN-1) {
					//merge
					newP = prev;
					isPrev = true;
				} else {
					newP = new BSTreePage(ind, parent, true);
					isNew = true;
				}
			}
			
			int nEntriesToKeep = (nEntries + newP.nEntries) >> 1;
			int nEntriesToCopy = nEntries - nEntriesToKeep;
			if (isNew) {
				//works only if new page follows current page
				System.arraycopy(keys, nEntriesToKeep, newP.keys, 0, nEntriesToCopy);
				System.arraycopy(values, nEntriesToKeep, newP.values, 0, nEntriesToCopy);
			} else if (isPrev) {
				//copy element to previous page
				System.arraycopy(keys, 0, newP.keys, newP.nEntries, nEntriesToCopy);
				System.arraycopy(values, 0, newP.values, newP.nEntries, nEntriesToCopy);
				//move element forward to beginning of page
				System.arraycopy(keys, nEntriesToCopy, keys, 0, nEntries-nEntriesToCopy);
				System.arraycopy(values, nEntriesToCopy, values, 0, nEntries-nEntriesToCopy);
			} else {
				//make space on next page
				System.arraycopy(newP.keys, 0, newP.keys, nEntriesToCopy, newP.nEntries);
				System.arraycopy(newP.values, 0, newP.values, nEntriesToCopy, newP.nEntries);
				//insert element in next page
				System.arraycopy(keys, nEntriesToKeep, newP.keys, 0, nEntriesToCopy);
				System.arraycopy(values, nEntriesToKeep, newP.values, 0, nEntriesToCopy);
			}
			nEntries = (short) nEntriesToKeep;
			newP.nEntries = (short) (nEntriesToCopy + newP.nEntries);
			//New page and min key
			if (isNew || !isPrev) {
				if (newP.keys[0] > key) {
					put(key, value);
				} else {
					newP.put(key, value);
				}
			} else {
				if (keys[0] > key) {
					newP.put(key, value);
				} else {
					put(key, value);
				}
			}
			if (isNew) {
				parent.addSubPage(newP, newP.keys[0]);
			} else {
				//TODO probably not necessary
				newP.parent.updateKey(newP, newP.keys[0]);
			}
			parent.updateKey(this, keys[0]);
		}
	}

	@Deprecated //TODO provide posInParent.
	void updateKey(BSTreePage indexPage, long key) {
		//TODO do we need this whole key update business????
		//-> surely not at the moment, where we only merge with pages that have the same 
		//   immediate parent...
		if (isLeaf) {
			throw new IllegalStateException("Tree inconsistency");
		}
		int start = binarySearch(0, nEntries, key);
		if (start < 0) {
			start = -(start+1);
		}
		
		for (int i = start; i <= nEntries; i++) {
			if (subPages[i] == indexPage) {
				if (i > 0) {
					keys[i-1] = key;
				} else {
					//parent page could be affected
					if (parent != null) {
						//TODO this recurses all parents!!!??? 
						parent.updateKey(this, key);
					}
				}
				return;
			}
		}
//		System.out.println("this:" + parent);
//		this.printLocal();
//		System.out.println("leaf: " + indexPage);
//		indexPage.printLocal();
		throw new IllegalStateException("leaf page not found.");
		
	}
	
	void addSubPage(BSTreePage newP, long minKey) {
		if (isLeaf) {
			throw new IllegalStateException("Tree inconsistency");
		}
		
		if (nEntries < ind.maxInnerN) {
			//add page here
			
			//For now, we assume a unique index.
            int i = binarySearch(0, nEntries, minKey);
            //If the key has a perfect match then something went wrong. This should
            //never happen so we don't need to check whether (i < 0).
        	i = -(i+1);
            
			if (i > 0) {
				System.arraycopy(keys, i, keys, i+1, nEntries-i);
				System.arraycopy(subPages, i+1, subPages, i+2, nEntries-i);
				if (!ind.isUnique()) {
					System.arraycopy(values, i, values, i+1, nEntries-i);
				}
				keys[i] = minKey;
				subPages[i+1] = newP;
				newP.setParent( this );
				nEntries++;
			} else {
				//decide whether before or after first page (both will end up before the current
				//first key).
				int ii;
				if (nEntries < 0) {
					//can happen for empty root page
					ii = 0;
				} else {
					System.arraycopy(keys, 0, keys, 1, nEntries);
					long oldKey = subPages[0].getMinKey();
					if (!ind.isUnique()) {
						System.arraycopy(values, 0, values, 1, nEntries);
//						long oldValue = subPages[0].getMinKeyValue();
//						if ((minKey > oldKey) || (minKey==oldKey && minValue > oldValue)) {
//							ii = 1;
//							keys[0] = minKey;
//							values[0] = minValue;
//						} else {
//							ii = 0;
//							keys[0] = oldKey;
//							values[0] = oldValue;
//						}
					}
//					} else {
					if ( minKey > oldKey ) {
						ii = 1;
						keys[0] = minKey;
					} else {
						ii = 0;
						keys[0] = oldKey;
					}
//					}
					System.arraycopy(subPages, ii, subPages, ii+1, nEntries-ii+1);
				}
				subPages[ii] = newP;
				newP.setParent( this );
				nEntries++;
			}
			return;
		} else {
			//treat page overflow
			BSTreePage newInner = (BSTreePage) ind.createPage(parent, false);
			
			//TODO use optimized fill ration for unique values, just like for leaves?.
			System.arraycopy(keys, ind.minInnerN+1, newInner.keys, 0, nEntries-ind.minInnerN-1);
			if (!ind.isUnique()) {
				System.arraycopy(values, ind.minInnerN+1, newInner.values, 0, nEntries-ind.minInnerN-1);
			}
			System.arraycopy(subPages, ind.minInnerN+1, newInner.subPages, 0, nEntries-ind.minInnerN);
			newInner.nEntries = (short) (nEntries-ind.minInnerN-1);
			newInner.assignThisAsRootToLeaves();

			if (parent == null) {
				//create a parent
				BSTreePage newRoot = ind.createPage(null, false);
				newRoot.subPages[0] = this;
				newRoot.nEntries = 0;  // 0: indicates one leaf / zero keys
				this.setParent( newRoot );
				ind.updateRoot(newRoot);
			}

			parent.addSubPage(newInner, keys[ind.minInnerN]);

			nEntries = (short) (ind.minInnerN);
			//finally add the leaf to the according page
			BSTreePage newHome;
			long newInnerMinKey = newInner.getMinKey();
			if (minKey < newInnerMinKey) {
				newHome = this;
			} else {
				newHome = newInner;
			}
			newHome.addSubPage(newP, minKey);
			return;
		}
	}
	
	long getMinKey() {
		if (isLeaf) {
			return keys[0];
		}
		return readPage(0).getMinKey();
	}
	
	long getMinKeyValue() {
		if (isLeaf) {
			return values[0];
		}
		return readPage(0).getMinKeyValue();
	}
	
	public void print(String indent) {
		if (isLeaf) {
			System.out.println(indent + "Leaf page: nK=" + nEntries + " keys=" + 
					Arrays.toString(keys));
			System.out.println(indent + "                         " + Arrays.toString(values));
		} else {
			System.out.println(indent + "Inner page: nK=" + nEntries + " keys=" + 
					Arrays.toString(keys));
			if (!ind.isUnique()) {
				System.out.println(indent + "              " + nEntries + " values=" + 
						Arrays.toString(values));
			}
//			System.out.println(indent + "                " + nEntries + " leaf=" + 
//					Arrays.toString(leaves));
			System.out.print(indent + "[");
			for (int i = 0; i <= nEntries; i++) {
				if (subPages[i] != null) { 
					System.out.print(indent + "i=" + i + ": ");
					subPages[i].print(indent + "  ");
				}
			}
			System.out.println(']');
		}
	}

	public void printLocal() {
		System.out.println("PrintLocal() for " + this);
		if (isLeaf) {
			System.out.println("Leaf page: nK=" + nEntries + " oids=" + 
					Arrays.toString(keys));
			System.out.println("                         " + Arrays.toString(values));
		} else {
			System.out.println("Inner page: nK=" + nEntries + " oids=" + 
					Arrays.toString(keys));
			if (!ind.isUnique()) {
				System.out.println("                      " + Arrays.toString(values));
			}
			System.out.println("                      " + Arrays.toString(subPages));
		}
	}

	protected short getNKeys() {
		return nEntries;
	}
	
	/**
	 * @param key
	 * @return the previous value
	 */
	protected long remove(long oid) {
        int i = binarySearch(0, nEntries, oid);
        if (i < 0) {
        	//key not found
        	throw new NoSuchElementException("Key not found: " + oid);
        }
        
        // first remove the element
        long prevValue = values[i];
        System.arraycopy(keys, i+1, keys, i, nEntries-i-1);
        System.arraycopy(values, i+1, values, i, nEntries-i-1);
        nEntries--;
        if (nEntries == 0) {
        	BSTree.statNLeaves--;
        	parent.removeLeafPage(this, oid);
        } else if (nEntries < (ind.maxLeafN >> 1) && (nEntries % 8 == 0)) {
        	//The second term prevents frequent reading of previous and following pages.
        	//TODO Should we instead check for nEntries==MAx>>1 then == (MAX>>2) then <= (MAX>>3)?

        	//now attempt merging this page
        	BSTreePage prevPage = (BSTreePage) parent.getPrevLeafPage(this);
        	if (prevPage != null) {
         		//We merge only if they all fit on a single page. This means we may read
        		//the previous page unnecessarily, but we avoid writing it as long as 
        		//possible. TODO find a balance, and do no read prev page in all cases
        		if (nEntries + prevPage.nEntries < ind.maxLeafN) {
        			//TODO for now this work only for leaves with the same root. We
        			//would need to update the min values in the inner nodes.
        			System.arraycopy(keys, 0, prevPage.keys, prevPage.nEntries, nEntries);
        			System.arraycopy(values, 0, prevPage.values, prevPage.nEntries, nEntries);
        			prevPage.nEntries += nEntries;
        			BSTree.statNLeaves--;
        			parent.removeLeafPage(this, keys[0]);
        		}
        	}
        }
        return prevValue;
	}


	protected void removeLeafPage(BSTreePage indexPage, long key) {
		int start = binarySearch(0, nEntries, key);
		if (start < 0) {
			start = -(start+1);
		}
		
		for (int i = start; i <= nEntries; i++) {
			if (subPages[i] == indexPage) {
				//remove sub page page from FSM.
				BTPool.reportFreePage(indexPage);

				if (nEntries > 0) { //otherwise we just delete this page
					//remove entry
					arraysRemoveInnerEntry(i);
					nEntries--;
				
					//Now try merging
					if (parent == null) {
						return;
					}
					BSTreePage prev = (BSTreePage) parent.getPrevInnerPage(this);
					if (prev != null && !prev.isLeaf) {
						//TODO this is only good for merging inside the same root.
						if ((nEntries % 2 == 0) && (prev.nEntries + nEntries < ind.maxInnerN)) {
							System.arraycopy(keys, 0, prev.keys, prev.nEntries+1, nEntries);
							if (!ind.isUnique()) {
								System.arraycopy(values, 0, prev.values, prev.nEntries+1, nEntries);
							}
							System.arraycopy(subPages, 0, prev.subPages, prev.nEntries+1, nEntries+1);
							//find key for the first appended page -> go up or go down????? Up!
							int pos = parent.getPagePosition(this)-1;
							prev.keys[prev.nEntries] = parent.keys[pos]; 
							if (!ind.isUnique()) {
								prev.values[prev.nEntries] = parent.values[pos]; 
							}
							prev.nEntries += nEntries + 1;  //for the additional key
							prev.assignThisAsRootToLeaves();
							parent.removeLeafPage(this, key);
						}
						return;
					}
					
					if (nEntries == 0) {
						//only one element left, no merging occurred -> move sub-page up to parent
						BSTreePage child = readPage(0);
						parent.replaceChildPage(this, key, child);
						BSTree.statNInner--;
					}
				} else {
					// nEntries == 0
					if (parent != null) {
						parent.removeLeafPage(this, key);
						BSTree.statNInner--;
					}
					// else : No root and this is a leaf page... -> we do nothing.
					subPages[0] = null;
					nEntries--;  //down to -1 which indicates an empty root page
				}
				return;
			}
		}
//		System.out.println("this:" + parent);
//		this.printLocal();
//		System.out.println("leaf: " + indexPage);
//		indexPage.printLocal();
		throw new IllegalStateException("leaf page not found.");
	}

	private void arraysRemoveKey(int pos) {
		System.arraycopy(keys, pos+1, keys, pos, nEntries-pos-1);
		if (!ind.isUnique()) {
			System.arraycopy(values, pos+1, values, pos, nEntries-pos-1);
		}
	}
	
	private void arraysRemoveChild(int pos) {
		System.arraycopy(subPages, pos+1, subPages, pos, nEntries-pos);
		subPages[nEntries] = null;
	}
	
	/**
	 * 
	 * @param posEntry The pos in the subPage-array. The according keyPos may be -1.
	 */
	private void arraysRemoveInnerEntry(int posEntry) {
		if (posEntry > 0) {
			arraysRemoveKey(posEntry - 1);
		} else {
			arraysRemoveKey(0);
		}
		arraysRemoveChild(posEntry);
	}

	/**
	 * Replacing sub-pages occurs when the sub-page shrinks down to a single sub-sub-page, in which
	 * case we pull up the sub-sub-page to the local page, replacing the sub-page.
	 */
	protected void replaceChildPage(BSTreePage indexPage, long key, BSTreePage subChild) {
		int start = binarySearch(0, nEntries, key);
		if (start < 0) {
			start = -(start+1);
		}
		for (int i = start; i <= nEntries; i++) {
			if (subPages[i] == indexPage) {
				//remove page from FSM.
				BTPool.reportFreePage(indexPage);
				subPages[i] = subChild;
				if (i>0) {
					keys[i-1] = subChild.getMinKey();
					if (!ind.isUnique()) {
						values[i-1] = subChild.getMinKeyValue();
					}
				}
				subChild.setParent(this);
				return;
			}
		}
//		System.out.println("this:" + parent);
//		this.printLocal();
//		System.out.println("sub-page:");
//		indexPage.printLocal();
		throw new IllegalStateException("Sub-page not found.");
	}

	BSTreePage getParent() {
		return parent;
	}
	
	void setParent(BSTreePage parent) {
		this.parent = parent;
	}
	
	public long getMax() {
		if (isLeaf) {
			if (nEntries == 0) {
				return Long.MIN_VALUE;
			}
			return keys[nEntries-1];
		}
		//handle empty indices
		if (nEntries == -1) {
			return Long.MIN_VALUE;
		}
		long max = ((BSTreePage)getPageByPos(nEntries)).getMax();
		return max;
	}


	protected void incrementNEntries() {
		nEntries++;
	}

	final long[] getKeys() {
		return keys;
	}

	final long[] getValues() {
		return values;
	}

	final void setNEntries(int n) {
		nEntries = (short) n;
	}
	
	
	protected final BSTreePage readPage(int pos) {
		return subPages[pos];
	}
	
	
	protected final BSTreePage readOrCreatePage(int pos, boolean allowCreate) {
		BSTreePage page = subPages[pos];
		if (page != null) {
			//page is in memory
			return page;
		}

		if (!allowCreate) {
			return null;
		}
		//create new page
		page = ind.createPage(this, true);
		incrementNEntries();
		subPages[pos] = page;
		return page;
		
	}

	/**
	 * Returns only INNER pages.
	 * TODO for now this ignores leafPages on a previous inner node. It returns only leaf pages
	 * from the current node.
	 * @param indexPage
	 * @return The previous leaf page or null, if the given page is the first page.
	 */
	final protected BSTreePage getPrevInnerPage(BSTreePage indexPage) {
		int pos = getPagePosition(indexPage);
		if (pos > 0) {
			BSTreePage page = getPageByPos(pos-1);
			if (page.isLeaf) {
				return null;
			}
			return page;
		}
		if (getParent() == null) {
			return null;
		}
		//TODO we really should return the last leaf page of the previous inner page.
		//But if they get merged, then we have to shift minimal values, which is
		//complicated. For now we ignore this case, hoping that it doesn't matter much.
		return null;
	}

	/**
	 * Returns only LEAF pages.
	 * TODO for now this ignores leafPages on a previous inner node. It returns only leaf pages
	 * from the current node.
	 * @param indexPage
	 * @return The previous leaf page or null, if the given page is the first page.
	 */
	final protected BSTreePage getPrevLeafPage(BSTreePage indexPage) {
		int pos = getPagePosition(indexPage);
		if (pos > 0) {
			BSTreePage page = getPageByPos(pos-1);
			return page.getLastLeafPage();
		}
		if (getParent() == null) {
			return null;
		}
		//TODO we really should return the last leaf page of the previous inner page.
		//But if they get merged, then we have to shift minimal values, which is
		//complicated. For now we ignore this case, hoping that it doesn't matter much.
		return null;
	}

	/**
	 * Returns only LEAF pages.
	 * TODO for now this ignores leafPages on other inner nodes. It returns only leaf pages
	 * from the current node.
	 * @param indexPage
	 * @return The previous next page or null, if the given page is the first page.
	 */
	final protected BSTreePage getNextLeafPage(BSTreePage indexPage) {
		int pos = getPagePosition(indexPage);
		if (pos < getNKeys()) {
			BSTreePage page = getPageByPos(pos+1);
			return page.getFirstLeafPage();
		}
		if (getParent() == null) {
			return null;
		}
		//TODO we really should return the last leaf page of the previous inner page.
		//But if they get merged, then we have to shift minimal values, which is
		//complicated. For now we ignore this case, hoping that it doesn't matter much.
		return null;
	}

	/**
	 * 
	 * @return The first leaf page of this branch.
	 */
	private BSTreePage getFirstLeafPage() {
		if (isLeaf) {
			return this;
		}
		return readPage(0).getFirstLeafPage();
	}

	/**
	 * 
	 * @return The last leaf page of this branch.
	 */
	private BSTreePage getLastLeafPage() {
		if (isLeaf) {
			return this;
		}
		return readPage(getNKeys()).getLastLeafPage();
	}

	/**
	 * Returns (and loads, if necessary) the page at the specified position.
	 */
	protected BSTreePage getPageByPos(int pos) {
		return subPages[pos];
	}

	/**
	 * This method will fail if called on the first page in the tree. However this should not
	 * happen, because when called, we already have a reference to a previous page.
	 * @param oidIndexPage
	 * @return The position of the given page in the subPage-array with 0 <= pos <= nEntries.
	 */
	int getPagePosition(BSTreePage indexPage) {
		//We know that the element exists, so we iterate to list.length instead of nEntires 
		//(which is not available in this context at the moment.
		for (int i = 0; i < subPages.length; i++) {
			if (subPages[i] == indexPage) {
				return i;
			}
		}
		throw new IllegalStateException("Leaf page not found in parent page.");
	}
	
	protected void assignThisAsRootToLeaves() {
		for (int i = 0; i <= getNKeys(); i++) {
			//leaves may be null if they are not loaded!
			if (subPages[i] != null) {
				subPages[i].setParent(this);
			}
		}
	}
	
	final void clear() {
		if (!isLeaf) {
			for (int i = 0; i < getNKeys()+1; i++) {
				BSTreePage p = readPage(i);
				p.clear();
				//0-IDs are automatically ignored.
				BTPool.reportFreePage(p);
			}
		}
		if (subPages != null) {
			for (int i = 0; i < subPages.length; i++) {
				subPages[i] = null;
			}
		}
		setNEntries(-1);
	}

}
