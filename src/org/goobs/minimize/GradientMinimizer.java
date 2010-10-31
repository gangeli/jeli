package org.goobs.minimize;

public interface GradientMinimizer {

	public double[] minimize(DifferentiableFunction objective, double[] initialWeights, double tolerance);
}
