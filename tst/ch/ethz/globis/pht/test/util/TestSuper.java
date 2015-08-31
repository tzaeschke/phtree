package ch.ethz.globis.pht.test.util;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class TestSuper {

	@BeforeClass
	public static void setUpClass() {
		TestUtil.beforeClass();
	}

	@AfterClass
	public static void tearDownClass() {
		TestUtil.afterClass();
	}

	@Before
	public void setUp() {
		TestUtil.beforeTest();
	}

	@After
	public void tearDown() {
		TestUtil.afterTest();
	}

}
