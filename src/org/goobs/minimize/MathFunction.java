package org.goobs.minimize;

public interface MathFunction {
  int dimension();
  double valueAt(double[] x);
}
