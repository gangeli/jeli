package org.goobs.distributions;

public interface Distribution<O> {
	public void addCount(O object, double count);
	public double getProb(O object);
	public O infer();
	public O sample();
}
