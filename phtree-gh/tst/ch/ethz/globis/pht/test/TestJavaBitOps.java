package ch.ethz.globis.pht.test;

import org.junit.Test;

import ch.ethz.globis.pht.util.Bits;

public class TestJavaBitOps {

	//@Test
	public void testAnd() {
		short s = -1234;
		int i = -1234;
		
		long rs1 = s & 0xFFFF;
		long rs2 = s & 0xFFFFL;
		long ri1 = i & 0xFFFFFFFF;
		long ri2 = i & 0xFFFFFFFFL;
		long ri3 = ((long)i) & 0xFFFFFFFFL;
		long ri4 = i >= 0 ? i & 0xFFFFFFFF : 0 ;
		
		System.out.println("oxFF = " + 0xFFFFFFFFL + "  " + Bits.toBinary(0xFFFFFFFFL));
		System.out.println("rs1 = " + rs1 + "  " + Bits.toBinary(rs1));
		System.out.println("rs2 = " + rs2 + "  " + Bits.toBinary(rs2));
		System.out.println("ri1 = " + ri1 + "  " + Bits.toBinary(ri1, 64));
		System.out.println("ri2 = " + ri2 + "  " + Bits.toBinary(ri2, 64));
		System.out.println("ri3 = " + ri3 + "  " + Bits.toBinary(ri3, 64));
		System.out.println("ri4 = " + ri4 + "  " + Bits.toBinary(ri4, 64));
	}
	
	//@Test
	public void testAndInt() {
		int i = -12345678;
		
//		long rs1 = s & 0xFFFF;
//		long rs2 = s & 0xFFFFL;
		long ri1 = i & 0xFFFFFFFF;
		long ri2 = i & 0xFFFFFFFFL;
		long ri3 = ((long)i) & 0xFFFFFFFFL;
		long ri4 = i >= 0 ? i & 0xFFFFFFFF : 0 ;
		
		System.out.println("oxFF = " + 0xFFFFFFFFL + "  " + Bits.toBinary(0xFFFFFFFFL));
//		System.out.println("rs1 = " + rs1 + "  " + Bits.toBinary(rs1));
//		System.out.println("rs2 = " + rs2 + "  " + Bits.toBinary(rs2));
		System.out.println("ri1 = " + ri1 + "  " + Bits.toBinary(ri1, 64));
		System.out.println("ri2 = " + ri2 + "  " + Bits.toBinary(ri2, 64));
		System.out.println("ri3 = " + ri3 + "  " + Bits.toBinary(ri3, 64));
		System.out.println("ri4 = " + ri4 + "  " + Bits.toBinary(ri4, 64));
	}
	
}
