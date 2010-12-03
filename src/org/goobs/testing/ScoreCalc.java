package org.goobs.testing;

import static org.goobs.exec.Log.*;

import org.goobs.utils.StatCalc;

public class ScoreCalc <T> {
	private static enum State{
		NONE,
		DISCRETE,
		CONTINUOUS,
	}
	
	private State state = State.NONE;
	
	private int exCount;
	
	/*
	 * DISCRETE
	 */
	// [[precision/recall]
	private int guessCorrect;
	private int guessTotal;
	private int goldCorrect;
	private int goldTotal;
	
	/*
	 * CONTINUOUS
	 */
	private StatCalc guessStats = new StatCalc();
	private StatCalc goldStats = new StatCalc();
	// [[pearson correlation]]
	private double pearsonSumSqGuess = 0.0;
	private double pearsonSumSqGold = 0.0;
	private double pearsonMeanGuess = Double.NaN; 
	private double pearsonMeanGold = Double.NaN;
	private double pearsonSumCoproduct = 0.0;
	
	public ScoreCalc(){
		
	}
	
	public void enterDiscrete(T guess, T gold){
		//(state check)
		if(state == State.NONE){ state = State.DISCRETE; }
		if(state != State.DISCRETE){
			throw new IllegalStateException("Mixing DISCRETE and " + state.toString() + " data");
		}
		//(score)
		if(guess == gold){
			guessCorrect += 1;
			goldCorrect += 1;
		}
		guessTotal += 1;
		goldTotal += 1;
		//(count)
		exCount += 1;
	}
	
	public void enterContinuous(double guess, double gold){
		//(state check)
		if(state == State.NONE){ state = State.CONTINUOUS; }
		if(state != State.CONTINUOUS){
			throw new IllegalStateException("Mixing CONTINUOUS and " + state.toString() + " data");
		}
		//--General
		guessStats.enter(guess);
		goldStats.enter(gold);
		//--Pearson
		if(exCount == 0){
			pearsonMeanGuess = guess;
			pearsonMeanGold = gold;
		}else{
			double exPlus1 = (double) (exCount + 1);
			double w = ( ((double) exCount)) * 1.0 / exPlus1;
			double deltaGuess = guess - pearsonMeanGuess;
			double deltaGold = gold - pearsonMeanGold;
			pearsonSumSqGuess += deltaGuess*deltaGuess*w;
			pearsonSumSqGold += deltaGold*deltaGold*w;
			pearsonSumCoproduct += deltaGuess*deltaGold*w;
			pearsonMeanGuess += deltaGuess / exPlus1;
			pearsonMeanGold += deltaGold / exPlus1;
		}
		//--Count
		exCount += 1;
	}
	
	public double pearson(){
		double exDbl = (double) exCount;
		double popSdGuess = Math.sqrt(pearsonSumSqGuess / exDbl);
		double popSdGold = Math.sqrt(pearsonSumSqGold / exDbl);
		double covar = pearsonSumCoproduct / exDbl;
		double denom = popSdGuess * popSdGold;
		if(denom == 0.0){
			return 0.0;
		}else{
			return covar / denom;
		}
	}
	
	public void enterUnordered(T[] guess, T[] gold){
		//TODO
		throw new IllegalStateException("Not implemented!");
	}
	public void enterOrdered(T[] guess, T[] gold){
		//TODO
		throw new IllegalStateException("Not implemented!");
	}
	
	
	public double precision(){
		if(guessTotal == 0){ throw new IllegalStateException("No data points for precision!"); }
		if(state != State.DISCRETE){ throw new IllegalStateException("Precision only defined for discrete data"); }
		return ((double) guessCorrect) / ((double) guessTotal);
	}
	public double recall(){
		if(goldTotal == 0){ throw new IllegalStateException("No data points for recall!"); }
		if(state != State.DISCRETE){ throw new IllegalStateException("Precision only defined for discrete data"); }
		return ((double) goldCorrect) / ((double) goldTotal);
	}
	public double FMeasure(double B){
		return (1.0+B) * (precision() * recall()) / (B*B*precision() + recall());
	}
	public double F1(){ return FMeasure(1.0); }
	public double F2(){ return FMeasure(2.0); }
	public double FHalf(){ return FMeasure(0.5); }
	
	
	public void printDiscrete(){ printDiscrete("Result"); }
	public void printDiscrete(String type){
		if(state != State.DISCRETE){ throw new IllegalStateException("Cannot printDiscrete for non-discrete data"); }
		log(type, "F1: " + F1(), true);
		log(type, "Precision: " + precision(), true);
		log(type, "Recall: " + recall(), true);
		log(type, "Total Examples: " + exCount, true);
	}
}
