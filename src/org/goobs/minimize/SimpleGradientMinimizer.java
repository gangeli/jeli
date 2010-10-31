package org.goobs.minimize;

import org.goobs.utils.Pair;

public class SimpleGradientMinimizer implements GradientMinimizer{

	public static interface StepSizeGenerator{
		public double stepSize(int iterationNum);
	}
	
	
	private int maxIterations;
	private StepSizeGenerator stepSize;
	
	/*
	 * CONSTRUCTORS
	 */
	public SimpleGradientMinimizer(){
		this(Integer.MAX_VALUE);
	}
	
	public SimpleGradientMinimizer(int maxIterations){
		this(maxIterations, new StepSizeGenerator(){
			@Override
			public double stepSize(int iterationNum) {
				return 1.0 / Math.sqrt((double) iterationNum);
			}
		});
	}
	
	public SimpleGradientMinimizer(int maxIterations, StepSizeGenerator stepSize){
		this.maxIterations = maxIterations;
		this.stepSize = stepSize;
	}
	
	
	/*
	 * MINIMIZE
	 */
	private Pair<double[], Double> iterate(
				int iterNum, 
				DifferentiableFunction objective, 
				double[] initialWeights){
		//(function information)
		double[] derivative = objective.derivativeAt(initialWeights);
		double val = objective.valueAt(initialWeights);
		System.out.println("Iteration " + iterNum + " begun with value " + val);
		
		//(increment the weights)
		double step = stepSize.stepSize(iterNum);
		double[] newWeights = new double[initialWeights.length];
		double sumDif = 0.0;
		
		for(int i=0; i<newWeights.length; i++){
			sumDif += Math.abs(derivative[i] * step);
			newWeights[i] = initialWeights[i] + derivative[i]*step;
		}
		
		//(return)
		return new Pair<double[], Double> (newWeights, sumDif);
	}
	
	@Override
	public double[] minimize(DifferentiableFunction objective,
			double[] initialWeights, double tolerance) {
		
		double[] rollingWeights = initialWeights;
		
		for(int i=0; i<maxIterations; i++){
			Pair<double[], Double> result = iterate(i, objective, rollingWeights);
			rollingWeights = result.car();
			double change = result.cdr();
			if(change < tolerance){
				break;
			}
		}
		
		return rollingWeights;
	}

}
