/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 * Copyright 2016-2018 Tilmann Zäschke. All Rights Reserved.
 * Copyright 2019 Improbable Worlds Limited. All rights reserved.
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
package ch.ethz.globis.phtree.test;

import ch.ethz.globis.phtree.PhTreeHelper;
import ch.ethz.globis.phtree.util.unsynced.ObjectPool;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObjectPoolTest {

	private static boolean POOLING;
	private static int MAX_OBJECT_POOL_SIZE;
	private static int DEFAULT_POOL_SIZE;

	@BeforeClass
	public static void beforeClass() {
		POOLING = PhTreeHelper.ARRAY_POOLING;
		MAX_OBJECT_POOL_SIZE = PhTreeHelper.MAX_OBJECT_POOL_SIZE;
		DEFAULT_POOL_SIZE = ObjectPool.DEFAULT_POOL_SIZE;
	}

	@AfterClass
	public static void afterClass() {
		PhTreeHelper.ARRAY_POOLING = POOLING;
		PhTreeHelper.MAX_OBJECT_POOL_SIZE = MAX_OBJECT_POOL_SIZE;
		ObjectPool.DEFAULT_POOL_SIZE = DEFAULT_POOL_SIZE;
	}


	@Test
	public void testObjectPoolOn() {
		PhTreeHelper.ARRAY_POOLING = true;
		PhTreeHelper.MAX_OBJECT_POOL_SIZE = 100;
		ObjectPool.DEFAULT_POOL_SIZE = 100;
		ObjectPool<Object> pool = ObjectPool.create(Object::new);
		//empty pool
		for (int i = 0; i < MAX_OBJECT_POOL_SIZE; i++) {
			pool.get();
		}

		Object test = new Object();
		pool.offer(test);
		Object test2 = pool.get();
		Object test3 = pool.get();
		assertTrue(test == test2);
		assertFalse(test == test3);
	}


	@Test
	public void testObjectPoolOff() {
		PhTreeHelper.ARRAY_POOLING = false;
		PhTreeHelper.MAX_OBJECT_POOL_SIZE = 100;
		ObjectPool.DEFAULT_POOL_SIZE = 100;
		ObjectPool<Object> pool = ObjectPool.create(Object::new);
		//empty pool
		for (int i = 0; i < MAX_OBJECT_POOL_SIZE; i++) {
			pool.get();
		}

		Object test = new Object();
		pool.offer(test);
		Object test2 = pool.get();
		Object test3 = pool.get();
		assertFalse(test == test2);
		assertFalse(test == test3);
	}


	@Test
	public void testObjectPool0() {
		PhTreeHelper.ARRAY_POOLING = true;
		PhTreeHelper.MAX_OBJECT_POOL_SIZE = 0;
		ObjectPool.DEFAULT_POOL_SIZE = 0;
		ObjectPool<Object> pool = ObjectPool.create(Object::new);
		//empty pool
		for (int i = 0; i < MAX_OBJECT_POOL_SIZE; i++) {
			pool.get();
		}

		Object test = new Object();
		pool.offer(test);
		Object test2 = pool.get();
		Object test3 = pool.get();
		assertFalse(test == test2);
		assertFalse(test == test3);
	}

}
