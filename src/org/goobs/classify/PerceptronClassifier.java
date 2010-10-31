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
import org.goobs.utils.Pair;
import org.goobs.utils.Tree;


public class PerceptronClassifier<Input,EncodeType,Output> 
	implements ProbabilisticClassifier<Input,EncodeType,Output>{
	
	private static boolean AVERAGED = false;
	private static boolean ONLINE = true;
	private static boolean LAZY_FEATURES = true;

	public static class Factory<I, F, L> extends ProbabilisticClassifierFactory<I,F,L> {
		private int iterations;
		private FeatureExtractor<I,F,L> featureExtractor;
		
		public Factory(int iterations,FeatureExtractor<I, F, L> featureExtractor) {
			this.iterations = iterations;
			this.featureExtractor = featureExtractor;
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public ProbabilisticClassifier<I,F,L> trainClassifier(
				List<LabeledInstance<I, L>> trainingData) {			
			//(get the feature factory)
			FeatureFactory <F> featureFact = new ArrayFeatureVector.ArrayFeatureFactory<F>();
			//(create the data)
			ClassificationDatum<F>[] data = new ClassificationDatum[trainingData.size()];
			int i=0;
			for(LabeledInstance <I,L> instance : trainingData){
				data[i] = labeledInstanceToDatum(instance, featureExtractor, featureFact, LAZY_FEATURES);
				i+=1;
			}
			//(initial pass of the data)
			dataPass(data);
			//(initial weights)
			double[] initWeights = buildInitialWeights(featureFact.numFeatures());
			//(train)
			double[] finalWeights = train(initWeights, data);
			//(return)
			return new PerceptronClassifier(finalWeights, featureExtractor, featureFact);
		}
		
		@Override
		public ProbabilisticClassifier<I,F,L> trainClassifier(
				List<LabeledInstance<I, L>> trainingData,
				FeatureExtractor<I,F,L> featureExtractor){
			this.featureExtractor = featureExtractor;
			return trainClassifier(trainingData);
		}
		
		private void dataPass(ClassificationDatum<F>[] data){
			for( ClassificationDatum <F> d : data ){
				for(int o=0; o<d.numPossibleOutputs(); o++){
					 d.possibleOutput(o);
				}
			}
		}
		
		/*
		 * TRAINING
		 */
		private double[] train(double[] initWeights, ClassificationDatum<F>[] data){
			//(overhead)
			DecimalFormat df = new DecimalFormat("##.##%");
			double length = (double) data.length;
			//(current weight vector)
			FlatParameterVector<Integer> weights = new FlatParameterVector<Integer>();
			for(int i=0; i<initWeights.length; i++){
				weights.setWeight(i, initWeights[i]);
			}
			//(new weight vector - for batch learning)
			FlatParameterVector<Integer> newWeights = weights.clone();
			
			//--Train
			for(int iter=0; iter<iterations; iter++){
				//(overhead)
				FlatParameterVector<Integer> toUpdate = ONLINE ? weights : newWeights;
				double misses = 0.0;
				//(loop)
				for(ClassificationDatum<F> datum : data){
					//--For each datum...
					//(classify)
					double[] probs = getScores(datum, weights);
					int argmax = 0; double max=Double.NEGATIVE_INFINITY;
					for(int cand=0; cand<probs.length; cand++){
						if(probs[cand] >= max){
							max = probs[cand];
							argmax = cand;
						}
					}
					//--Update if applicable
					FeatureVector <F> features;
					if(argmax != datum.outputIndex()){
						misses += 1.0;
						toUpdate.beginUpdate();
						//(raise correct answer)
						features = datum.possibleOutput(datum.outputIndex());
						for(int f=0; f<features.numFeatures(); f++){
							Integer globalIndex = features.globalIndex(f);
							toUpdate.incrWeight(globalIndex, features.getCount(f));
						}
						//(lower the incorrect answer)
						features = datum.possibleOutput(argmax);
						for(int f=0; f<features.numFeatures(); f++){
							Integer globalIndex = features.globalIndex(f);
							toUpdate.incrWeight(globalIndex, -features.getCount(f));
						}
						toUpdate.endUpdate();
					}
				}
				System.out.println("[PerceptronClassifier.train] Iteration " 
						+ iter + " ended with miss rate: " 
						+ misses + "(" + df.format(misses/length) + ")");
				//(overhead)
				if(!ONLINE){
					weights = newWeights.clone();
				}
			}
			
			//--Return Weight Vector
			double[] finalWeights = new double[initWeights.length];
			for(Integer feature : weights.getFeatures()){
				if(feature < 0 || feature >= finalWeights.length){
					throw new IllegalStateException("Feature out of range: " + feature);
				}
				finalWeights[feature] = weights.getWeight(feature, AVERAGED);
			}
			return finalWeights;
		}
		
		
		
		
	}

	private double[] weights;
	private FeatureExtractor<Input, EncodeType, Output> featureExtractor;
	private FeatureFactory<EncodeType> featureFact;
	
	private PerceptronClassifier(
			double[] weights,
			FeatureExtractor<Input, EncodeType, Output> featureExtractor,
			FeatureFactory<EncodeType> featureFact) {
		this.weights = weights;
		this.featureExtractor = featureExtractor;
		this.featureFact = featureFact;
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public Counter<Output> getProbabilities(Input input,
			Collection<Output> possibleOutputs) {
		//--Extract features
		FeatureVector <EncodeType>[] outputVect = new FeatureVector[possibleOutputs.size()];
		Output[] keyVect = (Output[]) new Object[possibleOutputs.size()];
		int i=0;
		for(Output out : possibleOutputs){
			outputVect[i] = featureFact.newTestFeature(featureExtractor.extractFeatures(input, out));
			keyVect[i] = out;
			i+=1;
		}
		
		//--Get Scores
		Counter <Output> counts = new Counter <Output> ();
		double[] scores = getScores(new ClassificationDatum<EncodeType>(outputVect,0), weights); //don't care about index ('0')
		for(int k=0; k<outputVect.length; k++){
			double prob = Math.exp(scores[k]);
			counts.setCount(keyVect[k], prob);
		}
		
		return counts;
	}
	
	@Override
	public Pair<OutputInfo<EncodeType>,OutputInfo<EncodeType>> getInfo(	Input input, 
																		Output guessOut, 
																		Output goldOut, 
																		Collection <Output> possibleOutputs){
		throw new IllegalArgumentException("Method not implemented for perceptron classifier yet");
	}

	@Override
	public double getProbability(Input input, Output output,
			Collection<Output> possibleOutputs) {
		return getProbabilities(input, possibleOutputs).getCount(output);
	}
	
	@Override
	public Output getOutput(Input input, Collection<Output> outputs){		
		return getProbabilities(input, outputs).argMax();
	}
	
	@Override
	public String dumpWeights() {
		PriorityQueue <String> pq = new PriorityQueue <String> ();
		HashMap <String, Double> mapping = new HashMap<String, Double> ();
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
			b.append(name);
			for(int i=0; i<(maxlen-name.length()); i++){
				b.append(' ');
			}
			b.append('\t');
			b.append(mapping.get(name));
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
	
	private static final <F> double[] getScores(ClassificationDatum<F> datum, double[] weights){
		double[] probs = new double[datum.numPossibleOutputs()];
		for(int output=0; output<probs.length; output++){
			//--Dot product of features with weights
			FeatureVector<F> features = datum.possibleOutput(output);
			double dot = 0;
			for(int f=0; f<features.numFeatures(); f++){
				int globalIndex = features.globalIndex(f);
				if(globalIndex != FeatureVector.UNK_GLOBAL_INDEX){
					dot += features.getCount(f) * weights[globalIndex];
				}else{
					dot += 0.0; //0 weight for unk features
				}
			}
			probs[output] = dot;
		}
		return probs;
	}
	
	private static final <F> double[] getScores(ClassificationDatum<F> datum, FlatParameterVector<Integer> weights){
		double[] probs = new double[datum.numPossibleOutputs()];
		for(int output=0; output<probs.length; output++){
			//--Dot product of features with weights
			FeatureVector<F> features = datum.possibleOutput(output);
			double dot = 0;
			for(int f=0; f<features.numFeatures(); f++){
				int globalIndex = features.globalIndex(f);
				if(globalIndex != FeatureVector.UNK_GLOBAL_INDEX){
					dot += features.getCount(f) * weights.getWeight(globalIndex, AVERAGED);
				}else{
					dot += 0.0;	//0 weight for unk features
				}
			}
			probs[output] = dot;
		}
		return probs;
	}
	
	@Override
	public Tree<MemoryReport> dumpMemoryUsage(int minUse) {
		int size = estimateMemoryUsage();
		if(size > minUse){
			Tree <MemoryReport> rtn = new Tree<MemoryReport>(new MemoryReport(size, "Perceptron Classifier"));
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
		System.out.println("PERCEPTRON");
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
				return a + "#" + b;
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
		
		PerceptronClassifier.Factory<String[],String,String> fact
			= new PerceptronClassifier.Factory<String[], String, String>(3, featureExtractor);

		ProbabilisticClassifier<String[],String,String> perceptronClassifier 
			= fact.trainClassifier(trainingData);
		System.out.println("------------\nFINAL\n------------");
		System.out.println(perceptronClassifier.dumpWeights());
		System.out
			.println("final weights: "
				+ arrayToString(((PerceptronClassifier) perceptronClassifier).weights));
		System.out.println("Probabilities on test instance: "
				+ perceptronClassifier.getProbabilities(
						datum4.getInput(), 
						Arrays.asList(possibleOutputs)));
	}

	
}
