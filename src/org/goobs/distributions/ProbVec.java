package org.goobs.distributions;

import java.util.Random;

import org.goobs.functional.Function;
import org.goobs.utils.Pair;
import org.goobs.utils.Utils;

public class ProbVec implements DiscreteDistribution<Integer>{

	private double[] data;
	private double totalCount = 0.0;
	
	public ProbVec(int size){
		this.data = new double[size];
	}
	
	@Override
	public double expectation(Function<Integer, Double> func) {
		double exp = 0.0;
		for(int i=0; i<data.length; i++){
			exp += func.eval(i)*data[i];
		}
		return exp;
	}

	@Override
	public void foreach(Function<Pair<Integer, Double>, Object> func) {
		for(int i=0; i<data.length; i++){
			func.eval(Pair.make(i, data[i]));
		}
	}

	@Override
	public double getProb(Integer object) {
		return data[object] / totalCount;
	}

	@Override
	public Integer infer() {
		return Utils.argmax(data);
	}

	@Override
	public Integer sample() {
		double target = new Random().nextDouble();
		double rolling = 0.0;
		int i=0;
		while(rolling < target){
			rolling += data[i] / totalCount;
			i += 1;
		}
		if(i >= data.length) throw new IllegalStateException("Sampled an index greater than the vector length");
		return i;
	}

	@Override
	public void addCount(Integer object, double count) {
		if(count < 0) throw new IllegalStateException();
		double lastCount = data[object];
		totalCount += count;
		data[object] = lastCount+count;
	}

	@Override
	public double getCount(Integer term) {
		return data[term];
	}

	@Override
	public double totalCount() {
		return totalCount;
	}
	
	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		for(int key=0; key<data.length; key++){
			b.append(key).append(" : ").append(data[key]).append(", ");
		}
		return b.toString();
	}

}
