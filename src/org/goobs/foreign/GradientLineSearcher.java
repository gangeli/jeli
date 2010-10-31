package org.goobs.foreign;

import org.goobs.minimize.DifferentiableFunction;

/**
 * @author Dan Klein
 */
public interface GradientLineSearcher {
  public double[] minimize(DifferentiableFunction function, double[] initial, double[] direction);
}
