package org.goobs.distributions;

import org.goobs.functional.Function;
import org.goobs.utils.Pair;

public interface DiscreteDistribution <Output> extends Distribution <Output> {
	public double expectation(Function<Output,Double> func);
	public void foreach(Function<Pair<Output,Double>,Object> func);
	public double getCount(Output term);
	public double totalCount();
}
