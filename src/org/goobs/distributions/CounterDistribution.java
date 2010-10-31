package org.goobs.distributions;

import java.util.Random;

import org.goobs.foreign.Counter;

/*
 * TODO replace my functionality with ProbHash
 */

public class CounterDistribution <Type> implements Distribution<Type>{
	private Counter <Type> counts;
	
	public CounterDistribution(Counter <Type> counts){
		this.counts = counts;
	}
	
	@Override
	public double getProb(Type object) {
		return counts.getProb(object);
	}

	@Override
	public Type infer() {
		return counts.argMax();
	}

	@Override
	public Type sample() {
		Random rand = new Random();
		double target = rand.nextDouble();
		double rolling = 0.0;
		Type argmax = null;
		for(Type cand : counts.keySet()){
			argmax = cand;
			if(rolling > target){
				break;
			}
		}
		return argmax;
	}

	@Override
	public void addCount(Type object, double count) {
		counts.incrementCount(object, count);
	}

}
