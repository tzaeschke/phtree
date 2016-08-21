/*
 * Copyright 2011-2016 ETH Zurich. All Rights Reserved.
 *
 * This software is the proprietary information of ETH Zurich.
 * Use is subject to license terms.
 */
package ch.ethz.globis.phtree.util;

import java.util.Arrays;

/**
 * Quality stats related to data characteristics and tree quality.
 */
public final class PhTreeStats {
	private final int bitWidth;
	public int nNodes;
	public int nAHC; //AHC nodes (formerly Nodes with AHC-postfix representation)
	public int nNtNodes; //NtNodes (formerly Nodes with sub-HC representation)
	public int nNT; //nodes with NT representation
	public int nTotalChildren;
	public long size;  //calculated size in bytes
	public int q_totalDepth;
	public int[] q_nPostFixN;  //filled with  x[currentDepth] = nPost;
	public int[] infixHist = new int[64];  //prefix len
	public int[] nodeDepthHist = new int[64];  //prefix len
	public int[] nodeSizeLogHist = new int[32];  //log (nEntries)
	
	public PhTreeStats() {
		this(64);
	}
	
	public PhTreeStats(int bitWidth) {
		this.bitWidth = bitWidth;
		this.q_nPostFixN = new int[bitWidth];
	}
	
	@Override
	public String toString() {
		StringBuilderLn r = new StringBuilderLn();
		r.appendLn("  nNodes = " + nNodes);
		r.appendLn("  avgNodeDepth = " + (double)q_totalDepth/(double)nNodes); 
		//            "  noPostChildren=" + q_nPostFix1 + "\n" +
		r.appendLn("  AHC=" + nAHC + "  NI=" + nNT + "  nNtNodes=" + nNtNodes);
		double apl = getAvgPostlen(r);
		r.appendLn("  avgPostLen = " + apl + " (" + (bitWidth-apl) + ")");

		return r.toString();
	}
	
	public String toStringHist() {
		StringBuilderLn r = new StringBuilderLn();
		r.appendLn("  infixLen      = " + Arrays.toString(infixHist));
		r.appendLn("  nodeSizeLog   = " + Arrays.toString(nodeSizeLogHist));
		r.appendLn("  nodeDepthHist = " + Arrays.toString(nodeDepthHist));
		r.appendLn("  depthHist     = " + Arrays.toString(q_nPostFixN));
		return r.toString();
	}
	
	/**
	 * 
	 * @param r
	 * @return average postLen, including the HC/LHC bit.
	 */
	public double getAvgPostlen(StringBuilderLn r) {
		long total = 0;
		int nEntry = 0;
		for (int i = 0; i < bitWidth; i++) {
			if (r!=null) {
				//r.appendLn("  depth= " + i + "  n= " + q_nPostFixN[i]);
			}
			total += (bitWidth-i)*(long)q_nPostFixN[i];
			nEntry += q_nPostFixN[i];
		}
		return total/(double)nEntry;
	}
	
	public int getNodeCount() {
		return nNodes;
	}
	
	public int getAhcCount() {
		return nAHC;
	}
	
	public int getNtInternalNodeCount() {
		return nNtNodes;
	}
	
	public int getNtCount() {
		return nNT;
	}

	public long getCalculatedMemSize() {
		return size;
	}

	public int getBitDepth() {
		return bitWidth;
	}

}