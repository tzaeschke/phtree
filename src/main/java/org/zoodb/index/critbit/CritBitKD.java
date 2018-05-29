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

import org.zoodb.index.critbit.CritBit.QueryIteratorKD;

/**
 * 
 * @author Tilmann Zaeschke
 * 
 * @param <V> value type
 */
public interface CritBitKD<V> {

	/** 
	 * @param key key
	 * @param value value 
	 * @return previous value or 'null' if none existed
	 * 
	 * @see CritBit#putKD(long[], Object) 
	 */
	V putKD(long[] key, V value);

	/** 
	 * @param key key
	 * @return 'true' if the key exists
	 * 
	 * @see CritBit#containsKD(long[]) 
	 */
	boolean containsKD(long[] key);

	/**
	 * @return Number of entries
	 *  
	 * @see CritBit#size() 
	 */  
	int size();

	/** 
	 * @param lowerLeft Lower left corner of the query window
	 * @param upperRight Upper right corner of the query window
	 * @return Iterator over query result
	 * 
	 * @see CritBit#queryKD(long[], long[]) 
	 */  
	QueryIteratorKD<V> queryKD(long[] lowerLeft, long[] upperRight);

	/** 
	 * @param key key
	 * @return previous value or 'null' if none existed
	 * 
	 * @see CritBit#removeKD(long[]) 
	 */  
	V removeKD(long[] key);

	/** 
	 * @see CritBit#printTree() 
	 */  
	void printTree();

	/** 
	 * @param key key
	 * @return the value or 'null' if the key does not exists
	 * 
	 * @see CritBit#getKD(long[]) 
	 */  
	V getKD(long[] key);

}
