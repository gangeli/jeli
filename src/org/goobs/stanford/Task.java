package org.goobs.stanford;

import org.goobs.testing.Dataset;
import org.goobs.testing.Datum;

public interface Task<E extends Datum> {
	public void perform(Dataset<E> d);
	public String name();
	public Class<? extends Task>[] dependencies();
}
