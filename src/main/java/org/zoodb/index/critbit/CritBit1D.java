/*
 * Copyright 2009-2017 Tilmann Zaeschke. All rights reserved.
 * 
 * This file is part of TinSpin.
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
package org.zoodb.index.critbit;

import org.zoodb.index.critbit.CritBit.FullIterator;
import org.zoodb.index.critbit.CritBit.QueryIterator;

/**
 * 
 * @author Tilmann Zaeschke
 * 
 * @param <V> value type
 */
public interface CritBit1D<V> {

	/**
	 * @param key key
	 * @param value value 
	 * @return previous value or 'null' if none existed
	 *
	 * @see CritBit#put(long[], Object) 
	 */
	V put(long[] key, V value);

	/** 
	 * @param key key
	 * @return 'true' if the key exists
	 *
	 * @see CritBit#contains(long[]) 
	 */
	boolean contains(long[] key);

	/** 
	 * @param min Lower left corner of the query window
	 * @param max Upper right corner of the query window
	 * @return Iterator over query result
	 * 
	 * @see CritBit#query(long[], long[]) 
	 */
	QueryIterator<V> query(long[] min, long[] max);

	/** 
	 * @return Number of entries
	 *  
	 * @see CritBit#size() 
	 */
	int size();

	/** 
	 * @param key key
	 * @return previous value or 'null' if none existed
	 * 
	 * @see CritBit#remove(long[]) 
	 */
	V remove(long[] key);

	/** 
	 * @see CritBit#printTree()
	 */
	void printTree();

	/** 
	 * @param key key
	 * @return the value or 'null' if the key does not exists
	 * 
	 * @see CritBit#get(long[]) 
	 */
	V get(long[] key);

	/**
	 * @return Iterator over all entries
	 * 
	 * @see CritBit#iterator() 
	 */
	FullIterator<V> iterator();
}
