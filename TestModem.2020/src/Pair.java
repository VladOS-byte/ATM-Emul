
public class Pair<T1, T2> {
	
	private T1 key;
	private T2 value;
	
	public Pair(final T1 key, final T2 value) {
		this.setKey(key);
		this.value = value;
	}
	
	public T1 getKey() {
		return key;
	}
	
	public T2 getValue() {
		return value;
	}

	public void setKey(T1 key) {
		this.key = key;
	}
}
