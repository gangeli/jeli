package org.goobs.distributions;

import org.goobs.foreign.Counter;

public interface GradualTrainedDistribution<Input,Encode,Output> {
	
	public static interface ConditionalFactory<Input,Encode,Output>{
		public GradualTrainedDistribution <Input,Encode,Output> train(Input in);
	}
	
	public Counter<Encode> gradualTrain(Counter<Encode> weightPointer, int depth);
}
