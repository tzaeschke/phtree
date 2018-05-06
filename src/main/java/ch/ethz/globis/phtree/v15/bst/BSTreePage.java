/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 *
 * This software is the proprietary information of Tilmann Zäschke.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.v15.bst;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.v15.bst.BSTree.REMOVE_OP;
import ch.ethz.globis.phtree.v15.bst.BSTree.Stats;

class BSTreePage<T> {
	
	private BSTreePage<T> parent;
	private final long[] keys;
	private final Object[] values;
	/** number of keys. There are nEntries+1 subPages in any leaf page. */
	private short nEntries;

	private final boolean isLeaf;
	private BSTreePage<T>[] subPages;


	@SuppressWarnings("unchecked")
	public BSTreePage(BSTree<T> ind, BSTreePage<T> parent, boolean isLeaf) {
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
		
		if (!isLeaf) {	
			subPages = new BSTreePage[ind.maxInnerN + 1];
			BSTree.statNInner++;
		} else {
			subPages = null;
			BSTree.statNLeaves++;
		}
		this.isLeaf = isLeaf;
	}

	public BSTreePage(BSTree<T> ind, BSTreePage<T> parent, BSTreePage<T> firstSubpage, BSTreePage<T> secondSubpage) {
		this(ind, parent, false);
		nEntries++;
		subPages[0] = firstSubpage;
		nEntries++;
		subPages[1] = secondSubpage;
		keys[0] = secondSubpage.getMinKey();
		firstSubpage.setParent(this);
		secondSubpage.setParent(this);
	}

	private int maxInnerN() {
		return keys.length;
	}
	
	private int maxLeafN() {
		return keys.length;
	}
	
	private final int minLeafN(int maxLeafN) { 
		return maxLeafN >> 1; 
	}
	
	private final int minInnerN(int maxInnerN) { 
		return maxInnerN >> 1; 
	}  
	
	BSTreePage<T> findSubPage(long key) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(0, nEntries, key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        return subPages[pos]; 
	}
	
	Object findAndRemove(long key, Function<T, REMOVE_OP> predicateRemove, BSTree<T> ind) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(0, nEntries, key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage<T> page = subPages[pos]; 
        Object result = null;
        if (page.isLeaf()) {
        	result = page.remove(key, predicateRemove, ind);
            checkUnderflowSubpageLeaf(pos);
        } else {
        	result = page.findAndRemove(key, predicateRemove, ind);
        	handleUnderflowSubInner(pos);
        }
        return result;
	}
	
	/**
	 * 
	 * @param key the lookup key
	 * @param collisionHandler 
	 * @return the page
	 */
	@SuppressWarnings("unchecked")
	public Object put(long key, T value, BiFunction<T, T, Object> collisionHandler, BSTree<T> ind) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(0, nEntries, key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage<T> page = getPageByPos(pos);
        if (page == null) {
        	page = createLeafPage(ind, pos);
        }
        if (page.isLeaf()) {
    		Object o = page.put(key, value, this, pos, collisionHandler, ind);
    		if (o instanceof BSTreePage) {
    			//add page
    			BSTreePage<T> newPage = (BSTreePage<T>) o;
    			addSubPage(newPage, newPage.getMinKey(), pos, ind);
    			return null;
    		}
    		return o;
        }
        return page;
	}
	
