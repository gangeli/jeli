package org.goobs.classify;

import java.util.Collection;

import org.goobs.foreign.Counter;
import org.goobs.utils.MemoryAccountable;
import org.goobs.utils.Pair;

/*
 * TODO merge ProbabilisticClassifier with Classifier
 */


public interface ProbabilisticClassifier<I,E,O> extends MemoryAccountable{
	public double getProbability(I input, O output, Collection<O> possibleOutputs);
	public Counter <O> getProbabilities(I input, Collection<O> output);
	public O getOutput(I input, Collection<O> outputs);

	public Pair<OutputInfo<E>,OutputInfo<E>> getInfo(I input, O guess, O gold, Collection <O> possibleOutputs);
	
	public String dumpWeights();
	public boolean dumpWeights(String filename);
	public Counter<E> getWeights();
}
