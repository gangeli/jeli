package org.goobs.stats;


public interface CountStore<D> extends Iterable<D>, Cloneable{
	public double getCount(D key);
	public void setCount(D key, double count);
	public CountStore<D> emptyCopy();
	public CountStore<D> clone() throws CloneNotSupportedException;
	public CountStore<D> clear();
}
