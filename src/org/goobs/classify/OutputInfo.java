package org.goobs.classify;

import java.text.DecimalFormat;
import java.util.PriorityQueue;
import java.util.LinkedList;

import org.goobs.foreign.Counter;

public class OutputInfo<Encode>{
	
	private static final DecimalFormat df = new DecimalFormat("0.000");

	private static class Term<Encode> implements Comparable<Term<Encode>>{
		private Encode term;
		private double count, weight;
		public Term(Encode term, double count, double weight){
			this.term = term;
			this.count = count;
			this.weight = weight;
		}
		private double magnitude(){
			return Math.abs(weight * count);
		}
		@Override
		public String toString(){
			StringBuilder b = new StringBuilder();
			b.append(df.format(weight))
				.append("  count=").append(df.format(count))
				.append("   ").append(term.toString()).append("\n");
			return b.toString();
		}
		@Override
		public int compareTo(Term<Encode> other){
			if(other.magnitude() < this.magnitude()){
				return -1;
			} else if(other.magnitude() > this.magnitude()){
				return 1;
			} else {
				return 0;
			}
		}
	}

	public double probability;
	public Counter <Encode> features = new Counter<Encode>();
	public Counter <Encode> featureWeights = new Counter<Encode>();

	public OutputInfo(){
		this(0);
	}
	public OutputInfo(double prob){
		this.probability = prob;
	}

	public void registerFeature(Encode feature, double count, double weight){
		features.incrementCount(feature, count);
		featureWeights.incrementCount(feature, weight);
	}

	public void setProbability(double prob){
		this.probability = prob;
	}

	public String dump(int numFeatures){
		StringBuilder b = new StringBuilder();
//		DecimalFormat df = new DecimalFormat("0.000");
		b.append("Probability: ").append(probability).append("\n");
		b.append("Features Fired:").append("\n");
		//(fill a priority queue)
		PriorityQueue<Term<Encode>> guessQueue = new PriorityQueue<Term<Encode>>();
		for(Encode key : features.keySet()){
			double count = features.getCount(key);
			double weight = featureWeights.getCount(key);
			double magnitude = Math.abs(count*weight);
			if(guessQueue.size() < numFeatures){
				guessQueue.offer(new Term<Encode>(key,count,weight));
			}else{
				Term<Encode> test = guessQueue.peek();
				if(test.magnitude() < magnitude){
					guessQueue.poll();
					guessQueue.offer(new Term<Encode>(key,count,weight));
				}
			}
		}
		//(reverse queue)
		LinkedList <Term<Encode>> lst = new LinkedList <Term<Encode>>();
		while(!guessQueue.isEmpty()){
			lst.addLast(guessQueue.poll());
		}
		//(add string)
		for( Term <Encode> key : lst ){
			b.append("\t").append(key.toString());
		}
		return b.toString();
	}
	
	@Override
	public String toString(){ return dump(10); }
}
