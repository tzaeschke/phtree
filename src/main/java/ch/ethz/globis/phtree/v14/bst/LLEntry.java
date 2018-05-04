package ch.ethz.globis.phtree.v14.bst;

public class LLEntry {
	
	private long key;
	private Object value;
	
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
	public void set(long key, Object value) {
		this.key = key;
		this.value = value;
	}
}