	public LLEntry getValueFromLeaf(long key) {
		if (!isLeaf) {
			throw new IllegalStateException("Leaf inconsistency.");
		}
		int pos = binarySearch(0, nEntries, key);
		if (pos >= 0) {
            return new LLEntry( key, values[pos] == BSTree.NULL ? null : values[pos]);
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
     * @param collisionHandler 
     */
	@SuppressWarnings("unchecked")
	public final Object put(long key, T value, BSTreePage<T> parent, int posPageInParent, 
			BiFunction<T, T, Object> collisionHandler, BSTree<T> ind) {
		if (!isLeaf) {
			throw new IllegalStateException("Tree inconsistency.");
		}

		//in any case, check whether the key(+value) already exists
        int pos = binarySearch(0, nEntries, key);
        //key found? -> pos >=0
        if (pos >= 0) {
        	T oldVal = (T) values[pos];
        	if (collisionHandler != null) {
        		//Collision -> use collision handler (this will retain BSTEntry but may update it's value T
        		return collisionHandler.apply(oldVal, value);
        	}
        	values[pos] = value;
            return oldVal;
        } 

    	if (collisionHandler != null) {
    		//No collision, call handler, but no need to return anything (except null)
    		collisionHandler.apply(null, null);
    	}
        
        if (nEntries < maxLeafN()) {
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
        BSTreePage<T> destP;
        //created new page?
        boolean isNew = false;
        //is previous page?
        boolean isPrev = false;
        
        if (parent == null) {
    		destP = new BSTreePage<>(ind, null, true);
    		isNew = true;
        } else {
	        //use ind.maxLeafN -1 to avoid pretty much pointless copying (and possible endless 
	        //loops, see iterator tests)
	        BSTreePage<T> next = parent.getNextLeafPage(posPageInParent);
	        if (next != null && next.nEntries < next.maxLeafN()-1) {
	        	//merge
	        	destP = next;
	        	isPrev = false;
	        } else {
	        	//Merging with prev is not make a big difference, maybe we should remove it...
	        	BSTreePage<T> prev = parent.getPrevLeafPage(posPageInParent);
	        	if (prev != null && prev.nEntries < prev.maxLeafN()-1) {
	        		//merge
	        		destP = prev;
	        		isPrev = true;
	        	} else {
	        		destP = new BSTreePage<>(ind, parent, true);
	        		isNew = true;
	        	}
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
       			put(key, value, parent, -2, null, ind);
       		} else {
       			destP.put(key, value, parent, -2, null, ind);
       		}
       	} else {
       		if (keys[0] > key) {
       			destP.put(key, value, parent, -2, null, ind);
       		} else {
       			put(key, value, parent, -2, null, ind);
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
	
	private void addSubPage(BSTreePage<T> newP, long minKey, int keyPos, BSTree<T> ind) {
		if (isLeaf) {
			throw new IllegalStateException("Tree inconsistency");
		}
		
		if (nEntries < maxInnerN()) {
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
			return;
		} else {
			//treat page overflow
			BSTreePage<T> newInner = ind.createPage(parent, false);
			
			//TODO use optimized fill ration for unique values, just like for leaves?.
			int minInnerN = minInnerN(keys.length);
			System.arraycopy(keys, minInnerN+1, newInner.keys, 0, nEntries-minInnerN-1);
			System.arraycopy(subPages, minInnerN+1, newInner.subPages, 0, nEntries-minInnerN);
			newInner.nEntries = (short) (nEntries-minInnerN-1);
			newInner.assignThisAsParentToLeaves();

			if (parent == null) {
				//create a parent
				BSTreePage<T> newRoot = ind.createPage(null, false);
				newRoot.subPages[0] = this;
				newRoot.nEntries = 0;  // 0: indicates one leaf / zero keys
				this.setParent( newRoot );
				ind.updateRoot(newRoot);
			}

			parent.addSubPage(newInner, keys[minInnerN], NO_POS, ind);

			nEntries = (short) (minInnerN);
			//finally add the leaf to the according page
			BSTreePage<T> newHome;
			long newInnerMinKey = newInner.getMinKey();
			if (minKey < newInnerMinKey) {
				newHome = this;
			} else {
				newHome = newInner;
			}
			newHome.addSubPage(newP, minKey, NO_POS, ind);
			return;
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
	

	@SuppressWarnings("unchecked") Object remove(long key, Function<T, REMOVE_OP> predicateRemove, BSTree<T> ind) {
        int i = binarySearch(0, nEntries, key);
        if (i < 0) {
        	//key not found
        	return null;
        }
        
        
        // first remove the element
        Object prevValue = values[i];
        REMOVE_OP op = REMOVE_OP.REMOVE_RETURN;
        if (predicateRemove != null) {
        	op = predicateRemove.apply((T) prevValue); 
        }
        switch (op) {
		case REMOVE_RETURN:
        	System.arraycopy(keys, i+1, keys, i, nEntries-i-1);
        	System.arraycopy(values, i+1, values, i, nEntries-i-1);
        	nEntries--;
        	ind.decreaseEntries();
        	//TODO merge with neighbors if to small
        	return prevValue;
		case KEEP_RETURN:
			return prevValue;
		case KEEP_RETURN_NULL:
			return null;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	private void checkUnderflowSubpageLeaf(int pos) {
		BSTreePage<T> subPage = getPageByPos(pos);
        if (subPage.nEntries == 0) {
        	BSTree.statNLeaves--;
        	removePage(pos);
        } else if (subPage.nEntries < (subPage.maxLeafN() >> 1) && (subPage.nEntries % 8 == 0)) {
        	//The second term prevents frequent reading of previous and following pages.
        	//TODO Should we instead check for nEntries==MAx>>1 then == (MAX>>2) then <= (MAX>>3)?

        	//now attempt merging this page
        	BSTreePage<T> prevPage = getPrevLeafPage(pos);
        	if (prevPage != null) {
         		//We merge only if they all fit on a single page. This means we may read
        		//the previous page unnecessarily, but we avoid writing it as long as 
        		//possible. TODO find a balance, and do no read prev page in all cases
        		if (subPage.nEntries + prevPage.nEntries < subPage.maxLeafN()) {
        			//TODO for now this work only for leaves with the same root. We
        			//would need to update the min values in the inner nodes.
        			System.arraycopy(subPage.keys, 0, prevPage.keys, prevPage.nEntries, subPage.nEntries);
        			System.arraycopy(subPage.values, 0, prevPage.values, prevPage.nEntries, subPage.nEntries);
        			prevPage.nEntries += subPage.nEntries;
        			BSTree.statNLeaves--;
        			removePage(pos);
        		}
        	}
        }
	}

	private void removePage(int posToRemove) {
		BSTreePage<T> indexPage = getPageByPos(posToRemove);
		
		//remove sub page page from FSM.
		BTPool.reportFreePage(indexPage);

		if (nEntries > 0) { //otherwise we just delete this page
			//remove entry
			arraysRemoveInnerEntry(posToRemove);
			nEntries--;
		}
	}
	
	private void handleUnderflowSubInner(int pos) {
		BSTreePage<T> sub = getPageByPos(pos);
		if (sub.nEntries < maxInnerN()>>1) {
			if (sub.nEntries >= 0) {
				BSTreePage<T> prev = getPrevInnerPage(pos);
				if (prev != null && !prev.isLeaf) {
					// this is only good for merging inside the same parent.
					if ((sub.nEntries % 2 == 0) && (prev.nEntries + sub.nEntries < maxInnerN())) {
						System.arraycopy(sub.keys, 0, prev.keys, prev.nEntries+1, sub.nEntries);
						System.arraycopy(sub.subPages, 0, prev.subPages, prev.nEntries+1, sub.nEntries+1);
						//find key for the first appended page -> go up or go down????? Up!
						prev.keys[prev.nEntries] = keys[pos - 1]; 
						prev.nEntries += sub.nEntries + 1;  //for the additional key
						prev.assignThisAsParentToLeaves();
						removePage(pos);
					}
					return;
				}
		
				if (sub.nEntries == 0) {
					//only one element left, no merging occurred -> move sub-page up to parent
					BSTreePage<T> child = sub.getPageByPos(0);
					replaceChildPage2(sub, child, pos);
					BSTree.statNInner--;
					BTPool.reportFreePage(sub);
				}
			} else {
				// nEntries == 0
				if (sub.parent != null) {
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
	private void replaceChildPage2(BSTreePage<T> indexPage, BSTreePage<T> subChild, int pos) {
		//remove page from FSM.
		BTPool.reportFreePage(indexPage);
		subPages[pos] = subChild;
		if (pos>0) {
			keys[pos-1] = subChild.getMinKey();
		}
		subChild.setParent(this);
	}
	
	void setParent(BSTreePage<T> parent) {
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
		return getPageByPos(nEntries).getMax();
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
	
	
	private BSTreePage<T> createLeafPage(BSTree<T> ind, int pos) {
		if (subPages[pos] != null) {
			throw new IllegalStateException();
		}

		//create new page
		BSTreePage<T> page = ind.createPage(this, true);
		incrementNEntries();
		subPages[pos] = page;
		return page;
		
	}


	/**
	 * Returns only INNER pages.
	 * TODO for now this ignores leafPages on a previous inner node. It returns only leaf pages
	 * from the current node.
	 * @param currentSubPos
	 * @return The previous leaf page or null, if the given page is the first page.
	 */
	private BSTreePage<T> getPrevInnerPage(int currentSubPos) {
		if (currentSubPos > 0) {
			BSTreePage<T> page = getPageByPos(currentSubPos-1);
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
	 * @param currentSubPos
	 * @return The previous leaf page or null, if the given page is the first page.
	 */
	private BSTreePage<T> getPrevLeafPage(int currentSubPos) {
		if (currentSubPos > 0) {
			BSTreePage<T> page = getPageByPos(currentSubPos-1);
			return page.getLastLeafPage();
		}
		return null;
	}

	/**
	 * Returns only LEAF pages.
	 * TODO for now this ignores leafPages in other inner nodes. It returns only leaf pages
	 * from the current node.
	 * @param currentSubPos
	 * @return The previous next page or null, if the given page is the first page.
	 */
	private BSTreePage<T> getNextLeafPage(int currentSubPos) {
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
	private BSTreePage<T> getFirstLeafPage() {
		if (isLeaf) {
			return this;
		}
		return getPageByPos(0).getFirstLeafPage();
	}

	/**
	 * 
	 * @return The last leaf page of this branch.
	 */
	private BSTreePage<T> getLastLeafPage() {
		if (isLeaf) {
			return this;
		}
		return getPageByPos(getNKeys()).getLastLeafPage();
	}

	/**
	 * Returns the page at the specified position.
	 */
	BSTreePage<T> getPageByPos(int pos) {
		return subPages[pos];
	}


	private void assignThisAsParentToLeaves() {
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
				BSTreePage<T> p = getPageByPos(i);
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
