package org.goobs.stats;


import java.io.Serializable;

public interface CountStore<D> extends Iterable<D>, Cloneable, Serializable {
	public double getCount(D key);
	public void setCount(D key, double count);
	public CountStore<D> emptyCopy();
	public CountStore<D> clone() throws CloneNotSupportedException;
	public CountStore<D> clear();
	public double totalCount();
}
