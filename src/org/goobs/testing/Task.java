package org.goobs.testing;

public interface Task<E extends Datum> {
	public Dataset<E> perform();
	public Dataset<E> load();
	public String name();
	public boolean isSatisfied();
	public Class[] dependencies();
}
