/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import ch.ethz.globis.phtree.PhDistance;
import ch.ethz.globis.phtree.PhDistanceF;
import ch.ethz.globis.phtree.PhDistanceF_L1;
import ch.ethz.globis.phtree.PhTree;
import ch.ethz.globis.phtree.PhTreeF;
import ch.ethz.globis.phtree.PhTree.PhIterator;
import ch.ethz.globis.phtree.PhTree.PhKnnQuery;
import ch.ethz.globis.phtree.PhTreeF.PhIteratorF;
import ch.ethz.globis.phtree.PhTreeF.PhKnnQueryF;
import ch.ethz.globis.phtree.util.BitTools;
import ch.ethz.globis.phtree.util.Bits;

public class TestNearestNeighbourF_L1 {

	private static final PhDistance DIST = PhDistanceF_L1.THIS;
	
	private <T> PhTreeF<T> newTreeF(int DIM) {
		return PhTreeF.create(DIM);
	}

	private <T> PhTree<T> newTree(int DIM) {
		return PhTree.create(DIM);
	}

	@Test
	public void testDirectHit() {
		PhTreeF<double[]> idx = newTreeF(2);
		idx.put(new double[]{2,2}, new double[]{2,2});
		idx.put(new double[]{1,1}, new double[]{1,1});
		idx.put(new double[]{1,3}, new double[]{1,3});
		idx.put(new double[]{3,1}, new double[]{3,1});
		
		List<double[]> result = toList(idx.nearestNeighbour(0, DIST, 3, 3));
		assertTrue(result.isEmpty());
		
		result = toList(idx.nearestNeighbour(1, DIST, 2, 2));
		assertEquals(1, result.size());
		check(8, result.get(0), 2, 2);
		
		result = toList(idx.nearestNeighbour(1, DIST, 1, 1));
		assertEquals(1, result.size());
		check(8, result.get(0), 1, 1);
		
		result = toList(idx.nearestNeighbour(1, DIST, 1, 3));
		assertEquals(1, result.size());
		check(8, result.get(0), 1, 3);
		
		result = toList(idx.nearestNeighbour(1, DIST, 3, 1));
		assertEquals(1, result.size());
		check(8, result.get(0), 3, 1);
	}
	
//	@Test
//	public void testNeighbour1of4() {
//		PhTreeF<double[]> idx = newTreeF(2);
//		idx.put(new double[]{2,2}, new double[]{2,2});
//		idx.put(new double[]{1,1}, new double[]{1,1});
//		idx.put(new double[]{1,3}, new double[]{1,3});
//		idx.put(new double[]{3,1}, new double[]{3,1});
//		
//		List<double[]> result = toList(idx.nearestNeighbour(1, DIST, 3, 3));
//		check(8, result.get(0), 2, 2);
//		assertEquals(1, result.size());
//	}
	
	@Test
	public void testNeighbour1of5DirectHit() {
		PhTreeF<double[]> idx = newTreeF(2);
		idx.put(new double[]{3,3}, new double[]{3,3});
		idx.put(new double[]{2,2}, new double[]{2,2});
		idx.put(new double[]{1,1}, new double[]{1,1});
		idx.put(new double[]{1,3}, new double[]{1,3});
		idx.put(new double[]{3,1}, new double[]{3,1});
		
		List<double[]> result = toList(idx.nearestNeighbour(1, DIST, 3, 3));
		check(8, result.get(0), 3, 3);
		assertEquals(1, result.size());
	}
	
	@Test
	public void testNeighbour4_5of4() {
		PhTreeF<double[]> idx = newTreeF(2);
		idx.put(new double[]{3,3}, new double[]{3,3});
		idx.put(new double[]{2,2}, new double[]{2,2});
		idx.put(new double[]{4,4}, new double[]{4,4});
		idx.put(new double[]{2,4}, new double[]{2,4});
		idx.put(new double[]{4,2}, new double[]{4,2});
		
		List<double[]> result = toList(idx.nearestNeighbour(4, DIST, 3, 3));
		
		checkContains(result, 3, 3);
		int n = 1;
		n += contains(result, 4, 4) ? 1 : 0;
		n += contains(result, 4, 2) ? 1 : 0;
		n += contains(result, 2, 2) ? 1 : 0;
		n += contains(result, 2, 4) ? 1 : 0;
		
		assertTrue(n >= 4);
	}
	
