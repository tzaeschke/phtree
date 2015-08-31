package ch.ethz.globis.pht.test.util;

import ch.ethz.globis.pht.PhTree;
import ch.ethz.globis.pht.nv.PhTreeNV;

public class TestUtilInMemory implements TestUtilAPI {

	@Override
	public PhTreeNV newTree(int dim, int depth) {
		return PhTreeNV.create(dim, depth);
	}

	@Override
	public <T> PhTree<T> newTreeV(int dim, int depth) {
		return PhTree.create(dim, depth);
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
