package org.goobs.testing;

import java.util.Comparator;
import java.util.ArrayList;

import static org.goobs.exec.Log.*;
import org.goobs.utils.StatCalc;
import org.goobs.utils.Pair;

public class ScoreCalc <T> {
	private static enum State{
		NONE,
		DISCRETE,
		CONTINUOUS,
	}
	
	private State state = State.NONE;
	private boolean streaming = true;
	private int cacheCond = -1;
	
	private int exCount = 0;
	
	/*
	 * DISCRETE
	 */
	// [[precision/recall]
	private int correct;
	private int guessTotal;
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
	// [[continuous]]
	private ArrayList<Double> guesses = null;
	private ArrayList<Double> golds = null;
	// [[spearman]]
	private Double spearmanCache = null;
	
	public ScoreCalc(){
		
	}

	public ScoreCalc<T> setStreaming(boolean shouldStream){
		if(!shouldStream && this.streaming && exCount > 0){
			throw new IllegalStateException("Already started recording!");
		}
		this.streaming = shouldStream;
		this.guesses = new ArrayList<Double>();
		this.golds = new ArrayList<Double>();
		return this;
	}
	
	public void enterDiscrete(T guess, T gold){
		//(state check)
		if(state == State.NONE){ state = State.DISCRETE; }
		if(state != State.DISCRETE){
			throw new IllegalStateException("Mixing DISCRETE and " + state.toString() + " data");
		}
		//(score)
		if(guess == gold){
			correct += 1;
			correct += 1;
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
		//--Non-streaming (e.g. Spearman)
		if(!streaming){
			guesses.add(guess);
			golds.add(gold);
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

	@SuppressWarnings("unchecked")
	public double spearman(){
		if(streaming){ throw new IllegalStateException("Cannot calculate Spearman for streamed data"); }
		if(exCount == cacheCond && spearmanCache != null){ return spearmanCache.doubleValue(); }
		//--Sort Terms
		Pair<Integer,Double>[] guessTmp = (Pair<Integer,Double>[]) new Pair[exCount];
		Pair<Integer,Double>[] goldTmp = (Pair<Integer,Double>[]) new Pair[exCount];
		for(int i=0; i<exCount; i++){
			guessTmp[i] = Pair.make(i,guesses.get(i));
			goldTmp[i]  = Pair.make(i,golds.get(i));
		}
		Comparator<Pair<Integer,Double>> cmp = new Comparator<Pair<Integer,Double>>(){
			public int compare(Pair<Integer,Double> a, Pair<Integer,Double> b){
				return a.cdr().compareTo(b.cdr());
			}
		};
		java.util.Arrays.sort(guessTmp, cmp);
		java.util.Arrays.sort(goldTmp, cmp);

		//--Clean Ties
		double[] guessRanks = new double[exCount];
		double[] goldRanks = new double[exCount];
		//(clean guess)
		int i=0;
		while(i<exCount){
			int guessSum = 0;
			int guessCount = 0;
			int j=0;
			while(i+j<exCount && guessTmp[i+j].cdr().equals(guessTmp[i].cdr())){
				guessSum += (i+j);
				guessCount += 1;
				j += 1;
			}
			double guessVal = ((double) guessSum) / ((double) guessCount);
			for(int k=0; k<j; k++){
				guessRanks[guessTmp[i+k].car()] = guessVal;
			}
			i += j;
		}
		//(clean gold)
		i=0;
		while(i<exCount){
			int goldSum = 0;
			int goldCount = 0;
			int j=0;
			while(i+j<exCount && goldTmp[i+j].cdr().equals(goldTmp[i].cdr())){
				goldSum += (i+j);
				goldCount += 1;
				j += 1;
			}
			double goldVal = ((double) goldSum) / ((double) goldCount);
			for(int k=0; k<j; k++){
				goldRanks[goldTmp[i+k].car()] = goldVal;
			}
			i += j;
		}

		//--Calculate
		ScoreCalc<Double> tmp = new ScoreCalc<Double>();
		for(i=0; i<exCount; i++){
			tmp.enterContinuous(guessRanks[i], goldRanks[i]);
		}
		spearmanCache = tmp.pearson();
		return spearmanCache.doubleValue();
		
	}
	
	/**
		Create a one-to-one matching between guess
		and gold, where each guess is mapped to a gold
		(if possible) independent of position, and each
		gold has at most one matching guess.
		For example, [1,2,1,3] and [1,1,1,2] would match 3/4
	*/
	public void enter(T[] guess, T[] gold){
		//(state check)
		if(state == State.NONE){ state = State.DISCRETE; }
		if(state != State.DISCRETE){
			throw new IllegalStateException("Mixing DISCRETE and " + state.toString() + " data");
		}
		//(update counts)
		boolean[] goldSeen = new boolean[gold.length];
		for(int guessI=0; guessI<guess.length; guessI++){
			for(int goldI=0; goldI<gold.length; goldI++){
				if(!goldSeen[goldI] && guess[guessI].equals(gold[goldI])){
					correct += 1;
					goldSeen[goldI] = true; //don't double-count golds
					continue; //don't double-count guesses
				}
			}
		}
		guessTotal += guess.length;
		goldTotal += gold.length;
	}

	public void enterRaw(int correct, int guessTotal, int goldTotal){
		//(state check)
		if(state == State.NONE){ state = State.DISCRETE; }
		if(state != State.DISCRETE){
			throw new IllegalStateException("Mixing DISCRETE and " + state.toString() + " data");
		}
		//(update data)
		this.correct += correct;
		this.guessTotal += guessTotal;
		this.goldTotal += goldTotal;
	}
	
	public double precision(){
		if(guessTotal == 0){ throw new IllegalStateException("No data points for precision!"); }
		if(state != State.DISCRETE){ throw new IllegalStateException("Precision only defined for discrete data"); }
		return ((double) correct) / ((double) guessTotal);
	}
	public double recall(){
		if(goldTotal == 0){ throw new IllegalStateException("No data points for recall!"); }
		if(state != State.DISCRETE){ throw new IllegalStateException("Precision only defined for discrete data"); }
		return ((double) correct) / ((double) goldTotal);
	}
	public double FMeasure(double B){
		return (1.0+B) * (precision() * recall()) / (B*B*precision() + recall());
	}
	public double F1(){ return FMeasure(1.0); }
	public double F2(){ return FMeasure(2.0); }
	public double FHalf(){ return FMeasure(0.5); }
	
	
	public void printDiscrete(){ printDiscrete("Result"); }
	public void printDiscrete(String type){
		if(state != State.DISCRETE){ throw new IllegalStateException("Cannot printDiscrete for non-discrete (or empty) data"); }
		log(type, "       F1: " + F1(), true);
		log(type, "Precision: " + precision(), true);
		log(type, "   Recall: " + recall(), true);
		log(type, "Data Size: " + exCount, true);
	}

	public void printContinuous(String type){
		if(state != State.CONTINUOUS){ throw new IllegalStateException("Cannot printContinuous for non-continuous (or empty) data"); }
		log(type, " Pearson Correlation: " + pearson(), true);
		log(type, "Spearman Correlation: " + spearman(), true);
		log(type, "           Data Size: " + exCount, true);
		
	}
}
