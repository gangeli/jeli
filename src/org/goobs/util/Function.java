package org.goobs.util;

import java.io.Serializable;

public interface Function <I,O> extends Serializable{
	public O eval(I input);
}
