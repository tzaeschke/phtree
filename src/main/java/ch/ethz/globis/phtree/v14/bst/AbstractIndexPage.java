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


/**
 * In the inner pages, the keys are the minimum values of the following page.
	 * 
	 * TODO
	 * To avoid special cases, the tree should be changed as follows:
 * - the empty tree should consist of one empty leaf and one node without keys. Then we could
 *   avoid the following special cases:
 *   - the parent of a leaf is never null, there is always a parent node.
 *   - a inner node is never empty. That means (nLeaf)===(nEntries+1).
 *   This means special treating when creating or deleting from the root node, but the
 *   iterators and internal navigation should be easier. Disadvantage: empty index has two 
 *   pages. -> Should not be a problem, because who creates empty indices? Or classes w/o
 *   instances?
 * 
 * @author Tilmann Zaeschke
 */
abstract class AbstractIndexPage {

	protected final AbstractPagedIndex ind;
	final transient boolean isLeaf;
	final AbstractIndexPage[] subPages;
	final int[] subPageIds;
	private int pageId = -1;
	
	
	AbstractIndexPage(AbstractPagedIndex ind, boolean isLeaf) {
		this.ind = ind;
		if (!isLeaf) {	
			subPages = new AbstractIndexPage[ind.maxInnerN + 1];
			subPageIds = new int[ind.maxInnerN + 1];
			ind.statNInner++;
		} else {
			subPages = null;
			subPageIds = null;
			ind.statNLeaves++;
		}
		this.isLeaf = isLeaf;
	}

	protected abstract AbstractIndexPage newInstance();

	/**
	 * Copy constructor.
	 * @param p
	 */
	AbstractIndexPage(AbstractIndexPage p) {
		ind = p.ind;
		isLeaf = p.isLeaf;
		if (!isLeaf) {
			subPageIds = p.subPageIds.clone();
			subPages = p.subPages.clone();
		} else {
			subPageIds = null;
			subPages = null;
		}
		pageId = p.pageId;
	}
	
	
	/** number of keys. There are nEntries+1 subPages in any leaf page. */
	abstract short getNKeys();

	protected final AbstractIndexPage readPage(int pos) {
		return readOrCreatePage(pos, false);
	}
	
	
	protected final AbstractIndexPage readOrCreatePage(int pos, boolean allowCreate) {
		AbstractIndexPage page = subPages[pos];
		if (page != null) {
			//page is in memory
			return page;
		}

		throw new UnsupportedOperationException();
		
//		//now try to load it
//		int pageId = subPageIds[pos];
//		if (pageId == 0) {
//			if (!allowCreate) {
//				return null;
//			}
//			//create new page
//			page = ind.createPage(this, true);
//			// we have to perform this makeDirty here, because calling it from the new Page
//			// will not work because it is already dirty.
//			incrementNEntries();
//		}
//		subPages[pos] = page;
//		return page;
	}
	
	protected abstract void incrementNEntries();
	
	final AbstractIndexPage readCachedPage(short pos) {
	    AbstractIndexPage page = subPages[pos];
		if (page != null) {
			//page is in memory
			return page;
		}
		
		return readPage(pos);
	}
	

	public abstract void print(String indent);
	
	abstract AbstractIndexPage getParent();

	abstract void setParent(AbstractIndexPage parent);

	/**
	 * Returns only INNER pages.
	 * TODO for now this ignores leafPages on a previous inner node. It returns only leaf pages
	 * from the current node.
	 * @param indexPage
	 * @return The previous leaf page or null, if the given page is the first page.
	 */
	final protected AbstractIndexPage getPrevInnerPage(AbstractIndexPage indexPage) {
		int pos = getPagePosition(indexPage);
		if (pos > 0) {
			AbstractIndexPage page = getPageByPos(pos-1);
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
	final protected AbstractIndexPage getPrevLeafPage(AbstractIndexPage indexPage) {
		int pos = getPagePosition(indexPage);
		if (pos > 0) {
			AbstractIndexPage page = getPageByPos(pos-1);
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
	final protected AbstractIndexPage getNextLeafPage(AbstractIndexPage indexPage) {
		int pos = getPagePosition(indexPage);
		if (pos < getNKeys()) {
			AbstractIndexPage page = getPageByPos(pos+1);
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
	private AbstractIndexPage getFirstLeafPage() {
		if (isLeaf) {
			return this;
		}
		return readPage(0).getFirstLeafPage();
	}

	/**
	 * 
	 * @return The last leaf page of this branch.
	 */
	private AbstractIndexPage getLastLeafPage() {
		if (isLeaf) {
			return this;
		}
		return readPage(getNKeys()).getLastLeafPage();
	}

	/**
	 * Returns (and loads, if necessary) the page at the specified position.
	 */
	protected AbstractIndexPage getPageByPos(int pos) {
		return subPages[pos];
	}

	/**
	 * This method will fail if called on the first page in the tree. However this should not
	 * happen, because when called, we already have a reference to a previous page.
	 * @param oidIndexPage
	 * @return The position of the given page in the subPage-array with 0 <= pos <= nEntries.
	 */
	int getPagePosition(AbstractIndexPage indexPage) {
		//We know that the element exists, so we iterate to list.length instead of nEntires 
		//(which is not available in this context at the moment.
		for (int i = 0; i < subPages.length; i++) {
			if (subPages[i] == indexPage) {
				return i;
			}
		}
		throw new IllegalStateException("Leaf page not found in parent page: " + 
				indexPage.pageId + "   " + Arrays.toString(subPageIds));
	}

	public abstract void printLocal();
	
	protected void assignThisAsRootToLeaves() {
		for (int i = 0; i <= getNKeys(); i++) {
			//leaves may be null if they are not loaded!
			if (subPages[i] != null) {
				subPages[i].setParent(this);
			}
		}
	}
	
	protected int pageId() {
		return pageId;
	}

	/**
	 * @return Minimal key on this branch.
	 */
	abstract long getMinKey();

	/**
	 * @return Value of minimal key on this branch.
	 */
	abstract long getMinKeyValue();
	
	final void setPageId(int pageId) {
		this.pageId = pageId;
	}

	final void clear() {
		if (!isLeaf) {
			for (int i = 0; i < getNKeys()+1; i++) {
				AbstractIndexPage p = readPage(i);
				p.clear();
				//0-IDs are automatically ignored.
				BTPool.reportFreePage(p);
			}
		}
		if (subPageIds != null) {
			for (int i = 0; i < subPageIds.length; i++) {
				subPageIds[i] = 0;
			}
		}
		if (subPages != null) {
			for (int i = 0; i < subPages.length; i++) {
				subPages[i] = null;
			}
		}
		setNEntries(-1);
	}

	abstract void setNEntries(int n);
}
