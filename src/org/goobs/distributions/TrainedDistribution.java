package org.goobs.distributions;

import org.goobs.foreign.Counter;
import org.goobs.utils.Pair;

public interface TrainedDistribution <Input, Encode, Output>{
	
	public static interface EmpiricalFactory <Input,Encode,Output>{	
		public TrainedDistribution <Input,Encode,Output> train(Pair<Input,Output>[] data, Counter<Encode> initWeights);
	}
	
	public static interface ConditionalFactory<Input,Encode,Output>{
		public TrainedDistribution <Input,Encode,Output> train(Input in, Counter<Encode> initWeights);
	}
	
	public void retrain(Counter<Encode> newWeights);
	
}
