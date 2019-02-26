/*
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds. All rights reserved.
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
package ch.ethz.globis.phtree.v16hd.bst;

import java.util.Arrays;

import ch.ethz.globis.phtree.util.StringBuilderLn;
import ch.ethz.globis.phtree.v16hd.BitsHD;
import ch.ethz.globis.phtree.v16hd.Node;
import ch.ethz.globis.phtree.v16hd.Node.BSTEntry;
import ch.ethz.globis.phtree.v16hd.Node.BSTStats;
import ch.ethz.globis.phtree.v16hd.Node.REMOVE_OP;
import ch.ethz.globis.phtree.v16hd.PhTree16HD;


public class BSTreePage {

	private static final int INITIAL_PAGE_SIZE = 4;
	
	private BSTreePage parent;
	private long[][] keys;
	private BSTEntry[] values;
	/** number of keys. There are nEntries+1 subPages in any leaf page. */
	private short nEntries;

	private boolean isLeaf;
	private BSTreePage[] subPages;
	private BSTreePage prevLeaf;
	private BSTreePage nextLeaf;


	BSTreePage(Node ind, BSTreePage parent, boolean isLeaf, BSTreePage leftPredecessor) {
		init(ind, parent, isLeaf, leftPredecessor);
	}
	
	void init(Node ind, BSTreePage parent, boolean isLeaf, BSTreePage leftPredecessor) {
		nextLeaf = null;
		prevLeaf = null;
		this.parent = parent;
		if (isLeaf) {
			nEntries = 0;
			int initialPageSize = ind.maxLeafN() <= 8 ? 2 : INITIAL_PAGE_SIZE;
			keys = BSTPool.arrayCreateLong(initialPageSize);
			values = BSTPool.arrayCreateEntries(initialPageSize);
			subPages = null;
			Node.statNLeaves++;
		} else {
			nEntries = -1;
			keys = BSTPool.arrayCreateLong(ind.maxInnerN());
			values = null;
			subPages = BSTPool.arrayCreateNodes(ind.maxInnerN() + 1);
			Node.statNInner++;
		}
		this.isLeaf = isLeaf;

		if (isLeaf && leftPredecessor != null) {
			nextLeaf = leftPredecessor.nextLeaf;
			prevLeaf = leftPredecessor;
			leftPredecessor.nextLeaf = this;
			if (nextLeaf != null) {
				nextLeaf.prevLeaf = this;
			}
		} else {
			nextLeaf = null;
			prevLeaf = null;
		}
	}

	public static BSTreePage create(Node ind, BSTreePage parent, boolean isLeaf, BSTreePage leftPredecessor) {
		return BSTPool.getNode(ind, parent, isLeaf, leftPredecessor);
	}
	
	public static BSTreePage create(Node ind, BSTreePage parent, BSTreePage firstSubpage, BSTreePage secondSubpage) {
		BSTreePage p = create(ind, parent, false, null);
		p.nEntries++;
		p.subPages[0] = firstSubpage;
		p.nEntries++;
		p.subPages[1] = secondSubpage;
		p.keys[0] = secondSubpage.getMinKey();
		firstSubpage.setParent(p);
		secondSubpage.setParent(p);
		return p;
	}

	private int maxInnerN() {
		return keys.length;
	}
	
	private final int minLeafN(int maxLeafN) { 
		return maxLeafN >> 1; 
	}
	
	private final int minInnerN(int maxInnerN) { 
		return maxInnerN >> 1; 
	}  
	
	public BSTreePage findSubPage(long[] key) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        return subPages[pos]; 
	}
	
	public BSTEntry findAndRemove(long[] key, long[] kdKey, Node node, PhTree16HD.UpdateInfo ui) {
		//The stored value[i] is the min-values of the according        int pos = binarySearch(key);
        int pos = binarySearch(key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage page = subPages[pos]; 
        BSTEntry result = null;
        if (page.isLeaf()) {
        	result = page.remove(key, kdKey, node, ui);
            checkUnderflowSubpageLeaf(pos, node);
        } else {
        	result = page.findAndRemove(key, kdKey, node, ui);
        	handleUnderflowSubInner(pos);
        }
        return result;
	}
	

	public Object getOrCreate(long[] key, Node ind) {
		//The stored value[i] is the min-values of the according page[i+1} 
        int pos = binarySearch(key);
        if (pos >= 0) {
            //pos of matching key
            pos++;
        } else {
            pos = -(pos+1);
        }
        //read page before that value
        BSTreePage page = getPageByPos(pos);
        if (page.isLeaf()) {
    		BSTEntry o = page.getOrCreate(key, this, pos, ind);
    		if (o.getKdKey() == null && o.getValue() instanceof BSTreePage) {
    			//add page
    			BSTreePage newPage = (BSTreePage) o.getValue();
    			addSubPage(newPage, newPage.getMinKey(), pos, ind);
    			o.set(key, null, null);
    			return o;
    		}
    		return o;
        }
        return page;
	}
	

	public BSTEntry getValueFromLeaf(long[] key) {
		int pos = binarySearch(key);
		if (pos >= 0) {
            return values[pos];
		}
		//If the value could is not on this page, it does not exist.
		return null;
	}


	/**
	 * Binary search.
	 *
	 * @param key search key
	 */
	int binarySearch(long[] key) {
		return BitsHD.binarySearch(keys, 0, nEntries, key);
	}

	private final void putUnchecked(int pos, long[] key, BSTEntry value, Node ind) {
        //okay so we add it locally
        shiftArrayForInsertion(pos, ind);
        keys[pos] = key;
        values[pos] = value;
        nEntries++;
        ind.incEntryCount();
 	}

	private void shiftArrayForInsertion(int pos, Node ind) {
		ensureSizePlusOne(ind);
		//Only shift if we do not append
		if (pos < nEntries) {
			System.arraycopy(keys, pos, keys, pos+1, nEntries-pos);
			System.arraycopy(values, pos, values, pos+1, nEntries-pos);
		}
	}
	
	private void ensureSizePlusOne(Node ind) {
		if (nEntries + 1 > keys.length) {
			int newLen = keys.length*2 > ind.maxLeafN() ? ind.maxLeafN() : keys.length*2;
			keys = BSTPool.arrayExpand(keys, newLen);
			values = BSTPool.arrayExpand(values, newLen);
		}
	}

	private void ensureSize(int newLen) {
		if (newLen > keys.length) {
			keys = BSTPool.arrayExpand(keys, newLen);
			values = BSTPool.arrayExpand(values, newLen);
		}
	}

	public final BSTEntry getOrCreate(long[] key, BSTreePage parent, int posPageInParent, Node ind) {
		if (!isLeaf) {
			throw new IllegalStateException("Tree inconsistency.");
		}

		//in any case, check whether the key(+value) already exists
        int pos = binarySearch(key);
        //key found? -> pos >=0
        if (pos >= 0) {
        	return values[pos];
        } 
        
        BSTEntry value = new BSTEntry(key, null, null);
        
        if (nEntries < ind.maxLeafN()) {
        	//okay so we add it locally
        	pos = -(pos+1);
    		this.ensureSizePlusOne(ind);
        	if (pos < nEntries) {
        		System.arraycopy(keys, pos, keys, pos+1, nEntries-pos);
        		System.arraycopy(values, pos, values, pos+1, nEntries-pos);
        	}
        	keys[pos] = key;
        	values[pos] = value;
        	nEntries++;
        	ind.incEntryCount();
        	return value;
        } 

        //treat page overflow
        
        //destination page
        BSTreePage destP;
        //created new page?
        boolean isNew = false;
        //is previous page?
        boolean isPrev = false;
        
        if (parent == null) {
    		destP = ind.bstCreatePage(null, true, this);
    		isNew = true;
        } else {
	        //use ind.maxLeafN -1 to avoid pretty much pointless copying (and possible endless 
	        //loops, see iterator tests)
	        BSTreePage next = parent.getNextLeafPage(posPageInParent);
	        if (next != null && next.nEntries < ind.maxLeafN()-1) {
	        	//merge
	        	destP = next;
	        	isPrev = false;
	        } else {
	        	//Merging with prev is not make a big difference, maybe we should remove it...
	        	BSTreePage prev = parent.getPrevLeafPage(posPageInParent);
	        	if (prev != null && prev.nEntries < ind.maxLeafN()-1) {
	        		//merge
	        		destP = prev;
	        		isPrev = true;
	        	} else {
	        		destP = ind.bstCreatePage(parent, true, this);
	        		isNew = true;
	        	}
	        }
        }

        //Ensure all nodes have full capacity
   		this.ensureSize(ind.maxLeafN());
   		destP.ensureSize(ind.maxLeafN());

        //We move 50% of data. For bulkloading, we could keep 95% or so in old page. 100%? But there is no bulk loading.
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
        pos = -(pos+1);
       	int oldNEntriesP = destP.nEntries;
       	nEntries = (short) nEntriesToKeep;
       	destP.nEntries = (short) (nEntriesToCopy + destP.nEntries);
       	//New page and min key
       	if (isNew || !isPrev) {
       		if (BitsHD.isLess(key, destP.keys[0])) {
       			putUnchecked(pos, key, value, ind);
       		} else {
       			destP.putUnchecked(pos - nEntriesToKeep, key, value, ind);
       		}
       	} else {
       		if (BitsHD.isLess(key, keys[0])) {
       			destP.putUnchecked(pos + oldNEntriesP, key, value, ind);
       		} else {
      			putUnchecked(pos - nEntriesToCopy, key, value, ind);
       		}
       	}
       	if (isNew) {
       		//own key remains unchanged
       		//Hack: we return the new page as value of BSEntry
       		value.setValue(destP);
       		return value;
       	} else {
       		//change own key in parent?
       		if (isPrev) {
       	       	//posInParent has not changed!
    			parent.updateKey(getMinKey(), posPageInParent-1);
       		} else {
       			//change key of 'next' page
    			parent.updateKey(destP.getMinKey(), posPageInParent-1+1);
       		}
       		return value;
       	}
	}

	private void updateKey(long[] key, int keyPos) {
		if (keyPos < 0 || keys[keyPos] == key) {
			//nothing changes
			return;
		}

		keys[keyPos] = key;
	}

	@Deprecated
	private static final int NO_POS = -1000;
	
	private void addSubPage(BSTreePage newP, long[] minKey, int keyPos, Node ind) {
		if (isLeaf) {
			throw new IllegalStateException("Tree inconsistency");
		}
		
		if (nEntries < maxInnerN()) {
			//add page here
			
			if (keyPos == NO_POS) {
			
				//For now, we assume a unique index.
				int i = binarySearch(minKey);
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
					long[] oldKey = subPages[0].getMinKey();
					if ( BitsHD.isLess(oldKey, minKey) ) {
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
			BSTreePage newInner = ind.bstCreatePage(parent, false, null);
			
			//TODO use optimized fill ratio for unique values, just like for leaves?.
			int minInnerN = minInnerN(keys.length);
			System.arraycopy(keys, minInnerN+1, newInner.keys, 0, nEntries-minInnerN-1);
			System.arraycopy(subPages, minInnerN+1, newInner.subPages, 0, nEntries-minInnerN);
			newInner.nEntries = (short) (nEntries-minInnerN-1);
			newInner.assignThisAsParentToLeaves();

			if (parent == null) {
				//create a parent
				BSTreePage newRoot = ind.bstCreatePage(null, false, null);
				newRoot.subPages[0] = this;
				newRoot.nEntries = 0;  // 0: indicates one leaf / zero keys
				this.setParent( newRoot );
				ind.bstUpdateRoot(newRoot);
			}

			parent.addSubPage(newInner, keys[minInnerN], NO_POS, ind);

			nEntries = (short) (minInnerN);
			//finally add the leaf to the according page
			BSTreePage newHome;
			long[] newInnerMinKey = newInner.getMinKey();
			if (BitsHD.isLess(minKey, newInnerMinKey)) {
				newHome = this;
			} else {
				newHome = newInner;
			}
			newHome.addSubPage(newP, minKey, NO_POS, ind);
			return;
		}
	}
	
	
	long[] getMinKey() {
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

	public short getNKeys() {
		return nEntries;
	}
	

	public BSTEntry remove(long[] key, long[] kdKey, Node node, PhTree16HD.UpdateInfo ui) {
        int i = binarySearch(key);
        if (i < 0) {
        	//key not found
        	return null;
        }
        
        // first remove the element
        BSTEntry prevValue = values[i];
        REMOVE_OP op = node.bstInternalRemoveCallback(prevValue, kdKey, ui);
        switch (op) {
		case REMOVE_RETURN:
        	System.arraycopy(keys, i+1, keys, i, nEntries-i-1);
        	System.arraycopy(values, i+1, values, i, nEntries-i-1);
        	nEntries--;
        	node.decEntryCount();
        	return prevValue;
		case KEEP_RETURN:
			return prevValue;
		case KEEP_RETURN_NULL:
			return null;
		default:
			throw new IllegalArgumentException();
		}
	}
	
	
	private void checkUnderflowSubpageLeaf(int pos, Node ind) {
		BSTreePage subPage = getPageByPos(pos);
        if (subPage.nEntries == 0) {
        	Node.statNLeaves--;
        	removePage(pos);
        } else if (subPage.nEntries < minLeafN(ind.maxLeafN()) && (subPage.nEntries % 8 == 0)) {
        	//The second term prevents frequent reading of previous and following pages.
        	//TODO Should we instead check for nEntries==MAx>>1 then == (MAX>>2) then <= (MAX>>3)?

        	//now attempt merging this page
        	BSTreePage prevPage = getPrevLeafPage(pos);
        	if (prevPage != null) {
         		//We merge only if they all fit on a single page. This means we may read
        		//the previous page unnecessarily, but we avoid writing it as long as 
        		//possible. TODO find a balance, and do no read prev page in all cases
        		if (subPage.nEntries + prevPage.nEntries < ind.maxLeafN()) {
        			//TODO for now this work only for leaves with the same root. We
        			//would need to update the min values in the inner nodes.
        			System.arraycopy(subPage.keys, 0, prevPage.keys, prevPage.nEntries, subPage.nEntries);
        			System.arraycopy(subPage.values, 0, prevPage.values, prevPage.nEntries, subPage.nEntries);
        			prevPage.nEntries += subPage.nEntries;
        			Node.statNLeaves--;
        			removePage(pos);
        		}
        	}
        }
	}

	private void removePage(int posToRemove) {
		BSTreePage indexPage = getPageByPos(posToRemove);
		
		//remove sub page page from FSM.
		BSTPool.reportFreeNode(indexPage);

		if (nEntries > 0) { //otherwise we just delete this page
			//remove entry
			arraysRemoveInnerEntry(posToRemove);
			nEntries--;
		}
	}
	
	private void handleUnderflowSubInner(int pos) {
		BSTreePage sub = getPageByPos(pos);
		if (sub.nEntries < maxInnerN()>>1) {
			if (sub.nEntries >= 0) {
				BSTreePage prev = getPrevInnerPage(pos);
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
					BSTreePage child = sub.getPageByPos(0);
					replaceChildPage(child, pos);
					Node.statNInner--;
					BSTPool.reportFreeNode(sub);
				}
			} else {
				// nEntries == 0
				if (sub.parent != null) {
					Node.statNInner--;
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
	private void replaceChildPage(BSTreePage subChild, int pos) {
		subPages[pos] = subChild;
		if (pos>0) {
			keys[pos-1] = subChild.getMinKey();
		}
		subChild.setParent(this);
	}
	
	void setParent(BSTreePage parent) {
		this.parent = parent;
	}

	final long[][] getKeys() {
		return keys;
	}

	final BSTEntry[] getValues() {
		return values;
	}

	private void setNEntries(int n) {
		nEntries = (short) n;
	}
	
	
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
	 * @param currentSubPos
	 * @return The previous leaf page or null, if the given page is the first page.
	 */
	private BSTreePage getPrevLeafPage(int currentSubPos) {
		if (currentSubPos > 0) {
			BSTreePage page = getPageByPos(currentSubPos-1);
			return page.getLastLeafPage();
		}
		return null;
	}

	/**
	 * Returns only LEAF pages.
	 * @param currentSubPos
	 * @return The previous next page or null, if the given page is the first page.
	 */
	private BSTreePage getNextLeafPage(int currentSubPos) {
		//do not add +1, because we compare pages to keys
		if (currentSubPos < getNKeys()) {
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


	private void assignThisAsParentToLeaves() {
		for (int i = 0; i <= getNKeys(); i++) {
			//leaves may be null if they are not loaded!
			if (subPages[i] != null) {
				subPages[i].setParent(this);
			}
		}
	}
	
	public final void clear() {
		if (!isLeaf) {
			for (int i = 0; i < getNKeys()+1; i++) {
				BSTreePage p = getPageByPos(i);
				p.clear();
				//0-IDs are automatically ignored.
				BSTPool.reportFreeNode(p);
			}
		}
		if (subPages != null) {
			for (int i = 0; i < subPages.length; i++) {
				subPages[i] = null;
			}
		}
		setNEntries(-1);
	}


	public boolean isLeaf() {
		return isLeaf;
	}

	public void getStats(BSTStats stats) {
		if (isLeaf()) {
			stats.nNodesLeaf++;
			stats.nEntriesLeaf += nEntries;
			stats.capacityLeaf += keys.length;
			if (nEntries < 1 && parent != null && parent.parent != null) {
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

	public BSTEntry getFirstValue() {
		return values[0];
	}

	public BSTreePage getFirstSubPage() {
		return subPages[0];
	}

	BSTreePage[] getSubPages() {
		return subPages;
	}

	void nullify() {
		keys = null;
		values = null;
		subPages = null;
		nextLeaf = null;
		prevLeaf = null;
		parent = null;
	}

	BSTreePage getNextLeaf() {
		return nextLeaf;
	}

	void updateNeighborsRemove() {
		if (prevLeaf != null) {
			prevLeaf.nextLeaf = nextLeaf;
		}
		if (nextLeaf != null) {
			nextLeaf.prevLeaf = prevLeaf;
		}
	}

}
