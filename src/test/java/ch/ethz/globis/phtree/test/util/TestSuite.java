/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test.util;

import ch.ethz.globis.phtree.bits.TestBitsInt;
import ch.ethz.globis.phtree.bits.TestBitsIntRemove;
import ch.ethz.globis.phtree.bits.TestBitsIntWrite;
import ch.ethz.globis.phtree.bits.TestBitsLong;
import ch.ethz.globis.phtree.bits.TestBitsLongRemove;
import ch.ethz.globis.phtree.bits.TestBitsLongWrite;
import ch.ethz.globis.phtree.bits.TestBitsToolsSplitMerge;
import ch.ethz.globis.phtree.bits.TestBitsToolsSplitMergeVar;
import ch.ethz.globis.phtree.bits.TestBitsToolsSplitMergeVarLong;
import ch.ethz.globis.phtree.bits.TestIncSuccessor;
import ch.ethz.globis.phtree.bits.TestIncrementor;
import ch.ethz.globis.phtree.bits.TestTranspose;
import ch.ethz.globis.phtree.hd.TestHighDimensions;
import ch.ethz.globis.phtree.test.*;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;


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