package org.goobs.stats;

public interface Distribution <DOMAIN> extends Cloneable, Iterable<DOMAIN>{
	public double prob(DOMAIN key);
	public String toString(KeyPrinter<DOMAIN> p);
}
