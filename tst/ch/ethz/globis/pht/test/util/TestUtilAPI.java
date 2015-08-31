package ch.ethz.globis.pht.test.util;

import ch.ethz.globis.pht.PhTree;
import ch.ethz.globis.pht.nv.PhTreeNV;

public interface TestUtilAPI {

	public PhTreeNV newTree(int dim, int depth);

	public <T> PhTree<T> newTreeV(int dim, int depth);

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
