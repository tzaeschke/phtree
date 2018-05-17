/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test.util;

import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.nv.PhTreeNV;

public class TestUtilInMemory implements TestUtilAPI {

	@Override
	public PhTreeNV newTree(int dim, int depth) {
		return PhTreeNV.create(dim);
	}

	@Override
	public <T> PhTree<T> newTreeV(int dim, int depth) {
		return PhTree.create(dim);
	}

	@Override
	public <T> PhTree<T> newTreeV(int dim) {
		return PhTree.create(dim);
	}

	@Override
	public <T> PhTree<T> newTreeHD(int dim) {
		return PhTree.create(dim);
	}

	@Override
	public void close(PhTreeNV tree) {
		//nothing to do
	}

	@Override
	public <T> void close(PhTree<T> tree) {
		//nothing to do
	}

	@Override
	public void beforeTest() {
		//nothing to do
	}

	@Override
	public void beforeTest(Object[] args) {
		//nothing to do
	}

	@Override
	public void afterTest() {
		//nothing to do
	}

	@Override
	public void beforeSuite() {
		//nothing to do
	}

	@Override
	public void afterSuite() {
		//nothing to do
	}

	@Override
	public void beforeClass() {
		//nothing to do
	}

	@Override
	public void afterClass() {
		//nothing to do
	}

}
