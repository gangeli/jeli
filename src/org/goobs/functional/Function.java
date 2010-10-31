package org.goobs.functional;

import java.io.Serializable;

public interface Function <I,O> extends Serializable{
	public O eval(I input);
}
