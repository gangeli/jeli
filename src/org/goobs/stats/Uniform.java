package org.goobs.stats;

import java.util.Iterator;
import java.util.Set;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class Uniform<DOMAIN> implements DiscreteDistribution<DOMAIN>, Bayesian<DOMAIN,Uniform<DOMAIN>,MLEPrior<DOMAIN, Uniform<DOMAIN>>> {

	@Override
	public void makeFlat() {
		//already flat
	}

	private static class UniformSufficientStatistics<D> extends ExpectedSufficientStatistics<D,Uniform<D>> {
		private Uniform<D> dist;
		private UniformSufficientStatistics(Uniform<D> dist){
			this.dist = dist;
		}
		@Override
		protected void registerDatum(D datum, double prob) {
			//do nothing
		}
		@Override
		public void clear() {
			//do nothing
		}
		@Override
		public Uniform<D> distribution() {
			return dist;
		}
	}

	public final int size;
	private Set<DOMAIN> elements;


	public Uniform(int size){
		this.size = size;
	}

	public Uniform(Set<DOMAIN> elements){
		this.elements = elements;
		this.size = elements.size();
	}

	@Override
	public ExpectedSufficientStatistics<DOMAIN, Uniform<DOMAIN>> newStatistics(MLEPrior<DOMAIN, Uniform<DOMAIN>> domainUniformMLEPrior) {
		return new UniformSufficientStatistics<DOMAIN>(this);
	}

	@Override
	public double prob(DOMAIN key) {
		return 1.0 / ((double) size);
	}

	@Override
	public String toString(KeyPrinter<DOMAIN> p) {
		return "Uniform("+size+")";
	}

	@Override
	public Iterator<DOMAIN> iterator() {
		if(this.elements == null){
			throw new IllegalStateException("Cannot iterate over Uniform distribution with only size set");
		} else {
			return elements.iterator();
		}
	}
}
