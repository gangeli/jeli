package org.goobs.classify;

import java.util.List;

/**
 * Classifier factories construct classifiers from training instances.
 */
public interface ClassifierFactory<I, L>{
	StaticClassifier<I, L> trainClassifier(
			List<LabeledInstance<I, L>> trainingData);
}
