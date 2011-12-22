package org.goobs.stats;

public interface DiscreteDistribution<DOMAIN> extends Distribution<DOMAIN>, Iterable<DOMAIN> {
	public void makeFlat();
}
