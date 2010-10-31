package org.goobs.classify;

import org.goobs.foreign.Counter;
import org.goobs.utils.MemoryAccountable;


/**
 * A FeatureVector is a collection of features.  This collection may or may not be a
 * list, depending on the implementation.
 */
public interface FeatureVector <F> extends MemoryAccountable{
	public static final int UNK_GLOBAL_INDEX = -1;
	
	Counter<F> getFeatures();
	double[] getFeatureVector();
	
	int numFeatures();
	double getCount(int internalIndex);
	int globalIndex(int internalIndex);
}