	@Test
	public void testQueryND64Random1() {
		final int DIM = 4;//5
		final int LOOP = 1;//10;
		final int N = 1000;
		final int NQ = 1000;
		final int MAXV = 1000;
		for (int d = 0; d < LOOP; d++) {
			final Random R = new Random(d);
			PhTreeF<Object> ind = newTreeF(DIM);
			PhKnnQueryF<Object> q = ind.nearestNeighbour(1, DIST, new double[DIM]);
			for (int i = 0; i < N; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ind.put(v, null);
			}
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				double[] exp = nearestNeighbor1(ind, v);
				List<double[]> nnList = toList(q.reset(1, DIST, v));
				
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				double[] nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}

	@Test
	public void testQueryND64Random1_int() {
		final int DIM = 5;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 1000;
		final int MAXV = 1000;
		for (int d = 0; d < LOOP; d++) {
			final Random R = new Random(d);
			PhTree<Object> ind = newTree(DIM);
			PhKnnQuery<Object> q = ind.nearestNeighbour(1, DIST, null, new long[DIM]);
			for (int i = 0; i < N; i++) {
				long[] v = new long[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextInt(MAXV);
				}
				ind.put(v, null);
			}
			for (int i = 0; i < NQ; i++) {
				long[] v = new long[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextInt(MAXV);
				}
				long[] exp = nearestNeighbor1(ind, v);
				List<long[]> nnList = toList(q.reset(1, DIST, v));
				
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				long[] nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}

	@Test
	public void testQueryND64RandomDistFunc() {
		final int DIM = 15;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 1000;
		final int MAXV = 1000;
		final Random R = new Random(0);
		for (int d = 0; d < LOOP; d++) {
			PhTreeF<Object> ind = newTreeF(DIM);
			PhKnnQueryF<Object> q = ind.nearestNeighbour(1, DIST, new double[DIM]);
			for (int i = 0; i < N; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ind.put(v, v);
			}
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				double[] exp = nearestNeighbor1(ind, v);
//				System.out.println("d="+ d + "   i=" + i + "   minD=" + dist(v, exp));
//				System.out.println("v="+ Arrays.toString(v));
//				System.out.println("exp="+ Arrays.toString(exp));
				List<double[]> nnList = toList(q.reset(1, DIST, v));
				
//				System.out.println(ind.toStringPlain());
//				System.out.println("v  =" + Arrays.toString(v));
//				System.out.println("exp=" + Arrays.toString(exp));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				double[] nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}


	@Test
	public void testQueryND64RandomDistFuncBug1() {
		final int DIM = 3;//15;
		final int LOOP = 100;
		final int N = 3;//1000;
		final int NQ = 1;//1000;
		final int MAXV = 10000;
		for (int d = 0; d < LOOP; d++) {
			final Random R = new Random(60);
			PhTreeF<Object> ind = newTreeF(DIM);
			PhKnnQueryF<Object> q = ind.nearestNeighbour(1, DIST, new double[DIM]);
			for (int i = 0; i < N; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ind.put(v, v);
			}
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				double[] exp = nearestNeighbor1(ind, v);
//				System.out.println("d="+ d + "   i=" + i + "   minD=" + dist(v, exp));
//				System.out.println("v="+ Arrays.toString(v));
//				System.out.println("exp="+ Arrays.toString(exp));
				List<double[]> nnList = toList(q.reset(1, DIST, v));
				
//				System.out.println(ind.toStringTree());
//				System.out.println("v  =" + Arrays.toString(v));
//				System.out.println("exp=" + Arrays.toString(exp));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				double[] nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}

	
	@Test
	public void testQueryND64RandomDistFunc_OnArray() {
		final int DIM = 15;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 1000;
		final int MAXV = 1000;
		final Random R = new Random(0);
		double[][] vA = new double[N][DIM];
		for (int d = 0; d < LOOP; d++) {
			PhTreeF<Object> ind = newTreeF(DIM);
			PhKnnQueryF<Object> q = ind.nearestNeighbour(1, DIST, new double[DIM]);
			for (int i = 0; i < N; i++) {
				double[] v = vA[i];//new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ind.put(v, v);
			}
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				double[] exp = nearestNeighbor1(vA, v);
				//        System.out.println("d="+ d + "   i=" + i + "   minD=" + dist(v, exp));
				//        System.out.println("v="+ Arrays.toString(v));
				//        System.out.println("exp="+ Arrays.toString(exp));
				List<double[]> nnList = toList(q.reset(1, DIST, v));

				//        System.out.println(ind.toStringPlain());
				//        System.out.println("v  =" + Arrays.toString(v));
				//        System.out.println("exp=" + Arrays.toString(exp));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				double[] nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}


	@Test
	public void testQueryND64RandomCenterAway() {
		final int DIM = 5;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 1000;
		final int MAXV = 1000;
		final Random R = new Random(0);
		for (int d = 0; d < LOOP; d++) {
			PhTreeF<Object> ind = newTreeF(DIM);
			PhKnnQueryF<Object> q = ind.nearestNeighbour(1, DIST, new double[DIM]);
			for (int i = 0; i < N; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble();//*MAXV;
				}
				ind.put(v, null);
			}
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				double[] exp = nearestNeighbor1(ind, v);
				//        System.out.println("d="+ d + "   i=" + i + "   minD=" + dist(v, exp));
				//        System.out.println("v="+ Arrays.toString(v));
				//        System.out.println("exp="+ Arrays.toString(exp));
				List<double[]> nnList = toList(q.reset(1, DIST, v));

				//        System.out.println(ind.toStringPlain());
				//        System.out.println("v  =" + Arrays.toString(v));
				//        System.out.println("exp=" + Arrays.toString(exp));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				double[] nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}

	@Test
	public void testQueryND64Random10() {
		//final int DIM = 4;//5
		//final int LOOP = 1;//10;
		final int N = 10000;
		final int NQ = 1000;
		final int MAXV = 1;
		for (int d = 2; d < 10; d++) {
			int DIM = d;
			final Random R = new Random(d);
			PhTreeF<Object> ind = newTreeF(DIM);
			PhKnnQueryF<Object> q = ind.nearestNeighbour(10, DIST, new double[DIM]);
			for (int i = 0; i < N; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ind.put(v, null);
			}
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ArrayList<double[]> exp = nearestNeighborK(ind, 10, v);
				List<double[]> nnList = toList(q.reset(10, DIST, v));
				
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				check(v, exp, nnList);
			}
		}
	}

	@Test
	public void testQueryND64Random10DistFunc() {
		final int DIM = 15;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 1000;
		final int MAXV = 1;
		final Random R = new Random(0);
		for (int d = 0; d < LOOP; d++) {
			PhTreeF<Object> ind = newTreeF(DIM);
			PhKnnQueryF<Object> q = ind.nearestNeighbour(10, new double[DIM]);
			for (int i = 0; i < N; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ind.put(v, v);
			}
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ArrayList<double[]> exp = nearestNeighborK(ind, 10, v);
				//        System.out.println("d="+ d + "   i=" + i + "   minD=" + dist(v, exp));
				//        System.out.println("v="+ Arrays.toString(v));
				//        System.out.println("exp="+ Arrays.toString(exp));
				List<double[]> nnList = toList(q.reset(10, DIST, v));

				//        System.out.println(ind.toStringPlain());
				//        System.out.println("v  =" + Arrays.toString(v));
				//        System.out.println("exp=" + Arrays.toString(exp));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				check(v, exp, nnList);
			}
		}
	}


	/**
	 * This used to return an empty result set.
	 */
	@Test
	public void testNN1EmptyResultError() {
		double[][] data = {
				{47, 15, 53, },
				{54, 77, 77, },
				{73, 62, 95, },
		};

		final int DIM = data[0].length;
		final int N = data.length;
		PhTreeF<Object> ind = newTreeF(DIM);
		for (int i = 0; i < N; i++) {
			ind.put(data[i], data[i]);
		}
		
		double[] v={44, 84, 75};
		double[] exp = nearestNeighbor1(ind, v);
		List<double[]> nnList = toList(ind.nearestNeighbour(1, DIST, v));
		assertTrue(!nnList.isEmpty());
		double[] nn = nnList.get(0);
		check(v, exp, nn);
	}
	
	
	@Test
	public void testNPE() {
		final int DIM = 2;
		final int N = 100;
		final int MAXV = 100;
		final Random R = new Random(0);

		PhTreeF<Object> ind = newTreeF(DIM);
		for (int i = 0; i < N; i++) {
		  double[] v = new double[DIM];
			for (int j = 0; j < DIM; j++) {
				v[j] = R.nextDouble()*MAXV;
			}
			ind.put(v, null);
		}

		double[] v = new double[DIM];
		for (int j = 0; j < DIM; j++) {
			v[j] = R.nextDouble()*MAXV;
		}
		double[] exp = nearestNeighbor1(ind, v);
		List<double[]> nnList = toList(ind.nearestNeighbour(1, DIST, v));
		assertTrue(!nnList.isEmpty());
		double[] nn = nnList.get(0);
		check(v, exp, nn);
	}
	
	
	private ArrayList<double[]> nearestNeighborK(PhTreeF<?> tree, int k, double[] q) {
		double dMax = Double.MAX_VALUE;
		ArrayList<double[]> best = new ArrayList<>();
		PhIteratorF<?> i = tree.queryExtent();
		while (i.hasNext()) {
			double[] cand = i.nextKey();
			double dNew = dist(q, cand);
			if (dNew < dMax || best.size() < k) {
				if (best.isEmpty()) {
					dMax = dNew;
					best.add( cand );
				} else {
					int j = 0;
					for ( ; j < best.size(); j++) {
						double dJ = dist(q, best.get(j));
						if (dJ > dNew) {
							best.add(j, cand);
							break;
						}
					}
					if (j == best.size()) {
						best.add(cand);
					}
					if (best.size() > k) {
						best.remove(k); 
					}
					dMax = dist(q, best.get(best.size()-1)); 
				}
			}
		}
		return best;
	}
	
	private double[] nearestNeighbor1(PhTreeF<?> tree, double[] q) {
		double d = Double.MAX_VALUE;
		double[] best = null;
		PhIteratorF<?> i = tree.queryExtent();
		while (i.hasNext()) {
			double[] cand = i.nextKey();
			double dNew = dist(q, cand);
			if (dNew < d) {
				d = dNew;
				best = cand;
			}
		}
		return best;
	}
	
	private double[] nearestNeighbor1(double[][] vA, double[] q) {
		double d = Double.MAX_VALUE;
		double[] best = null;
		for (int i = 0; i < vA.length; i++) {
			double[] cand = vA[i];
			double dNew = dist(q, cand);
			if (dNew < d) {
				d = dNew;
				best = cand;
			}
		}
		return best;
	}
	
	private long[] nearestNeighbor1(PhTree<?> tree, long[] q) {
		double d = Double.MAX_VALUE;
		long[] best = null;
		PhIterator<?> i = tree.queryExtent();
		while (i.hasNext()) {
			long[] cand = i.nextKey();
			double dNew = dist(q, cand);
			if (dNew < d) {
				d = dNew;
				best = cand;
			}
		}
		return best;
	}
	
//	private long[] nearestNeighbor1(long[][] vA, long[] q) {
//		double d = Double.MAX_VALUE;
//		long[] best = null;
//		for (int i = 0; i < vA.length; i++) {
//			long[] cand = vA[i];
//			double dNew = dist(q, cand);
//			if (dNew < d) {
//				d = dNew;
//				best = cand;
//			}
//		}
//		return best;
//	}
	
//	private double[] nearestNeighborK(PhTreeF<?> tree, double[] q) {
//		double d = Double.MAX_VALUE;
//		double[] best = null;
//		PhIteratorF<?> i = tree.queryExtent();
//		while (i.hasNext()) {
//		  double[] cand = i.nextKey();
//			double dNew = dist(q, cand);
//			if (dNew < d) {
//				d = dNew;
//				best = cand;
//			}
//		}
//		return best;
//	}
	
	private void check(double[] v, List<double[]> l1, List<double[]> l2) {
		double distPrev = -1;
		for (int e = 0; e < l2.size(); e++) {
			double d = dist(v, l2.get(e));
			if (distPrev > d) {
				fail();
			}
			distPrev = d;
		}
		for (int e = 0; e < l1.size(); e++) {
			double[] c1 = l1.get(e);
			double[] c2 = l2.get(e);
			for (int i = 0; i < c1.length; i++) {
				if (c1[i] != c2[i]) {
					double d1 = dist(v, c1);
					double d2 = dist(v, c2);
					double maxEps = Math.abs(d2-d1)/(d1+d2);
					if (maxEps >= 0.00001) {
						System.out.println("WARNING: different values found: e=" + e + " d=" + d1 + "/" + d2);
						System.out.println("v =" + Arrays.toString(v));
						System.out.println("c1=" + Arrays.toString(c1));
						System.out.println("c2=" + Arrays.toString(c2));
						System.out.println("v =" + Bits.toBinary(v));
						System.out.println("c1=" + Bits.toBinary(c1));
						System.out.println("c2=" + Bits.toBinary(c2));
						fail();
					}
					break;
				}
			}
		}
	}
	
	private void check(double[] v, double[] c1, double[] c2) {
		for (int i = 0; i < c1.length; i++) {
			if (c1[i] != c2[i]) {
				double d1 = dist(v, c1);
				double d2 = dist(v, c2);
				double maxEps = Math.abs(d2-d1)/(d1+d2);
				if (maxEps >= 0.00001) {
					System.out.println("WARNING: different values found: " + d1 + "/" + d2);
					System.out.println("v =" + Arrays.toString(v));
					System.out.println("c1=" + Arrays.toString(c1));
					System.out.println("c2=" + Arrays.toString(c2));
					System.out.println("v =" + Bits.toBinary(v));
					System.out.println("c1=" + Bits.toBinary(c1));
					System.out.println("c2=" + Bits.toBinary(c2));
					fail();
				}
				break;
			}
		}
	}

	private void check(long[] v, long[] c1, long[] c2) {
		for (int i = 0; i < c1.length; i++) {
			if (c1[i] != c2[i]) {
				double d1 = dist(v, c1);
				double d2 = dist(v, c2);
				double maxEps = Math.abs(d2-d1)/(d1+d2);
				if (maxEps >= 0.00001) {
					System.out.println("WARNING: different values found: " + d1 + "/" + d2);
					System.out.println("c1=" + Arrays.toString(c1));
					System.out.println("c2=" + Arrays.toString(c2));
					System.out.println("c1=" + Bits.toBinary(c1));
					System.out.println("c2=" + Bits.toBinary(c2));
					fail();
				}
				break;
			}
		}
	}

	private double dist(double[] v1, double[] v2) {
		double d = 0;
		if (DIST == PhDistanceF.THIS) {
			for (int i = 0; i < v1.length; i++) {
				double dl = v1[i] - v2[i];
				d += dl*dl;
			}
			return Math.sqrt(d);
		}
		if (DIST == PhDistanceF_L1.THIS) {
			for (int i = 0; i < v1.length; i++) {
				double dl = v1[i] - v2[i];
				d += Math.abs(dl);
			}
			return d;
		}
		throw new UnsupportedOperationException();
	}
	
	private double dist(long[] v1, long[] v2) {
		return DIST.dist(v1, v2);
	}
	
	private void check(int DEPTH, double[] t, double ... ints) {
		for (int i = 0; i < ints.length; i++) {
			assertEquals("i=" + i + " | " + toBinary(ints, DEPTH) + " / " + 
					toBinary(t, DEPTH), ints[i], t[i], 0.0);
		}
	}

	private void checkContains(List<double[]> l, double ... v) {
		for (double[] vl: l) {
			if (Arrays.equals(vl, v)) {
				return;
			}
		}
		fail("Not found: " + Arrays.toString(v));
	}
	
	private boolean contains(List<double[]> l, double ... v) {
		for (double[] vl: l) {
			if (Arrays.equals(vl, v)) {
				return true;
			}
		}
		return false;
	}
	
//	private void check(long[] t, long[] s) {
//		for (int i = 0; i < s.length; i++) {
//			assertEquals("i=" + i + " | " + Bits.toBinary(s) + " / " + 
//					Bits.toBinary(t), (short)s[i], (short)t[i]);
//		}
//	}
	
	private List<double[]> toList(PhKnnQueryF<?> q) {
		ArrayList<double[]> ret = new ArrayList<>();
		while (q.hasNext()) {
			ret.add(q.nextKey());
		}
		return ret;
	}
	
	private List<long[]> toList(PhKnnQuery<?> q) {
		ArrayList<long[]> ret = new ArrayList<>();
		while (q.hasNext()) {
			ret.add(q.nextKey());
		}
		return ret;
	}
	
	private String toBinary(double[] d, int DEPTH) {
	  long[] l = new long[d.length];
	  for (int i = 0; i < l.length; i++) {
	    l[i] = BitTools.toSortableLong(d[i]);
	  }
	  return Bits.toBinary(l, DEPTH);
	}
	
}
