package org.goobs.distributions;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.PriorityQueue;

import org.goobs.foreign.Counter;
import org.goobs.utils.Pair;

public class TrainedGradualDistribution <Input, EncodeType, Output> 
	implements TrainedDistribution<Input, EncodeType, Output>, ConditionalDistribution<Input,Output>{

	public static class Factory <I,F,O> implements TrainedDistribution.EmpiricalFactory <I,F,O>{
		
		private int maxIterations;
		private int maxDepth;
		private Particle.PartialFeatureExtractor<I, F, O> featureExtractor;
		private GradualTrainedDistribution.ConditionalFactory<I,F,O> probEstimateFact;
		private TrainedDistribution.ConditionalFactory<I,F,O> probEstimateFactTest;
		
		private final void update(Counter <F> weights, Counter <F> diff, double positive, double updateCount){
			double factor = 1 / Math.sqrt(updateCount);
//			factor = 1.0;	//uncomment to keep constant factor updates
			for(F key : diff.keySet()){
				weights.incrementCount(key, factor * positive * diff.getCount(key));
			}
		}

		@Override
		public TrainedDistribution<I, F, O> train(Pair<I, O>[] data,
				Counter<F> initWeights) {
			Counter <F> weights = new Counter<F>();

			double updateCount = 1.0;
			for(int depth=0; depth <= maxDepth; depth++){
				System.out.println("DEPTH = " + depth);
				updateCount = 1.0;
				if(depth == maxDepth){
//					break;
					depth = Particle.INFINITE_DEPTH;
				}else if(depth == Particle.INFINITE_DEPTH+1){
					break;
				}
				for(int i=0; i<maxIterations; i++){
					System.out.println("Iteration " + i + " started");
					for(Pair<I,O> datum : data){
						//--Overhead
						//(get datum)
						I input = datum.car();
						O output = datum.cdr();
						Counter<F> gold = featureExtractor.extractFeatures(input, output, depth);
						for(int k=0; k<1; k++){
							//(get conditional distribution)
							GradualTrainedDistribution<I,F,O> dist = probEstimateFact.train(input);
							//--Train distribution
							Counter<F> exp = dist.gradualTrain(weights, depth);
							System.out.println("GOLD  " + gold);
							System.out.println("EXP    " + exp);
							System.out.println("--");
							update(weights, gold, 1.0, updateCount);
							update(weights, exp, -1.0, updateCount);
						}
						System.out.println("\n------------\n");
					}
					updateCount += 1.0;
				}
			}

			return new TrainedGradualDistribution<I, F, O>(weights, probEstimateFactTest);

		}
		
		public Factory(
				Particle.PartialFeatureExtractor<I, F, O> featureExtractor,
				GradualTrainedDistribution.ConditionalFactory<I,F,O> probEstimateFactTrain,
				TrainedDistribution.ConditionalFactory<I,F,O> probEstimateFactTest,
				int maxIterations,
				int maxDepth
				){
			this.featureExtractor = featureExtractor;
			this.maxDepth = maxDepth;
			this.maxIterations = maxIterations;
			this.probEstimateFact = probEstimateFactTrain;
			this.probEstimateFactTest = probEstimateFactTest;
		}
		
	}
	
	private Counter <EncodeType> weights;
	private TrainedDistribution.ConditionalFactory<Input, EncodeType, Output> distFact;
	
	public TrainedGradualDistribution(
			Counter <EncodeType> weights,
			TrainedDistribution.ConditionalFactory<Input, EncodeType, Output> distFact
			) {
		this.weights = weights;
		this.distFact = distFact;
		retrain(weights);
	}


	@Override
	public void retrain(Counter<EncodeType> newWeights) {
		weights = newWeights;
	}


	@SuppressWarnings("unchecked")
	@Override
	public double getProb(Input condition, Output output) {
		TrainedDistribution<Input,EncodeType,Output> dist = distFact.train(condition, weights);
		dist.retrain(weights);
		return ((Distribution<Output>) dist).getProb(output);
	}


	@SuppressWarnings("unchecked")
	@Override
	public Output infer(Input condition) {
		TrainedDistribution<Input,EncodeType,Output> dist = distFact.train(condition, weights);
		dist.retrain(weights);
		return ((Distribution<Output>) dist).infer();
	}


	@SuppressWarnings("unchecked")
	@Override
	public Output sample(Input condition) {
		TrainedDistribution<Input,EncodeType,Output> dist = distFact.train(condition, weights);
		dist.retrain(weights);
		return ((Distribution<Output>) dist).sample();
	}


	@Override
	public String dumpWeights() {
		
		PriorityQueue <String> pq = new PriorityQueue <String> ();
		HashMap <String, Double> mapping = new HashMap<String, Double> ();
		DecimalFormat format = new DecimalFormat("0.000");
		int maxlen = 0;
		for(EncodeType name : weights.keySet()){
			String str = name.toString();
			pq.add(str);
			maxlen = Math.max(maxlen, str.length());
			mapping.put(str, weights.getCount(name));
		}
		
		StringBuilder b = new StringBuilder();
		while(!pq.isEmpty()){
			String name = pq.poll();
			b.append(name);
			for(int i=0; i<(maxlen-name.length()+3); i++){
				b.append(' ');
			}
			double val = mapping.get(name);
			if(val >= 0) b.append(' ');
			b.append(format.format(val));
			b.append("\n");
		}

		return b.toString();
	}

}
