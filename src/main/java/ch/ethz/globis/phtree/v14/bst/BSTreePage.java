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

import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.v14.bst.BSTree.Stats;
import ch.ethz.globis.phtree.v14.bst.BSTreeIterator.LLEntry;

class BSTreePage {
	
	private BSTreePage parent;
	private final long[] keys;
	private final Object[] values;
	/** number of keys. There are nEntries+1 subPages in any leaf page. */
	private short nEntries;

	private final BSTree ind;
	private final boolean isLeaf;
	private BSTreePage[] subPages;
	
	
	public BSTreePage(BSTree ind, BSTreePage parent, boolean isLeaf) {
		this.parent = parent;
		if (isLeaf) {
			nEntries = 0;
			keys = new long[ind.maxLeafN];
			values = new Object[ind.maxLeafN];
		} else {
			nEntries = -1;
			keys = new long[ind.maxInnerN];
			values = null;
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

	
	public BSTreePage findSubPage(long key, boolean remove) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(0, nEntries, key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage page = subPages[pos]; 
        if (remove && page.isLeaf()) {
        	page.remove(key, pos);
        }
    	return page;
	}
	
	/**
	 * 
	 * @param key Key
	 * @return 'null' (not found), value or subpage (in case a page needs to be removed).
	 */
	@Deprecated
	Object findAndRemoveOld(long key, int posPageInParent) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(0, nEntries, key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage page = subPages[pos]; 
        Object result = null;
        if (page.isLeaf()) {
        	result = page.remove(key, pos);
        } else {
        	result = page.findAndRemoveOld(key, pos);
        }
        if (result instanceof BSTreePage) {
        	return removeLeafPage2(pos, posPageInParent);
        }
        return result;
	}
	
	Object findAndRemove(long key) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(0, nEntries, key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage page = subPages[pos]; 
        Object result = null;
        if (page.isLeaf()) {
        	result = page.remove3(key);
            checkUnderflowSubpageLeaf(pos);
        } else {
        	result = page.findAndRemove(key);
        	handleUnderflowSubInner(pos);
        }
        return result;
	}
	
	/**
	 * 
	 * @param key the lookup key
	 * @return the page
	 */
	public Object put(long key, Object value) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(0, nEntries, key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage page = getPageByPos(pos);
        if (page == null) {
        	page = createLeafPage(pos);
        }
        if (page.isLeaf()) {
    		Object o = page.put(key, value, this, pos);
    		if (o instanceof BSTreePage) {
    			//add page
    			BSTreePage newPage = (BSTreePage) o;
    			addSubPage(newPage, newPage.getMinKey(), pos);
    			return null;
    		}
    		return o;
        }
        return page;
	}
	
	public LLEntry getValueFromLeafUnique(long oid) {
		if (!isLeaf) {
			throw new IllegalStateException("Leaf inconsistency.");
		}
		int pos = binarySearch(0, nEntries, oid);
		if (pos >= 0) {
            return new LLEntry( oid, values[pos] == BSTree.NULL ? null : values[pos]);
		}
		//If the value could is not on this page, it does not exist.
		return null;
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
	public final Object put(long key, Object value, BSTreePage parent, int posPageInParent) {
		if (!isLeaf) {
			throw new IllegalStateException("Tree inconsistency.");
		}

		//in any case, check whether the key(+value) already exists
        int pos = binarySearch(0, nEntries, key);
        //key found? -> pos >=0
        if (pos >= 0) {
        	Object oldVal = values[pos];
//            if (value != values[pos]) {
//                values[pos] = value;
//            }
            return oldVal;
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
        	return null;
        } 

        //treat page overflow
        
        //destination page
        BSTreePage destP;
        //created new page?
        boolean isNew = false;
        //is previous page?
        boolean isPrev = false;
        
        //use ind.maxLeafN -1 to avoid pretty much pointless copying (and possible endless 
        //loops, see iterator tests)
        BSTreePage next = (BSTreePage) parent.getNextLeafPage(posPageInParent);
        if (next != null && next.nEntries < ind.maxLeafN-1) {
        	//merge
        	destP = next;
        	isPrev = false;
        } else {
        	//Merging with prev is not make a big difference, maybe we should remove it...
        	BSTreePage prev = (BSTreePage) parent.getPrevLeafPage(posPageInParent);
        	if (prev != null && prev.nEntries < ind.maxLeafN-1) {
        		//merge
        		destP = prev;
        		isPrev = true;
        	} else {
        		destP = new BSTreePage(ind, parent, true);
        		isNew = true;
        	}
        }

        //TODO during bulkloading, keep 95% or so in old page. 100%?
        int nEntriesToKeep = (nEntries + destP.nEntries) >> 1;
       	int nEntriesToCopy = nEntries - nEntriesToKeep;
       	if (isNew) {
       		//works only if new page follows current page
       		System.arraycopy(keys, nEntriesToKeep, destP.keys, 0, nEntriesToCopy);
       		System.arraycopy(values, nEntriesToKeep, destP.values, 0, nEntriesToCopy);
       	} else if (isPrev) {
       		//copy element to previous page
       		System.arraycopy(keys, 0, destP.keys, destP.nEntries, nEntriesToCopy);
       		System.arraycopy(values, 0, destP.values, destP.nEntries, nEntriesToCopy);
       		//move element forward to beginning of page
       		System.arraycopy(keys, nEntriesToCopy, keys, 0, nEntries-nEntriesToCopy);
       		System.arraycopy(values, nEntriesToCopy, values, 0, nEntries-nEntriesToCopy);
       	} else {
       		//make space on next page
       		System.arraycopy(destP.keys, 0, destP.keys, nEntriesToCopy, destP.nEntries);
       		System.arraycopy(destP.values, 0, destP.values, nEntriesToCopy, destP.nEntries);
       		//insert element in next page
       		System.arraycopy(keys, nEntriesToKeep, destP.keys, 0, nEntriesToCopy);
       		System.arraycopy(values, nEntriesToKeep, destP.values, 0, nEntriesToCopy);
       	}
       	nEntries = (short) nEntriesToKeep;
       	destP.nEntries = (short) (nEntriesToCopy + destP.nEntries);
       	//New page and min key
       	if (isNew || !isPrev) {
       		if (destP.keys[0] > key) {
       			//posInParent=-2, because we won't need it there
       			put(key, value, parent, -2);
       		} else {
       			destP.put(key, value, parent, -2);
       		}
       	} else {
       		if (keys[0] > key) {
       			destP.put(key, value, parent, -2);
       		} else {
       			put(key, value, parent, -2);
       		}
       	}
       	if (isNew) {
       		//own key remains unchanged
       		return destP;
       	} else {
       		//change own key in parent?
       		if (isPrev) {
       	       	//posInParent has not changed!
    			parent.updateKey(getMinKey(), posPageInParent-1);
       		} else {
       			//change key of 'next' page
    			parent.updateKey(destP.getMinKey(), posPageInParent-1+1);
       		}
       		return null;
       	}
	}


	private void updateKey(long key, int keyPos) {
		if (keyPos < 0 || keys[keyPos] == key) {
			//nothing changes
			return;
		}

		keys[keyPos] = key;
	}

	@Deprecated
	private static final int NO_POS = -1000;
	
	private BSTreePage addSubPage(BSTreePage newP, long minKey, int keyPos) {
		if (isLeaf) {
			throw new IllegalStateException("Tree inconsistency");
		}
		
		if (nEntries < ind.maxInnerN) {
			//add page here
			
			if (keyPos == NO_POS) {
			
				//For now, we assume a unique index.
				int i = binarySearch(0, nEntries, minKey);
				//If the key has a perfect match then something went wrong. This should
				//never happen so we don't need to check whether (i < 0).
				keyPos = -(i+1);
			}
			
			if (keyPos > 0) {
				System.arraycopy(keys, keyPos, keys, keyPos+1, nEntries-keyPos);
				System.arraycopy(subPages, keyPos+1, subPages, keyPos+2, nEntries-keyPos);
				keys[keyPos] = minKey;
				subPages[keyPos+1] = newP;
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
					if ( minKey > oldKey ) {
						ii = 1;
						keys[0] = minKey;
					} else {
						ii = 0;
						keys[0] = oldKey;
					}
					System.arraycopy(subPages, ii, subPages, ii+1, nEntries-ii+1);
				}
				subPages[ii] = newP;
				newP.setParent( this );
				nEntries++;
			}
			return null;
		} else {
			//treat page overflow
			BSTreePage newInner = (BSTreePage) ind.createPage(parent, false);
			
			//TODO use optimized fill ration for unique values, just like for leaves?.
			System.arraycopy(keys, ind.minInnerN+1, newInner.keys, 0, nEntries-ind.minInnerN-1);
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

			parent.addSubPage(newInner, keys[ind.minInnerN], NO_POS);

			nEntries = (short) (ind.minInnerN);
			//finally add the leaf to the according page
			BSTreePage newHome;
			long newInnerMinKey = newInner.getMinKey();
			if (minKey < newInnerMinKey) {
				newHome = this;
			} else {
				newHome = newInner;
			}
			newHome.addSubPage(newP, minKey, NO_POS);
			//TODO
			//TODO
			//TODO
			//TODO
			//TODO
			//TODO
			return null;
		}
	}
	
	
	long getMinKey() {
		if (isLeaf) {
			return keys[0];
		}
		return getPageByPos(0).getMinKey();
	}
	
	public void print(String indent) {
		if (isLeaf) {
			System.out.println(indent + "Leaf page: nK=" + nEntries + " keys=" + 
					Arrays.toString(keys));
			System.out.println(indent + "                         " + Arrays.toString(values));
		} else {
			System.out.println(indent + "Inner page: nK=" + nEntries + " keys=" + 
					Arrays.toString(keys));
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

	public void toStringTree(StringBuilderLn sb, String indent) {
		if (isLeaf) {
			sb.appendLn(indent + "Leaf page: nK=" + nEntries + " keys=" + 
					Arrays.toString(keys));
			sb.appendLn(indent + "                         " + Arrays.toString(values));
		} else {
			sb.appendLn(indent + "Inner page: nK=" + nEntries + " keys=" + 
					Arrays.toString(keys));
//			System.out.println(indent + "                " + nEntries + " leaf=" + 
//					Arrays.toString(leaves));
			sb.append(indent + "[");
			for (int i = 0; i <= nEntries; i++) {
				if (subPages[i] != null) { 
					sb.append(indent + "i=" + i + ": ");
					subPages[i].toStringTree(sb, indent + "  ");
				}
			}
			sb.appendLn("]");
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
			System.out.println("                      " + Arrays.toString(subPages));
		}
	}

	short getNKeys() {
		return nEntries;
	}
	
	/**
	 * @param key
	 * @return the previous value
	 */
	private Object remove(long oid, int posPageInParent) {
        int i = binarySearch(0, nEntries, oid);
        if (i < 0) {
        	//key not found
        	//TODO return null???
        	throw new NoSuchElementException("Key not found: " + oid);
        }
        
        // first remove the element
        Object prevValue = values[i];
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
        	BSTreePage prevPage = parent.getPrevLeafPage(posPageInParent);
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

	private Object remove3(long oid) {
        int i = binarySearch(0, nEntries, oid);
        if (i < 0) {
        	//key not found
        	//TODO return null???
        	throw new NoSuchElementException("Key not found: " + oid);
        }
        
        // first remove the element
        Object prevValue = values[i];
        System.arraycopy(keys, i+1, keys, i, nEntries-i-1);
        System.arraycopy(values, i+1, values, i, nEntries-i-1);
        nEntries--;
        return prevValue;
	}
	
	private void checkUnderflowSubpageLeaf(int pos) {
		BSTreePage subPage = getPageByPos(pos);
        if (subPage.nEntries == 0) {
        	BSTree.statNLeaves--;
        	removePage3(pos);
        } else if (subPage.nEntries < (ind.maxLeafN >> 1) && (subPage.nEntries % 8 == 0)) {
        	//The second term prevents frequent reading of previous and following pages.
        	//TODO Should we instead check for nEntries==MAx>>1 then == (MAX>>2) then <= (MAX>>3)?

        	//now attempt merging this page
        	BSTreePage prevPage = getPrevLeafPage(pos);
        	if (prevPage != null) {
         		//We merge only if they all fit on a single page. This means we may read
        		//the previous page unnecessarily, but we avoid writing it as long as 
        		//possible. TODO find a balance, and do no read prev page in all cases
        		if (subPage.nEntries + prevPage.nEntries < ind.maxLeafN) {
        			//TODO for now this work only for leaves with the same root. We
        			//would need to update the min values in the inner nodes.
        			System.arraycopy(subPage.keys, 0, prevPage.keys, prevPage.nEntries, subPage.nEntries);
        			System.arraycopy(subPage.values, 0, prevPage.values, prevPage.nEntries, subPage.nEntries);
        			prevPage.nEntries += subPage.nEntries;
        			BSTree.statNLeaves--;
        			removePage3(pos);
        		}
        	}
        }
	}

	private Object remove2(long oid, int posPageInParent) {
        int i = binarySearch(0, nEntries, oid);
        if (i < 0) {
        	//key not found
        	//TODO return null???
        	throw new NoSuchElementException("Key not found: " + oid);
        }
        
        // first remove the element
        Object prevValue = values[i];
        System.arraycopy(keys, i+1, keys, i, nEntries-i-1);
        System.arraycopy(values, i+1, values, i, nEntries-i-1);
        nEntries--;
        if (nEntries == 0) {
        	BSTree.statNLeaves--;
        	return this;
        } else if (nEntries < (ind.maxLeafN >> 1) && (nEntries % 8 == 0)) {
        	//The second term prevents frequent reading of previous and following pages.
        	//TODO Should we instead check for nEntries==MAx>>1 then == (MAX>>2) then <= (MAX>>3)?

        	//now attempt merging this page
        	BSTreePage prevPage = parent.getPrevLeafPage(posPageInParent);
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
        			return this;
        		}
        	}
        }
        return prevValue;
	}


	private void removeLeafPage(BSTreePage indexPage, long key) {
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
							System.arraycopy(subPages, 0, prev.subPages, prev.nEntries+1, nEntries+1);
							//find key for the first appended page -> go up or go down????? Up!
							int pos = parent.getPagePosition(this)-1;
							prev.keys[prev.nEntries] = parent.keys[pos]; 
							prev.nEntries += nEntries + 1;  //for the additional key
							prev.assignThisAsRootToLeaves();
							parent.removeLeafPage(this, key);
						}
						return;
					}
					
					if (nEntries == 0) {
						//only one element left, no merging occurred -> move sub-page up to parent
						BSTreePage child = getPageByPos(0);
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
		throw new IllegalStateException("leaf page not found.");
	}


	private BSTreePage removeLeafPage2(int posToRemove, int posPageInParent) {
		BSTreePage indexPage = getPageByPos(posToRemove);
		
		//remove sub page page from FSM.
		BTPool.reportFreePage(indexPage);

		if (nEntries > 0) { //otherwise we just delete this page
			//remove entry
			arraysRemoveInnerEntry(posToRemove);
			nEntries--;

			//Now try merging
			if (parent == null) {
				return null;
			}
			BSTreePage prev = (BSTreePage) parent.getPrevInnerPage(posPageInParent);
			if (prev != null && !prev.isLeaf) {
				//TODO this is only good for merging inside the same root.
				if ((nEntries % 2 == 0) && (prev.nEntries + nEntries < ind.maxInnerN)) {
					System.arraycopy(keys, 0, prev.keys, prev.nEntries+1, nEntries);
					System.arraycopy(subPages, 0, prev.subPages, prev.nEntries+1, nEntries+1);
					//find key for the first appended page -> go up or go down????? Up!
					prev.keys[prev.nEntries] = parent.keys[posPageInParent - 1]; 
					prev.nEntries += nEntries + 1;  //for the additional key
					prev.assignThisAsRootToLeaves();
					//return this;//TODO remove 
					parent.removeLeafPage2(posPageInParent, -100);
				}
				return null;
			}

			if (nEntries == 0) {
				//only one element left, no merging occurred -> move sub-page up to parent
				BSTreePage child = getPageByPos(0);
				parent.replaceChildPage2(this, child, posPageInParent);
				BSTree.statNInner--;
			}
		} else {
			// nEntries == 0
			if (parent != null) {
				BSTree.statNInner--;
				return this;
			}
			// else : No root and this is a leaf page... -> we do nothing.
			subPages[0] = null;
			nEntries--;  //down to -1 which indicates an empty root page
		}
		return null;
	}

	private void removePage3(int posToRemove) {
		BSTreePage indexPage = getPageByPos(posToRemove);
		
		//remove sub page page from FSM.
		BTPool.reportFreePage(indexPage);

		if (nEntries > 0) { //otherwise we just delete this page
			//remove entry
			arraysRemoveInnerEntry(posToRemove);
			nEntries--;
		}
	}
	
	private void handleUnderflowSubInner(int pos) {
		BSTreePage sub = getPageByPos(pos);
		if (sub.nEntries < ind.maxInnerN>>1) {
			if (sub.nEntries >= 0) {
				BSTreePage prev = getPrevInnerPage(pos);
				if (prev != null && !prev.isLeaf) {
					//TODO this is only good for merging inside the same root.
					if ((sub.nEntries % 2 == 0) && (prev.nEntries + sub.nEntries < ind.maxInnerN)) {
						System.arraycopy(sub.keys, 0, prev.keys, prev.nEntries+1, sub.nEntries);
						System.arraycopy(sub.subPages, 0, prev.subPages, prev.nEntries+1, sub.nEntries+1);
						//find key for the first appended page -> go up or go down????? Up!
						prev.keys[prev.nEntries] = keys[pos - 1]; 
						prev.nEntries += sub.nEntries + 1;  //for the additional key
						prev.assignThisAsRootToLeaves();
						//return this;//TODO remove ? 
						removePage3(pos);
					}
					return;
				}
		
				if (sub.nEntries == 0) {
					//only one element left, no merging occurred -> move sub-page up to parent
					BSTreePage child = sub.getPageByPos(0);
					replaceChildPage2(sub, child, pos);
					BSTree.statNInner--;
				}
			
				//TODO return page to pool?
				
			} else {
				// nEntries == 0
				if (sub.parent != null) {
//					removePage3(pos);
					BSTree.statNInner--;
					return;
				}
				// else : No root and this is a leaf page... -> we do nothing.
				sub.subPages[0] = null;
				sub.nEntries--;  //down to -1 which indicates an empty root page
			}
		}
	}

	private void arraysRemoveKey(int pos) {
		System.arraycopy(keys, pos+1, keys, pos, nEntries-pos-1);
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
	private void replaceChildPage(BSTreePage indexPage, long key, BSTreePage subChild) {
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

	/**
	 * Replacing sub-pages occurs when the sub-page shrinks down to a single sub-sub-page, in which
	 * case we pull up the sub-sub-page to the local page, replacing the sub-page.
	 */
	private void replaceChildPage2(BSTreePage indexPage, BSTreePage subChild, int pos) {
		//remove page from FSM.
		BTPool.reportFreePage(indexPage);
		subPages[pos] = subChild;
		if (pos>0) {
			keys[pos-1] = subChild.getMinKey();
		}
		subChild.setParent(this);
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


	private void incrementNEntries() {
		nEntries++;
	}

	final long[] getKeys() {
		return keys;
	}

	final Object[] getValues() {
		return values;
	}

	private void setNEntries(int n) {
		nEntries = (short) n;
	}
	
	
	private BSTreePage createLeafPage(int pos) {
		if (subPages[pos] != null) {
			throw new IllegalStateException();
		}

		//create new page
		BSTreePage page = ind.createPage(this, true);
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
	private BSTreePage getPrevInnerPage(BSTreePage indexPage) {
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
//	private BSTreePage getPrevLeafPage(BSTreePage indexPage) {
//		int pos = getPagePosition(indexPage);
//		if (pos > 0) {
//			BSTreePage page = getPageByPos(pos-1);
//			return page.getLastLeafPage();
//		}
//		if (getParent() == null) {
//			return null;
//		}
//		//TODO we really should return the last leaf page of the previous inner page.
//		//But if they get merged, then we have to shift minimal values, which is
//		//complicated. For now we ignore this case, hoping that it doesn't matter much.
//		return null;
//	}

	/**
	 * Returns only LEAF pages.
	 * TODO for now this ignores leafPages on other inner nodes. It returns only leaf pages
	 * from the current node.
	 * @param indexPage
	 * @return The previous next page or null, if the given page is the first page.
	 */
//	private BSTreePage getNextLeafPage(BSTreePage indexPage) {
//		int pos = getPagePosition(indexPage);
//		if (pos < getNKeys()) {
//			BSTreePage page = getPageByPos(pos+1);
//			return page.getFirstLeafPage();
//		}
//		if (getParent() == null) {
//			return null;
//		}
//		//TODO we really should return the last leaf page of the previous inner page.
//		//But if they get merged, then we have to shift minimal values, which is
//		//complicated. For now we ignore this case, hoping that it doesn't matter much.
//		return null;
//	}

	/**
	 * Returns only INNER pages.
	 * TODO for now this ignores leafPages on a previous inner node. It returns only leaf pages
	 * from the current node.
	 * @param currentSubPos
	 * @return The previous leaf page or null, if the given page is the first page.
	 */
	private BSTreePage getPrevInnerPage(int currentSubPos) {
		if (currentSubPos > 0) {
			BSTreePage page = getPageByPos(currentSubPos-1);
			if (page.isLeaf) {
				return null;
			}
			return page;
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
	 * @param currentSubPos
	 * @return The previous leaf page or null, if the given page is the first page.
	 */
	private BSTreePage getPrevLeafPage(int currentSubPos) {
		if (currentSubPos > 0) {
			BSTreePage page = getPageByPos(currentSubPos-1);
			return page.getLastLeafPage();
		}
		//TODO we really should return the last leaf page of the previous inner page.
		//But if they get merged, then we have to shift minimal values, which is
		//complicated. For now we ignore this case, hoping that it doesn't matter much.
		return null;
	}

	/**
	 * Returns only LEAF pages.
	 * TODO for now this ignores leafPages in other inner nodes. It returns only leaf pages
	 * from the current node.
	 * @param currentSubPos
	 * @return The previous next page or null, if the given page is the first page.
	 */
	private BSTreePage getNextLeafPage(int currentSubPos) {
		//do not add +1, because we compare pages to keys
		if (currentSubPos < getNKeys()) {
			//TODO can we handle this (leaves in other parent) everywhere?
			return getPageByPos(currentSubPos+1).getFirstLeafPage(); 
		}
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
		return getPageByPos(0).getFirstLeafPage();
	}

	/**
	 * 
	 * @return The last leaf page of this branch.
	 */
	private BSTreePage getLastLeafPage() {
		if (isLeaf) {
			return this;
		}
		return getPageByPos(getNKeys()).getLastLeafPage();
	}

	/**
	 * Returns the page at the specified position.
	 */
	BSTreePage getPageByPos(int pos) {
		return subPages[pos];
	}

	/**
	 * This method will fail if called on the first page in the tree. However this should not
	 * happen, because when called, we already have a reference to a previous page.
	 * @param oidIndexPage
	 * @return The position of the given page in the subPage-array with 0 <= pos <= nEntries.
	 */
	@Deprecated
	private int getPagePosition(BSTreePage indexPage) {
		//We know that the element exists, so we iterate to list.length instead of nEntires 
		//(which is not available in this context at the moment.
		for (int i = 0; i < subPages.length; i++) {
			if (subPages[i] == indexPage) {
				return i;
			}
		}
		throw new IllegalStateException("Leaf page not found in parent page.");
	}
	
	private void assignThisAsRootToLeaves() {
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
				BSTreePage p = getPageByPos(i);
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


	boolean isLeaf() {
		return isLeaf;
	}


	void getStats(Stats stats) {
		if (isLeaf()) {
			stats.nNodesLeaf++;
			stats.nEntriesLeaf += nEntries;
			stats.capacityLeaf += keys.length;
			if (nEntries < 1 && parent.parent != null) {
				throw new IllegalStateException();
			}
		} else {
			stats.nNodesInner++;
			stats.nEntriesInner += nEntries + 1;
			stats.capacityInner += keys.length + 1;
			if (nEntries < 1 && parent != null) {
				throw new IllegalStateException();
			}
			for (int i = 0; i < getNKeys() + 1; i++) {
				getPageByPos(i).getStats(stats);
			}
		}
	}

}
