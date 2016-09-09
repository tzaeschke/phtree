/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import ch.ethz.globis.phtree.PhDistanceF;
import ch.ethz.globis.phtree.PhDistanceSF;
import ch.ethz.globis.phtree.PhDistanceSFCenterDist;
import ch.ethz.globis.phtree.PhDistanceSFEdgeDist;
import ch.ethz.globis.phtree.PhTreeSolidF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhEntrySF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhIteratorSF;
import ch.ethz.globis.phtree.PhTreeSolidF.PhKnnQuerySF;
import ch.ethz.globis.phtree.pre.PreProcessorRangeF;
import ch.ethz.globis.phtree.util.Bits;

@RunWith(Parameterized.class)
public class TestNearestNeighbourFS {

	private static final double BOX_LEN = 0.00001;
	
	private final boolean useEdgeDistFn;
	
	public TestNearestNeighbourFS(boolean useEdgeDistFn) {
		this.useEdgeDistFn = useEdgeDistFn;
	}
	
	@Parameters
	public static List<Object[]> distanceFunctions() {
		return Arrays.asList(new Object[][] {
			{ true },
			{ false }
		});
	}
	
	private <T> PhTreeSolidF<T> newTreeSF(int DIM) {
		return PhTreeSolidF.create(DIM);
	}
  
	private PhDistanceSF newDistFn(PhTreeSolidF<?> tree) {
		PreProcessorRangeF pre = tree.getPreProcessor();
		int dims = tree.getDims();
		if (useEdgeDistFn) {
			return new PhDistanceSFEdgeDist(pre, dims);
		} else {
			return new PhDistanceSFCenterDist(pre, dims);
		}
	}
	
	private void populate(PhTreeSolidF<?> ind, Random R, 
			int N, int DIM, int MAXV) {
		for (int i = 0; i < N; i++) {
			double[] vMin = new double[DIM];
			double[] vMax = new double[DIM];
			for (int j = 0; j < DIM; j++) {
				vMin[j] = R.nextDouble()*MAXV;
				vMax[j] = vMin[j] + R.nextDouble()*BOX_LEN*MAXV;
			}
			ind.put(vMin, vMax, null);
		}
	}
	
