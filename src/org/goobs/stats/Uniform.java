package org.goobs.stats;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A distribution in itself, it can also serve as a "force everything to be uniform" prior for a multinomial
 */
public class Uniform<DOMAIN> extends  DiscreteDistribution<DOMAIN> implements Bayesian<DOMAIN,Uniform<DOMAIN>, Prior<DOMAIN, Uniform<DOMAIN>>>, Prior<DOMAIN,Multinomial<DOMAIN>> {

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

	public final double size;
	private Set<DOMAIN> elements = new HashSet<DOMAIN>();

	public Uniform(double size){
		this.size = size;
	}

	public Uniform(int size){
		this.size = size;
	}

	public Uniform(Set<DOMAIN> elements){
		this.elements = elements;
		this.size = elements.size();
	}

	@Override
	public void makeFlat() {
		//already flat
	}

	@Override
	public Multinomial<DOMAIN> posterior(Multinomial<DOMAIN> empirical) {
		Multinomial<DOMAIN> rtn = null;
		try {
			rtn = empirical.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
		for(DOMAIN key : rtn){
			rtn.setCount(key, 1.0);
		}
		return rtn;
	}

	@Override
	public ExpectedSufficientStatistics<DOMAIN, Uniform<DOMAIN>> newStatistics(Prior<DOMAIN, Uniform<DOMAIN>> domainUniformMLEPrior) {
		return new UniformSufficientStatistics<DOMAIN>(this);
	}

	@Override
	public double prob(DOMAIN key) {
		return 1.0 / size;
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
	
	public static <DOMAIN> Uniform<DOMAIN> mkPrior(){
		return new Uniform<DOMAIN>(Double.POSITIVE_INFINITY);
	}
}
