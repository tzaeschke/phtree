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


public abstract class AbstractPageIterator<E> implements LongLongIndex.LongLongIterator<E> {

	protected final AbstractPagedIndex ind;
	
	private final int modCount;
	
	protected AbstractPageIterator(AbstractPagedIndex ind) {
		this.ind = ind;
		this.modCount = ind.getModCount();
	}
	
	protected final AbstractIndexPage findPage(AbstractIndexPage currentPage, short pagePos) {
		return currentPage.readCachedPage(pagePos);
	}

	protected void checkValidity() {
		ind.checkValidity(modCount);
	}
}