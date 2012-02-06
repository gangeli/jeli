package org.goobs.stats;

import java.io.Serializable;

public interface Distribution <DOMAIN> extends Cloneable, Serializable {
	public double prob(DOMAIN key);
	public String toString(KeyPrinter<DOMAIN> p);
}
