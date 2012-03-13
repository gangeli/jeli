package org.goobs.stats;

import java.io.Serializable;
import java.util.Random;

public interface Distribution <DOMAIN> extends Cloneable, Serializable {
	public double prob(DOMAIN key);
	public DOMAIN sample(Random r);
	public String toString(KeyPrinter<DOMAIN> p);
}
