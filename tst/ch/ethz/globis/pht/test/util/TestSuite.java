package ch.ethz.globis.pht.test.util;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import ch.ethz.globis.pht.bits.TestBitsInt;
import ch.ethz.globis.pht.bits.TestBitsIntRemove;
import ch.ethz.globis.pht.bits.TestBitsIntWrite;
import ch.ethz.globis.pht.bits.TestBitsLong;
import ch.ethz.globis.pht.bits.TestBitsLongRemove;
import ch.ethz.globis.pht.bits.TestBitsLongWrite;
import ch.ethz.globis.pht.bits.TestBitsToolsSplitMerge;
import ch.ethz.globis.pht.bits.TestBitsToolsSplitMergeVar;
import ch.ethz.globis.pht.bits.TestBitsToolsSplitMergeVarLong;
import ch.ethz.globis.pht.bits.TestIncSuccessor;
import ch.ethz.globis.pht.bits.TestIncrementor;
import ch.ethz.globis.pht.bits.TestTranspose;
import ch.ethz.globis.pht.test.TestHighDimensions;
import ch.ethz.globis.pht.test.TestIndexDeletion;
import ch.ethz.globis.pht.test.TestIndexInsertion;
import ch.ethz.globis.pht.test.TestIndexPrint;
import ch.ethz.globis.pht.test.TestIndexQueries;
import ch.ethz.globis.pht.test.TestNearestNeighbour;
import ch.ethz.globis.pht.test.TestRangeDouble;
import ch.ethz.globis.pht.test.TestValues;
import ch.ethz.globis.pht.test.TestValuesD;


@RunWith(Suite.class)
@SuiteClasses({
	TestBitsInt.class, TestBitsIntRemove.class, TestBitsIntWrite.class,
	TestBitsLong.class, TestBitsLongRemove.class, TestBitsLongWrite.class,
	TestBitsToolsSplitMerge.class, TestBitsToolsSplitMergeVar.class, 
	TestBitsToolsSplitMergeVarLong.class,
	//TestDomTree.class, TestDomTree1.class,
	TestIncrementor.class, TestIncSuccessor.class,
	TestIndexDeletion.class, TestIndexInsertion.class, TestIndexPrint.class, TestIndexQueries.class,
	TestNearestNeighbour.class, TestRangeDouble.class, TestTranspose.class,
	TestValues.class, TestValuesD.class,
        TestHighDimensions.class
    })
public class TestSuite {

	@BeforeClass
	public static void setUpClass() {
		TestUtil.beforeSuite();
	}

	@AfterClass
	public static void tearDownClass() {
		TestUtil.afterSuite();
	}

}