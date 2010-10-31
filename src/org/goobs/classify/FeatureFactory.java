package org.goobs.classify;

import org.goobs.foreign.Counter;
import org.goobs.utils.MemoryAccountable;

public interface FeatureFactory <F> extends MemoryAccountable{
	public FeatureVector <F> newTrainFeature(Counter <F> features);
	public FeatureVector <F> newTestFeature(Counter <F> features);
	public int numFeatures();
	public F getFeature(int featureIndex);
	public int getIndex(F featureValue);
	public Counter<F> weightCounter(double[] weights);
}