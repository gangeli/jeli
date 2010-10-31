package org.goobs.minimize;

public interface DifferentiableFunction extends MathFunction{
  double[] derivativeAt(double[] x);
}
