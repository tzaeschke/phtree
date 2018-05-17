/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test.util;

import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.nv.PhTreeNV;

public interface TestUtilAPI {

	public PhTreeNV newTree(int dim, int depth);

	public <T> PhTree<T> newTreeV(int dim, int depth);
	public <T> PhTree<T> newTreeV(int dim);
	public <T> PhTree<T> newTreeHD(int dim);

	public void close(PhTreeNV tree);
	public <T> void close(PhTree<T> tree);
	public void beforeTest();
	public void beforeTest(Object[] args);
	public void afterTest();
	public void beforeSuite();
	public void afterSuite();
	public void beforeClass();
	public void afterClass();
}
