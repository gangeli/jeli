package org.goobs.stats;

import java.util.Random;

public abstract class DiscreteDistribution<DOMAIN> implements Distribution<DOMAIN>, Iterable<DOMAIN> {
	public abstract void makeFlat();

	@Override
	public DOMAIN sample(Random r) {
		double target = r.nextDouble();
		double cdf = 0.0;
		for(DOMAIN d : this){
			cdf += prob(d);
			if(cdf >= target){
				return d;
			}
		}
		throw new IllegalStateException("Sampled out of domain [0,1]: sumProb=" + cdf + " target was " + target);
	}
}
