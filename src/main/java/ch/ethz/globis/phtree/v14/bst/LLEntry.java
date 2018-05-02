package ch.ethz.globis.phtree.v14.bst;

public class LLEntry {
	private final long key;
	private final Object value;
	public LLEntry(long k, Object v) {
		key = k;
		value = v;
	}
	public long getKey() {
		return key;
	}
	public Object getValue() {
		return value;
	}
}