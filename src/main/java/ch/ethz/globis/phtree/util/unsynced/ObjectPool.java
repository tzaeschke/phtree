/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable. All rights reserved.
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
package ch.ethz.globis.phtree.util.unsynced;

import ch.ethz.globis.phtree.PhTreeHelper;

import java.util.function.Supplier;

/**
 * Reference pooling and management for Node instances.
 * 
 * @author ztilmann
 */
public class ObjectPool<T> {

	/**
	 * Size of object pools, currently only used for node objects.
	 * To disable pooling, set to '0'.
	 */
	public static int DEFAULT_POOL_SIZE = PhTreeHelper.MAX_OBJECT_POOL_SIZE;

	private final T[] pool;
	private int poolSize;
	private final Supplier<T> constructor;

	@SuppressWarnings("unchecked")
	private ObjectPool(Supplier<T> constructor) {
		this.constructor = constructor;
		this.pool = (T[]) new Object[DEFAULT_POOL_SIZE];
	}

	public static <T> ObjectPool<T> create(Supplier<T> constructor) {
		return new ObjectPool<>(constructor);
	}

	public T get() {
		return poolSize == 0 ? constructor.get(): pool[--poolSize];
	}

	public void offer(T node) {
		if (poolSize < pool.length) {
			pool[poolSize++] = node;
		}
	}
}
