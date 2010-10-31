package org.goobs.classify;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

import org.goobs.foreign.Counter;
import org.goobs.foreign.LBFGSMinimizer;
import org.goobs.utils.Pair;
import org.goobs.utils.Tree;
import org.goobs.utils.ConfigFile;
import org.goobs.minimize.DifferentiableFunction;
import org.goobs.minimize.GradientMinimizer;



public class MaximumEntropyClassifier<Input, EncodeType, Output> implements
ProbabilisticClassifier<Input,EncodeType,Output> {

	/**
	 * Factory for training MaximumEntropyClassifiers.
	 */
	public static class Factory<I, F, L> extends
	ProbabilisticClassifierFactory<I,F,L> {

		private double sigma;
		private int iterations;
		private FeatureExtractor<I, F, L> featureExtractor;

		@SuppressWarnings("unchecked")
		public ProbabilisticClassifier<I,F,L> trainClassifier(List<LabeledInstance<I, L>> trainingData) {
			
			FeatureFactory<F> featureFact
				= new ArrayFeatureVector.ArrayFeatureFactory<F>();
			
			//(create data)
			ClassificationDatum<F>[] data = new ClassificationDatum[trainingData.size()];
			int i=0;
			int memUse = 0;
			for(LabeledInstance <I,L> instance : trainingData){
				data[i] = labeledInstanceToDatum(instance, featureExtractor, featureFact, false);
				if(ConfigFile.META_CONFIG.getBoolean("safe_mode", false)){
					ClassificationDatum<F> check = labeledInstanceToDatum(instance, featureExtractor, featureFact, false);
					if(!data[i].equals(check)){
						throw new IllegalStateException("Extracted different datums on two subsequent runs!");
					}
				}
				memUse += data[i].estimateMemoryUsage();
				i+=1;
			}
			
			//(create initial weights)
			double[] initialWeights = buildInitialWeights(featureFact.numFeatures());
			
			//(minimize)
			DifferentiableFunction objective 
				= new ObjectiveFunction<F,L>(data, sigma, featureFact.numFeatures());
			GradientMinimizer minimizer = new LBFGSMinimizer(iterations);
			double[] weights = minimizer.minimize(objective, initialWeights, 1e-4);
			//(safe mode redundancy check)
			if(ConfigFile.META_CONFIG.getBoolean("safe_mode", false)){
				objective = new ObjectiveFunction<F,L>(data, sigma, featureFact.numFeatures());
				minimizer = new LBFGSMinimizer(iterations);
				initialWeights = buildInitialWeights(featureFact.numFeatures());
				double[] weightsCheck = minimizer.minimize(objective, initialWeights, 1e-4);
				for(int j=0; j<weights.length; j++){
					if(weights[j] != weightsCheck[j]){
						throw new IllegalStateException("Weights don't match: " + weights[j] + " vs " + weightsCheck[j]);
					}
				}
			}
			
			//(return)
			return new MaximumEntropyClassifier<I, F, L>(weights, featureExtractor, featureFact);
		}
		
		@Override
		public ProbabilisticClassifier<I,F,L> trainClassifier(
				List<LabeledInstance<I, L>> trainingData,
				FeatureExtractor<I,F,L> featureExtractor){
			this.featureExtractor = featureExtractor;
			return trainClassifier(trainingData);
		}

		/**
		 * Sigma controls the variance on the prior / penalty term. 1.0 is a
		 * reasonable value for large problems, bigger sigma means LESS
		 * smoothing. Zero sigma is a special indicator that no smoothing is to
		 * be done. <p/> Iterations determines the maximum number of iterations
		 * the optimization code can take before stopping.
		 */
		public Factory(double sigma, int iterations,
				FeatureExtractor<I, F, L> featureExtractor) {
			this.sigma = sigma;
			this.iterations = iterations;
			this.featureExtractor = featureExtractor;
		}
	}

	/**
	 * This is the MaximumEntropy objective function: the (negative) log
	 * conditional likelihood of the training data, possibly with a penalty for
	 * large weights. Note that this objective get MINIMIZED so it's the
	 * negative of the objective we normally think of.
	 */
	public static class ObjectiveFunction<F, L> implements DifferentiableFunction {

		private ClassificationDatum<F>[] data;
		private double sigma;
		private int dimension;
		private double lastValue;
		private double[] lastDerivative;
		private double[] lastX;

		public int dimension() {
			return dimension;
		}

		public double valueAt(double[] x) {
			ensureCache(x);
			return lastValue;
		}

		public double[] derivativeAt(double[] x) {
			ensureCache(x);
			return lastDerivative;
		}

		private void ensureCache(double[] x) {
			if (requiresUpdate(lastX, x)) {
				Pair<Double, double[]> currentValueAndDerivative = calculate(x);
				lastValue = currentValueAndDerivative.car();
				lastDerivative = currentValueAndDerivative.cdr();
				lastX = x;
			}
		}

		private boolean requiresUpdate(double[] lastX, double[] x) {
			if (lastX == null)
				return true;
			for (int i = 0; i < x.length; i++) {
				if (lastX[i] != x[i])
					return true;
			}
			return false;
		}

		/**
		 * The most important part of the classifier learning process! This
		 * method determines, for the given weight vector x, what the (negative)
		 * log conditional likelihood of the data is, as well as the derivatives
		 * of that likelihood wrt each weight parameter.
		 */
		private Pair<Double, double[]> calculate(double[] weights) {
			double objective = 0.0;
			double[] derivatives = new double[dimension()];

			//--Generate smoothing parameters
			double wNormSq = 0.0;
			for(double w : weights){
				wNormSq += w*w;
			}
			double sigTerm = 1.0 /(2.0*this.sigma*this.sigma);
			double objSmooth = sigTerm * wNormSq;

			//--Generate Likelihood Data
			double[] actCount = new double[dimension()];
			double[] expCount = new double[dimension()];
			double sum = 0.0;
			for(ClassificationDatum<F> datum : data){
				//(get probabilities)
				double[] logProbs = getLogProbabilities(datum, weights);
				double[] probs = getProbabilities(datum, weights);
				int numOutputs = datum.numPossibleOutputs();
				//(asserts)
				assert(logProbs.length == numOutputs);
				assert(probs.length == numOutputs);
				
				//(log likelihood sum)
				sum += logProbs[datum.outputIndex()];
				
				//--Derivative
				//(actual count)
				FeatureVector <F> features = datum.output();
				for(int actFeature = 0; actFeature < features.numFeatures(); actFeature++){
					int absoluteIndex = features.globalIndex(actFeature);
					double featureCount = features.getCount(actFeature);
					actCount[absoluteIndex] += featureCount;
				}
				//(expected count)
				for(int possibleOutput=0; possibleOutput<datum.numPossibleOutputs(); possibleOutput++){
					FeatureVector <F> featuresPrime = datum.possibleOutput(possibleOutput);
					for(int actFeature=0; actFeature < featuresPrime.numFeatures(); actFeature++){
						int absoluteIndex = featuresPrime.globalIndex(actFeature);
						double featureCount = featuresPrime.getCount(actFeature);
						expCount[absoluteIndex] += probs[possibleOutput] * featureCount;
					}
				}
				
			}
			
			//--Generate Objective
			objective = -(sum - objSmooth);

			//--Generate Derivative
			for(int linFeature = 0; linFeature < dimension(); linFeature++){
				double derSmooth = sigTerm * 2* weights[linFeature];
				derivatives[linFeature] = -(actCount[linFeature] - expCount[linFeature] - derSmooth);
			}

			return new Pair<Double, double[]>(objective, derivatives);
		}
		

		public ObjectiveFunction(ClassificationDatum<F>[] data, double sigma, int dimension) {
			this.data = data;
			this.sigma = sigma;
			this.dimension = dimension;
		}
	}
	
	
	private double[] weights;
	private FeatureExtractor<Input, EncodeType, Output> featureExtractor;
	private FeatureFactory<EncodeType> featureFact;


	public MaximumEntropyClassifier(
			double[] weights,
			FeatureExtractor<Input, EncodeType, Output> featureExtractor,
			FeatureFactory<EncodeType> featureFact) {
		this.weights = weights;
		this.featureExtractor = featureExtractor;
		this.featureFact = featureFact;
	}

	private static <F, L> double[] getProbabilities(
			ClassificationDatum <F> datum,
			double[] weights  ) {
		int numOutputs = datum.numPossibleOutputs();

		// --Get the distribution
		double[] probs = new double[numOutputs];
		double normalize = 0.0;
		for (int output = 0; output < numOutputs; output++) {
			double score = 0.0;
			FeatureVector <F> featureVect = datum.possibleOutput(output);
			int numActiveFeatures = featureVect.numFeatures();
			for (int feature = 0; feature < numActiveFeatures; feature++) {
				int globalIndex = featureVect.globalIndex(feature);
				double weight = (globalIndex<0) ? 0.0 : weights[globalIndex];
				double count = featureVect.getCount(feature);
				
				score += weight * count;
			}
			// (denomiator term sum(exp(w*f(y'))) )
			normalize += Math.exp(score);
			// (set the probability)
			probs[output] = Math.exp(score);
		}
		// (normalize and log the probability)
		for (int output = 0; output < numOutputs; output++) {
			probs[output] = probs[output] / normalize;
		}

		// --Return the distribution
		return probs;
	}


	/**
	 * Calculate the log probabilities of each class, for the given datum
	 * (feature bundle). Note that the weighted votes (referred to as
	 * activations) are *almost* log probabilities, but need to be normalized.
	 */
	private static <F, L> double[] getLogProbabilities(
			ClassificationDatum<F> datum,
			double[] weights ) {

		int numOutputs = datum.numPossibleOutputs();

		// --Get the distribution
		double[] logProbs = new double[numOutputs];
		double normalize = 0.0;
		for (int output = 0; output < numOutputs; output++) {
			double score = 0.0;
			FeatureVector <F> featureVect = datum.possibleOutput(output);
			int numActiveFeatures = featureVect.numFeatures();
			for (int feature = 0; feature < numActiveFeatures; feature++) {
				int globalIndex = featureVect.globalIndex(feature);
				double weight = (globalIndex<0) ? 0.0 : weights[globalIndex];
				double count = featureVect.getCount(feature);
				
				score += weight * count;
			}
			// (denomiator term sum(exp(w*f(y'))) )
			normalize += Math.exp(score);
			// (set the probability)
			logProbs[output] = score;
		}
		// (normalize and log the probability)
		double log = Math.log(normalize);
		for (int output = 0; output < numOutputs; output++) {
			logProbs[output] = logProbs[output] - log;
		}

		// --Return the distribution
		return logProbs;
	}

	@Override
	public double getProbability(Input input, Output output, Collection<Output> possibleOutputs){
		return getProbabilities(input, possibleOutputs).getCount(output);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Counter<Output> getProbabilities(Input input, Collection<Output> possibleOutputs) {
		//--Extract features
		FeatureVector <EncodeType>[] outputVect = new FeatureVector[possibleOutputs.size()];
		Output[] keyVect = (Output[]) new Object[possibleOutputs.size()];
		int i=0;
		for(Output out : possibleOutputs){
			outputVect[i] = featureFact.newTestFeature(featureExtractor.extractFeatures(input, out));
			keyVect[i] = out;
			i+=1;
		}
		
		//--Get Probabilities
		Counter <Output> counts = new Counter <Output> ();
		ClassificationDatum<EncodeType> datum = new ClassificationDatum<EncodeType>(outputVect, 0);	//don't care about index ('0')
		double[] probabilities = getProbabilities(datum, weights);
		for(int k=0; k<outputVect.length; k++){
			double prob = probabilities[k];
			counts.setCount(keyVect[k], prob);
		}
		
		//--Return
		counts.normalize();
		return counts;
	}
	
	@Override
	public Output getOutput(Input input, Collection<Output> outputs){
		return getProbabilities(input, outputs).argMax();
	}
	
	@Override
	public Pair<OutputInfo<EncodeType>,OutputInfo<EncodeType>> getInfo(	Input input, 
																		Output guessOut, 
																		Output goldOut, 
																		Collection <Output> possibleOutputs){
		//--Get probabilities
		Counter <Output> probs = getProbabilities(input, possibleOutputs);
		probs.normalize();
		OutputInfo<EncodeType> guessInfo = new OutputInfo<EncodeType>(probs.getCount(guessOut));
		OutputInfo<EncodeType> goldInfo = new OutputInfo<EncodeType>(probs.getCount(goldOut));
		//--Get Features
		//(guess)
		Counter<EncodeType> guess = featureExtractor.extractFeatures(input, guessOut);
		for(EncodeType key : guess.keySet()){
			int index = featureFact.getIndex(key);
			double count = guess.getCount(key);
			double weight = weights[index];
			guessInfo.registerFeature(key,count,weight);
		}
		//(gold)
		Counter<EncodeType> gold = featureExtractor.extractFeatures(input, goldOut);
		for(EncodeType key : gold.keySet()){
			int index = featureFact.getIndex(key);
			double count = gold.getCount(key);
			double weight = weights[index];
			goldInfo.registerFeature(key,count,weight);
		}
		//--Return
		return new Pair<OutputInfo<EncodeType>,OutputInfo<EncodeType>>(guessInfo, goldInfo);
	}
	
	@Override
	public String dumpWeights() {
		PriorityQueue <String> pq = new PriorityQueue <String> ();
		HashMap <String, Double> mapping = new HashMap<String, Double> ();
		DecimalFormat format = new DecimalFormat("0.000");
		int maxlen = 0;
		for(int i=0; i<weights.length; i++){
			EncodeType name = featureFact.getFeature(i);
			String str = name.toString();
			pq.add(str);
			maxlen = Math.max(maxlen, str.length());
			mapping.put(str, weights[i]);
		}
		
		StringBuilder b = new StringBuilder();
		while(!pq.isEmpty()){
			String name = pq.poll();
			//(put value)
			double val = mapping.get(name);
			if(val >= 0) b.append(' ');
			b.append(format.format(val));
			b.append("\t");
			//(put name)
			b.append(name);
			/*
			//(put value)
			for(int i=0; i<(maxlen-name.length()); i++){
				b.append(' ');
			}
			b.append('\t');
			double val = mapping.get(name);
			if(val >= 0) b.append(' ');
			b.append(format.format(val));
			*/
			b.append("\n");
		}

		return b.toString();
	}
	
	@Override
	public boolean dumpWeights(String filename){
		try {
			String toDump = dumpWeights();
			File f = new File(filename);
			if(!f.exists()){
				f.createNewFile();
			}
			FileWriter writer = new FileWriter(new File(filename));
			writer.write(toDump);
			writer.flush();
			writer.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}
	
	@Override
	public Counter<EncodeType> getWeights(){
		Counter<EncodeType> rtn = new Counter<EncodeType> ();
		for(int i=0; i<weights.length; i++){
			rtn.incrementCount(featureFact.getFeature(i), weights[i]);
		}
		return rtn;
	}
	
	@Override
	public Tree<MemoryReport> dumpMemoryUsage(int minUse) {
		int size = estimateMemoryUsage();
		if(size > minUse){
			Tree <MemoryReport> rtn = new Tree<MemoryReport>(new MemoryReport(size, "Maxent Classifier"));
			rtn.addChild(featureFact.dumpMemoryUsage(minUse));
			return rtn;
		}else{
			return new Tree<MemoryReport>();
		}
	}

	@Override
	public int estimateMemoryUsage() {
		int size = OBJ_OVERHEAD;
		size += weights.length * DBL_SIZE;	//weights
		size += OBJ_OVERHEAD + REF_SIZE; //featurefact
		size += featureFact.estimateMemoryUsage();
		return size;
	}

	
	
	
	private static String arrayToString(double[] array) {
		StringBuilder b = new StringBuilder();
		b.append("[ ");
		for (double d : array) {
			b.append(d);
			b.append(" ");
		}
		b.append("]");
		return b.toString();
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		System.out.println("MAXENT");
		String[] possibleOutputs = new String[]{"cat", "bear"};
		// create datums
		LabeledInstance<String[], String> datum1 = new LabeledInstance<String[], String>(
				new String[] { "fuzzy", "claws", "small" }, possibleOutputs, 0); //cat
		LabeledInstance<String[], String> datum2 = new LabeledInstance<String[], String>(
				new String[] { "fuzzy", "claws", "big"}, possibleOutputs, 1); //bear
		LabeledInstance<String[], String> datum3 = new LabeledInstance<String[], String>(
				new String[] { "claws", "medium"}, possibleOutputs, 0); //cat
		LabeledInstance<String[], String> datum4 = new LabeledInstance<String[], String>(
				new String[] { "claws", "small"}, possibleOutputs, 0); //cat

		// create training set
		List<LabeledInstance<String[], String>> trainingData = new ArrayList<LabeledInstance<String[], String>>();
		trainingData.add(datum1);
		trainingData.add(datum2);
		trainingData.add(datum3);

		// create test set
		List<LabeledInstance<String[], String>> testData = new ArrayList<LabeledInstance<String[], String>>();
		testData.add(datum4);

		// build classifier
		FeatureExtractor<String[], String, String> featureExtractor 
		= new FeatureExtractor<String[], String, String>() {
			@Override
			protected String concat(String a, String b) {
				return a + '#' + b;
			}
			@Override
			protected void fillFeatures(
					String[] input,
					Counter<String> inFeatures, 
					String output,
					Counter<String> outFeatures) {
				outFeatures.incrementCount("label-" + output, 1.0);
				for(String in : input){
					inFeatures.incrementCount(in, 1.0);
				}
			}
		};
		MaximumEntropyClassifier.Factory<String[], String, String> maximumEntropyClassifierFactory 
		= new MaximumEntropyClassifier.Factory<String[], String, String>(
				1.0, 20, featureExtractor);
		ProbabilisticClassifier<String[],String,String> maximumEntropyClassifier = maximumEntropyClassifierFactory
		.trainClassifier(trainingData);
		System.out.println("------------\nFINAL\n------------");
		maximumEntropyClassifier.dumpWeights("/home/gabor/weights.txt");
		System.out.println(maximumEntropyClassifier.dumpWeights());
		System.out
		.println("final weights: "
				+ arrayToString(((MaximumEntropyClassifier) maximumEntropyClassifier).weights));
		System.out.println("Probabilities on test instance: "
				+ maximumEntropyClassifier.getProbabilities(
						datum4.getInput(), 
						Arrays.asList(new String[]{"cat", "bear"})));
	}
}