	@Test
	public void testQueryND64Random1() {
		final int DIM = 3;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 100;
		final int MAXV = 1000;
		for (int d = 0; d < LOOP; d++) {
			final Random R = new Random(d);
			PhTreeSolidF<Object> ind = newTreeSF(DIM);
			PhKnnQuerySF<Object> q = ind.nearestNeighbour(1, newDistFn(ind), new double[DIM]);
			populate(ind, R, N, DIM, MAXV);
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				PhEntrySF<Object> exp = nearestNeighbor1(ind, v);
				List<PhEntrySF<Object>> nnList = toList(q.reset(1, null, v));
				
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				PhEntrySF<Object> nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}

	@Test
	public void testQueryND64RandomDistFunc() {
		final int DIM = 15;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 100;
		final int MAXV = 1000;
		final Random R = new Random(0);
		for (int d = 0; d < LOOP; d++) {
			PhTreeSolidF<Object> ind = newTreeSF(DIM);
			PhKnnQuerySF<Object> q = ind.nearestNeighbour(1, newDistFn(ind), new double[DIM]);
			populate(ind, R, N, DIM, MAXV);
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				PhEntrySF<Object> exp = nearestNeighbor1(ind, v);
				//        System.out.println("d="+ d + "   i=" + i + "   minD=" + dist(v, exp));
				//        System.out.println("v="+ Arrays.toString(v));
				//        System.out.println("exp="+ Arrays.toString(exp));
				List<PhEntrySF<Object>> nnList = toList(q.reset(1, PhDistanceF.THIS, v));

				//        System.out.println(ind.toStringPlain());
				//        System.out.println("v  =" + Arrays.toString(v));
				//        System.out.println("exp=" + Arrays.toString(exp));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				PhEntrySF<Object> nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}


	@Test
	public void testQueryND64RandomCenterAway() {
		final int DIM = 5;
		final int LOOP = 10;
		final int N = 1000;
		final int NQ = 100;
		final int MAXV = 1000;
		final Random R = new Random(0);
		for (int d = 0; d < LOOP; d++) {
			PhTreeSolidF<Object> ind = newTreeSF(DIM);
			PhKnnQuerySF<Object> q = ind.nearestNeighbour(1, newDistFn(ind), new double[DIM]);
			populate(ind, R, N, DIM, 1);
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				PhEntrySF<?> exp = nearestNeighbor1(ind, v);
				//        System.out.println("d="+ d + "   i=" + i + "   minD=" + dist(v, exp));
				//        System.out.println("v="+ Arrays.toString(v));
				//        System.out.println("exp="+ Arrays.toString(exp));
				List<PhEntrySF<Object>> nnList = toList(q.reset(1, PhDistanceF.THIS, v));

				//        System.out.println(ind.toStringPlain());
				//        System.out.println("v  =" + Arrays.toString(v));
				//        System.out.println("exp=" + Arrays.toString(exp));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				PhEntrySF<Object> nn = nnList.get(0);
				check(v, exp, nn);
			}
		}
	}

	@Test
	public void testQueryND64Random10() {
		//final int DIM = 4;//5
		//final int LOOP = 1;//10;
		final int N = 1000;
		final int NQ = 100;
		final int MAXV = 1;
		for (int d = 2; d < 10; d++) {
			int DIM = d;
			final Random R = new Random(d);
			PhTreeSolidF<Object> ind = newTreeSF(DIM);
			PhKnnQuerySF<Object> q = ind.nearestNeighbour(10, newDistFn(ind), new double[DIM]);
			populate(ind, R, N, DIM, MAXV);
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ArrayList<PhEntrySF<Object>> exp = nearestNeighborK(ind, 10, v);
				List<PhEntrySF<Object>> nnList = toList(q.reset(10, null, v));
				
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
		final int NQ = 100;
		final int MAXV = 1;
		final Random R = new Random(0);
		for (int d = 0; d < LOOP; d++) {
			PhTreeSolidF<Object> ind = newTreeSF(DIM);
			PhKnnQuerySF<Object> q = ind.nearestNeighbour(10, newDistFn(ind), new double[DIM]);
			populate(ind, R, N, DIM, MAXV);
			for (int i = 0; i < NQ; i++) {
				double[] v = new double[DIM];
				for (int j = 0; j < DIM; j++) {
					v[j] = R.nextDouble()*MAXV;
				}
				ArrayList<PhEntrySF<Object>> exp = nearestNeighborK(ind, 10, v);
				//        System.out.println("d="+ d + "   i=" + i + "   minD=" + dist(v, exp));
				//        System.out.println("v="+ Arrays.toString(v));
				//        System.out.println("exp="+ Arrays.toString(exp));
				List<PhEntrySF<Object>> nnList = toList(q.reset(10, PhDistanceF.THIS, v));

				//        System.out.println(ind.toStringPlain());
				//        System.out.println("v  =" + Arrays.toString(v));
				//        System.out.println("exp=" + Arrays.toString(exp));
				assertTrue("i=" + i + " d=" + d, !nnList.isEmpty());
				check(v, exp, nnList);
			}
		}
	}


	@Test
	public void testNPE() {
		final int DIM = 2;
		final int N = 100;
		final int MAXV = 100;
		final Random R = new Random(0);

		PhTreeSolidF<Object> ind = newTreeSF(DIM);
		populate(ind, R, N, DIM, MAXV);

		double[] v = new double[DIM];
		for (int j = 0; j < DIM; j++) {
			v[j] = R.nextDouble()*MAXV;
		}
		PhEntrySF<Object> exp = nearestNeighbor1(ind, v);
		List<PhEntrySF<Object>> nnList = toList(ind.nearestNeighbour(1, newDistFn(ind), v));
		assertTrue(!nnList.isEmpty());
		PhEntrySF<Object> nn = nnList.get(0);
		check(v, exp, nn);
	}
	
	
	private <T> ArrayList<PhEntrySF<T>> nearestNeighborK(PhTreeSolidF<T> tree, int k, double[] q) {
		double dMax = Double.MAX_VALUE;
		ArrayList<PhEntrySF<T>> best = new ArrayList<>();
		PhIteratorSF<T> i = tree.iterator();
		while (i.hasNext()) {
			PhEntrySF<T> cand = i.nextEntry();
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
	
	private <T> PhEntrySF<T> nearestNeighbor1(PhTreeSolidF<T> tree, double[] q) {
		double d = Double.MAX_VALUE;
		PhEntrySF<T> best = null;
		PhIteratorSF<T> i = tree.iterator();
		while (i.hasNext()) {
			PhEntrySF<T> cand = i.nextEntry();
			double dNew = dist(q, cand);
			if (dNew < d) {
				d = dNew;
				best = cand;
			}
		}
		return best;
	}
	
	private <T> void check(double[] v, List<PhEntrySF<T>> l1, List<PhEntrySF<T>> l2) {
		double distPrev = -1;
		for (int e = 0; e < l2.size(); e++) {
			double d = dist(v, l2.get(e));
			if (distPrev > d) {
				System.out.println("c1: " + l2.get(e-1).toString());
				System.out.println("c2: " + l2.get(e).toString());
				fail();
			}
			distPrev = d;
		}
		for (int e = 0; e < l1.size(); e++) {
			PhEntrySF<?> c1 = l1.get(e);
			PhEntrySF<?> c2 = l2.get(e);
			check(v, c1, c2);
		}
	}

	private void check(double[] v, PhEntrySF<?> c1, PhEntrySF<?> c2) {
		double[] min1 = c1.lower();
		double[] max1 = c1.upper();
		double[] min2 = c2.lower();
		double[] max2 = c2.upper();
		for (int i = 0; i < min1.length; i++) {
			if (min1[i] != min2[i] || max1[i] != max2[i]) {
				double d1 = dist(v, c1);
				double d2 = dist(v, c2);
				double maxEps = Math.abs(d2-d1)/(d1+d2);
				if (maxEps >= 0.00001) {
					System.out.println("WARNING: different values found: " + d1 + "/" + d2);
					System.out.println("v =" + Arrays.toString(v));
					System.out.println("c1=" + Arrays.toString(min1) + "/" + Arrays.toString(max1));
					System.out.println("c2=" + Arrays.toString(min2) + "/" + Arrays.toString(max2));
					System.out.println("v =" + Bits.toBinary(v));
					System.out.println("c1=" + Bits.toBinary(min1) + "/" + Bits.toBinary(max1));
					System.out.println("c2=" + Bits.toBinary(min2) + "/" + Bits.toBinary(max2));
					System.out.println("c1: " + c1.toString());
					System.out.println("c2: " + c2.toString());
					fail();
				}
				break;
			}
		}
	}

	private double dist(double[] v1, PhEntrySF<?> e2) {
		if (useEdgeDistFn) {
			return distEdge(v1, e2);
		} else {
			return distCenter(v1, e2);
		}
	}
	
	private double distEdge(double[] v1, PhEntrySF<?> e2) {
		double[] min = e2.lower();
		double[] max = e2.upper();
		double d = 0;
		for (int i = 0; i < v1.length; i++) {
			double dl = 0;
			if (v1[i] < min[i]) {
				dl = min[i] - v1[i];
			} else if (v1[i] > max[i]) {
				dl = v1[i] - max[i];
			}
			d += dl*dl;
		}
		return Math.sqrt(d);
	}
	
	private double distCenter(double[] v1, PhEntrySF<?> e2) {
		double[] min = e2.lower();
		double[] max = e2.upper();
		double d = 0;
		for (int i = 0; i < v1.length; i++) {
			double dl = (max[i] + min[i]) / 2 - v1[i];
			d += dl*dl;
		}
		return Math.sqrt(d);
	}
	
	private <T> List<PhEntrySF<T>> toList(PhKnnQuerySF<T> q) {
		ArrayList<PhEntrySF<T>> ret = new ArrayList<>();
		while (q.hasNext()) {
			ret.add(q.nextEntry());
		}
		return ret;
	}
}
