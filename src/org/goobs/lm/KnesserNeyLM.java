package org.goobs.lm;

import java.util.HashMap;

import org.goobs.distributions.DiscreteDistribution;
import org.goobs.exec.Log;

public class KnesserNeyLM extends LanguageModel{

	private HashMap<Tuple, Double> alphaCache = new HashMap<Tuple,Double>();
	
	public KnesserNeyLM(int depth) {
		super(depth);
	}
	
	private final double delta(double count){
		if(count < 1) return 0.0;
		else if(count < 2) return 0.5;
		else return 0.75;
	}
	
	private final double alpha(DiscreteDistribution<Integer> probs){
		if(probs.totalCount() == 0.0){
			//(case: completely new context: all weight on lower order model)
			return 1.0;
		} else {
			//(case: seen context before: calculate alpha)
			double sumDelta = 0.0;
			for(int word=0; word<N(); word++){
				sumDelta += delta(probs.getCount(word));
			}
			return sumDelta / probs.totalCount();
		}
	}
	
	@Override
	protected double getCondProb(int word, Tuple tail){
		if(tail == null){
			//--Base Case (knesser-ney)
			return uniqueContexts.getProb(word);
		} else {
			//--Recursive Case (absolute discount)
			//(get the distribution)
			DiscreteDistribution<Integer> probs = getDistribution(tail);
			
			//(get Prob if exists)
			double discounted = 0.0;
			if(probs.totalCount() > 0 && probs.getCount(word) > 0){
				//(case: seen this before)
				double c = probs.getCount(word);
				double d = delta(c);
				discounted = (c-d) / probs.totalCount();
			}else{
				//(case: never seen this before)
				discounted = 0.0;
			}
			
			//(get alpha)
			double a = -1;
			if(alphaCache.containsKey(tail)){
				a = alphaCache.get(tail);
			}else{
				a = alpha(probs);
				alphaCache.put(tail, a);
			}
			
			//(drop to lower order case)
			double rtn = discounted + a * getCondProb(word, tail.cdr());
			if(rtn == 0) throw Log.internal("0 Probability for sequence: " + tail.prettyString() + " -> " + wstr(word));
			if(rtn < 0) throw Log.internal("negative probability for sequence: " + tail.prettyString() + " -> " + wstr(word));
			return rtn;
		}
	}
}
