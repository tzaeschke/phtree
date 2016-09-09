/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;

import java.util.Iterator;

public interface PhIteratorBase<K, V, E> extends Iterator<V> {

	public V nextValue();

	public E nextEntry();

	/**
	 * Special 'next' method that avoids creating new objects internally by reusing Entry objects.
	 * Advantage: Should completely avoid any GC effort.
	 * Disadvantage: Returned PhEntries are not stable and are only valid until the
	 * next call to next(). After that they may change state. Modifying returned entries may
	 * invalidate the backing tree.
	 * @return The next entry
	 */
	public E nextEntryReuse();

}