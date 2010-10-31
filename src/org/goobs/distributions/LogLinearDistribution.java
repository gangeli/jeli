package org.goobs.distributions;

import org.goobs.classify.ArrayFeatureVector;
import org.goobs.classify.FeatureExtractor;
import org.goobs.classify.FeatureFactory;
import org.goobs.classify.FeatureVector;
import org.goobs.foreign.Counter;
import org.goobs.foreign.LBFGSMinimizer;
import org.goobs.functional.Function;
import org.goobs.minimize.DifferentiableFunction;
import org.goobs.minimize.GradientMinimizer;
import org.goobs.utils.Pair;
import org.goobs.utils.ProgressBar;

public class LogLinearDistribution<Input, EncodeType, Output> 
	implements TrainedDistribution<Input, EncodeType, Output>, ConditionalDistribution<Input,Output>{

	public static class Factory <I,F,O> implements TrainedDistribution.EmpiricalFactory <I,F,O>{

		private boolean lazy = false;
		private double unkProb = 0.0;
		private FeatureExtractor<I, F, O> featureExtractor;
		private TrainedDistribution.ConditionalFactory<I,F,O> probEstimateFact;
		private TrainedDistribution.ConditionalFactory<I,F,O> probEstimateFactTest;
		private int maxIterations = -1;
		
		@SuppressWarnings("unchecked")
		@Override
		public TrainedDistribution<I,F,O> train(Pair<I, O>[] data, Counter<F> initWeights) {
			
			//(extract features)
			FeatureVector<F>[] paraData = new FeatureVector[data.length];
			FeatureFactory<F> featureFact = new ArrayFeatureVector.ArrayFeatureFactory<F>();
			for(int i=0; i<data.length; i++){
				Pair<I,O> datum = data[i];
				paraData[i] = featureFact.newTrainFeature(featureExtractor
						.extractFeatures(datum.car(), datum.cdr()));
			}
			//(get dimension)
			int dimension = featureFact.numFeatures();
			//(build weights)
			double[] initialWeights = new double[dimension];
			for(int i=0; i<initialWeights.length; i++){
				initialWeights[i] = initWeights.getCount(featureFact.getFeature(i));
			}
			//(build probability estimate distributions)
			TrainedDistribution<I,F,O>[] probEstimate = new TrainedDistribution[data.length];
			for(int i=0; i<data.length; i++){
				probEstimate[i] = probEstimateFact.train( data[i].car(), new Counter<F>() );
			}
			//(objective function)
			DifferentiableFunction objective = new ObjectiveFunction<I,F,O>(
					data, 
					paraData,
					featureExtractor, 
					featureFact,
					probEstimate,
					dimension, 
					unkProb,
					lazy
					);
					
			//--Minimize
			GradientMinimizer minimizer = new LBFGSMinimizer(maxIterations);
			System.out.println("MINIMIZING");
			double[] weights = minimizer.minimize(objective, initialWeights, 1e-4);
			
			//--Return
			return new LogLinearDistribution<I, F, O>(featureFact.weightCounter(weights), probEstimateFactTest);
		}		
		
		public Factory(
				FeatureExtractor<I, F, O> featureExtractor,
				TrainedDistribution.ConditionalFactory<I,F,O> probEstimateFactTrain,
				TrainedDistribution.ConditionalFactory<I,F,O> probEstimateFactTest,
				int maxIterations,
				boolean lazy,
				double unkProb
				){
			this.maxIterations = maxIterations;
			this.probEstimateFact = probEstimateFactTrain;
			this.probEstimateFactTest = probEstimateFactTest;
			this.lazy = lazy;
			this.unkProb = unkProb;
			this.featureExtractor = featureExtractor;
		}
		
		
	}
	
	public static class ObjectiveFunction <I,F,O> implements DifferentiableFunction {
		
		private Pair<I,O>[] data;
		private int dimension;
		private double unkProb;
		private FeatureExtractor<I,F,O> featureExtractor;
		private FeatureFactory<F> featureFact;
		
		private FeatureVector<F>[] paraData;
		
		private double[] lastX;
		private double[] lastDerivative;
		private double lastValue;
		private TrainedDistribution<I,F,O>[] normalization;


		@SuppressWarnings("unchecked")
		public ObjectiveFunction(	
				Pair<I,O>[] data, 
				FeatureVector<F>[] paraData,
				FeatureExtractor<I,F,O> featureExtractor, 
				FeatureFactory<F> featureFact,
				TrainedDistribution<I,F,O>[] probEstimate,
				int dimension, 
				double unkProb,
				boolean lazy
				) {
			//(error checking)
			try{
				for(int i=0; i<probEstimate.length; i++){
					@SuppressWarnings("unused")
					DiscreteDistribution<O> test = (DiscreteDistribution<O>) probEstimate[i];
				}
			}catch(ClassCastException e){
				throw new IllegalArgumentException("Probability estimate distribution does not implement DiscreteDistribution");
			}
			//(set variables)
			this.data = data;
			this.paraData = paraData;
			this.featureFact = featureFact;
			this.featureExtractor = featureExtractor;
			normalization = probEstimate;
			this.dimension = dimension;
			this.unkProb = unkProb;
			//(initialize features)
			paraData = new FeatureVector[data.length];
			if(!lazy){
				for(int i=0; i<data.length; i++){
					paraData[i] = featurize(data[i].car(), data[i].cdr());
				}
			}
		}

		@Override
		public double[] derivativeAt(double[] x) {
			ensureCache(x);
			return lastDerivative;
		}

		@Override
		public int dimension() {
			return dimension;
		}

		@Override
		public double valueAt(double[] x) {
			ensureCache(x);
			return lastValue;
		}
		
		private FeatureVector<F> featurize(I input, O output){
			return featureFact.newTrainFeature(featureExtractor.extractFeatures(input, output));
		}
		
		private void ensureCache(double[] x) {
			if (requiresUpdate(lastX, x)) {
				Pair<Double, double[]> currentValueAndDerivative = calculate(x);
				lastValue = currentValueAndDerivative.car();		//new objective value
				lastDerivative = currentValueAndDerivative.cdr();	//new derivative of value
				lastX = x;											//new weights
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
		
		@SuppressWarnings("unchecked")
		private double smoothedProb(int dataIndex, O output){
			double cand = ((DiscreteDistribution<O>) normalization[dataIndex]).getProb(output);
			if(cand == 0.0)
				return unkProb;
			else
				return (1.0-unkProb)*cand;
		}
		
		@SuppressWarnings("unchecked")
		private Pair<Double, double[]> calculate(double[] weights){
			double objective = 0.0;
			final double[] derivatives = new double[dimension()];
			System.out.println("Calculate");
			ProgressBar progress = new ProgressBar(data.length, 50);

			//--Calculate Objective Function
			System.out.print("\tObjective");
			Counter<F> featureCounts = featureFact.weightCounter(weights);
			for(int i=0; i<data.length; i++){
				progress.tick();
				normalization[i].retrain(featureCounts);
				Pair<I,O> datum = data[i];
				O output = datum.cdr();
				double prob = smoothedProb(i,output);
				if(prob > 0.0){
					objective += Math.log(prob);
				} else {
					//TODO what do we do with a 0 prob event with no smoothing?
					//for now, do nothing (ignore it)
				}
			}
			objective = -objective;
			
			//--Calculate Derivatives
			System.out.print("\tDerivatives");
			for(int i=0; i<data.length; i++){
				FeatureVector<F> datum = paraData[i];
				if(datum == null){	//case: we're lazily extracting features
					datum = featurize(data[i].car(), data[i].cdr());
				}
				
				//(actual term)
				for(int f=0; f<datum.numFeatures(); f++){
					int index = datum.globalIndex(f);
					double count = datum.getCount(f);
					derivatives[index] += count;
				}
				
				//(expectation term)
				TrainedDistribution <I,F,O> dist = normalization[i];
				final I input = data[i].car();
				
				Function <Pair<O, Double>,Object> featureFunc = new Function<Pair<O, Double>,Object>(){
					private static final long serialVersionUID = -6291824380020033516L;

					@Override
					public Object eval(Pair<O,Double> pair) {
						//(get variables)
						O output = pair.car();
						double prob = pair.cdr();
						FeatureVector <F> feats = featurize(input, output); 
						//(update derivative)
						for(int f=0; f<feats.numFeatures(); f++){
							int index = feats.globalIndex(f);
							double count = feats.getCount(f);
							derivatives[index] += prob*count;	//DERIVATIVE UPDATE
						}
						//return (doesn't matter what)
						return null;
					}
				};
				
				((DiscreteDistribution<O>) dist).foreach(featureFunc);
			}
			
			return new Pair<Double, double[]>(objective, derivatives);
		}
		
	}
	
	
	private Counter <EncodeType> weights;
	private TrainedDistribution.ConditionalFactory<Input, EncodeType, Output> distFact;
	
	
	public LogLinearDistribution(
			Counter <EncodeType> weights,
			TrainedDistribution.ConditionalFactory<Input, EncodeType, Output> distFact
			) {
		this.weights = weights;
		this.distFact = distFact;
	}


	@Override
	public void retrain(Counter<EncodeType> newWeights) {
		weights = newWeights;
	}


	@SuppressWarnings("unchecked")
	@Override
	public double getProb(Input condition, Output output) {
		TrainedDistribution<Input,EncodeType,Output> dist = distFact.train(condition, weights);
		return ((Distribution<Output>) dist).getProb(output);
	}


	@SuppressWarnings("unchecked")
	@Override
	public Output infer(Input condition) {
		TrainedDistribution<Input,EncodeType,Output> dist = distFact.train(condition, weights);
		return ((Distribution<Output>) dist).infer();
	}


	@SuppressWarnings("unchecked")
	@Override
	public Output sample(Input condition) {
		TrainedDistribution<Input,EncodeType,Output> dist = distFact.train(condition, weights);
		return ((Distribution<Output>) dist).sample();
	}


	@Override
	public String dumpWeights() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

}
