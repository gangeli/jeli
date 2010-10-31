package org.goobs.classify;

import java.util.List;

import org.goobs.utils.ConfigFile;


/**
 * Probabilistic classifier factories construct probabilistic classifiers from
 * training instances.
 */
public abstract class ProbabilisticClassifierFactory<I, F, L> {
	public abstract ProbabilisticClassifier<I,F,L> trainClassifier(
			List<LabeledInstance<I, L>> trainingData);
	public ProbabilisticClassifier<I,F,L> trainClassifier(
			List<LabeledInstance<I, L>> trainingData,
			FeatureExtractor<I,F,L> featureExtractor){
		return trainClassifier(trainingData);	//default implementation
	}

	@SuppressWarnings("unchecked")
	protected ClassificationDatum<F> labeledInstanceToDatum(LabeledInstance<I, L> instance,
			FeatureExtractor<I, F, L> featureExtractor,
			FeatureFactory<F> featureFact,
			boolean lazy) {
		if(lazy){
			return new ClassificationDatum(instance, featureExtractor, featureFact);
		}else{
			// (extract features for possible outputs)
			L[] outputs = instance.getPossbleOutputs();
			FeatureVector<F>[] possibleOutputs = new FeatureVector[outputs.length];
			for (int i = 0; i < outputs.length; i++) {
				possibleOutputs[i] = featureFact.newTrainFeature(featureExtractor
						.extractFeatures(instance.getInput(), outputs[i]));
				//Safe mode check
				if(ConfigFile.META_CONFIG.getBoolean("safe_mode", false)){
					FeatureVector<F> check = featureFact.newTrainFeature(featureExtractor
							.extractFeatures(instance.getInput(), outputs[i]));
					if(!possibleOutputs[i].equals(check)){
						System.err.println(possibleOutputs[i]);
						System.err.println(check);
						throw new IllegalStateException("Returned different features on two runs of feature extractor");
					}
				}
			}
			// (get actual output)
			int output = instance.getOutputIndex();

			// (return)
			return new ClassificationDatum<F>(possibleOutputs, output);
		}
	}

	protected double[] buildInitialWeights(int numFeatures) {
		double[] rtn = new double[numFeatures];
		for(int i=0; i<rtn.length; i++){
			rtn[i] = 0.0;
		}
		return rtn;
	}
}
