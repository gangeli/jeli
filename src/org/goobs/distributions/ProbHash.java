package org.goobs.distributions;

import java.util.HashMap;
import java.util.Random;

import org.goobs.functional.Function;
import org.goobs.utils.Pair;

public class ProbHash <Type> implements DiscreteDistribution <Type> {

	private HashMap <Type, Double> values = new HashMap <Type, Double> ();
	private double totalCount;
	
	public ProbHash(){
		
	}
	
	public void setCount(Type obj, double count){
		Double old = values.put(obj, count);
		if(old != null){
			totalCount += count - old;
		}else{
			totalCount += count;
		}
	}
	
	public double getCount(Type obj){
		Double count = values.get(obj);
		if(count == null){
			return 0.0;
		}else{
			return count;
		}
	}
	
	@Override
	public double getProb(Type object) {
		return getCount(object) / totalCount;
	}

	@Override
	public Type infer() {
		if(values.keySet().size() == 0){
			throw new IllegalStateException("Cannot infer from empty distribution");
		}
		double max = Double.NEGATIVE_INFINITY;
		Type argmax = null;
		for(Type cand : values.keySet()){
			if(values.get(cand) > max){
				argmax = cand;
				max = values.get(cand);
			}
		}
		if(argmax == null) throw new IllegalStateException("This should be impossible");
		return argmax;
	}

	@Override
	public Type sample() {
		Random rand = new Random();
		double target = rand.nextDouble();
		double rolling = 0.0;
		for(Type cand : values.keySet()){
			rolling += getProb(cand);
			if(rolling > target){
				return cand;
			}
		}
		throw new IllegalStateException("Probabilities do not sum to 1.0: " + rolling);
	}

	@Override
	public double expectation(Function<Type, Double> func) {
		double sum = 0.0;
		for(Type t : values.keySet()){
			double prob = values.get(t);
			sum += func.eval(t)*prob;
		}
		return sum;
	}

	@Override
	public void foreach(Function<Pair<Type, Double>, Object> func) {
		Pair <Type, Double> reusablePair = new Pair<Type, Double>(null,null);
		for(Type t : values.keySet()){
			reusablePair.setCar(t);
			reusablePair.setCdr(values.get(t));
			func.eval(reusablePair);
		}
	}

	@Override
	public void addCount(Type object, double count) {
		double lastCount = getCount(object);
		setCount(object, lastCount+count);
	}

	@Override
	public double totalCount() {
		return totalCount;
	}
	
	@Override
	public String toString(){
		StringBuilder b = new StringBuilder();
		for(Type key : values.keySet()){
			b.append(key).append(" : ").append(values.get(key)).append(", ");
		}
		return b.toString();
	}

}
