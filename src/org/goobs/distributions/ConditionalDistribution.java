package org.goobs.distributions;

public interface ConditionalDistribution<I,O> {
	public double getProb(I condition, O output);
	public O infer(I condition);
	public O sample(I condition);
	public String dumpWeights();
}
