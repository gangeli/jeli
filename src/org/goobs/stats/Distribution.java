package org.goobs.stats;

public interface Distribution <DOMAIN> extends Cloneable {
	public double prob(DOMAIN key);
	public String toString(KeyPrinter<DOMAIN> p);
}
