/*
 * Copyright 2009-2014 Tilmann Zaeschke. All rights reserved.
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
package org.zoodb.index.critbit;

import org.zoodb.index.critbit.CritBit.FullIterator;
import org.zoodb.index.critbit.CritBit.QueryIterator;

/**
 * 
 * @author Tilmann Zaeschke
 */
public interface CritBit1D<V> {

	/** @see CritBit#put(long[], Object) */
	V put(long[] key, V value);

	/** @see CritBit#contains(long[]) */
	boolean contains(long[] key);

	/** @see CritBit#query(long[], long[]) */
	QueryIterator<V> query(long[] min, long[] max);

	/** @see CritBit#size() */
	int size();

	/** @see CritBit#remove(long[]) */
	V remove(long[] key);

	/** @see CritBit#printTree() */
	void printTree();

	/** @see CritBit#get(long[]) */
	V get(long[] key);

	/** @see CritBit#iterator() */
	FullIterator<V> iterator();
}
